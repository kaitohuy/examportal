// src/main/java/com/exam/examserver/service/import_export/AnswerPackService.java
package com.exam.examserver.service.import_export;

import com.exam.examserver.enums.ArchiveVariant;
import com.exam.examserver.model.exam.FileArchive;
import com.exam.examserver.repo.FileArchiveRepository;
import com.exam.examserver.service.QuestionService;
import com.exam.examserver.storage.GcsObjectHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.apache.poi.xwpf.usermodel.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class AnswerPackService {
    private final FileArchiveRepository fileRepo;
    private final ExportQuestionService exportService;
    private final FileArchiveService fileArchiveService;
    private final GcsObjectHelper gcs;
    private final ObjectMapper om = new ObjectMapper();
    private final QuestionService questionService; // dùng khi cần enrich thêm

    public AnswerPackService(FileArchiveRepository fileRepo,
                             ExportQuestionService exportService,
                             FileArchiveService fileArchiveService,
                             GcsObjectHelper gcs,
                             QuestionService questionService) {
        this.fileRepo = fileRepo;
        this.exportService = exportService;
        this.fileArchiveService = fileArchiveService;
        this.gcs = gcs;
        this.questionService = questionService;
    }

    /** Xây gói đáp án từ 1 submission (đã APPROVED); releaseAt có thể null (đặt sau). */
    public FileArchive buildAndSaveAnswerFromSubmission(Long submissionArchiveId,
                                                        Long actorUserId,
                                                        Instant releaseAtOrNull) throws Exception {
        FileArchive sub = fileRepo.findById(submissionArchiveId).orElseThrow();
        if (!"SUBMISSION".equalsIgnoreCase(sub.getKind()))
            throw new IllegalArgumentException("Archive #" + submissionArchiveId + " không phải SUBMISSION");
        if (sub.getVariant() != ArchiveVariant.EXAM)
            throw new IllegalArgumentException("SUBMISSION không phải biến thể EXAM");
        if (sub.getReviewStatus() == null || sub.getReviewStatus().name().equals("PENDING"))
            throw new IllegalStateException("SUBMISSION chưa được duyệt");

        // 1) Đọc ZIP submission từ GCS
        byte[] subZip = gcs.readBytes(sub.getStorageKey());
        if (subZip == null || subZip.length == 0)
            throw new IllegalStateException("Không đọc được nội dung submission ZIP");

        // 1.a) Thử lấy blueprint.json như cũ; nếu không có -> tìm matrix.docx
        Blueprint bp = null;
        byte[] blueprintBytes = null;
        byte[] matrixBytes = null;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(subZip))) {
            for (ZipEntry e; (e = zis.getNextEntry()) != null; ) {
                if (e.isDirectory()) continue;
                String name = e.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith("blueprint.json")) {
                    blueprintBytes = zis.readAllBytes();
                    break; // ưu tiên blueprint.json
                }
                // nếu tên chứa "matrix" hoặc "ma_tran"
                if (matrixBytes == null && (name.contains("matrix") || name.contains("ma_tran"))) {
                    matrixBytes = zis.readAllBytes();
                    // không break ở đây để vẫn ưu tiên blueprint nếu xuất hiện sau
                }
            }
        }

        if (blueprintBytes != null) {
            bp = om.readValue(blueprintBytes, Blueprint.type());
        } else if (matrixBytes != null) {
            MatrixParseResult m = parseMatrixDocx(matrixBytes);
            bp = buildBlueprintFromCodes(m); // map mã -> ID
        } else {
            throw new IllegalStateException("Thiếu blueprint.json và không tìm thấy matrix.docx trong ZIP.");
        }

        // 2) Sinh per-variant DOCX có đáp án
        ByteArrayOutputStream baosZip = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baosZip)) {
            for (int k = 0; k < bp.variants; k++) {
                List<List<Long>> rowsIds = new ArrayList<>();
//                for (Blueprint.Row row : bp.rows) {
//                    List<Long> ids = (k < row.cells.size() ? row.cells.get(k) : List.of());
//                    rowsIds.add(ids == null ? List.of() : ids);
//                }
                for (Blueprint.Row row : bp.rows) {
                    List<Long> qids = new ArrayList<>();

                    if (k < row.cells.size()) {
                        Object cell = row.cells().get(k);

                        // case 1: cell là list ID (format cũ)
                        if (cell instanceof List<?>) {
                            ((List<?>) cell).forEach(n -> {
                                if (n instanceof Number num) qids.add(num.longValue());
                            });
                        }
                        // case 2: cell là object { ids, codes }
                        else if (cell instanceof Map<?, ?> map) {
                            Object idsObj = map.get("ids");
                            Object codesObj = map.get("codes");

                            // ưu tiên ids
                            if (idsObj instanceof List<?>) {
                                ((List<?>) idsObj).forEach(n -> {
                                    if (n instanceof Number num) qids.add(num.longValue());
                                });
                            }
                            // fallback codes
                            else if (codesObj instanceof List<?>) {
                                List<String> codes = new ArrayList<>();
                                ((List<?>) codesObj).forEach(c -> {
                                    if (c instanceof String s && !s.isBlank()) codes.add(s);
                                });
                                if (!codes.isEmpty()) {
                                    Map<String, Long> m = questionService.findIdMapByCodes(codes);
                                    for (String code : codes) {
                                        Long id = m.get(code.trim().toLowerCase(Locale.ROOT));
                                        if (id != null) qids.add(id);
                                    }
                                }
                            }
                        }
                    }

                    rowsIds.add(qids);
                }
                var header = new ExportQuestionService.ExamHeader(
                        "ĐÁP ÁN", "", "", "", "", "", "", "",
                        "", null, "", ""
                );
                byte[] docx = exportService.exportExamFromBlueprintWithAnswers(rowsIds, header);

                // đặt ngay ở root ZIP
                zos.putNextEntry(new ZipEntry(String.format("De_%02d_DapAn.docx", (k + 1))));
                zos.write(docx);
                zos.closeEntry();
            }
        }
        byte[] answerZip = baosZip.toByteArray();

        // 3) Lưu vào file_archive (APPROVED)
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("variant", "ANSWER");
        meta.put("format", "ZIP_DOCX");
        meta.put("examArchiveId", submissionArchiveId);
        meta.put("variants", bp.variants);
        meta.put("releaseAt", releaseAtOrNull == null ? null : releaseAtOrNull.toString());

        String fname = "Dap_an_Sub#" + submissionArchiveId + ".zip";
        return fileArchiveService.save("EXPORT",
                sub.getSubjectId(),
                actorUserId,          // người thực hiện build (HEAD)
                fname,
                "application/zip",
                answerZip,
                meta);
    }

    /* ====== Blueprint schema v2 (hỗ trợ cell dạng object {ids, codes}) ====== */
    public record Blueprint(int variants, List<Row> rows) {
        public static TypeReference<Blueprint> type() { return new TypeReference<>() {}; }
        public Blueprint { Objects.requireNonNull(rows, "rows"); }

        public static record Row(List<Object> cells) {}
    }

    // Tách token mã câu hỏi theo xuống dòng, dấu phẩy, chấm phẩy…
    // GIỮ nguyên raw token (kể cả ")")
    private static List<String> splitCodes(String raw) {
        if (raw == null) return List.of();
        String s = raw
                .replace('\u00A0', ' ')
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .trim();
        if (s.isBlank()) return List.of();
        String[] parts = s.split("[\\s,;\\/|]+"); // tách theo whitespace / , ; / |
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);       // ví dụ: "NH1.11.b)" được GIỮ NGUYÊN
        }
        return out;
    }

    private static class MatrixParseResult {
        int variants;                       // số đề (số cột biến thể)
        List<List<List<String>>> codes;     // [rowIndex][variantIndex] -> list mã (String)
    }

    /** Đọc matrix.docx bytes -> cấu trúc mảng mã theo từng biến thể */
    private MatrixParseResult parseMatrixDocx(byte[] docxBytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            List<XWPFTable> tables = doc.getTables();
            if (tables.isEmpty()) throw new IOException("matrix.docx không có bảng nào.");

            // lấy bảng lớn nhất (an toàn hơn nếu văn bản có nhiều bảng)
            XWPFTable table = tables.stream()
                    .max(Comparator.comparingInt(t -> t.getNumberOfRows() * t.getRow(0).getTableCells().size()))
                    .orElse(tables.get(0));

            // Xác định số cột (đề 1..đề N). Theo ma trận mẫu: cột 0 = “Cấu trúc…”, cột 1 = “Đề 1”, …
            XWPFTableRow header = table.getRow(0);
            int colCount = header.getTableCells().size();
            if (colCount < 3) throw new IOException("matrix.docx: số cột < 3, không đúng định dạng.");

            int variants = colCount - 1; // bỏ cột mô tả

            List<List<List<String>>> rows = new ArrayList<>();
            // Bỏ hàng tiêu đề (i=0). Duyệt tới hết hoặc dừng khi gặp hàng "TỔNG ĐIỂM"
            for (int i = 1; i < table.getNumberOfRows(); i++) {
                XWPFTableRow row = table.getRow(i);
                if (row == null) continue;

                String firstCell = getCellText(row, 0).toUpperCase(Locale.ROOT);
                if (firstCell.contains("TỔNG ĐIỂM")) break;  // kết thúc phần dữ liệu

                List<List<String>> rowPerVariant = new ArrayList<>();
                for (int v = 1; v < colCount; v++) {
                    String cellText = getCellText(row, v);
                    rowPerVariant.add(splitCodes(cellText));
                }
                rows.add(rowPerVariant);
            }

            MatrixParseResult rs = new MatrixParseResult();
            rs.variants = variants;
            rs.codes = rows;
            return rs;
        }
    }

    // Lấy text của 1 ô (gộp paragraph & xuống dòng đúng)
    private static String getCellText(XWPFTableRow row, int colIdx) {
        if (row == null) return "";
        var cell = row.getCell(colIdx);
        if (cell == null) return "";
        return Optional.ofNullable(cell.getText()).orElse("").trim();
    }

    private Blueprint buildBlueprintFromCodes(MatrixParseResult m) {
        // 1) thu thập mã
        Set<String> allCodes = new LinkedHashSet<>();
        for (var row : m.codes)
            for (var variant : row)
                allCodes.addAll(variant);

        // 2) lấy map codeLower -> id
        Map<String, Long> idMap = questionService.findIdMapByCodes(allCodes);

        // 3) báo các mã thiếu (so sánh bằng lower-case)
        List<String> missing = new ArrayList<>();
        for (String c : allCodes) {
            if (!idMap.containsKey(c.toLowerCase(Locale.ROOT))) {
                missing.add(c);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Các mã không tìm thấy: " + String.join(", ", missing));
        }

        // 4) build rows: tra id bằng lower-case
        List<Blueprint.Row> rows = new ArrayList<>();
        for (var row : m.codes) {
            List<Object> cells = new ArrayList<>();
            for (var variantCodes : row) {
                List<Long> ids = new ArrayList<>(variantCodes.size());
                for (String code : variantCodes) {
                    ids.add(idMap.get(code.toLowerCase(Locale.ROOT)));
                }
                cells.add(ids);
            }
            rows.add(new Blueprint.Row(cells));
        }
        return new Blueprint(m.variants, rows);
    }

}
