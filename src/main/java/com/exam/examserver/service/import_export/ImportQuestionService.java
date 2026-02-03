package com.exam.examserver.service.import_export;

import com.exam.examserver.dto.exam.CreateQuestionDTO;
import com.exam.examserver.dto.exam.QuestionDTO;
import com.exam.examserver.dto.importing.*;
import com.exam.examserver.enums.*;
import com.exam.examserver.model.exam.Question;
import com.exam.examserver.model.exam.QuestionBundle;
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
    private final QuestionRepository questionRepository;
    private final ImageStorageService imageStorageService;
    private final ImportPreviewStore previewStore;
    private final GcsArchiveStorage gcsArchiveStorage;
    private final GcsObjectHelper gcsObjectHelper;
    private final FileArchiveService fileArchiveService;
    private final FingerprintService fingerprintService;
    private final QuestionMetaService questionMetaService;
    private final BundleService bundleService;
    private final Map<String, AnswerImportSession> answerImportSessions = new java.util.concurrent.ConcurrentHashMap<>();
    // ====== NEW: regex phục vụ footer + điểm ======
    private static final Pattern P_FOOTER = Pattern
            .compile("(?is)\\n?Ghi\\s*chú:.*?(?:\\z|\\n\\s*Họ\\s*tên\\s*SV:.*|\\n\\s*Ký\\s*tên:.*)");
    private static final Pattern P_HEADER_POINTS = Pattern.compile(
            "^\\s*C(?:âu|au)\\s*\\d+\\s*[:\\.]?\\s*(?:\\(\\s*(\\d+)\\s*đi(?:ể|e)m\\s*\\))?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
    private static final Pattern P_POINTS_INLINE = Pattern.compile("\\(\\s*\\d+\\s*đi(?:ể|e)m\\s*\\)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    // Dòng bắt đầu bằng "Đáp án:", "Giải thích:", "Lời giải" ...
    private static final Pattern P_ANSWER_LINE = Pattern.compile(
            "(?iu)^(đáp\\s*án|giải\\s*thích|lời\\s*giải)\\s*[:：].*$",
            Pattern.MULTILINE);

    private static record PreludeCut(String body, String preludeImages) {
    }

    public ImportQuestionService(QuestionService questionService, QuestionRepository questionRepository,
            ImageStorageService imageStorageService,
            ImportPreviewStore previewStore,
            GcsArchiveStorage gcsArchiveStorage,
            GcsObjectHelper gcsObjectHelper,
            FileArchiveService fileArchiveService, QuestionRepository questionRepo,
            FingerprintService fingerprintService, QuestionMetaService questionMetaService,
            BundleService bundleService) {
        this.questionService = questionService;
        this.questionRepository = questionRepository;
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

    public PreviewResponse buildPreview(Long subjectId, MultipartFile file, boolean saveCopy,
            Set<QuestionLabel> defaultLabels) {
        ExtractResult ext = extractTextAndImages(file);

        String full = TextNormalize.normalizePreserveNewlines(ext.getText());
        full = compactHighlightMarkers(full);
        full = breakChapterInline(full);
        full = breakHeaderAnswerInline(full);

        List<byte[]> images = ext.getImages();
        String[] chapChunks = P_SPLIT_BY_CHAPTER.split(full);

        List<PreviewBlock> blocks = new ArrayList<>();
        int idx = 0;
        Integer currentChapter = null;

        Set<QuestionLabel> def = (defaultLabels == null || defaultLabels.isEmpty())
                ? EnumSet.of(QuestionLabel.PRACTICE)
                : EnumSet.copyOf(defaultLabels);

        for (String chapRaw : chapChunks) {
            String chap = chapRaw.trim();
            if (chap.isEmpty())
                continue;

            Integer ch = findChapterNumber(chap);
            if (ch != null) {
                currentChapter = ch;
                chap = stripChapterHeader(chap);
                chap = removeSectionHeadingLines(chap);
                chap = cutPreludeBeforeFirstQuestion(chap);
            } else {
                Matcher hasQ = P_SPLIT_BY_HEADER.matcher(chap);
                if (!hasQ.find())
                    continue;
                chap = chap.substring(hasQ.start()).trim();
            }

            chap = stripFooter(chap);

            String[] qChunks = P_SPLIT_BY_HEADER.split(chap);
            for (String raw : qChunks) {
                String block = raw.trim();
                if (block.isEmpty())
                    continue;

                PreviewBlock b = parseOneBlockForPreview(block, images);

                // đoán type nếu parser chưa set
                if (b.questionType == null) {
                    boolean looksMC = b.optionA != null && !b.optionA.isBlank() &&
                            b.optionB != null && !b.optionB.isBlank() &&
                            b.optionC != null && !b.optionC.isBlank() &&
                            b.optionD != null && !b.optionD.isBlank();
                    b.questionType = looksMC ? QuestionType.MULTIPLE_CHOICE : QuestionType.ESSAY;
                }

                if (looksLikeDocHeader(block))
                    continue;

                boolean mcOk = (b.questionType == QuestionType.MULTIPLE_CHOICE)
                        && b.optionA != null && b.optionB != null && b.optionC != null && b.optionD != null;

                boolean hasContent = (b.content != null && !b.content.isBlank())
                        || mcOk || (b.imageIndexes != null && !b.imageIndexes.isEmpty());
                if (!hasContent)
                    continue;

                idx++;
                b.index = idx;
                b.labels = EnumSet.copyOf(def);
                b.raw = block;
                if (currentChapter != null)
                    b.chapter = currentChapter;

                // === MÃ XEM TRƯỚC THEO TYPE (KHÔNG DÙNG INDEX) ===
                String typeCode = extractNumericTypeCode(block); // ví dụ: "1.1", "2.1.1"
                b.headerNo = typeCode;
                String prefix = choosePrefix(b.labels); // OT | TC
                b.previewPrefix = prefix;

                if (typeCode != null) {
                    if (b.questionType == QuestionType.ESSAY) {
                        List<String> parts = splitEssaySubitems(firstNonNull(b.raw, b.content));
                        if (parts != null && parts.size() >= 2) {
                            b.previewSubCodes = new ArrayList<>();
                            for (int i = 0; i < parts.size(); i++) {
                                b.previewSubCodes.add(buildCodeFromType(prefix, typeCode, i + 1)); // TC2.1.1.a)
                            }
                        } else {
                            b.previewCode = buildCodeFromType(prefix, typeCode, null); // TC2.1.1
                        }
                    } else {
                        b.previewCode = buildCodeFromType(prefix, typeCode, null); // MCQ: TC2.1
                    }
                }

                // === Dự đoán mã clone nếu user có "(Mã: ...)" ===
                try {
                    String sourceRaw = firstNonNull(b.raw, b.content);
                    String declared = extractDeclaredCode(sourceRaw);
                    if (declared != null && !declared.isBlank()) {
                        b.declaredCode = declared.trim();

                        // parseCloneCode(declared) -> (desiredIdx, baseCode)
                        var pr = parseCloneCode(declared);
                        Integer desiredIdx = pr.getKey();
                        String baseCode = pr.getValue(); // ví dụ "OT2.1.1"
                        b.cloneBaseCode = baseCode;
                        b.cloneDesiredIndex = desiredIdx;

                        if (baseCode != null && !baseCode.isBlank()) {
                            Long parentId = questionService.findParentIdByCode(subjectId, baseCode).orElse(null);
                            if (parentId != null) {
                                int max = questionRepository.findMaxCloneIndexByParentId(parentId);
                                int nextIdx;
                                if (desiredIdx == null) {
                                    nextIdx = max + 1;
                                } else {
                                    nextIdx = desiredIdx;
                                    // nhảy qua index đã tồn tại
                                    while (questionRepository.existsBySubjectIdAndQuestionCodeIgnoreCase(
                                            subjectId, "C" + nextIdx + "." + baseCode)) {
                                        nextIdx++;
                                    }
                                }
                                b.cloneNextIndex = nextIdx;
                                b.clonePreviewCode = "C" + nextIdx + "." + baseCode; // ← FE sẽ hiển thị
                            } else {
                                b.warnings.add("Không tìm thấy câu gốc theo mã: " + baseCode);
                            }
                        }
                    }
                } catch (Exception ignore) {
                }

                // ===== BUNDLE-LEVEL duplicate check (điểm thật) =====
                try {
                    var sr = splitEssaySubitemsWithStem(firstNonNull(b.raw, b.content));
                    if (sr.parts() != null && sr.parts().size() >= 2) {
                        // Chuẩn hoá stem giống commit
                        String stemClean = normalizeStem(sr.stem());

                        // Probe + FP cho bundle preview
                        String bProbe = fingerprintService.buildBundleProbe(stemClean, sr.parts());
                        var bfp = fingerprintService.build(bProbe);

                        // Lấy ứng viên qua LSH + chấm điểm thật (Hamming/TF-IDF)
                        Map<Long, Double> scored = fingerprintService.scoreBundleCandidates(subjectId, bProbe, bfp,
                                200);

                        if (scored != null && !scored.isEmpty()) {
                            // sắp xếp theo score giảm dần
                            var sorted = scored.entrySet().stream()
                                    .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                                    .toList();

                            double bestB = sorted.get(0).getValue();
                            List<Long> dupB = sorted.stream()
                                    .filter(e -> e.getValue() >= 0.70) // ngưỡng nghi trùng
                                    .map(Map.Entry::getKey)
                                    .toList();

                            b.duplicateBundleIds = new ArrayList<>(dupB);
                            b.duplicateBundleScore = bestB;

                            if (bestB >= 0.85) {
                                b.warnings.add("Nghi ngờ trùng khối câu hỏi hiện có.");
                            }
                        } else {
                            b.duplicateBundleIds = java.util.Collections.emptyList();
                            b.duplicateBundleScore = null;
                        }
                    }
                } catch (Exception ignore) {
                    // Không chặn preview nếu check bundle lỗi
                }

                // ===== duplicate-check câu đơn (giữ nguyên, nhưng probe gồm content +
                // answerText cho ESSAY) =====
                String probe = (b.questionType == QuestionType.MULTIPLE_CHOICE)
                        ? TextSim.packMultipleChoice(b.content, b.optionA, b.optionB, b.optionC, b.optionD)
                        : ((b.content == null ? "" : b.content) + "\n" + (b.answerText == null ? "" : b.answerText));
                var fp = fingerprintService.build(probe);
                var candIds = fingerprintService.candidates(subjectId, fp, 200);
                var cands = questionService.findByIds(candIds);

                double best = 0.0;
                List<Long> dupIds = new ArrayList<>();
                for (var dto : cands) {
                    String otherProbe = (dto.getQuestionType() == QuestionType.MULTIPLE_CHOICE)
                            ? TextSim.packMultipleChoice(dto.getContent(), dto.getOptionA(), dto.getOptionB(),
                                    dto.getOptionC(), dto.getOptionD())
                            : ((dto.getContent() == null ? "" : dto.getContent()) + "\n"
                                    + (dto.getAnswerText() == null ? "" : dto.getAnswerText()));
                    var otherFp = fingerprintService.build(otherProbe);
                    int ham = com.exam.examserver.util.simhash.SimHash64.hamming(fp.simhash(), otherFp.simhash());
                    double score = (ham <= 3) ? 0.95
                            : (ham <= 6 ? com.exam.examserver.util.simhash.TfidfCosine.cosine(probe, otherProbe) : 0.0);
                    if (score >= 0.70) {
                        dupIds.add(dto.getId());
                        best = Math.max(best, score);
                    }
                }
                b.duplicateOfIds = dupIds;
                b.duplicateScore = best;
                if (best >= 0.85)
                    b.warnings.add("Nghi ngờ trùng câu hỏi (≈ " + Math.round(best * 100) + "%).");

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
                previewStore.attachTempUpload(session.id, put.storageKey(), origName, contentType, raw.length);
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

    // ImportQuestionService.java
    public ImportResult commitPreview(Long subjectId, Long userId, CommitRequest req, boolean saveCopy,
            ArchiveVariant variant) {
        var session = previewStore.get(req.sessionId);
        if (session == null)
            throw new IllegalArgumentException("Preview session expired or not found");

        final java.math.BigDecimal DEFAULT_POINTS = new java.math.BigDecimal("1.00");

        int total = 0, success = 0;
        List<String> errors = new ArrayList<>();

        Map<Integer, PreviewBlock> base = new HashMap<>();
        for (PreviewBlock b : session.blocks)
            base.put(b.index, b);

        for (CommitBlock cb : req.blocks) {
            if (!cb.include)
                continue;
            total++;

            try {
                PreviewBlock orig = base.get(cb.index);
                if (orig == null)
                    throw new IllegalArgumentException("Invalid block index: " + cb.index);

                QuestionType qt = (cb.questionType != null) ? cb.questionType : orig.questionType;

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
                    dto.setOptionA(null);
                    dto.setOptionB(null);
                    dto.setOptionC(null);
                    dto.setOptionD(null);
                    dto.setAnswer(null);
                }

                Set<QuestionLabel> labels = (cb.labels != null && !cb.labels.isEmpty()) ? cb.labels
                        : (orig.labels != null && !orig.labels.isEmpty()) ? new HashSet<>(orig.labels)
                                : EnumSet.of(QuestionLabel.PRACTICE);
                dto.setLabels(labels);

                // === Nguồn để bắt TYPE/CODE; cắt “Mã: …” trước khi tách số/ý
                String sourceRaw = firstNonNull(orig.raw, cb.content, orig.content);
                String declared = extractDeclaredCode(sourceRaw); // để xử lý clone
                String sourceClean = stripCodeDeclaration(sourceRaw); // để phân tích nội dung
                String typeCode = firstNonNull(orig.headerNo, extractNumericTypeCode(sourceClean), null);
                String prefix = choosePrefix(labels);

                // Nếu có "Mã:" -> chuẩn bị thông tin clone (C#.<base> hoặc chỉ <base>)
                Integer desiredIdx = null;
                String baseCode = null;
                if (declared != null) {
                    var pr = parseCloneCode(declared);
                    desiredIdx = pr.getKey(); // null nếu chỉ ghi base
                    baseCode = pr.getValue(); // mã gốc (giữ ')')
                }

                // ===== ESSAY có a)/b)/c) =====
                if (qt == QuestionType.ESSAY) {
                    // parts = các ý a), b), c) ... đã tách
                    List<String> parts = splitEssaySubitems(sourceClean);

                    // ansMap = đáp án cho từng ý, nếu có
                    Map<String, String> ansMap = splitEssayAnswers(sourceRaw);
                    String globalAns = ansMap.get("__ALL__"); // đáp án chung (nếu không tách theo a/b)

                    if (parts.size() >= 2) {
                        List<Long> createdIds = new ArrayList<>();
                        for (int i = 0; i < parts.size(); i++) {
                            String seg = parts.get(i);
                            try {
                                CreateQuestionDTO sub = new CreateQuestionDTO();
                                sub.setQuestionType(QuestionType.ESSAY);
                                sub.setDifficulty(dto.getDifficulty());
                                sub.setChapter(dto.getChapter());
                                sub.setContent(beautifyMath(sanitizeText(seg)));

                                // quyết định đáp án cho ý này
                                String letter = String.valueOf((char) ('a' + i)); // a, b, c ...
                                String rawAns = ansMap.get(letter);
                                if (rawAns == null)
                                    rawAns = globalAns; // nếu không có a/b riêng -> dùng đáp án chung
                                if (rawAns == null)
                                    rawAns = "";

                                sub.setAnswerText(beautifyMath(sanitizeText(rawAns)));
                                sub.setLabels(dto.getLabels());

                                QuestionDTO subSaved = questionService.create(subjectId, sub, userId, null);
                                Long subId = subSaved.getId();

                                // gán code theo TYPE nếu có
                                if (typeCode != null) {
                                    String rawCode = buildCodeFromType(prefix, typeCode, i + 1); // NH1.1.a)
                                    String qc = ensureUniqueCode(subjectId, rawCode);
                                    questionService.updateQuestionCode(subId, qc);
                                }

                                // ảnh
                                List<Integer> imgIdxs = (cb.imageIndexes != null && !cb.imageIndexes.isEmpty())
                                        ? cb.imageIndexes
                                        : orig.imageIndexes;
                                if (imgIdxs != null && !imgIdxs.isEmpty()) {
                                    List<String> urls = new ArrayList<>();
                                    for (Integer ix : imgIdxs) {
                                        if (ix == null || ix < 0 || ix >= session.images.size())
                                            continue;
                                        byte[] bytes = session.images.get(ix);
                                        String url = imageStorageService.storeImage(bytes, subId, "imported.png", null);
                                        urls.add(url);
                                    }
                                    if (!urls.isEmpty())
                                        questionService.addImages(subId, urls);
                                }

                                // meta
                                ItemNature nature = (i == 0 ? ItemNature.THEORY : ItemNature.EXERCISE);
                                questionMetaService.upsertDefault(
                                        subId, UnitKind.SUB_ITEM, DEFAULT_POINTS,
                                        sub.getChapter(), null, RecordStatus.APPROVED,
                                        null, typeCode, nature,
                                        firstNonNull(cb.problemType, orig.problemType));

                                createdIds.add(subId);
                                success++;
                            } catch (Exception subEx) {
                                errors.add(String.format("Block#%d sub[%d] ERROR: %s", cb.index, i + 1,
                                        subEx.getMessage()));
                            }
                        }

                        // bundle ≥2 sub (giữ nguyên như cũ)
                        if (createdIds.size() >= 2) {
                            try {
                                List<BundleService.CreateItem> items = new ArrayList<>();
                                for (int i = 0; i < createdIds.size(); i++) {
                                    items.add(
                                            new BundleService.CreateItem(createdIds.get(i), i + 1, null, "auto-split"));
                                }
                                SplitResult sr = splitEssaySubitemsWithStem(sourceClean);
                                String stemClean = normalizeStem(sr.stem());
                                String bundleTitle = "Câu " + (typeCode == null ? "" : typeCode);
                                QuestionBundle b = bundleService.create(subjectId, userId, bundleTitle, stemClean,
                                        items, null, RecordStatus.DRAFT);
                                try {
                                    String bundleProbe = fingerprintService.buildBundleProbe(stemClean, sr.parts());
                                    var fp = fingerprintService.build(bundleProbe);
                                    fingerprintService.upsertBundle(b.getId(), subjectId, bundleProbe);
                                } catch (Exception bex) {
                                    errors.add(
                                            String.format("Block#%d bundle FP ERROR: %s", cb.index, bex.getMessage()));
                                }
                            } catch (Exception bex) {
                                errors.add(String.format("Block#%d bundle ERROR: %s", cb.index, bex.getMessage()));
                            }
                        }
                        continue; // đã xử lý xong block này
                    }
                }

                // ===== CÂU ĐƠN =====
                QuestionDTO saved = questionService.create(subjectId, dto, userId, null);
                Long qId = saved.getId();

                if (typeCode != null) {
                    String rawCode = buildCodeFromType(prefix, typeCode, null); // TC2.1.1
                    String qc = ensureUniqueCode(subjectId, rawCode);
                    questionService.updateQuestionCode(qId, qc);
                }

                // ảnh
                List<Integer> imgIdxs = (cb.imageIndexes != null && !cb.imageIndexes.isEmpty())
                        ? cb.imageIndexes
                        : orig.imageIndexes;
                if (imgIdxs != null && !imgIdxs.isEmpty()) {
                    List<String> urls = new ArrayList<>();
                    for (Integer ix : imgIdxs) {
                        if (ix == null || ix < 0 || ix >= session.images.size())
                            continue;
                        byte[] bytes = session.images.get(ix);
                        String url = imageStorageService.storeImage(bytes, qId, "imported.png", null);
                        urls.add(url);
                    }
                    if (!urls.isEmpty())
                        questionService.addImages(qId, urls);
                }

                // Nếu có "Mã:" → convert thành clone
                if (baseCode != null && !baseCode.isBlank()) {
                    Long parentId = questionService.findParentIdByCode(subjectId, baseCode).orElse(null);
                    if (parentId == null) {
                        throw new IllegalStateException("Không tìm thấy câu gốc theo mã: " + baseCode);
                    }
                    int max = questionRepository.findMaxCloneIndexByParentId(parentId);
                    int idx = (desiredIdx == null) ? (max + 1) : desiredIdx;

                    if (desiredIdx != null) {
                        while (questionRepository.existsBySubjectIdAndQuestionCodeIgnoreCase(
                                subjectId, "C" + idx + "." + baseCode)) {
                            idx++;
                        }
                    }

                    String finalCode = "C" + idx + "." + baseCode;
                    questionService.convertToClone(qId, parentId, idx, finalCode);
                }

                // meta
                questionMetaService.upsertDefault(
                        qId, UnitKind.FULL_QUESTION, DEFAULT_POINTS,
                        dto.getChapter(), null, RecordStatus.DRAFT,
                        null, typeCode,
                        ItemNature.UNKNOWN,
                        firstNonNull(cb.problemType, orig.problemType));

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
                    meta.put("variant", variant);

                    fileArchiveService.saveExistingByKey(
                            "IMPORT", subjectId, userId, originalName, temp.contentType(), finalKey, meta);
                    previewStore.clearTempUpload(req.sessionId);
                }
            } catch (Exception ignore) {
            }
        }

        return new ImportResult(total, success, errors);
    }

    // ============ IMPORT ĐÁP ÁN – PREVIEW ============

    public AnswerImportPreviewResponse buildAnswerPreview(
            Long subjectId,
            MultipartFile file,
            Set<QuestionLabel> defaultLabels) {
        // 0) Extract text (bỏ qua ảnh)
        String rawText;
        try {
            var extracted = extractTextAndImages(file);
            rawText = extracted.getText();
        } catch (Exception e) {
            throw new RuntimeException("Cannot read DOCX/PDF", e);
        }

        // 1) Chuẩn hoá text giống hệt import câu hỏi
        String cleaned = TextNormalize.normalizePreserveNewlines(rawText);
        cleaned = compactHighlightMarkers(cleaned);
        cleaned = breakChapterInline(cleaned);
        cleaned = breakHeaderAnswerInline(cleaned);

        // === LOẠI BỎ HOÀN TOÀN {hl}{/hl} ===
        cleaned = cleaned.replaceAll("\\{/?hl\\}", "");

        // === FIX BUG HEADER NGÂN HÀNG + TÊN HỌC PHẦN + CHƯƠNG ===
        cleaned = removeSectionHeadingLines(cleaned);
        cleaned = stripChapterHeader(cleaned);
        cleaned = cutPreludeBeforeFirstQuestion(cleaned);

        // 2) Tách block theo header số (2.2, 3.1.4, ...)
        String[] rawBlocks = P_SPLIT_BY_HEADER.split(cleaned);
        List<AnswerUpdatePreviewBlock> previewBlocks = new ArrayList<>();

        // labels mặc định => quyết định prefix OT/NH
        Set<QuestionLabel> defLabels = (defaultLabels == null || defaultLabels.isEmpty())
                ? java.util.EnumSet.of(QuestionLabel.PRACTICE)
                : java.util.EnumSet.copyOf(defaultLabels);

        int idx = 0;

        for (String raw : rawBlocks) {
            String trimmed = raw.trim();
            if (trimmed.isBlank())
                continue;

            // === UPDATE MỚI: BỎ QUA HOÀN TOÀN HEADER TÀI LIỆU (NGÂN HÀNG, TÊN HỌC PHẦN,
            // TRƯỜNG, ĐỀ THI...) ===
            if (looksLikeDocHeader(trimmed)) {
                System.out.println("[ANSWER_IMPORT] Skipped document header block (length=" + trimmed.length() + ")");
                continue;
            }

            AnswerUpdatePreviewBlock pb = new AnswerUpdatePreviewBlock();
            pb.index = ++idx;

            // Lưu raw đã được dọn sạch hoàn toàn để FE hiển thị đẹp
            pb.raw = TextNormalize.normalizePreserveNewlines(trimmed)
                    .replaceAll("\\s+", " ")
                    .trim();

            pb.valid = false;
            pb.warnings = new ArrayList<>();
            pb.targetQuestionIds = new LinkedHashMap<>();
            pb.currentAnswers = new LinkedHashMap<>();
            pb.newAnswers = new LinkedHashMap<>();
            pb.include = true;

            // 3) Lấy typeCode dạng số: "2.2", "3.1.4", ...
            String typeCode = extractNumericTypeCode(trimmed);
            pb.typeCode = typeCode;

            if (typeCode == null) {
                pb.warnings.add("Không tìm thấy mã câu hỏi dạng số (ví dụ 2.2, 3.1.4) ở đầu block.");
                previewBlocks.add(pb);
                continue;
            }

            // 4) Ghép thành baseCode: OT2.2 / NH2.2
            String baseCode = buildCodeFromTypeCode(typeCode, defLabels);
            pb.baseCode = baseCode;

            // 5) Lấy danh sách câu hỏi trong DB có questionCode bắt đầu bằng baseCode
            List<Question> questions = questionRepository.findBySubjectIdAndQuestionCodePrefix(subjectId, baseCode);

            if (questions.isEmpty()) {
                pb.warnings.add("Không tìm thấy câu hỏi nào trong DB có mã bắt đầu bằng: " + baseCode);
                previewBlocks.add(pb);
                continue;
            }

            // Tạm thời lấy questionType theo câu đầu
            QuestionType qtype = questions.get(0).getQuestionType();
            pb.questionType = qtype;

            // 6) Tách phần đáp án trong block
            if (qtype == QuestionType.MULTIPLE_CHOICE) {
                Matcher m = P_ANSWER_LABEL.matcher(trimmed);
                String mcAns = null;
                if (m.find()) {
                    String g2 = m.group(2);
                    if (g2 != null) {
                        mcAns = stripInlineMarkers(sanitizeText(g2.trim())).toUpperCase(Locale.ROOT);
                    }
                }

                if (mcAns == null || mcAns.isBlank()) {
                    pb.warnings.add("Không lấy được đáp án MC (dòng 'Đáp án: ...').");
                    previewBlocks.add(pb);
                    continue;
                }

                Question q = questions.get(0);
                pb.targetQuestionIds.put("", q.getId());
                pb.currentAnswers.put("", (q.getAnswer() == null ? "" : q.getAnswer()));
                pb.newAnswers.put("", mcAns);
                pb.valid = true;

                previewBlocks.add(pb);
                continue;
            }

            // ESSAY: lấy map đáp án (a, b, c, ..., hoặc "__ALL__")
            Map<String, String> ansMap = splitEssayAnswers(trimmed);
            if (ansMap.isEmpty()) {
                pb.warnings.add("Không tìm thấy phần 'Đáp án:' hoặc nội dung đáp án trong block.");
                previewBlocks.add(pb);
                continue;
            }

            boolean hasLetterKey = ansMap.keySet().stream().anyMatch(k -> !"__ALL__".equals(k));

            // CASE nhiều ý: NH2.2a), NH2.2b), ...
            if (hasLetterKey) {
                for (Map.Entry<String, String> e : ansMap.entrySet()) {
                    String label = e.getKey();
                    if ("__ALL__".equals(label))
                        continue;

                    String newAns = e.getValue() != null ? e.getValue() : "";

                    Question target = questions.stream()
                            .filter(q -> {
                                String qc = q.getQuestionCode();
                                return qc != null
                                        && qc.toLowerCase(Locale.ROOT).endsWith(label.toLowerCase(Locale.ROOT) + ")");
                            })
                            .findFirst()
                            .orElse(null);

                    if (target == null) {
                        pb.warnings.add(
                                "Không tìm thấy câu hỏi có mã kết thúc bằng '" + label + ")' (base: " + baseCode + ")");
                        continue;
                    }

                    Long qid = target.getId();
                    pb.targetQuestionIds.put(label, qid);
                    pb.currentAnswers.put(label, target.getAnswerText() == null ? "" : target.getAnswerText());
                    pb.newAnswers.put(label, newAns);
                    pb.valid = true;
                }

                if (!pb.valid && pb.warnings.isEmpty()) {
                    pb.warnings.add("Không ghép được đáp án nào với câu hỏi trong DB.");
                }

                previewBlocks.add(pb);
                continue;
            }

            // CASE ESSAY 1 ý duy nhất
            String global = ansMap.get("__ALL__");
            if (global == null) {
                pb.warnings.add("Không tìm thấy đáp án chung cho câu tự luận.");
                previewBlocks.add(pb);
                continue;
            }

            Question q = questions.get(0);
            pb.targetQuestionIds.put("", q.getId());
            pb.currentAnswers.put("", q.getAnswerText() == null ? "" : q.getAnswerText());
            pb.newAnswers.put("", global);
            pb.valid = true;

            previewBlocks.add(pb);
        }

        // 7) Lưu session
        String sessionId = java.util.UUID.randomUUID().toString();
        AnswerImportSession session = new AnswerImportSession(sessionId, previewBlocks);
        this.answerImportSessions.put(sessionId, session);

        // 8) Response
        AnswerImportPreviewResponse res = new AnswerImportPreviewResponse();
        res.sessionId = sessionId;
        res.blocks = previewBlocks;
        res.totalBlocks = previewBlocks.size();
        return res;
    }

    // ============ IMPORT ĐÁP ÁN – COMMIT ============

    public AnswerImportResult commitAnswerImport(
            Long subjectId,
            Long userId,
            AnswerImportCommitRequest req) {
        if (req == null || req.sessionId == null) {
            throw new IllegalArgumentException("Missing sessionId");
        }

        AnswerImportSession session = this.answerImportSessions.get(req.sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Answer import session not found or expired");
        }

        // Map index -> previewBlock
        Map<Integer, AnswerUpdatePreviewBlock> indexMap = new HashMap<>();
        for (AnswerUpdatePreviewBlock b : session.blocks()) {
            indexMap.put(b.index, b);
        }

        int total = 0;
        int success = 0;
        List<String> errors = new ArrayList<>();

        if (req.blocks != null) {
            // 🔁 DÙNG AnswerImportCommitBlock (không phải Request.Block)
            for (AnswerImportCommitBlock cb : req.blocks) {
                if (!cb.include)
                    continue; // user bỏ chọn
                total++;

                AnswerUpdatePreviewBlock pb = indexMap.get(cb.index);
                if (pb == null) {
                    errors.add("Block#" + cb.index + " không tồn tại trong session.");
                    continue;
                }

                if (!pb.valid || pb.targetQuestionIds == null || pb.targetQuestionIds.isEmpty()) {
                    errors.add("Block#" + cb.index + " không có câu hỏi nào hợp lệ để cập nhật.");
                    continue;
                }

                try {
                    // với mỗi key: "" (single) hoặc "a","b","c"...
                    for (Map.Entry<String, Long> e : pb.targetQuestionIds.entrySet()) {
                        String key = e.getKey();
                        Long qid = e.getValue();
                        if (qid == null)
                            continue;

                        var optQ = questionRepository.findById(qid);
                        if (optQ.isEmpty()) {
                            errors.add("Không tìm thấy câu hỏi id=" + qid + " (block#" + cb.index + ").");
                            continue;
                        }

                        Question q = optQ.get();
                        // kiểm tra đúng môn (an toàn)
                        if (q.getSubject() == null || !q.getSubject().getId().equals(subjectId)) {
                            errors.add("Câu hỏi id=" + qid + " không thuộc môn " + subjectId + " (block#" + cb.index
                                    + ").");
                            continue;
                        }

                        String newAns = (pb.newAnswers != null ? pb.newAnswers.get(key) : null);
                        if (newAns == null)
                            newAns = "";

                        if (q.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
                            q.setAnswer(newAns.trim().toUpperCase(Locale.ROOT));
                        } else {
                            q.setAnswerText(newAns.trim());
                        }

                        questionRepository.save(q);
                    }

                    success++;
                } catch (Exception ex) {
                    errors.add("Block#" + cb.index + " ERROR: " + ex.getMessage());
                }
            }
        }

        // Option: clear session sau commit
        // this.answerImportSessions.remove(req.sessionId);

        // ✅ Không dùng constructor, set field trực tiếp
        AnswerImportResult result = new AnswerImportResult();
        result.totalBlocks = total; // số block được tick
        result.totalQuestions = success; // số câu hỏi thực sự update thành công
        result.notFound = 0; // nếu sau này muốn đếm riêng số câu không tìm thấy thì set ở trên
        result.errors.addAll(errors);

        return result;
    }

    private String buildCodeFromTypeCode(String typeCode, Set<QuestionLabel> labels) {
        if (typeCode == null)
            return null;
        Set<QuestionLabel> ls = (labels == null || labels.isEmpty())
                ? java.util.EnumSet.of(QuestionLabel.PRACTICE)
                : java.util.EnumSet.copyOf(labels);
        String prefix = choosePrefix(ls); // OT / NH – đã có sẵn helper choosePrefix ở cuối file
        return prefix + typeCode;
    }

    // Bóc tag highlight + normalize NBSP trước khi tách
    private static String stripHlAndNormalize(String s) {
        if (s == null)
            return null;
        return s.replace("{hl}", "")
                .replace("{/hl}", "")
                .replace('\u00A0', ' '); // NBSP -> space
    }

    /**
     * Cắt bỏ block "Đáp án: ..." ở cuối (nếu có), chỉ giữ phần nội dung câu hỏi.
     * Dùng chung cho các hàm tách ý a), b), c) để không đếm nhầm a), b) ở phần đáp
     * án.
     */
    private String stripAnswerBlock(String source) {
        if (source == null)
            return null;

        String orig = source;

        // Ưu tiên dùng P_ANSWER_LABEL (bắt cả text đáp án phía sau)
        Matcher m = P_ANSWER_LABEL.matcher(source);
        int cutPos = -1;
        if (m.find()) {
            cutPos = m.start();
            System.out.println("[IMPORT][stripAnswerBlock] Hit P_ANSWER_LABEL at index " + cutPos);
        } else {
            // Fallback: chỉ bắt dòng "Đáp án:" / "Giải thích:" / "Lời giải"
            Matcher mLine = P_ANSWER_LINE.matcher(source);
            if (mLine.find()) {
                cutPos = mLine.start();
                System.out.println("[IMPORT][stripAnswerBlock] Hit P_ANSWER_LINE at index " + cutPos);
            }
        }

        if (cutPos >= 0) {
            String questionPart = source.substring(0, cutPos).trim();
            String answerPart = orig.substring(cutPos).trim();

            System.out.println("========== [IMPORT][stripAnswerBlock] ==========");
            System.out.println("[QUESTION PART]:");
            System.out.println(questionPart);
            System.out.println("--------------- [ANSWER PART]:");
            System.out.println(answerPart);
            System.out.println("===============================================");

            return questionPart;
        }

        System.out.println("[IMPORT][stripAnswerBlock] No answer label found, keep full block.");
        return source;
    }

    private List<String> splitEssaySubitems(String source) {
        if (source == null)
            return List.of();

        // 0) bỏ phần Đáp án để không đếm a), b) trong phần answer
        source = stripAnswerBlock(source);

        // 1) bóc {hl} trước khi split
        String text = stripHlAndNormalize(source);

        // 2) bỏ dòng header "Câu n ..."
        text = P_HEADER_LINE.matcher(text).replaceFirst("");

        // DEBUG
        System.out.println("========== [IMPORT][splitEssaySubitems] ==========");
        System.out.println("[INPUT AFTER stripAnswerBlock]:");
        System.out.println(text);

        // 3) tìm các đầu mục a)/b)/...
        Matcher m = P_SUBITEM_HEADER.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (m.find())
            starts.add(m.start());

        System.out.println("[IMPORT][splitEssaySubitems] subitem count = " + starts.size());

        if (starts.size() < 2) {
            System.out.println("[IMPORT][splitEssaySubitems] <2 subitems -> return empty");
            return List.of(); // không có ≥2 ý => không tách
        }

        // 4) cắt theo spans
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = (i + 1 < starts.size()) ? starts.get(i + 1) : text.length();
            String seg = text.substring(from, to);

            // bỏ tiền tố a)/a./a:/ (a)
            seg = P_SUBITEM_HEADER.matcher(seg).replaceFirst("");
            seg = seg.trim();
            if (!seg.isBlank()) {
                parts.add(seg);
                System.out.println("[IMPORT][splitEssaySubitems] part[" + i + "]: " + seg);
            }
        }
        System.out.println("===============================================");
        return parts;
    }

    private SplitResult splitEssaySubitemsWithStem(String source) {
        if (source == null)
            return new SplitResult(null, List.of());

        // 0) bỏ phần Đáp án, chỉ giữ phần câu hỏi để tách stem + các ý
        source = stripAnswerBlock(source);

        // bóc {hl}, normalize y như cũ
        String text = stripHlAndNormalize(source);
        // bỏ dòng "Câu n ..."
        text = P_HEADER_LINE.matcher(text).replaceFirst("");

        System.out.println("====== [IMPORT][splitEssaySubitemsWithStem] ======");
        System.out.println("[INPUT AFTER stripAnswerBlock]:");
        System.out.println(text);

        // tìm vị trí các đầu mục a)/b)/...
        Matcher m = P_SUBITEM_HEADER.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (m.find())
            starts.add(m.start());

        System.out.println("[IMPORT][splitEssaySubitemsWithStem] subitem count = " + starts.size());

        if (starts.size() < 2)
            return new SplitResult(null, List.of()); // không đủ để tạo bundle

        // stem = phần trước ý đầu tiên
        String stem = text.substring(0, starts.get(0)).trim();

        // cắt từng ý như cũ
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = (i + 1 < starts.size()) ? starts.get(i + 1) : text.length();
            String seg = text.substring(from, to);
            seg = P_SUBITEM_HEADER.matcher(seg).replaceFirst("").trim();
            if (!seg.isBlank()) {
                parts.add(seg);
                System.out.println("[IMPORT][splitEssaySubitemsWithStem] part[" + i + "]: " + seg);
            }
        }

        if (stem != null && stem.isBlank())
            stem = null;

        System.out.println("[STEM]: " + stem);
        System.out.println("===============================================");
        return new SplitResult(stem, parts);
    }

    private Map<String, String> splitEssayAnswers(String source) {
        Map<String, String> map = new LinkedHashMap<>();
        if (source == null)
            return map;

        Matcher labelM = P_ANSWER_LABEL.matcher(source);
        if (!labelM.find()) {
            System.out.println("[IMPORT][splitEssayAnswers] no ANSWER_LABEL");
            return map;
        }

        // Lấy nội dung nằm cùng dòng với "Đáp án:" (nếu có)
        String inline = labelM.group(2).trim();

        // Lấy phần còn lại sau dòng chứa "Đáp án:"
        String below = "";
        if (labelM.end() < source.length()) {
            below = source.substring(labelM.end());
        }

        String ansBlock = (inline + "\n" + below).trim();
        if (ansBlock.isBlank())
            return map;

        // bóc {hl}, NBSP ...
        String text = stripHlAndNormalize(ansBlock);

        System.out.println("==== [IMPORT][splitEssayAnswers] ====");
        System.out.println("[RAW ANSWER BLOCK]:");
        System.out.println(ansBlock);
        System.out.println("[AFTER normalize]:");
        System.out.println(text);

        // Tìm các đầu mục a)/b)/...
        Matcher m = P_SUBITEM_HEADER.matcher(text);
        List<Integer> starts = new ArrayList<>();
        List<String> letters = new ArrayList<>();
        while (m.find()) {
            String whole = m.group(); // ví dụ "a) ", "b) "
            // tự bóc chữ cái đầu tiên trong match
            char letterChar = 0;
            for (int i = 0; i < whole.length(); i++) {
                char ch = whole.charAt(i);
                if (Character.isLetter(ch)) {
                    letterChar = Character.toLowerCase(ch);
                    break;
                }
            }
            if (letterChar == 0) {
                System.out.println("[splitEssayAnswers] WARN cannot extract letter from match: '" + whole + "'");
                continue;
            }
            String letter = String.valueOf(letterChar); // "a", "b", ...
            starts.add(m.start());
            letters.add(letter);
        }

        System.out.println("[splitEssayAnswers] sub answers found = " + starts.size());

        // Không có a)/b)/... -> coi là 1 đáp án chung cho toàn câu
        if (starts.isEmpty()) {
            String v = text.trim();
            if (!v.isBlank()) {
                map.put("__ALL__", v);
                System.out.println("Global answer: " + v);
            }
            System.out.println("===================================");
            return map;
        }

        // Cắt từng đoạn đáp án theo a), b), ...
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = (i + 1 < starts.size()) ? starts.get(i + 1) : text.length();

            String seg = text.substring(from, to);
            seg = P_SUBITEM_HEADER.matcher(seg).replaceFirst("").trim();

            String letter = letters.get(i); // "a", "b", ...
            if (!seg.isBlank()) {
                map.put(letter, seg);
                System.out.println(" ans[" + letter + "] = " + seg);
            }
        }

        System.out.println("===================================");
        return map;
    }

    private boolean looksLikeDocHeader(String s) {
        if (s == null)
            return false;
        // chỉ xét vài dòng đầu để tránh “ăn” nhầm nội dung thật
        StringBuilder head = new StringBuilder();
        int lines = 0;
        for (String ln : s.split("\\R", -1)) {
            if (lines++ >= 6)
                break;
            head.append(ln).append('\n');
        }
        return P_DOC_HEADER_HINT.matcher(head.toString()).find();
    }

    // ImportQuestionService.java
    private PreviewBlock parseOneBlockForPreview(String rawBlock, List<byte[]> allImages) {
        PreviewBlock b = new PreviewBlock();
        b.problemType = null;
        // NEW: đọc mã khai báo + set previewCode
        String declared = extractDeclaredCode(rawBlock);
        if (declared != null && !declared.isBlank()) {
            b.previewCode = declared.trim();
        }

        // NEW: loại bỏ "(Mã: ...)" khỏi block để phần còn lại không rơi vào content
        String work = stripCodeDeclaration(rawBlock);

        // A) Đọc điểm từ header để set Difficulty
        Integer pts = null;
        Matcher headPt = P_HEADER_POINTS.matcher(work);
        if (headPt.find()) {
            String g = headPt.group(1);
            if (g != null)
                try {
                    pts = Integer.parseInt(g);
                } catch (Exception ignore) {
                }
        }

        // B) cắt nhãn Answer (chưa phân loại)
        Matcher ansM = P_ANSWER_LABEL.matcher(work);
        String block = work;
        boolean hasAnswerSection = false;
        String mcAnswerRaw = null; // dùng cho MCQ

        if (ansM.find()) {
            hasAnswerSection = true;
            // group(2) thường là phần sau "Đáp án:" trên cùng dòng -> đủ dùng cho MCQ
            String g2 = null;
            try {
                g2 = ansM.group(2);
            } catch (Exception ignore) {
            }
            if (g2 != null) {
                mcAnswerRaw = beautifyMath(sanitizeText(stripInlineMarkers(g2.trim())));
            }
            // bỏ phần Đáp án khỏi block nội dung
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
            if (firstOptStart < 0)
                firstOptStart = detectM.start();
            keys.add(detectM.group(1).toUpperCase(Locale.ROOT));
        }
        boolean isMC = (keys.size() == 4);

        b.questionType = isMC ? QuestionType.MULTIPLE_CHOICE : QuestionType.ESSAY;
        b.difficulty = mapPoints(pts); // mặc định C nếu null

        // E) gán Answer đúng field
        if (hasAnswerSection) {
            if (isMC) {
                // MCQ: giữ cách cũ – dùng dòng sau "Đáp án:" (ví dụ "A", "AB")
                if (mcAnswerRaw != null) {
                    b.answer = mcAnswerRaw.toUpperCase(Locale.ROOT);
                }
            } else {
                // ESSAY: dùng splitEssayAnswers để lấy đủ a), b), c) ... cho preview
                Map<String, String> ansMapPreview = splitEssayAnswers(work);
                if (!ansMapPreview.isEmpty()) {
                    String ansText;
                    if (ansMapPreview.size() == 1 && ansMapPreview.containsKey("__ALL__")) {
                        // 1 đáp án chung cả câu
                        ansText = ansMapPreview.get("__ALL__");
                    } else {
                        // ghép lại thành:
                        // a) ...
                        // b) ...
                        StringBuilder sb = new StringBuilder();
                        ansMapPreview.entrySet().stream()
                                .filter(e -> !"__ALL__".equals(e.getKey()))
                                .sorted(Map.Entry.comparingByKey()) // a,b,c,...
                                .forEach(e -> {
                                    if (sb.length() > 0)
                                        sb.append("\n");
                                    sb.append(e.getKey()).append(") ").append(e.getValue());
                                });
                        ansText = sb.toString();
                    }
                    if (ansText != null) {
                        ansText = beautifyMath(sanitizeText(stripInlineMarkers(ansText.trim())));
                        b.answerText = ansText;
                    }
                }
            }
        }

        // F) Parse theo loại
        if (isMC) {
            List<String> emph = new ArrayList<>();
            Matcher optM = P_OPT_EXTRACT.matcher(bodyForDetect);
            while (optM.find()) {
                String whole = optM.group(0);
                String key = optM.group(1).toUpperCase(Locale.ROOT);
                String rawVal = sanitizeText(optM.group(2).trim());

                boolean highlighted = whole.contains("{hl}") || rawVal.contains("{hl}") || rawVal.contains("{/hl}");
                String val = beautifyMath(stripInlineMarkers(removePointsInline(rawVal)).trim());

                switch (key) {
                    case "A":
                        b.optionA = val;
                        if (highlighted)
                            emph.add("A");
                        break;
                    case "B":
                        b.optionB = val;
                        if (highlighted)
                            emph.add("B");
                        break;
                    case "C":
                        b.optionC = val;
                        if (highlighted)
                            emph.add("C");
                        break;
                    case "D":
                        b.optionD = val;
                        if (highlighted)
                            emph.add("D");
                        break;
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
            if (b.answerText == null)
                b.answerText = "";
            else
                b.answerText = beautifyMath(sanitizeText(enforceInlineListBreaks(collapseSoftBreaks(b.answerText))));
        }

        // Ảnh
        Matcher imgM = P_IMAGE_PLACEHOLDER.matcher(body);
        while (imgM.find()) {
            int idx = safeIndex(imgM.group(1));
            if (idx >= 0 && idx < allImages.size())
                b.imageIndexes.add(idx);
        }

        // Footer guard
        b.content = stripFooter(b.content);
        if (b.answerText != null)
            b.answerText = stripFooter(b.answerText);

        if (isMC) {
            if (b.optionA == null || b.optionB == null || b.optionC == null || b.optionD == null)
                b.warnings.add("Thiếu option A/B/C/D.");
        } else if (b.content == null || b.content.isBlank()) {
            b.warnings.add("Nội dung trống.");
        }

        if (b.content != null)
            b.content = stripHl(b.content);
        return b;
    }

    /* ==================== helpers ==================== */

    private int safeIndex(String oneBased) {
        try {
            return Integer.parseInt(oneBased) - 1;
        } catch (Exception e) {
            return -1;
        }
    }

    private String removeAllImagePlaceholders(String text) {
        return P_IMAGE_PLACEHOLDER.matcher(text).replaceAll("").trim();
    }

    private String removeSectionHeadingLines(String text) {
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\\R")) {
            String s = sanitizeText(line).trim().toLowerCase(Locale.ROOT);
            boolean isHeading = s
                    .matches("^(chương|chuong|chapter|mục|muc|phần|phan|bài|bai|câu\\s*hỏi\\s*loại)\\b.*$");
            if (!isHeading)
                out.append(line).append('\n');
        }
        return out.toString();
    }

    private String cutPreludeBeforeFirstQuestion(String fullText) {
        Matcher m = P_SPLIT_BY_HEADER.matcher(fullText);
        return m.find() ? fullText.substring(m.start()).trim() : fullText;
    }

    private String stripFooter(String s) {
        return s == null ? null : P_FOOTER.matcher(s).replaceAll("").trim();
    }

    private String sanitizeText(String s) {
        return TextNormalize.normalizeSoftMath(s);
    }

    private String beautifyMath(String s) {
        if (s == null)
            return null;
        String out = s;
        out = out.replace("∞", "\\infty")
                .replace("¥", "\\infty");
        ;
        out = convertPipeMatrixToLatex(out);
        // 1) Bọc danh sách số sau \in hoặc ∈ thành \{ … \}
        out = out.replaceAll(
                "(?<=\\\\in)\\s*(?![\\[{(])([0-9]+(?:\\s*[,;]\\s*[0-9]+)+)",
                " \\\\{$1\\\\}");
        out = out.replaceAll(
                "(?<=∈)\\s*(?![\\[{(])([0-9]+(?:\\s*[,;]\\s*[0-9]+)+)",
                " \\\\{$1\\\\}");

        // 2) Bọc các token LaTeX “trần” (x^{2}, a_{i}, …) CHỈ ở ngoài các khối toán
        out = TextNormalize.wrapBareInlineMath(out); // <-- thêm dòng này
        // 2b) Gỡ sub/sup rỗng (nếu còn lạc)
        out = out.replaceAll("(?<!\\\\)_\\{\\s*\\}", "");
        out = out.replaceAll("(?<!\\\\)\\^\\{\\s*\\}", "");

        // 3) Làm sạch nhẹ
        out = out.replace('−', '-').replaceAll("\\s{2,}", " ").trim();
        return out;
    }

    private String firstNonNull(String... candidates) {
        for (String c : candidates)
            if (c != null)
                return c;
        return null;
    }

    private static String stripHl(String s) {
        return s == null ? null : s.replace("{hl}", "").replace("{/hl}", "");
    }

    private String removePointsInline(String s) {
        return s == null ? null : P_POINTS_INLINE.matcher(s).replaceAll("").trim();
    }

    private Difficulty mapPoints(Integer pts) {
        if (pts == null)
            return Difficulty.C;
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
        String name = (file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename())
                .toLowerCase(Locale.ROOT);
        try (InputStream is = file.getInputStream()) {
            if (name.endsWith(".docx")) {
                try {
                    return DocxOmmlExtractor.extractWord(is);
                } catch (Exception e) {
                    throw new RuntimeException("DOCX/OMML extract failed", e);
                }
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
    private static final Pattern P_MATH_CONT_END = Pattern.compile("[,;:=><≤≥→\\-+*/)\\]]\\s*$");
    private static final Pattern P_MATH_CONT_START = Pattern.compile("^(?:\\d|[a-zA-Z]|\\\\\\(|\\(|max\\b|min\\b)");

    private static boolean shouldHardBreakForMath(String prevTrim, String currTrim) {
        if (prevTrim == null || currTrim == null)
            return false;
        // ví dụ: "… → max," + "7x1 + 2x2 …"
        if (P_MATH_CONT_END.matcher(prevTrim).find() && P_MATH_CONT_START.matcher(currTrim).find())
            return true;

        // thêm vài ca phổ biến của ràng buộc/điều kiện
        boolean prevHasObjective = prevTrim.matches("(?i).*(max|min)\\s*,?\\s*$");
        boolean currLooksConstraint = currTrim.matches(".*(=|≤|≥|<=|>=)\\s*\\d+.*");
        if (prevHasObjective && currLooksConstraint)
            return true;

        // nếu cả 2 dòng đều “toán tính” (tỉ lệ kí tự toán cao) thì cũng nên xuống dòng
        String mathChars = "+\\-*/=<>≤≥(),;:\\\\^_\\d";
        double prevRatio = prevTrim.replaceAll("[^" + mathChars + "a-zA-Z]", "").length()
                / (double) Math.max(1, prevTrim.length());
        double currRatio = currTrim.replaceAll("[^" + mathChars + "a-zA-Z]", "").length()
                / (double) Math.max(1, currTrim.length());
        return prevRatio > 0.35 && currRatio > 0.35; // ngưỡng nhẹ
    }

    private String collapseSoftBreaks(String s) {
        if (s == null)
            return null;
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
                if (!lastBlank && out.length() > 0)
                    out.append("\n\n");
                lastBlank = true;
                prevTrim = null;
                continue;
            }

            if (first) {
                out.append(t);
            } else if (bullet || lastBlank || tableLike || shouldHardBreakForMath(prevTrim, t)) {
                out.append('\n').append(t); // ↓ xuống dòng cứng
            } else {
                int L = out.length();
                if (L > 0 && out.charAt(L - 1) == '-') {
                    out.setLength(L - 1); // nối chữ bị ngắt bằng gạch nối cuối dòng
                    out.append(t);
                } else {
                    out.append(' ').append(t); // ghép mềm mặc định
                }
            }
            first = false;
            lastBlank = false;
            prevTrim = t;
        }
        return out.toString().replaceAll("\\s{2,}", " ").trim();
    }

    private String enforceInlineListBreaks(String s) {
        if (s == null)
            return null;
        // ...". a) ..." -> "\n a) ..."
        s = s.replaceAll("(?<=\\.|\\?|!|:)\\s+([a-dA-D][\\)\\.])\\s+", "\n$1 ");
        return s;
    }

    // Nhận một đoạn có dạng: a | b | c ... (toàn số hoặc ∞/¥)
    // Nếu tổng số token là perfect square (N×N) thì biến thành
    // $$\begin{pmatrix}...\end{pmatrix}$$
    private static final Pattern P_PIPE_MATRIX_CHUNK = Pattern.compile(
            "(?:\\\\infty|∞|¥|\\d+)(?:\\s*\\|\\s*(?:\\\\infty|∞|¥|\\d+))+(?:\\s+(?:\\\\infty|∞|¥|\\d+)(?:\\s*\\|\\s*(?:\\\\infty|∞|¥|\\d+))+)*");

    private String convertPipeMatrixToLatex(String text) {
        if (text == null || text.isBlank())
            return text;
        Matcher m = P_PIPE_MATRIX_CHUNK.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String chunk = m.group();

            // Lấy toàn bộ token theo thứ tự
            java.util.List<String> toks = new java.util.ArrayList<>();
            Matcher t = Pattern.compile("(\\\\infty|∞|¥|\\d+)").matcher(chunk);
            while (t.find())
                toks.add(t.group());

            int K = toks.size();
            int n = (int) Math.round(Math.sqrt(K));

            if (n * n == K && n >= 2 && n <= 100) {
                StringBuilder mat = new StringBuilder();
                mat.append("$$\\begin{pmatrix}\n");
                for (int i = 0; i < K; i++) {
                    String v = toks.get(i)
                            .replace("¥", "\\infty")
                            .replace("∞", "\\infty");
                    mat.append(v);
                    if (i % n != n - 1)
                        mat.append(" & ");
                    else
                        mat.append("\\\\\n");
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

    private static String toLetter(int subOrder) {
        return String.valueOf((char) ('a' + (subOrder - 1)));
    }

    /** OT1.1.a) hoặc TC2.3.c) */
    private static String buildBaseCode(String prefix, int blockIndex, Integer subOrderOrNull) {
        if (subOrderOrNull == null)
            return prefix + blockIndex;
        return prefix + blockIndex + "." + subOrderOrNull + "." + toLetter(subOrderOrNull) + ")";
    }

    /** OT nếu có PRACTICE, ngược lại TC */
    private static String choosePrefix(Set<QuestionLabel> labels) {
        return (labels != null && labels.contains(QuestionLabel.PRACTICE)) ? "OT" : "NH";
    }

    /** Ghép mã theo TYPE: TC2.1.1 hoặc TC2.1.1.a) */
    private static String buildCodeFromType(String prefix, String typeCode, Integer subIdx) {
        String base = prefix + typeCode;
        if (subIdx == null)
            return base;
        char letter = (char) ('a' + Math.max(0, subIdx - 1));
        return base + letter + ")";
    }

    /** Đảm bảo duy nhất trong 1 môn (thêm -2, -3 nếu cần) */
    private String ensureUniqueCode(Long subjectId, String code) {
        String base = code;
        int n = 1;
        String candidate = base;
        while (questionRepository.existsBySubjectIdAndQuestionCodeIgnoreCase(subjectId, candidate)) {
            n++;
            candidate = base + "-" + n;
        }
        return candidate;
    }
}
