package com.exam.examserver.controller;

import com.exam.examserver.dto.ai.AiSuggestRequest;
import com.exam.examserver.dto.ai.AiSuggestResponse;
import com.exam.examserver.dto.exam.*;
import com.exam.examserver.dto.importing.CommitRequest;
import com.exam.examserver.dto.importing.PreviewResponse;
import com.exam.examserver.dto.importing.ImportResult;
import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.enums.QuestionType;
import com.exam.examserver.model.exam.CloneRequest;
import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.model.user.CustomUserDetails;
import com.exam.examserver.dto.importing.ImportPreviewStore;
import com.exam.examserver.repo.QuestionBundleRepository;
import com.exam.examserver.service.AiSuggestService;
import com.exam.examserver.service.SubjectService;
import com.exam.examserver.service.import_export.FileArchiveService;
import com.exam.examserver.service.import_export.ImportQuestionService;
import com.exam.examserver.service.import_export.ExportQuestionService;
import com.exam.examserver.service.QuestionService;
import org.apache.tika.Tika;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    private final AiSuggestService aiSuggestService;
    private final QuestionBundleRepository bundleRepo;
    private final Tika tika = new Tika();
    private final Map<Long, Deque<Long>> aiBuckets = new ConcurrentHashMap<>();


    public QuestionController(QuestionService questionService,
                              SubjectService subjectService,
                              ExportQuestionService exportQuestionService,
                              ImportQuestionService importService,
                              ImportPreviewStore previewStore,
                              FileArchiveService fileArchiveService, AiSuggestService aiSuggestService, QuestionBundleRepository bundleRepo) {
        this.questionService = questionService;
        this.subjectService = subjectService;
        this.exportQuestionService = exportQuestionService;
        this.importService = importService;
        this.previewStore = previewStore;
        this.fileArchiveService = fileArchiveService;
        this.aiSuggestService = aiSuggestService;
        this.bundleRepo = bundleRepo;
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionDTO createQuestion(@PathVariable("subjectId") Long subjectId,
                                      @RequestPart("question") CreateQuestionDTO payload,
                                      @RequestPart(value = "images", required = false) List<MultipartFile> images,
                                      @AuthenticationPrincipal CustomUserDetails me) {
        return questionService.create(subjectId, payload, me.getId(), images);
    }

    @PutMapping(value = "/{questionId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public QuestionDTO updateQuestion(@PathVariable("subjectId") Long subjectId,
                                      @PathVariable("questionId") Long questionId,
                                      @RequestPart("question") CreateQuestionDTO payload,
                                      @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return questionService.update(questionId, payload, images);
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

                                                 @RequestBody BulkSelectionRequest sel,
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
        List<Long> questionIds = resolveSelectionToIds(subjectId, sel);
        if (questionIds.isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }
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
                        (program == null ? "" : program),
                        (subj.getDepartment() != null ? subj.getDepartment().getName() : ""),
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

    private List<Long> resolveSelectionToIds(Long subjectId, BulkSelectionRequest sel) {
        if (sel == null || sel.getMode() == null)
            throw new IllegalArgumentException("Invalid selection");

        switch (sel.getMode()) {
            case IDS: {
                List<Long> raw = Optional.ofNullable(sel.getIds()).orElseGet(List::of);
                if (raw.isEmpty()) return raw;
                // Lọc ID thuộc đúng subject và là root (parent IS NULL) cho an toàn
                var filter = new QuestionFilter();
                // (không set gì ngoài q/filter khác, chỉ cần scope subject + root)
                List<Long> scoped = questionService.findIdsByFilter(subjectId, filter);
                var scopedSet = new HashSet<>(scoped);
                return raw.stream().filter(scopedSet::contains).toList();
            }
            case FILTER: {
                QuestionFilter f = Optional.ofNullable(sel.getFilter()).orElseGet(QuestionFilter::new);
                List<Long> allIds = questionService.findIdsByFilter(subjectId, f);
                if (sel.getExcludeIds() != null && !sel.getExcludeIds().isEmpty()) {
                    var exclude = new HashSet<>(sel.getExcludeIds());
                    allIds.removeIf(exclude::contains);
                }
                return allIds;
            }
            default:
                throw new IllegalArgumentException("Unsupported selection mode");
        }
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

    @GetMapping
    public Page<QuestionDTO> listQuestions(
            @PathVariable("subjectId") Long subjectId,
            @RequestParam(name = "labels", required = false) Set<QuestionLabel> labels,
            @RequestParam(name = "difficulty", required = false) Difficulty difficulty,
            @RequestParam(name = "chapter", required = false) Integer chapter,
            @RequestParam(name = "type", required = false) QuestionType type,
            @RequestParam(name = "createdBy", required = false) String createdBy,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name="flagged", required=false) Boolean flagged,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable,
            Authentication auth
    ) {
        boolean privileged = isHeadOrAdmin(auth);

        var filter = new QuestionFilter();
        filter.setLabels(labels);
        filter.setDifficulty(difficulty);
        filter.setChapter(chapter);
        filter.setType(type);
        filter.setCreatedBy(createdBy);
        filter.setFrom(from);
        filter.setTo(to);
        filter.setQ(q);
        filter.setFlagged(flagged);
        Page<QuestionDTO> page = questionService.pageBySubject(subjectId, filter, pageable);

        if (!privileged) {
            page.getContent().forEach(dto -> {
                if (dto.getLabels() != null && dto.getLabels().contains(QuestionLabel.EXAM)) {
                    dto.setAnswer(null);
                    dto.setAnswerText(null);
                }
            });
        }
        return page;
    }

    @GetMapping("/{questionId}/clones")
    public Page<QuestionDTO> listClones(@PathVariable Long subjectId,
                                        @PathVariable Long questionId,
                                        @PageableDefault(size = 20, sort = "cloneIndex")
                                            Pageable pageable,
                                        Authentication auth) {
        boolean privileged = isHeadOrAdmin(auth);
        Page<QuestionDTO> page = questionService.getClones(questionId, pageable);
        if (!privileged) {
            page.getContent().forEach(dto -> {
                if (dto.getLabels() != null && dto.getLabels().contains(QuestionLabel.EXAM)) {
                    dto.setAnswer(null);
                    dto.setAnswerText(null);
                }
            });
        }
        return page;
    }

    @PostMapping("/bulk-delete")
    public ResponseEntity<Map<String, Object>> bulkDelete(@PathVariable Long subjectId,
                                                          @RequestBody BulkSelectionRequest sel) {
        List<Long> ids = resolveSelectionToIds(subjectId, sel);
        if (ids.isEmpty()) {
            return ResponseEntity.ok(Map.of("deleted", 0, "requested", 0));
        }
        int deleted = questionService.deleteAllByIds(ids);
        return ResponseEntity.ok(Map.of("deleted", deleted, "requested", ids.size()));
    }

    @GetMapping("/lookup")
    public List<QuestionDTO> lookupQuestions(@PathVariable Long subjectId,
                                             @RequestParam List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        // lấy theo danh sách, giữ đúng thứ tự ids
        List<QuestionDTO> rows = questionService.findByIds(ids);
        // (tuỳ chọn) lọc về đúng subject + chỉ câu gốc
        return rows.stream()
                .filter(q -> q.getSubjectId() != null && q.getSubjectId().equals(subjectId))
                .filter(q -> q.getParentId() == null)        // chỉ root (nếu DTO có parentId)
                .toList();
    }

    @PostMapping("/ai/suggest")
    public AiSuggestResponse suggestAnswer(@PathVariable Long subjectId,
                                           @RequestBody AiSuggestRequest req,
                                           @AuthenticationPrincipal CustomUserDetails me,
                                           Authentication auth) {
        guardRate(me.getId());
        // quyền tối thiểu: TEACHER/HEAD/ADMIN
        boolean allowed = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> Set.of("ADMIN","HEAD","TEACHER").contains(a.getAuthority()));
        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        }
        return aiSuggestService.suggest(req);
    }

    private void guardRate(Long userId) {
        long now = System.currentTimeMillis(), oneMinAgo = now - 60_000L, oneSecAgo = now - 1000L;
        var q = aiBuckets.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < oneMinAgo) q.pollFirst();
            long last = q.isEmpty() ? 0 : q.peekLast();
            if (last > oneSecAgo) throw new LocalRateLimitException("Too fast, wait a second");
            if (q.size() >= 30) throw new LocalRateLimitException("Too many in a minute");
            q.addLast(now);
        }
    }
    public static class LocalRateLimitException extends RuntimeException {
        public LocalRateLimitException(String msg) { super(msg); }
    }

    // QuestionController
    @GetMapping("/trash")
    public Page<QuestionDTO> listDeleted(
            @PathVariable Long subjectId,
            @RequestParam(name="labels", required=false) Set<QuestionLabel> labels,
            @RequestParam(name="difficulty", required=false) Difficulty difficulty,
            @RequestParam(name="chapter", required=false) Integer chapter,
            @RequestParam(name="type", required=false) QuestionType type,
            @RequestParam(name="createdBy", required=false) String createdBy,
            @RequestParam(name="from", required=false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(name="to", required=false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(name="q", required=false) String q,
            @PageableDefault(size=20, sort="deletedAt", direction=org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable
    ) {
        var f = new QuestionFilter();
        f.setLabels(labels); f.setDifficulty(difficulty); f.setChapter(chapter);
        f.setType(type); f.setCreatedBy(createdBy); f.setFrom(from); f.setTo(to);
        f.setQ(q);
        // service mới: pageDeletedBySubject (giống pageBySubject nhưng dùng QuestionSpecs.deletedOnly())
        return questionService.pageDeletedBySubject(subjectId, f, pageable);
    }

    @PostMapping("/{questionId}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long subjectId, @PathVariable Long questionId) {
        questionService.restore(questionId);
    }

    @PostMapping("/bulk-restore")
    public Map<String,Object> bulkRestore(@PathVariable Long subjectId, @RequestBody BulkSelectionRequest sel) {
        var ids = resolveSelectionToIds(subjectId, sel);
        int ok=0; for (Long id: ids) { questionService.restore(id); ok++; }
        return Map.of("restored", ok, "requested", ids.size());
    }

    @DeleteMapping("/{questionId}/purge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purge(@PathVariable Long subjectId, @PathVariable Long questionId) {
        questionService.purge(questionId);
    }

    @PostMapping("/bulk-purge")
    public Map<String,Object> bulkPurge(@PathVariable Long subjectId, @RequestBody BulkSelectionRequest sel) {
        var ids = resolveSelectionToIds(subjectId, sel);
        int ok=0; for (Long id: ids) { questionService.purge(id); ok++; }
        return Map.of("purged", ok, "requested", ids.size());
    }
}
