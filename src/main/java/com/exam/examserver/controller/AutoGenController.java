// src/main/java/com/exam/examserver/controller/AutoGenController.java
package com.exam.examserver.controller;

import com.exam.examserver.dto.autogen.*;
import com.exam.examserver.enums.AutoSettingKind;
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
                                          @RequestParam(name = "kind", defaultValue = "EXAM")
                                          AutoSettingKind kind,
                                          @RequestBody AutoGenRequest req) {
        return autoService.preview(subjectId, req, kind);
    }

    // Ghi dấu sử dụng (lastUsedAt/usageCount) – trả lại ma trận giống preview
    @PostMapping("/commit")
    public AutoGenPreviewResponse commit(@PathVariable Long subjectId,
                                         @RequestParam(name = "kind", defaultValue = "EXAM")
                                         AutoSettingKind kind,
                                         @RequestBody AutoGenRequest req) {
        return autoService.commit(subjectId, req, kind);
    }

    // === NEW: Xuất ZIP gồm N đề (DOCX) + Ma trận (DOCX), đồng thời lưu vào kho ===
    @PostMapping("/export")
    public ResponseEntity<byte[]> exportZip(@PathVariable Long subjectId,
                                            @RequestBody(required = false) AutoGenRequest req,
                                            @RequestParam(defaultValue = "false") boolean commit,
                                            @RequestParam(defaultValue = "false") boolean merge,
                                            @RequestParam(defaultValue = "De_tu_dong") String fileName,

                                            // --- HEADER PARAMS ---
                                            @RequestParam(required = false) String faculty, // (Tên Khoa - map từ FE faculty)
                                            @RequestParam(required = false) String program, // (Tên Bộ môn - map từ FE program nếu có, hoặc lấy từ Department)
                                            @RequestParam(required = false) String semester,
                                            @RequestParam(required = false) String academicYear,
                                            @RequestParam(required = false) String classes,
                                            @RequestParam(defaultValue = "90 phút") String duration,
                                            @RequestParam(defaultValue = "Hình thức thi viết") String examForm,
                                            @RequestParam(required = false, name = "mau") String mauLabel,

                                            // [NEW] 2 Params mới
                                            @RequestParam(defaultValue = "Đại học") String level,
                                            @RequestParam(defaultValue = "Chính quy") String trainingType,

                                            @AuthenticationPrincipal CustomUserDetails me
    ) throws Exception {
        // 1) Chuẩn hoá request
        AutoGenRequest effective = (req == null) ? new AutoGenRequest() : req;
        if (effective.variants <= 0) effective.variants = 5;

        // Luôn dùng preview
        AutoGenPreviewResponse resp = autoService.preview(subjectId, effective);

        // 2) Header cơ sở
        Subject subj = subjectService.getSubjectById(subjectId);

        // Logic ưu tiên: Nếu FE gửi lên thì dùng, không thì lấy từ DB Subject
        String finalFaculty = (faculty != null && !faculty.isBlank()) ? faculty : "";
        // Logic cũ của bạn: Program (Bộ môn) lấy từ Department name
        String finalProgram = (program != null && !program.isBlank()) ? program
                : (subj.getDepartment() != null ? subj.getDepartment().getName() : "");

        // Tạo Base Header (PaperNo = null)
        var baseHeader = new ExportQuestionService.ExamHeader(
                "HỌC VIỆN CÔNG NGHỆ BƯU CHÍNH VIỄN THÔNG",
                finalFaculty, // Khoa
                finalProgram, // Bộ môn
                subj.getName(),
                subj.getCode(),
                (semester == null ? "" : semester),
                (academicYear == null ? "" : academicYear),
                (classes == null ? "" : classes),
                (duration == null ? "" : duration),
                null,
                (examForm == null ? "" : examForm),
                (mauLabel == null ? "" : mauLabel),
                // [NEW] Map 2 trường mới vào cuối
                level,
                trainingType
        );

        // 3) Tạo ZIP in-memory
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            if (merge) {
                // === CASE A: Gộp 1 file ===
                List<List<List<Long>>> allVariants = new ArrayList<>();
                for (int k = 0; k < resp.variants; k++) {
                    List<List<Long>> rowsIds = new ArrayList<>();
                    for (AutoGenRowDTO row : resp.rows) {
                        var cell = row.columns.get(k);
                        rowsIds.add(cell.questionIds == null ? List.of() : cell.questionIds);
                    }
                    allVariants.add(rowsIds);
                }

                // Gọi hàm exportMergedExams (đã update ở bước trước để xử lý level/trainingType)
                System.out.println("resp: " + resp.rows.get(0).clos);
                byte[] mergedDoc = exportService.exportMergedExams(allVariants, resp.rows, baseHeader, false);

                ZipEntry e = new ZipEntry(fileName + "_All.docx");
                zos.putNextEntry(e);
                zos.write(mergedDoc);
                zos.closeEntry();

            } else {
                // === CASE B: Tách rời từng file ===
                for (int k = 0; k < resp.variants; k++) {
                    List<List<Long>> rowsIds = new ArrayList<>();
                    for (AutoGenRowDTO row : resp.rows) {
                        var cell = row.columns.get(k);
                        rowsIds.add(cell.questionIds == null ? List.of() : cell.questionIds);
                    }

                    // [UPDATE] Copy header đầy đủ 14 tham số
                    var variantHeader = new ExportQuestionService.ExamHeader(
                            baseHeader.institute(), baseHeader.faculty(), baseHeader.program(),
                            baseHeader.subjectName(), baseHeader.subjectCode(),
                            baseHeader.semester(), baseHeader.academicYear(),
                            baseHeader.classes(), baseHeader.duration(),
                            k + 1, // Đề số
                            baseHeader.examForm(), baseHeader.mauLabel(),
                            baseHeader.level(),         // Copy level
                            baseHeader.trainingType()   // Copy trainingType
                    );

                    // Dùng hàm exportMergedExams cho list đơn để tận dụng logic header mới
                    // (Thay vì dùng exportExamFromBlueprint cũ nếu hàm đó chưa update header mới)
                    List<List<List<Long>>> singleList = List.of(rowsIds);
                    byte[] docx = exportService.exportMergedExams(singleList, resp.rows, variantHeader, false);

                    ZipEntry e = new ZipEntry(String.format("De_%02d.docx", (k + 1)));
                    zos.putNextEntry(e);
                    zos.write(docx);
                    zos.closeEntry();
                }
            }

            // 3b) Ma trận (Giữ nguyên)
            byte[] matrix = exportService.exportMatrixDocx(resp);
            ZipEntry e = new ZipEntry("Ma_tran.docx");
            zos.putNextEntry(e);
            zos.write(matrix);
            zos.closeEntry();
        }

        byte[] zipBytes = baos.toByteArray();

        // 4) Lưu vào kho file_archive
        Map<String, Object> meta = new HashMap<>();
        meta.put("variant", "EXAM");
        meta.put("format", "ZIP_DOCX");

        // Lưu thêm thông tin merge vào meta để dễ trace
        meta.put("merged", merge);

        meta.put("variants", resp.variants);
        meta.put("totalPerPaper", resp.paperTotals == null ? null :
                Arrays.stream(resp.paperTotals).map(BigDecimal::toPlainString).toList());
        meta.put("rows", resp.rows == null ? 0 : resp.rows.size());
        meta.put("labelScope", (effective.labels == null || effective.labels.isEmpty())
                ? "ALL" : effective.labels);
        String finalName = (fileName == null || fileName.isBlank() ? "Auto_De" : fileName) + ".zip";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", finalName);
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentLength(zipBytes.length);
        return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);
    }
}
