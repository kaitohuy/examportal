package com.exam.examserver.service.import_export;

import com.exam.examserver.dto.exam.CreateQuestionDTO;
import com.exam.examserver.dto.exam.QuestionDTO;
import com.exam.examserver.dto.importing.*;
import com.exam.examserver.enums.*;
import com.exam.examserver.repo.QuestionRepository;
import com.exam.examserver.service.BundleService;
import com.exam.examserver.service.QuestionMetaService;
import com.exam.examserver.service.QuestionService;
import com.exam.examserver.service.dup.FingerprintService;
import com.exam.examserver.storage.GcsArchiveStorage;
import com.exam.examserver.storage.GcsObjectHelper;
import com.exam.examserver.storage.ImageStorageService;
import com.exam.examserver.util.SplitResult;
import com.exam.examserver.util.TextNormalize;
import com.exam.examserver.util.TextSim;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.exam.examserver.service.import_export.DocxOmmlExtractor.extractPdf;
import static com.exam.examserver.util.ImportRegex.*;

@Service
public class ImportQuestionService {

    private final QuestionService questionService;
    private final ImageStorageService imageStorageService;
    private final ImportPreviewStore previewStore;
    private final GcsArchiveStorage gcsArchiveStorage;
    private final GcsObjectHelper gcsObjectHelper;
    private final FileArchiveService fileArchiveService;
    private final FingerprintService fingerprintService;
    private final QuestionMetaService questionMetaService;
    private final BundleService bundleService;
    // ====== NEW: regex phục vụ footer + điểm ======
    private static final Pattern P_FOOTER =
            Pattern.compile("(?is)\\n?Ghi\\s*chú:.*?(?:\\z|\\n\\s*Họ\\s*tên\\s*SV:.*|\\n\\s*Ký\\s*tên:.*)");
    private static final Pattern P_HEADER_POINTS =
            Pattern.compile("^\\s*C(?:âu|au)\\s*\\d+\\s*[:\\.]?\\s*(?:\\(\\s*(\\d+)\\s*đi(?:ể|e)m\\s*\\))?",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
    private static final Pattern P_POINTS_INLINE =
            Pattern.compile("\\(\\s*\\d+\\s*đi(?:ể|e)m\\s*\\)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static record PreludeCut(String body, String preludeImages) {}

    public ImportQuestionService(QuestionService questionService,
                                 ImageStorageService imageStorageService,
                                 ImportPreviewStore previewStore,
                                 GcsArchiveStorage gcsArchiveStorage,
                                 GcsObjectHelper gcsObjectHelper,
                                 FileArchiveService fileArchiveService, QuestionRepository questionRepo, FingerprintService fingerprintService, QuestionMetaService questionMetaService, BundleService bundleService) {
        this.questionService = questionService;
        this.imageStorageService = imageStorageService;
        this.previewStore = previewStore;
        this.gcsArchiveStorage = gcsArchiveStorage;
        this.gcsObjectHelper = gcsObjectHelper;
        this.fileArchiveService = fileArchiveService;
        this.fingerprintService = fingerprintService;
        this.questionMetaService = questionMetaService;
        this.bundleService = bundleService;
    }

    /* ==================== PREVIEW / COMMIT ==================== */

    public PreviewResponse buildPreview(Long subjectId, MultipartFile file, boolean saveCopy, Set<QuestionLabel> defaultLabels) {
        ExtractResult ext = extractTextAndImages(file);

        String full = TextNormalize.normalizePreserveNewlines(ext.getText());
        full = compactHighlightMarkers(full);
        full = breakChapterInline(full);
        full = breakHeaderAnswerInline(full);

        List<byte[]> images = ext.getImages();

        // 1) CẮT THEO CHƯƠNG
        String[] chapChunks = P_SPLIT_BY_CHAPTER.split(full);

        List<PreviewBlock> blocks = new ArrayList<>();
        int idx = 0;
        Integer currentChapter = null;

        Set<QuestionLabel> def = (defaultLabels == null || defaultLabels.isEmpty())
                ? EnumSet.of(QuestionLabel.PRACTICE) : EnumSet.copyOf(defaultLabels);

        for (String chapRaw : chapChunks) {
            String chap = chapRaw.trim();
            if (chap.isEmpty()) continue;

            Integer ch = findChapterNumber(chap);
            if (ch != null) {
                currentChapter = ch;
                chap = stripChapterHeader(chap);
                chap = removeSectionHeadingLines(chap);
                chap = cutPreludeBeforeFirstQuestion(chap); // <— thêm dòng này
            } else {
                // Preface không có header “Câu …” => bỏ luôn
                Matcher hasQ = P_SPLIT_BY_HEADER.matcher(chap);
                if (!hasQ.find()) continue;
                chap = chap.substring(hasQ.start()).trim();
            }

            // (khuyến nghị) bỏ footer sớm
            chap = stripFooter(chap);

            // 2) Cắt theo header câu hỏi
            String[] qChunks = P_SPLIT_BY_HEADER.split(chap);
            for (String raw : qChunks) {
                String block = raw.trim();
                if (block.isEmpty()) continue;
                PreviewBlock b = parseOneBlockForPreview(block, images);

                // ——— Bộ lọc block rỗng/nhầm tiêu ngữ (xem mục 3 & 4) ———
                if (looksLikeDocHeader(block)) continue; // bỏ block là tiêu ngữ/hành chính

                boolean mcOk = (b.questionType == QuestionType.MULTIPLE_CHOICE)
                        && b.optionA != null && b.optionB != null && b.optionC != null && b.optionD != null;

                boolean hasContent =
                        (b.content != null && !b.content.isBlank())
                                || mcOk
                                || (b.imageIndexes != null && !b.imageIndexes.isEmpty());

                if (!hasContent) continue; // bỏ block rỗng

                idx++;
                b.labels = EnumSet.copyOf(def);
                b.index = idx;
                b.raw = block;
                if (currentChapter != null) b.chapter = currentChapter;

                // "nội dung so giống”
                var probe = (b.questionType == QuestionType.MULTIPLE_CHOICE)
                        ? TextSim.packMultipleChoice(b.content, b.optionA, b.optionB, b.optionC, b.optionD)
                        : b.content;

                var fp = fingerprintService.build(probe);
                var candIds = fingerprintService.candidates(subjectId, fp, 200);

                // Tải minimal DTO theo id (bạn đã có questionService.findByIds(ids))
                var cands = questionService.findByIds(candIds);

                double best = 0.0;
                List<Long> dupIds = new ArrayList<>();

                for (var dto : cands) {
                    // build probe của câu trong DB
                    String otherProbe;
                    if (dto.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
                        otherProbe = TextSim.packMultipleChoice(dto.getContent(), dto.getOptionA(), dto.getOptionB(), dto.getOptionC(), dto.getOptionD());
                    } else {
                        otherProbe = (dto.getContent()==null?"":dto.getContent()) + "\n" + (dto.getAnswerText()==null?"":dto.getAnswerText());
                    }

                    var otherFp = fingerprintService.build(otherProbe);
                    int ham = com.exam.examserver.util.simhash.SimHash64.hamming(fp.simhash(), otherFp.simhash());

                    double score;
                    if (ham <= 3) {
                        score = 0.95; // rất giống
                    } else if (ham <= 6) {
                        // xác nhận thêm cosine (shingle 3-5)
                        score = com.exam.examserver.util.simhash.TfidfCosine.cosine(probe, otherProbe);
                    } else {
                        continue;
                    }

                    if (score >= 0.70) {
                        dupIds.add(dto.getId());
                        best = Math.max(best, score);
                    }
                }

                b.duplicateOfIds = dupIds;
                b.duplicateScore = best;
                if (best >= 0.85) {
                    b.warnings.add("Nghi ngờ trùng câu hỏi (≈ " + Math.round(best*100) + "%).");
                }


                blocks.add(b);
            }
        }

        var session = previewStore.create(images, blocks);

        if (saveCopy) {
            try {
                byte[] raw = file.getBytes();
                String origName = file.getOriginalFilename();
                String contentType = file.getContentType();
                var put = gcsArchiveStorage.putTmp(raw, contentType, (origName == null ? "import.bin" : origName));
                String tempKey = put.storageKey();

                previewStore.attachTempUpload(
                        session.id,
                        tempKey,
                        origName,
                        contentType,
                        raw.length
                );
            } catch (Exception e) {
                System.out.println("lỗi: " + e);
            }
        }

        PreviewResponse resp = new PreviewResponse();
        resp.sessionId = session.id;
        resp.totalBlocks = blocks.size();
        resp.blocks = blocks;
        return resp;
    }

    public ImportResult commitPreview(Long subjectId, Long userId, CommitRequest req, boolean saveCopy) {
        var session = previewStore.get(req.sessionId);
        if (session == null) throw new IllegalArgumentException("Preview session expired or not found");

        final java.math.BigDecimal DEFAULT_POINTS = new java.math.BigDecimal("1.00");

        int total = 0, success = 0;
        List<String> errors = new ArrayList<>();

        Map<Integer, PreviewBlock> base = new HashMap<>();
        for (PreviewBlock b : session.blocks) base.put(b.index, b);

        for (CommitBlock cb : req.blocks) {
            if (!cb.include) continue;
            total++;

            try {
                PreviewBlock orig = base.get(cb.index);
                if (orig == null) throw new IllegalArgumentException("Invalid block index: " + cb.index);

                QuestionType qt = (cb.questionType != null) ? cb.questionType : orig.questionType;

                // ===== COMMON DTO (y như code 10/10)
                CreateQuestionDTO dto = new CreateQuestionDTO();
                dto.setQuestionType(qt);
                dto.setDifficulty(cb.difficulty != null ? cb.difficulty : orig.difficulty);
                dto.setChapter(cb.chapter != 0 ? cb.chapter : orig.chapter);

                String content = firstNonNull(cb.content, orig.content);
                dto.setContent(beautifyMath(sanitizeText(content)));

                if (qt == QuestionType.MULTIPLE_CHOICE) {
                    dto.setOptionA(beautifyMath(sanitizeText(firstNonNull(cb.optionA, orig.optionA))));
                    dto.setOptionB(beautifyMath(sanitizeText(firstNonNull(cb.optionB, orig.optionB))));
                    dto.setOptionC(beautifyMath(sanitizeText(firstNonNull(cb.optionC, orig.optionC))));
                    dto.setOptionD(beautifyMath(sanitizeText(firstNonNull(cb.optionD, orig.optionD))));
                    dto.setAnswer(beautifyMath(sanitizeText(firstNonNull(cb.answer, orig.answer))));
                } else {
                    dto.setAnswerText(beautifyMath(sanitizeText(firstNonNull(cb.answerText, orig.answerText, ""))));
                    dto.setOptionA(null); dto.setOptionB(null); dto.setOptionC(null); dto.setOptionD(null); dto.setAnswer(null);
                }

                Set<QuestionLabel> labels =
                        (cb.labels != null && !cb.labels.isEmpty()) ? cb.labels
                                : (orig.labels != null && !orig.labels.isEmpty()) ? new HashSet<>(orig.labels)
                                : EnumSet.of(QuestionLabel.PRACTICE);
                dto.setLabels(labels);

                // ====== ESSAY: tách theo a)/b)/c) — GIỮ NGUYÊN logic 10/10
                if (qt == QuestionType.ESSAY) {
                    String rawForSplit = firstNonNull(orig.raw, cb.content, orig.content);
                    List<String> parts = splitEssaySubitems(rawForSplit); // đã strip {hl}+NBSP bên trong

                    for (int i = 0; i < parts.size(); i++) {
                        String sample = parts.get(i);
                        String head = sample.substring(0, Math.min(60, sample.length())).replaceAll("\\s+", " ");
                    }

                    if (parts.size() >= 2) {
                        List<Long> createdIds = new ArrayList<>();

                        // Lưu từng sub-item: thêm log chi tiết & bắt lỗi từng ý
                        for (int i = 0; i < parts.size(); i++) {
                            String seg = parts.get(i);
                            try {
                                CreateQuestionDTO sub = new CreateQuestionDTO();
                                sub.setQuestionType(QuestionType.ESSAY);
                                sub.setDifficulty(dto.getDifficulty());
                                sub.setChapter(dto.getChapter());
                                sub.setContent(beautifyMath(sanitizeText(seg)));
                                sub.setAnswerText("");
                                sub.setLabels(dto.getLabels());

                                QuestionDTO subSaved = questionService.create(subjectId, sub, userId, null);
                                Long subId = subSaved.getId();
                                createdIds.add(subId);

                                // Ảnh (như cũ)
                                List<Integer> imgIdxs = (cb.imageIndexes != null && !cb.imageIndexes.isEmpty())
                                        ? cb.imageIndexes : orig.imageIndexes;
                                if (imgIdxs != null && !imgIdxs.isEmpty()) {
                                    List<String> urls = new ArrayList<>();
                                    for (Integer ix : imgIdxs) {
                                        if (ix == null || ix < 0 || ix >= session.images.size()) continue;
                                        byte[] bytes = session.images.get(ix);
                                        String url = imageStorageService.storeImage(bytes, subId, "imported.png", null);
                                        urls.add(url);
                                    }
                                    if (!urls.isEmpty()) {
                                        questionService.addImages(subId, urls);
                                        System.out.printf("[IMPORT]   sub[%d/%d] images attached: %d url(s)%n",
                                                i+1, parts.size(), urls.size());
                                    }
                                }
                                String sourceForType = firstNonNull(orig.raw, cb.content, orig.content);
                                String typeCode = extractNumericTypeCode(sourceForType);
                                ItemNature nature = (i == 0 ? ItemNature.THEORY : ItemNature.EXERCISE);
                                // Meta cho sub
                                var meta = questionMetaService.upsertDefault(
                                        subId,
                                        UnitKind.SUB_ITEM,
                                        DEFAULT_POINTS,
                                        sub.getChapter(),
                                        null,
                                        RecordStatus.APPROVED,
                                        null,
                                        typeCode,
                                        nature
                                );
                                success++; // tính riêng từng sub
                            } catch (Exception subEx) {
                                String msg = String.format("Block#%d sub[%d] ERROR: %s", cb.index, i+1, subEx.getMessage());
                                System.out.println("[IMPORT]   " + msg);
                                errors.add(msg);
                                // tiếp tục sub tiếp theo
                            }
                        }

                        // Bundle nếu có ≥2 sub
                        if (createdIds.size() >= 2) {
                            try {
                                List<BundleService.CreateItem> items = new ArrayList<>();
                                for (int i = 0; i < createdIds.size(); i++) {
                                    items.add(new BundleService.CreateItem(
                                            createdIds.get(i),
                                            i + 1,      // order
                                            null,       // points: dùng meta của từng sub
                                            "auto-split"
                                    ));
                                }
                                String bundleTitle = "Câu " + cb.index;
                                SplitResult sr = splitEssaySubitemsWithStem(rawForSplit);
                                String stemRaw = sr.stem();
                                String stemClean = normalizeStem(stemRaw);  // <— DÙNG LẠI

                                bundleService.create(
                                        subjectId,
                                        userId,
                                        bundleTitle,
                                        stemClean,
                                        items,
                                        null,
                                        RecordStatus.DRAFT
                                );
                            } catch (Exception bex) {
                                String msg = String.format("Block#%d bundle ERROR: %s", cb.index, bex.getMessage());
                                System.out.println("[IMPORT] " + msg);
                                errors.add(msg);
                            }
                        } else {
                            System.out.printf("[IMPORT] Block#%d bundle SKIPPED (createdIds=%d)%n", cb.index, createdIds.size());
                        }

                        // xong block đã tách
                        continue;
                    }
                    // nếu không tách được -> xuống nhánh đơn
                }

                // ====== LƯU 1 CÂU (MCQ/ESSAY không có a)/b)) — thêm meta
                System.out.printf("[IMPORT] Block#%d SAVE single question...%n", cb.index);
                QuestionDTO saved = questionService.create(subjectId, dto, userId, null);
                Long qId = saved.getId();
                System.out.printf("[IMPORT] Block#%d single OK questionId=%d%n", cb.index, qId);

                // Ảnh (như cũ)
                List<Integer> imgIdxs = (cb.imageIndexes != null && !cb.imageIndexes.isEmpty())
                        ? cb.imageIndexes : orig.imageIndexes;
                if (imgIdxs != null && !imgIdxs.isEmpty()) {
                    List<String> urls = new ArrayList<>();
                    for (Integer ix : imgIdxs) {
                        if (ix == null || ix < 0 || ix >= session.images.size()) continue;
                        byte[] bytes = session.images.get(ix);
                        String url = imageStorageService.storeImage(bytes, qId, "imported.png", null);
                        urls.add(url);
                    }
                    if (!urls.isEmpty()) {
                        questionService.addImages(qId, urls);
                        System.out.printf("[IMPORT] Block#%d single images attached: %d url(s)%n", cb.index, urls.size());
                    }
                }

                String sourceForType = firstNonNull(orig.raw, cb.content, orig.content);
                String typeCode = extractNumericTypeCode(sourceForType);

                // Meta cho câu đơn
                var meta = questionMetaService.upsertDefault(
                        qId,
                        UnitKind.FULL_QUESTION,
                        DEFAULT_POINTS,
                        dto.getChapter(),
                        null,
                        RecordStatus.DRAFT,
                        null,
                        typeCode
                );
                System.out.printf("[IMPORT] Block#%d single meta saved: questionId=%d, unit=%s, points=%s%n",
                        cb.index, meta.getQuestionId(), meta.getUnitKind(), meta.getPoints());

                success++;
            } catch (Exception ex) {
                String msg = "Block#" + cb.index + " ERROR: " + ex.getMessage();
                System.out.println("[IMPORT] " + msg);
                errors.add(msg);
            }
        }

        if (saveCopy) {
            try {
                var temp = previewStore.getTempUpload(req.sessionId);
                if (temp != null && temp.key() != null && !temp.key().isBlank()) {
                    String originalName = (temp.originalName() == null ? "import.bin" : temp.originalName());
                    String finalKey = "archives/" + UUID.randomUUID() + "_" + originalName;

                    gcsObjectHelper.copyAndDelete(temp.key(), finalKey);

                    Map<String, Object> meta = new HashMap<>();
                    meta.put("sessionId", req.sessionId);
                    meta.put("blocksRequested", (req.blocks == null ? 0 : req.blocks.size()));
                    meta.put("totalCommitted", success);
                    meta.put("errorsCount", errors.size());
                    meta.put("source", "preview-upload");

                    fileArchiveService.saveExistingByKey(
                            "IMPORT",
                            subjectId,
                            userId,
                            originalName,
                            temp.contentType(),
                            finalKey,
                            meta
                    );

                    previewStore.clearTempUpload(req.sessionId);
                    System.out.printf("[IMPORT] Saved source file copy: %s -> %s%n", originalName, finalKey);
                }
            } catch (Exception ignore) {}
        }

        System.out.printf("[IMPORT] COMMIT done: blocksProcessed=%d, successSaved=%d, errors=%d%n",
                total, success, errors.size());

        return new ImportResult(total, success, errors);
    }

    // Bóc tag highlight + normalize NBSP trước khi tách
    private static String stripHlAndNormalize(String s) {
        if (s == null) return null;
        return s.replace("{hl}", "")
                .replace("{/hl}", "")
                .replace('\u00A0', ' '); // NBSP -> space
    }

    private List<String> splitEssaySubitems(String source) {
        if (source == null) return List.of();

        // 1) bóc {hl} trước khi split
        String text = stripHlAndNormalize(source);

        // 2) bỏ dòng header "Câu n ..."
        text = P_HEADER_LINE.matcher(text).replaceFirst("");

        // 3) tìm các đầu mục a)/b)/...
        Matcher m = P_SUBITEM_HEADER.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (m.find()) starts.add(m.start());
        if (starts.size() < 2) return List.of(); // không có ≥2 ý => không tách

        // 4) cắt theo spans
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = (i + 1 < starts.size()) ? starts.get(i + 1) : text.length();
            String seg = text.substring(from, to);

            // bỏ tiền tố a)/a./a:/ (a)
            seg = P_SUBITEM_HEADER.matcher(seg).replaceFirst("");
            seg = seg.trim();
            if (!seg.isBlank()) parts.add(seg);
        }
        return parts;
    }

    private boolean looksLikeDocHeader(String s) {
        if (s == null) return false;
        // chỉ xét vài dòng đầu để tránh “ăn” nhầm nội dung thật
        StringBuilder head = new StringBuilder();
        int lines = 0;
        for (String ln : s.split("\\R", -1)) {
            if (lines++ >= 6) break;
            head.append(ln).append('\n');
        }
        return P_DOC_HEADER_HINT.matcher(head.toString()).find();
    }

    private PreviewBlock parseOneBlockForPreview(String rawBlock, List<byte[]> allImages) {
        PreviewBlock b = new PreviewBlock();

        // A) Đọc điểm từ header để set Difficulty
        Integer pts = null;
        Matcher headPt = P_HEADER_POINTS.matcher(rawBlock);
        if (headPt.find()) {
            String g = headPt.group(1);
            if (g != null) try { pts = Integer.parseInt(g); } catch (Exception ignore) {}
        }

        // B) cắt nhãn Answer (chưa phân loại)
        Matcher ansM = P_ANSWER_LABEL.matcher(rawBlock);
        String block = rawBlock;
        String pendingAnswer = null;
        if (ansM.find()) {
            pendingAnswer = beautifyMath(sanitizeText(stripInlineMarkers(ansM.group(2).trim())));
            block = block.substring(0, ansM.start()).trim();
        }

        // C) bỏ header -> body
        String body = stripHeader(block);

        // D) DÒ option chỉ để PHÂN LOẠI
        String bodyForDetect = breakOptionsInline(body);
        Matcher detectM = P_OPT_EXTRACT.matcher(bodyForDetect);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        int firstOptStart = -1;
        while (detectM.find()) {
            if (firstOptStart < 0) firstOptStart = detectM.start();
            keys.add(detectM.group(1).toUpperCase(Locale.ROOT));
        }
        boolean isMC = (keys.size() == 4);

        b.questionType = isMC ? QuestionType.MULTIPLE_CHOICE : QuestionType.ESSAY;
        b.difficulty = mapPoints(pts); // đặt theo điểm (mặc định C nếu null)

        // E) gán Answer đúng field
        if (pendingAnswer != null) {
            if (isMC) b.answer = pendingAnswer.toUpperCase(Locale.ROOT);
            else      b.answerText = pendingAnswer;
        }

        // F) Parse theo loại
        if (isMC) {
            List<String> emph = new ArrayList<>();
            Matcher optM = P_OPT_EXTRACT.matcher(bodyForDetect);
            while (optM.find()) {
                String whole  = optM.group(0);
                String key    = optM.group(1).toUpperCase(Locale.ROOT);
                String rawVal = sanitizeText(optM.group(2).trim());

                boolean highlighted = whole.contains("{hl}") || rawVal.contains("{hl}") || rawVal.contains("{/hl}");
                String val = beautifyMath(stripInlineMarkers(removePointsInline(rawVal)).trim());

                switch (key) {
                    case "A": b.optionA = val; if (highlighted) emph.add("A"); break;
                    case "B": b.optionB = val; if (highlighted) emph.add("B"); break;
                    case "C": b.optionC = val; if (highlighted) emph.add("C"); break;
                    case "D": b.optionD = val; if (highlighted) emph.add("D"); break;
                }
            }
            if ((b.answer == null || b.answer.isBlank()) && !emph.isEmpty()) {
                b.answer = String.join("", emph).toUpperCase(Locale.ROOT);
            }

            String stem = (firstOptStart >= 0) ? bodyForDetect.substring(0, firstOptStart).trim() : body.trim();
            String stemClean = removeAllImagePlaceholders(stripInlineMarkers(removePointsInline(stem)));
            stemClean = collapseSoftBreaks(stemClean);
            stemClean = enforceInlineListBreaks(stemClean);
            b.content = beautifyMath(sanitizeText(stemClean));

        } else {
            String cont = removeAllImagePlaceholders(stripHl(removePointsInline(body)));
            cont = collapseSoftBreaks(cont);
            cont = enforceInlineListBreaks(cont);
            b.content = beautifyMath(sanitizeText(cont));
            if (b.answerText == null) b.answerText = "";
            else b.answerText = beautifyMath(sanitizeText(enforceInlineListBreaks(collapseSoftBreaks(b.answerText))));

        }

        // Ảnh → imageIndexes (từ body sau khi đã gắn placeholder ở PDF)
        Matcher imgM = P_IMAGE_PLACEHOLDER.matcher(body);
        while (imgM.find()) {
            int idx = safeIndex(imgM.group(1));
            if (idx >= 0 && idx < allImages.size()) b.imageIndexes.add(idx);
        }

        // Footer guard
        b.content = stripFooter(b.content);
        if (b.answerText != null) b.answerText = stripFooter(b.answerText);

        if (isMC) {
            if (b.optionA == null || b.optionB == null || b.optionC == null || b.optionD == null)
                b.warnings.add("Thiếu option A/B/C/D.");
        } else if (b.content == null || b.content.isBlank()) {
            b.warnings.add("Nội dung trống.");
        }

        if (b.content != null) b.content = stripHl(b.content);
        return b;
    }

    /* ==================== helpers ==================== */

    private int safeIndex(String oneBased) { try { return Integer.parseInt(oneBased) - 1; } catch (Exception e) { return -1; } }

    private String removeAllImagePlaceholders(String text) { return P_IMAGE_PLACEHOLDER.matcher(text).replaceAll("").trim(); }

    private String removeSectionHeadingLines(String text) {
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\\R")) {
            String s = sanitizeText(line).trim().toLowerCase(Locale.ROOT);
            boolean isHeading = s.matches("^(chương|chuong|chapter|mục|muc|phần|phan|bài|bai|câu\\s*hỏi\\s*loại)\\b.*$");
            if (!isHeading) out.append(line).append('\n');
        }
        return out.toString();
    }

    // ImportService (hoặc ImportRegex helper)
    private SplitResult splitEssaySubitemsWithStem(String source) {
        if (source == null) return new SplitResult(null, List.of());

        // bóc {hl}, normalize y như cũ
        String text = stripHlAndNormalize(source);
        // bỏ dòng "Câu n ..."
        text = P_HEADER_LINE.matcher(text).replaceFirst("");

        // tìm vị trí các đầu mục a)/b)/...
        Matcher m = P_SUBITEM_HEADER.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (m.find()) starts.add(m.start());

        if (starts.size() < 2) return new SplitResult(null, List.of()); // không đủ để tạo bundle

        // stem = phần trước ý đầu tiên
        String stem = text.substring(0, starts.get(0)).trim();
        // cắt từng ý như cũ
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = (i + 1 < starts.size()) ? starts.get(i + 1) : text.length();
            String seg = text.substring(from, to);
            seg = P_SUBITEM_HEADER.matcher(seg).replaceFirst("").trim();
            if (!seg.isBlank()) parts.add(seg);
        }
        if (stem != null && stem.isBlank()) stem = null;
        return new SplitResult(stem, parts);
    }

    private String cutPreludeBeforeFirstQuestion(String fullText) {
        Matcher m = P_SPLIT_BY_HEADER.matcher(fullText);
        return m.find() ? fullText.substring(m.start()).trim() : fullText;
    }

    private String stripFooter(String s) { return s == null ? null : P_FOOTER.matcher(s).replaceAll("").trim(); }

    private String sanitizeText(String s) { return TextNormalize.normalizeSoftMath(s); }

    private String beautifyMath(String s) {
        if (s == null) return null;
        String out = s;
        out = out.replace("∞", "\\infty")
                .replace("¥", "\\infty");;
        out = convertPipeMatrixToLatex(out);
        // 1) Bọc danh sách số sau \in hoặc ∈ thành \{ … \}
        out = out.replaceAll(
                "(?<=\\\\in)\\s*(?![\\[{(])([0-9]+(?:\\s*[,;]\\s*[0-9]+)+)",
                " \\\\{$1\\\\}"
        );
        out = out.replaceAll(
                "(?<=∈)\\s*(?![\\[{(])([0-9]+(?:\\s*[,;]\\s*[0-9]+)+)",
                " \\\\{$1\\\\}"
        );

        // 2) Bọc các token LaTeX “trần” (x^{2}, a_{i}, …) CHỈ ở ngoài các khối toán
        out = TextNormalize.wrapBareInlineMath(out);  // <-- thêm dòng này
        // 2b) Gỡ sub/sup rỗng (nếu còn lạc)
        out = out.replaceAll("(?<!\\\\)_\\{\\s*\\}", "");
        out = out.replaceAll("(?<!\\\\)\\^\\{\\s*\\}", "");

        // 3) Làm sạch nhẹ
        out = out.replace('−','-').replaceAll("\\s{2,}", " ").trim();
        return out;
    }

    private String firstNonNull(String... candidates) { for (String c : candidates) if (c != null) return c; return null; }

    private static String stripHl(String s) { return s == null ? null : s.replace("{hl}", "").replace("{/hl}", ""); }

    private String removePointsInline(String s) { return s == null ? null : P_POINTS_INLINE.matcher(s).replaceAll("").trim(); }

    private Difficulty mapPoints(Integer pts) {
        if (pts == null) return Difficulty.C;
        return switch (pts) {
            case 1 -> Difficulty.E;
            case 2 -> Difficulty.D;
            case 3 -> Difficulty.C;
            case 4 -> Difficulty.B;
            case 5 -> Difficulty.A;
            default -> Difficulty.C;
        };
    }

    /* ==================== extract DOCX/PDF ==================== */

    private ExtractResult extractTextAndImages(MultipartFile file) {
        String name = (file.getOriginalFilename()==null ? "upload" : file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        try (InputStream is = file.getInputStream()) {
            if (name.endsWith(".docx")) {
                try { return DocxOmmlExtractor.extractWord(is); }
                catch (Exception e) { throw new RuntimeException("DOCX/OMML extract failed", e); }
            } else if (name.endsWith(".pdf")) {
                return extractPdf(is);
            } else {
                throw new IllegalArgumentException("Unsupported file type (only .docx/.pdf)");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }

    // Thêm vào class:
    private static final Pattern P_MATH_CONT_END =
            Pattern.compile("[,;:=><≤≥→\\-+*/)\\]]\\s*$");
    private static final Pattern P_MATH_CONT_START =
            Pattern.compile("^(?:\\d|[a-zA-Z]|\\\\\\(|\\(|max\\b|min\\b)");

    private static boolean shouldHardBreakForMath(String prevTrim, String currTrim) {
        if (prevTrim == null || currTrim == null) return false;
        // ví dụ: "… → max,"  +  "7x1 + 2x2 …"
        if (P_MATH_CONT_END.matcher(prevTrim).find() && P_MATH_CONT_START.matcher(currTrim).find()) return true;

        // thêm vài ca phổ biến của ràng buộc/điều kiện
        boolean prevHasObjective = prevTrim.matches("(?i).*(max|min)\\s*,?\\s*$");
        boolean currLooksConstraint = currTrim.matches(".*(=|≤|≥|<=|>=)\\s*\\d+.*");
        if (prevHasObjective && currLooksConstraint) return true;

        // nếu cả 2 dòng đều “toán tính” (tỉ lệ kí tự toán cao) thì cũng nên xuống dòng
        String mathChars = "+\\-*/=<>≤≥(),;:\\\\^_\\d";
        double prevRatio = prevTrim.replaceAll("[^" + mathChars + "a-zA-Z]", "").length() / (double)Math.max(1, prevTrim.length());
        double currRatio = currTrim.replaceAll("[^" + mathChars + "a-zA-Z]", "").length() / (double)Math.max(1, currTrim.length());
        return prevRatio > 0.35 && currRatio > 0.35; // ngưỡng nhẹ
    }

    private String collapseSoftBreaks(String s) {
        if (s == null) return null;
        String[] lines = s.split("\\R");
        StringBuilder out = new StringBuilder();
        boolean first = true;
        boolean lastBlank = false;
        String prevTrim = null;

        for (String line : lines) {
            String t = line.trim();
            boolean blank = t.isEmpty();
            boolean bullet = !blank && P_BULLET_LINE.matcher(t).matches();
            boolean tableLike = t.contains("|");

            if (blank) {
                if (!lastBlank && out.length() > 0) out.append("\n\n");
                lastBlank = true;
                prevTrim = null;
                continue;
            }

            if (first) {
                out.append(t);
            } else if (bullet || lastBlank || tableLike || shouldHardBreakForMath(prevTrim, t)) {
                out.append('\n').append(t);           // ↓ xuống dòng cứng
            } else {
                int L = out.length();
                if (L > 0 && out.charAt(L - 1) == '-') {
                    out.setLength(L - 1);             // nối chữ bị ngắt bằng gạch nối cuối dòng
                    out.append(t);
                } else {
                    out.append(' ').append(t);        // ghép mềm mặc định
                }
            }
            first = false;
            lastBlank = false;
            prevTrim = t;
        }
        return out.toString().replaceAll("\\s{2,}", " ").trim();
    }

    private String enforceInlineListBreaks(String s) {
        if (s == null) return null;
        // ...". a) ..." -> "\n a) ..."
        s = s.replaceAll("(?<=\\.|\\?|!|:)\\s+([a-dA-D][\\)\\.])\\s+", "\n$1 ");
        return s;
    }

    // Nhận một đoạn có dạng:  a | b | c  ... (toàn số hoặc ∞/¥)
// Nếu tổng số token là perfect square (N×N) thì biến thành $$\begin{pmatrix}...\end{pmatrix}$$
    private static final Pattern P_PIPE_MATRIX_CHUNK =
            Pattern.compile("(?:\\\\infty|∞|¥|\\d+)(?:\\s*\\|\\s*(?:\\\\infty|∞|¥|\\d+))+(?:\\s+(?:\\\\infty|∞|¥|\\d+)(?:\\s*\\|\\s*(?:\\\\infty|∞|¥|\\d+))+)*");

    private String convertPipeMatrixToLatex(String text) {
        if (text == null || text.isBlank()) return text;
        Matcher m = P_PIPE_MATRIX_CHUNK.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String chunk = m.group();

            // Lấy toàn bộ token theo thứ tự
            java.util.List<String> toks = new java.util.ArrayList<>();
            Matcher t = Pattern.compile("(\\\\infty|∞|¥|\\d+)").matcher(chunk);
            while (t.find()) toks.add(t.group());

            int K = toks.size();
            int n = (int)Math.round(Math.sqrt(K));

            if (n*n == K && n >= 2 && n <= 100) {
                StringBuilder mat = new StringBuilder();
                mat.append("$$\\begin{pmatrix}\n");
                for (int i = 0; i < K; i++) {
                    String v = toks.get(i)
                            .replace("¥", "\\infty")
                            .replace("∞", "\\infty");
                    mat.append(v);
                    if (i % n != n - 1) mat.append(" & ");
                    else mat.append("\\\\\n");
                }
                mat.append("\\end{pmatrix}$$");
                m.appendReplacement(sb, Matcher.quoteReplacement(mat.toString()));
            } else {
                // không chắc là ma trận -> để nguyên
                m.appendReplacement(sb, Matcher.quoteReplacement(chunk));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

}
