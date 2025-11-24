// src/main/java/com/exam/examserver/controller/AutoGenController.java
package com.exam.examserver.controller;

import com.exam.examserver.dto.autogen.*;
import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.model.user.CustomUserDetails;
import com.exam.examserver.repo.QuestionRepository;
import com.exam.examserver.service.SubjectService;
import com.exam.examserver.service.auto.AutoPaperService;
import com.exam.examserver.service.import_export.ExportQuestionService;
import com.exam.examserver.service.import_export.FileArchiveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/subject/{subjectId}/autogen")
@CrossOrigin("*")
public class AutoGenController {

    private final AutoPaperService autoService;
    private final ExportQuestionService exportService;
    private final SubjectService subjectService;
    private final FileArchiveService fileArchiveService;
    private final QuestionRepository questionRepo;
    private final ObjectMapper om = new ObjectMapper();

    public AutoGenController(AutoPaperService autoService,
                             ExportQuestionService exportService,
                             SubjectService subjectService, FileArchiveService fileArchiveService, QuestionRepository questionRepo
    ) {
        this.autoService = autoService;
        this.exportService = exportService;
        this.subjectService = subjectService;
        this.fileArchiveService = fileArchiveService;
        this.questionRepo = questionRepo;
    }

    // Xem ma trận pick (không ghi DB)
    @PostMapping("/preview")
    public AutoGenPreviewResponse preview(@PathVariable Long subjectId,
                                          @RequestBody AutoGenRequest req) {
        return autoService.preview(subjectId, req);
    }

    // Ghi dấu sử dụng (lastUsedAt/usageCount) – trả lại ma trận giống preview
    @PostMapping("/commit")
    public AutoGenPreviewResponse commit(@PathVariable Long subjectId,
                                         @RequestBody AutoGenRequest req) {
        return autoService.commit(subjectId, req);
    }

    // === NEW: Xuất ZIP gồm N đề (DOCX) + Ma trận (DOCX), đồng thời lưu vào kho ===
    @PostMapping("/export")
    public ResponseEntity<byte[]> exportZip(@PathVariable Long subjectId,
                                            @RequestBody(required = false) AutoGenRequest req,
                                            //@RequestParam(defaultValue = "true") boolean commit,
                                            @RequestParam(defaultValue = "false") boolean commit,

                                            @RequestParam(defaultValue = "De_tu_dong") String fileName,
                                            // Header tuỳ chọn (có thể bỏ trống – dùng default)
                                            @RequestParam(required = false) String program,
                                            @RequestParam(required = false) String semester,
                                            @RequestParam(required = false) String academicYear,
                                            @RequestParam(required = false) String classes,
                                            @RequestParam(defaultValue = "90 phút") String duration,
                                            @RequestParam(defaultValue = "Hình thức thi viết") String examForm,
                                            @RequestParam(required = false, name = "mau") String mauLabel,
                                            @AuthenticationPrincipal CustomUserDetails me
    ) throws Exception {
        // 1) Chuẩn hoá request (nếu FE không gửi gì ⇒ variants=5, steps mặc định do service build)
        AutoGenRequest effective = (req == null) ? new AutoGenRequest() : req;
        if (effective.variants <= 0) effective.variants = 5;

//        AutoGenPreviewResponse resp = commit
//                ? autoService.commit(subjectId, effective)
//                : autoService.preview(subjectId, effective);

        // Luôn dùng preview: không ghi usage ở giai đoạn export
        AutoGenPreviewResponse resp = autoService.preview(subjectId, effective);

        // 2) Header cho đề thi (dùng thông tin môn)
        Subject subj = subjectService.getSubjectById(subjectId);
        String faculty = subj.getDepartment() != null ? subj.getDepartment().getName() : "";

        // 3) Tạo ZIP in-memory
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // 3a) Các đề: De_01.docx ... De_N.docx
            for (int k = 0; k < resp.variants; k++) {
                // gom ID theo từng "Câu"
                List<List<Long>> rowsIds = new ArrayList<>();
                for (AutoGenRowDTO row : resp.rows) {
                    var cell = row.columns.get(k);
                    rowsIds.add(cell.questionIds == null ? List.of() : cell.questionIds);
                }

                var header = new ExportQuestionService.ExamHeader(
                        "HỌC VIỆN CÔNG NGHỆ BƯU CHÍNH VIỄN THÔNG",
                        faculty,
                        (program == null ? "" : program),
                        subj.getName(),
                        subj.getCode(),
                        (semester == null ? "" : semester),
                        (academicYear == null ? "" : academicYear),
                        (classes == null ? "" : classes),
                        (duration == null ? "" : duration),
                        k + 1,                               // Đề số
                        (examForm == null ? "" : examForm),
                        (mauLabel == null ? "" : mauLabel)
                );

                byte[] docx = exportService.exportExamFromBlueprint(rowsIds, header);

                ZipEntry e = new ZipEntry(String.format("De_%02d.docx", (k + 1)));
                zos.putNextEntry(e);
                zos.write(docx);
                zos.closeEntry();
            }

            // 3b) Ma trận: Ma_tran.docx
            byte[] matrix = exportService.exportMatrixDocx(resp);
            ZipEntry e = new ZipEntry("Ma_tran.docx");
            zos.putNextEntry(e);
            zos.write(matrix);
            zos.closeEntry();

            // 3c) blueprint.json — để build đáp án khi approve submission
            String bpJson = buildBlueprintJson(resp);
            ZipEntry eBp = new ZipEntry("blueprint.json");
            zos.putNextEntry(eBp);
            zos.write(bpJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        byte[] zipBytes = baos.toByteArray();

        // 4) Lưu vào kho file_archive
        Map<String, Object> meta = new HashMap<>();
        meta.put("variant", "EXAM");
        meta.put("format", "ZIP_DOCX");
        meta.put("variants", resp.variants);
        meta.put("totalPerPaper", resp.paperTotals == null ? null :
                Arrays.stream(resp.paperTotals).map(BigDecimal::toPlainString).toList());
        meta.put("rows", resp.rows == null ? 0 : resp.rows.size());
        meta.put("labelScope", (effective.labels == null || effective.labels.isEmpty())
                ? "ALL" : effective.labels);
        String finalName = (fileName == null || fileName.isBlank() ? "Auto_De" : fileName) + ".zip";
        // mới: lưu vào tmp, đặt trạng thái PENDING để duyệt
//        fileArchiveService.savePendingExport(
//                subjectId,
//                me.getId(),
//                finalName,                     // chú ý: chỉ basename, KHÔNG chứa "archives/"
//                "application/zip",
//                zipBytes,
//                meta
//        );

        // 5) Trả về file cho client
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", finalName);
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentLength(zipBytes.length);
        return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);
    }

    private String buildBlueprintJson(AutoGenPreviewResponse resp) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        int N = Math.max(0, resp.variants);
        int R = (resp.rows == null ? 0 : resp.rows.size());

        root.put("variants", N);
        List<Map<String,Object>> rows = new ArrayList<>();
        for (int r = 0; r < R; r++) {
            var row = resp.rows.get(r);
            List<Map<String, Object>> cells = new ArrayList<>(N);

            for (int k = 0; k < N; k++) {
                var cell = row.columns.get(k);
                List<Long> ids = (cell == null || cell.questionIds == null)
                        ? List.of() : cell.questionIds;

                // map id -> code (nếu Question có trường code; đổi getter cho đúng thực tế của bạn)
                List<String> codes = ids.isEmpty()
                        ? List.of()
                        : questionRepo.findAllById(ids).stream()
                        .map(q -> q.getQuestionCode()) // hoặc getCode()
                        .filter(Objects::nonNull)
                        .toList();

                Map<String, Object> cellObj = new LinkedHashMap<>();
                cellObj.put("ids", ids);
                cellObj.put("codes", codes); // để rỗng nếu bạn chưa cần

                cells.add(cellObj);
            }
            Map<String,Object> rowObj = new LinkedHashMap<>();
            rowObj.put("cells", cells);
            rows.add(rowObj);
        }
        root.put("rows", rows);
        return om.writeValueAsString(root);
    }
}
