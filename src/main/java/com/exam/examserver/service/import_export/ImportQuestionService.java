package com.exam.examserver.service.import_export;

import com.exam.examserver.dto.exam.CreateQuestionDTO;
import com.exam.examserver.dto.exam.QuestionDTO;
import com.exam.examserver.dto.importing.*;
import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.enums.QuestionType;
import com.exam.examserver.service.QuestionService;
import com.exam.examserver.storage.GcsArchiveStorage;
import com.exam.examserver.storage.GcsObjectHelper;
import com.exam.examserver.storage.ImageStorageService;
import com.exam.examserver.util.TextNormalize;
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
                                 FileArchiveService fileArchiveService) {
        this.questionService = questionService;
        this.imageStorageService = imageStorageService;
        this.previewStore = previewStore;
        this.gcsArchiveStorage = gcsArchiveStorage;
        this.gcsObjectHelper = gcsObjectHelper;
        this.fileArchiveService = fileArchiveService;
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

    public ImportResult commitPreview(Long subjectId, Long userId, CommitRequest req, boolean saveCopy) {
        var session = previewStore.get(req.sessionId);
        if (session == null) throw new IllegalArgumentException("Preview session expired or not found");

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

                QuestionDTO saved = questionService.create(subjectId, dto, userId, null);
                Long qId = saved.getId();

                // ảnh
                List<Integer> imgIdxs = (cb.imageIndexes != null && !cb.imageIndexes.isEmpty())
                        ? cb.imageIndexes : orig.imageIndexes;
                if (imgIdxs != null && !imgIdxs.isEmpty()) {
                    List<String> urls = new ArrayList<>();
                    for (Integer i : imgIdxs) {
                        if (i == null || i < 0 || i >= session.images.size()) continue;
                        byte[] bytes = session.images.get(i);
                        String url = imageStorageService.storeImage(bytes, qId, "imported.png", null);
                        urls.add(url);
                    }
                    if (!urls.isEmpty()) questionService.addImages(qId, urls);
                }
                success++;
            } catch (Exception ex) {
                errors.add("Block#" + cb.index + ": " + ex.getMessage());
            }
        }

        if (saveCopy) {
            try {
                var temp = previewStore.getTempUpload(req.sessionId);
                if (temp != null && temp.key() != null && !temp.key().isBlank()) {
                    String originalName = (temp.originalName() == null ? "import.bin" : temp.originalName());
                    String finalKey = "archives/" + java.util.UUID.randomUUID() + "_" + originalName;

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
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return new ImportResult(total, success, errors);
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

    private String cutPreludeBeforeFirstQuestion(String fullText) {
        Matcher m = P_SPLIT_BY_HEADER.matcher(fullText);
        return m.find() ? fullText.substring(m.start()).trim() : fullText;
    }

    private String stripFooter(String s) { return s == null ? null : P_FOOTER.matcher(s).replaceAll("").trim(); }

    private String sanitizeText(String s) { return TextNormalize.normalizeSoftMath(s); }

//    private String beautifyMath(String s) {
//        if (s == null) return null;
//        String out = s;
//        out = out.replace('−', '-');
//        out = out.replaceAll("\\s*([∧∨≡⇒=+×÷])\\s*", " $1 ");
//        out = out.replaceAll("(?<=[\\p{L}\\p{N}\\)\\]])\\s*-\\s*(?=[\\p{L}\\p{N}\\(\\[])", " - ");
//        out = out.replaceAll("\\s{2,}", " ").trim();
//        return out;
//    }

    private String beautifyMath(String s) {
        if (s == null) return null;
        // An toàn cho LaTeX: không chèn/thêm khoảng trắng quanh toán tử
        // (tránh phá cú pháp \frac{...}{...}, \sum_{...}^{...}, \overline{...}...)
        String out = s;
        out = out.replace('−', '-');      // normalize minus
        out = out.replaceAll("\\s{2,}", " ").trim(); // gộp cách thừa
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

    private String collapseSoftBreaks(String s) {
        if (s == null) return null;
        String[] lines = s.split("\\R");
        StringBuilder out = new StringBuilder();
        boolean first = true;
        boolean lastBlank = false;

        for (String line : lines) {
            String t = line.trim();
            boolean blank = t.isEmpty();
            boolean bullet = !blank && P_BULLET_LINE.matcher(t).matches();

            if (blank) {
                if (!lastBlank && out.length() > 0) out.append("\n\n"); // đoạn mới
                lastBlank = true;
                continue;
            }

            if (first) {
                out.append(t);
            } else if (bullet || lastBlank) {
                out.append('\n').append(t);
            } else {
                // nếu dòng trước kết thúc bằng dấu gạch nối -> bỏ gạch và nối liền
                int L = out.length();
                if (L > 0 && out.charAt(L - 1) == '-') {
                    out.setLength(L - 1);
                    out.append(t);
                } else {
                    out.append(' ').append(t);
                }
            }
            first = false;
            lastBlank = false;
        }
        return out.toString().replaceAll("\\s{2,}", " ").trim();
    }

    private String enforceInlineListBreaks(String s) {
        if (s == null) return null;
        // ...". a) ..." -> "\n a) ..."
        s = s.replaceAll("(?<=\\.|\\?|!|:)\\s+([a-dA-D][\\)\\.])\\s+", "\n$1 ");
        return s;
    }
}
