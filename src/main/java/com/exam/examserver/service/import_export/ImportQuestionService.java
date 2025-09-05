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

import static com.exam.examserver.service.import_export.DocxOmmlExtractor.extractPdf;
import static com.exam.examserver.service.import_export.DocxOmmlExtractor.extractWord;
import static com.exam.examserver.util.ImportRegex.*;

@Service
public class ImportQuestionService {

    private final QuestionService questionService;
    private final ImageStorageService imageStorageService;
    private final ImportPreviewStore previewStore;
    private final GcsArchiveStorage gcsArchiveStorage;
    private final GcsObjectHelper gcsObjectHelper;
    private final FileArchiveService fileArchiveService;

    public ImportQuestionService(QuestionService questionService,
                                 ImageStorageService imageStorageService,
                                 ImportPreviewStore previewStore, GcsArchiveStorage gcsArchiveStorage, GcsObjectHelper gcsObjectHelper, FileArchiveService fileArchiveService) {
        this.questionService = questionService;
        this.imageStorageService = imageStorageService;
        this.previewStore = previewStore;
        this.gcsArchiveStorage = gcsArchiveStorage;
        this.gcsObjectHelper = gcsObjectHelper;
        this.fileArchiveService = fileArchiveService;
    }

    /* ==================== PREVIEW / COMMIT ==================== */

    public PreviewResponse buildPreview(Long subjectId, MultipartFile file, boolean saveCopy, Set<QuestionLabel> defaultLabels) {
        // (giữ nguyên đoạn extract như cũ)
        ExtractResult ext = extractTextAndImages(file);

        String full = TextNormalize.normalizePreserveNewlines(ext.getText());
        full = compactHighlightMarkers(full);
        full = breakChapterInline(full);
        full = breakHeaderAnswerInline(full);     // chỉ Header chữ + Answer + header-số an toàn

        List<byte[]> images = ext.getImages();

        // 1) CẮT THEO CHƯƠNG (HL-aware)
        String[] chapChunks = P_SPLIT_BY_CHAPTER.split(full);
        System.out.println(Arrays.toString(chapChunks));

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
                chap = cutPreludeBeforeFirstQuestion(chap);
            } else {
                // 👉 phần mở đầu: bỏ qua nếu không có header câu hỏi
                Matcher hasQ = P_SPLIT_BY_HEADER.matcher(chap);
                if (!hasQ.find()) {
                    continue; // SKIP preface
                }
                chap = chap.substring(hasQ.start()).trim();
            }

            // 2) Cắt theo header câu hỏi
            String[] qChunks = P_SPLIT_BY_HEADER.split(chap);
            for (String raw : qChunks) {
                String block = raw.trim();
                if (block.isEmpty()) continue;

                idx++;
                PreviewBlock b = parseOneBlockForPreview(block, images);
                b.labels = EnumSet.copyOf(def);
                b.index = idx;
                b.raw = block;

                if (currentChapter != null) b.chapter = currentChapter;
                blocks.add(b);
            }
        }

        // Tạo session preview như cũ
        var session = previewStore.create(images, blocks);

        // ===== NEW: nếu saveCopy=true -> upload file gốc vào prefix tmp/ và gắn vào session (KHÔNG ghi DB)
        if (saveCopy) {
            try {

                byte[] raw = file.getBytes();
                String origName = file.getOriginalFilename();
                String contentType = file.getContentType();

                // yêu cầu: key nằm trong "tmp/" để lifecycle rule tự xoá nếu user cancel
                var put = gcsArchiveStorage.putTmp(raw, contentType, (origName == null ? "import.bin" : origName));
                String tempKey = put.storageKey(); // ví dụ: tmp/<uuid>_originalName

                // Lưu metadata tạm vào preview session để commit có thể promote
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

        // Trả về response như cũ
        PreviewResponse resp = new PreviewResponse();
        resp.sessionId = session.id;
        resp.totalBlocks = blocks.size();
        resp.blocks = blocks;
        return resp;
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

                // 🔴 PHẢI set labels trước khi create
                Set<QuestionLabel> labels =
                        (cb.labels != null && !cb.labels.isEmpty()) ? cb.labels
                                : (orig.labels != null && !orig.labels.isEmpty()) ? new HashSet<>(orig.labels)
                                : EnumSet.of(QuestionLabel.PRACTICE);
                dto.setLabels(labels);

                // create -> labels được lưu đúng
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

        // ===== NEW: nếu saveCopy=true và có tempKey trong session -> promote + ghi DB
        if (saveCopy) {
            try {
                var temp = previewStore.getTempUpload(req.sessionId); // {key, originalName, contentType, size}
                if (temp != null && temp.key() != null && !temp.key().isBlank()) {
                    String originalName = (temp.originalName() == null ? "import.bin" : temp.originalName());
                    String finalKey = "archives/" + java.util.UUID.randomUUID() + "_" + originalName;

                    // copy tmp/... -> archives/...; rồi delete tmp/...
                    gcsObjectHelper.copyAndDelete(temp.key(), finalKey);

                    // ghi metadata DB dựa trên object đã tồn tại (không re-upload)
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

                    // xoá dấu vết temp trong session
                    previewStore.clearTempUpload(req.sessionId);
                }
            } catch (Exception e) {
                // KHÔNG làm fail commit nếu promote/lưu DB lỗi
                // TODO: log warn nếu bạn dùng logger
            }
        }

        return new ImportResult(total, success, errors);
    }

    private PreviewBlock parseOneBlockForPreview(String rawBlock, List<byte[]> allImages) {
        PreviewBlock b = new PreviewBlock();

        // 1) cắt nhãn Answer (chưa phân loại)
        Matcher ansM = P_ANSWER_LABEL.matcher(rawBlock);
        String block = rawBlock;
        String pendingAnswer = null;
        if (ansM.find()) {
            pendingAnswer = beautifyMath(sanitizeText(stripInlineMarkers(ansM.group(2).trim())));
            block = block.substring(0, ansM.start()).trim();
        }

        // 2) bỏ header -> body
        String body = stripHeader(block);
        System.out.println(body);

        // 3) DÒ option chỉ để PHÂN LOẠI: break option tạm thời rồi đếm distinct A–D
        String bodyForDetect = breakOptionsInline(body);
        Matcher detectM = P_OPT_EXTRACT.matcher(bodyForDetect);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        int firstOptStart = -1;
        while (detectM.find()) {
            if (firstOptStart < 0) firstOptStart = detectM.start();
            keys.add(detectM.group(1).toUpperCase(Locale.ROOT));
        }
        boolean isMC = (keys.size() == 4);                   // ← phải đủ 4

        b.questionType = isMC ? QuestionType.MULTIPLE_CHOICE : QuestionType.ESSAY;
        b.difficulty = Difficulty.C;

        // 4) gán Answer đúng field
        if (pendingAnswer != null) {
            if (isMC) b.answer = pendingAnswer.toUpperCase(Locale.ROOT);
            else      b.answerText = pendingAnswer;
        }

        // 5) Parse theo loại
        if (isMC) {
            // CHỈ MC mới parse option trên bodyForDetect
            List<String> emph = new ArrayList<>();
            Matcher optM = P_OPT_EXTRACT.matcher(bodyForDetect);
            while (optM.find()) {
                String whole  = optM.group(0);
                String key    = optM.group(1).toUpperCase(Locale.ROOT);
                String rawVal = sanitizeText(optM.group(2).trim());

                boolean highlighted = whole.contains("{hl}") || rawVal.contains("{hl}") || rawVal.contains("{/hl}");
                String val = beautifyMath(stripInlineMarkers(rawVal).trim());

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
            b.content = beautifyMath(sanitizeText(removeAllImagePlaceholders(stripInlineMarkers(stem))));
        } else {
            // ESSAY: tuyệt đối KHÔNG break option; và nếu không muốn hiển thị hl thì strip
            b.content = beautifyMath(sanitizeText(removeAllImagePlaceholders(stripHl(body))));
            if (b.answerText == null) b.answerText = "";
        }

        // ảnh
        Matcher imgM = P_IMAGE_PLACEHOLDER.matcher(body);
        while (imgM.find()) {
            int idx = safeIndex(imgM.group(1));
            if (idx >= 0 && idx < allImages.size()) b.imageIndexes.add(idx);
        }

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
            boolean isHeading = s.matches("^(CHƯƠNG|Chương|chương|chuong|chapter|mục|muc|phần|phan|bài|bai|câu\\s*hỏi\\s*loại)\\b.*$");
            if (!isHeading) out.append(line).append('\n');
        }
        return out.toString();
    }

    private String cutPreludeBeforeFirstQuestion(String fullText) {
        // dùng chính pattern split-by-header (lookahead) để tìm vị trí header đầu
        Matcher m = P_SPLIT_BY_HEADER.matcher(fullText);
        return m.find() ? fullText.substring(m.start()).trim() : fullText;
    }

    private String sanitizeText(String s) { return TextNormalize.normalizeSoftMath(s); }

    private String beautifyMath(String s) {
        if (s == null) return null;
        String out = s.replaceAll("\\s*([∧∨≡⇒=+\\-×÷])\\s*", " $1 ");
        out = out.replaceAll("\\s{2,}", " ").trim();
        return out;
    }

    private String firstNonNull(String... candidates) { for (String c : candidates) if (c != null) return c; return null; }

    private static String stripHl(String s) { return s == null ? null : s.replace("{hl}", "").replace("{/hl}", ""); }

    /* ==================== extract DOCX/PDF ==================== */

    private ExtractResult extractTextAndImages(MultipartFile file) {
        String name = (file.getOriginalFilename()==null ? "upload" : file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        try (InputStream is = file.getInputStream()) {
            if (name.endsWith(".docx")) {
                try {
                    return extractWord(is);
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

}
