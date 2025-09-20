package com.exam.examserver.controller;

import com.exam.examserver.dto.exam.CreateQuestionDTO;
import com.exam.examserver.dto.exam.QuestionDTO;
import com.exam.examserver.dto.importing.CommitRequest;
import com.exam.examserver.dto.importing.PreviewResponse;
import com.exam.examserver.dto.importing.ImportResult;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.model.exam.CloneRequest;
import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.model.user.CustomUserDetails;
import com.exam.examserver.dto.importing.ImportPreviewStore;
import com.exam.examserver.service.SubjectService;
import com.exam.examserver.service.import_export.FileArchiveService;
import com.exam.examserver.service.import_export.ImportQuestionService;
import com.exam.examserver.service.import_export.ExportQuestionService;
import com.exam.examserver.service.QuestionService;
import org.apache.tika.Tika;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/subject/{subjectId}/questions")
@CrossOrigin("*")
public class QuestionController {

    private final QuestionService questionService;
    private final SubjectService subjectService;
    private final ExportQuestionService exportQuestionService;
    private final ImportQuestionService importService;
    private final ImportPreviewStore previewStore;
    private final FileArchiveService fileArchiveService;
    private final Tika tika = new Tika();

    public QuestionController(QuestionService questionService,
                              SubjectService subjectService,
                              ExportQuestionService exportQuestionService,
                              ImportQuestionService importService,
                              ImportPreviewStore previewStore,
                              FileArchiveService fileArchiveService) {
        this.questionService = questionService;
        this.subjectService = subjectService;
        this.exportQuestionService = exportQuestionService;
        this.importService = importService;
        this.previewStore = previewStore;
        this.fileArchiveService = fileArchiveService;
    }

    @GetMapping
    public List<QuestionDTO> listQuestions(@PathVariable("subjectId") Long subjectId,
                                           @RequestParam(name = "labels", required = false) Set<QuestionLabel> labels,
                                           Authentication auth) {
        boolean privileged = isHeadOrAdmin(auth);

        List<QuestionDTO> items = (labels == null || labels.isEmpty())
                ? questionService.getAllBySubject(subjectId)
                : questionService.getAllBySubject(subjectId, labels);

        if (!privileged) {
            items.forEach(dto -> {
                if (dto.getLabels() != null && dto.getLabels().contains(QuestionLabel.EXAM)) {
                    dto.setAnswer(null);
                    dto.setAnswerText(null);
                }
            });
        }
        return items;
    }

    @GetMapping("/{questionId}")
    public ResponseEntity<QuestionDTO> getQuestion(@PathVariable("subjectId") Long subjectId,
                                                   @PathVariable("questionId") Long questionId,
                                                   Authentication auth) {
        boolean privileged = isHeadOrAdmin(auth);
        QuestionDTO dto = questionService.getById(questionId);

        if (!privileged && dto.getLabels() != null && dto.getLabels().contains(QuestionLabel.EXAM)) {
            dto.setAnswer(null);
            dto.setAnswerText(null);
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionDTO createQuestion(@PathVariable("subjectId") Long subjectId,
                                      @RequestPart("question") CreateQuestionDTO payload,
                                      @RequestPart(value = "image", required = false) MultipartFile image,
                                      @AuthenticationPrincipal CustomUserDetails me) {
        Long userId = me.getId();
        return questionService.create(subjectId, payload, userId, image);
    }

    @PutMapping(value = "/{questionId}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public QuestionDTO updateQuestion(@PathVariable("subjectId") Long subjectId,
                                      @PathVariable("questionId") Long questionId,
                                      @RequestPart("question") CreateQuestionDTO payload,
                                      @RequestPart(value = "image", required = false) MultipartFile image) {
        return questionService.update(questionId, payload, image);
    }

    @DeleteMapping("/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuestion(@PathVariable("subjectId") Long subjectId,
                               @PathVariable("questionId") Long questionId) {
        questionService.delete(questionId);
    }

    // ===== Export =====
    @PostMapping("/export")
    public ResponseEntity<byte[]> exportSelected(@PathVariable Long subjectId,
                                                 @RequestBody List<Long> questionIds,
                                                 @AuthenticationPrincipal(expression = "id") Long userId,
                                                 Authentication auth,
                                                 @RequestParam(defaultValue = "file") String fileName,
                                                 @RequestParam(defaultValue = "false") boolean includeAnswers,
                                                 @RequestParam(defaultValue = "pdf") String format,
                                                 @RequestParam(defaultValue = "practice") String variant,
                                                 @RequestParam(defaultValue = "TU_LUAN") String form,
                                                 @RequestParam(required = false) String semester,
                                                 @RequestParam(required = false) String academicYear,
                                                 @RequestParam(required = false) String classes,
                                                 @RequestParam(required = false) String duration,
                                                 @RequestParam(required = false) Integer paperNo,
                                                 @RequestParam(required = false) String examForm,
                                                 @RequestParam(required = false) String program,
                                                 @RequestParam(required = false, name = "mau") String mauLabel,
                                                 @RequestParam(defaultValue = "Đại học chính quy") String level,
                                                 // NEW: PRACTICE có tùy chọn lưu
                                                 @RequestParam(defaultValue = "false") boolean saveCopy
    ) throws Exception {

        System.out.println("program: " + program);
        Subject subj = subjectService.getSubjectById(subjectId);

        // ---- Generate file (PDF/DOCX) giữ nguyên như cũ ----
        byte[] data;
        boolean isDocx = "docx".equalsIgnoreCase(format) || "word".equalsIgnoreCase(format);
        String fileNameWithExt;

        if (isDocx) {
            if ("practice".equalsIgnoreCase(variant)) {
                String bankTitle = "NGÂN HÀNG CÂU HỎI THI " +
                        ("TRAC_NGHIEM".equalsIgnoreCase(form) ? "TRẮC NGHIỆM" : "TỰ LUẬN");

                ExportQuestionService.PracticeHeader ph = new ExportQuestionService.PracticeHeader(
                        bankTitle,
                        subj.getName(),
                        subj.getCode(),
                        subj.getDepartment() != null ? subj.getDepartment().getName() : "",
                        level
                );
                data = exportQuestionService.exportQuestionsToWordPractice(questionIds, includeAnswers, ph);
            } else {
                ExportQuestionService.ExamHeader eh = new ExportQuestionService.ExamHeader(
                        "HỌC VIỆN CÔNG NGHỆ BƯU CHÍNH VIỄN THÔNG",
                        (subj.getDepartment() != null ? subj.getDepartment().getName() : ""),
                        (program == null ? "" : program),
                        subj.getName(),
                        subj.getCode(),
                        (semester == null ? "" : semester),
                        (academicYear == null ? "" : academicYear),
                        (classes == null ? "" : classes),
                        (duration == null ? "" : duration),
                        paperNo,
                        (examForm == null ? "" : examForm),
                        (mauLabel == null ? "" : mauLabel)
                );
                data = exportQuestionService.exportQuestionsToWordExam(questionIds, includeAnswers, eh);
            }
            fileNameWithExt = fileName + ".docx";
        } else {
            data = exportQuestionService.exportQuestionsToPdf(questionIds, includeAnswers);
            fileNameWithExt = fileName + ".pdf";
        }

        // Chuẩn hóa variant/format để đưa vào meta & quyết định luồng lưu
        final boolean isExamVariant = "exam".equalsIgnoreCase(variant);
        final String vUpper = isExamVariant ? "EXAM" : "PRACTICE";
        final String fmtUpper = isDocx ? "DOCX" : "PDF";

        String mime = isDocx
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                : "application/pdf";

        Map<String, Object> meta = new HashMap<>();
        meta.put("variant", vUpper);          // EXAM | PRACTICE
        meta.put("format", fmtUpper);         // PDF | DOCX
        meta.put("includeAnswers", includeAnswers);
        meta.put("form", form);
        meta.put("semester", semester);
        meta.put("academicYear", academicYear);
        meta.put("classes", classes);
        meta.put("duration", duration);
        meta.put("paperNo", paperNo);
        meta.put("examForm", examForm);
        meta.put("program", program);
        meta.put("mauLabel", mauLabel);

        // === Chính sách lưu ===
        if (isExamVariant) {
            // EXAM → luôn lưu (FE bỏ nút saveCopy với EXAM)
            boolean privileged = isHeadOrAdmin(auth);
            if (privileged) {
                // HEAD/ADMIN → lưu thẳng APPROVED
                fileArchiveService.save("EXPORT", subjectId, userId, fileNameWithExt, mime, data, meta);
            } else {
                // TEACHER → PENDING
                fileArchiveService.savePendingExport(subjectId, userId, fileNameWithExt, mime, data, meta);
            }
        } else {
            // PRACTICE: nếu saveCopy=true → lưu APPROVED; false → chỉ tải về
            if (saveCopy) {
                fileArchiveService.save("EXPORT", subjectId, userId, fileNameWithExt, mime, data, meta);
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", fileNameWithExt);
        headers.setContentType(isDocx
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                : MediaType.APPLICATION_PDF);
        headers.setContentLength(data.length);

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    // ====== PREVIEW (import) ======
    @PostMapping(value = {"/preview", "/import/preview"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PreviewResponse preview(@PathVariable Long subjectId,
                                   @RequestPart("file") MultipartFile file,
                                   @RequestParam(defaultValue = "false") boolean saveCopy,
                                   @RequestParam(name = "labels", required = false) Set<QuestionLabel> defaultLabels) {
        return importService.buildPreview(subjectId, file, saveCopy, defaultLabels);
    }

    @PostMapping(value = {"/commit", "/import/commit"})
    public ImportResult commit(@PathVariable Long subjectId,
                               @RequestBody CommitRequest req,
                               @AuthenticationPrincipal CustomUserDetails me,
                               @RequestParam(defaultValue = "false") boolean saveCopy) {
        return importService.commitPreview(subjectId, me.getId(), req, saveCopy);
    }

    @GetMapping("/image/{sessionId}/{idx}")
    public ResponseEntity<byte[]> previewImage(@PathVariable String sessionId,
                                               @PathVariable int idx) {
        var s = previewStore.get(sessionId);
        if (s == null || idx < 0 || idx >= s.images.size()) return ResponseEntity.notFound().build();
        byte[] bytes = s.images.get(idx);
        String mime = tika.detect(bytes);
        MediaType mt = MediaType.parseMediaType(mime);
        return ResponseEntity.ok().contentType(mt).body(bytes);
    }

    // ===== Clone endpoints =====
    @GetMapping("/{questionId}/clones")
    public List<QuestionDTO> listClones(@PathVariable Long subjectId,
                                        @PathVariable Long questionId,
                                        Authentication auth) {
        boolean privileged = isHeadOrAdmin(auth);
        List<QuestionDTO> items = questionService.getClones(questionId);
        if (!privileged) {
            items.forEach(dto -> {
                if (dto.getLabels() != null && dto.getLabels().contains(QuestionLabel.EXAM)) {
                    dto.setAnswer(null);
                    dto.setAnswerText(null);
                }
            });
        }
        return items;
    }

    @PostMapping("/{questionId}/clone")
    public List<QuestionDTO> cloneQuestion(@PathVariable Long subjectId,
                                           @PathVariable Long questionId,
                                           @RequestBody CloneRequest req,
                                           @AuthenticationPrincipal CustomUserDetails me) {
        return questionService.cloneQuestion(subjectId, questionId, me.getId(), req);
    }

    // ===== helpers =====
    private boolean isHeadOrAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN") || a.getAuthority().equals("HEAD"));
    }
}
