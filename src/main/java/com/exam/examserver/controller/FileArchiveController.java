package com.exam.examserver.controller;

import com.exam.examserver.config.ScopeResolver;
import com.exam.examserver.dto.exam.ReleaseAtDTO;
import com.exam.examserver.dto.importing.FileArchiveDTO;
import com.exam.examserver.dto.importing.PageDTO;
import com.exam.examserver.enums.ArchiveVariant;
import com.exam.examserver.enums.ExamTaskStatus;
import com.exam.examserver.enums.ReviewStatus;
import com.exam.examserver.enums.RoleType;
import com.exam.examserver.model.exam.ExamTask;
import com.exam.examserver.model.exam.FileArchive;
import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.model.user.User;
import com.exam.examserver.repo.ExamTaskRepository;
import com.exam.examserver.repo.FileArchiveRepository;
import com.exam.examserver.repo.SubjectRepository;
import com.exam.examserver.repo.UserRepository;
import com.exam.examserver.service.impl.ExamTaskService;
import com.exam.examserver.service.import_export.AnswerPackService;
import com.exam.examserver.service.import_export.FileArchiveService;
import com.exam.examserver.storage.GcsObjectHelper;
import com.exam.examserver.storage.GcsSignedUrl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
@CrossOrigin("*")
public class FileArchiveController {

    private final FileArchiveService fileArchiveService;
    private final FileArchiveRepository fileRepo;
    private final UserRepository userRepo;
    private final SubjectRepository subjectRepo;
    private final GcsSignedUrl signer;
    private final GcsObjectHelper gcs;
    private final ScopeResolver scopeResolver;
    private final ExamTaskRepository taskRepository;
    private final ExamTaskService examTaskService;
    private final ObjectMapper om = new ObjectMapper();
    private final AnswerPackService answerPackService;

    public FileArchiveController(FileArchiveService fileArchiveService,
                                 FileArchiveRepository fileRepo,
                                 UserRepository userRepo,
                                 SubjectRepository subjectRepo,
                                 GcsSignedUrl signer,
                                 GcsObjectHelper gcs, ScopeResolver scopeResolver, ExamTaskRepository taskRepository, ExamTaskService examTaskService, AnswerPackService answerPackService) {
        this.fileArchiveService = fileArchiveService;
        this.fileRepo = fileRepo;
        this.userRepo = userRepo;
        this.subjectRepo = subjectRepo;
        this.signer = signer;
        this.gcs = gcs;
        this.scopeResolver = scopeResolver;
        this.taskRepository = taskRepository;
        this.examTaskService = examTaskService;
        this.answerPackService = answerPackService;
    }

    // ================= LIST + FILTER =================
//    @GetMapping
//    public PageDTO<FileArchiveDTO> list(
//            @RequestParam(required = false) Long subjectId,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size,
//            @RequestParam(required = false) String kind,      // IMPORT/EXPORT/SUBMISSION
//            @RequestParam(required = false) String q,
//            @RequestParam(required = false) String subject,
//            @RequestParam(required = false) String uploader,
//            @RequestParam(required = false) String from,
//            @RequestParam(required = false) String to,
//            @RequestParam(required = false) String variant,   // EXAM | PRACTICE | ANSWER
//            @RequestParam(required = false) String reviewStatus,
//            @RequestParam(required = false) Long linkedTaskId,
//            @RequestParam(required = false) String viewMode
//    ) {
//        size = Math.min(Math.max(size, 1), 100);
//        Pageable p = PageRequest.of(page, size,
//                Sort.by(Sort.Direction.DESC, "reviewedAt", "createdAt"));
//
//        final String qq = norm(q);
//        final String kk = norm(kind);
//        final String sq = norm(subject);
//        final String uq = norm(uploader);
//        final Instant fromTs = parseDateStart(from);
//        final Instant toTs   = parseDateEnd(to);
//
//        // ---- parse variant (final cho lambda)
//        ArchiveVariant favTmp = null;
//        if (variant != null && !variant.isBlank()) {
//            try { favTmp = ArchiveVariant.valueOf(variant.trim().toUpperCase()); } catch (Exception ignore) {}
//        }
//        final ArchiveVariant fav = favTmp;
//
//        // ---- parse reviewStatus: hỗ trợ CSV như "APPROVED,REJECTED"
//        Set<ReviewStatus> frsSetTmp = null;
//        if (reviewStatus != null && !reviewStatus.isBlank()) {
//            frsSetTmp = Arrays.stream(reviewStatus.split("[,;]"))
//                    .map(String::trim)
//                    .filter(s -> !s.isEmpty())
//                    .map(s -> {
//                        try { return ReviewStatus.valueOf(s.toUpperCase()); }
//                        catch (Exception ignore) { return null; }
//                    })
//                    .filter(Objects::nonNull)
//                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(ReviewStatus.class)));
//        }
//        final Set<ReviewStatus> frsSet = (frsSetTmp != null && !frsSetTmp.isEmpty()) ? EnumSet.copyOf(frsSetTmp) : null;
//
//        // ---- id lists cho search theo text
//        final List<Long> subjIds = (sq != null) ? subjectRepo.searchIdsByKeyword(sq) : null;
//        if (subjIds != null && subjIds.isEmpty()) return PageDTO.from(Page.empty(p));
//
//        final List<Long> usrIds  = (uq != null) ? userRepo.searchIdsByKeyword(uq)  : null;
//        if (usrIds != null && usrIds.isEmpty())  return PageDTO.from(Page.empty(p));
//
//        String vm = norm(viewMode);
//        Specification<FileArchive> spec = scopeSpec(vm);
//        List<Specification<FileArchive>> specs = new ArrayList<>();
//        if (linkedTaskId != null) {
//            var optTask = taskRepository.findById(linkedTaskId);
//            if (optTask.isEmpty() || optTask.get().getSubmissionArchiveId() == null) {
//                return PageDTO.from(Page.empty(p)); // chưa nộp gì -> rỗng
//            }
//            Long archiveId = optTask.get().getSubmissionArchiveId();
//            specs.add((root, cq, cb) -> cb.equal(root.get("id"), archiveId));
//            // tuỳ chọn, “chắc kèo”:
//            specs.add((root, cq, cb) -> cb.equal(cb.upper(root.get("kind")), "SUBMISSION"));
//            // nếu muốn tự ép Pending ở BE khi có linkedTaskId (không bắt buộc):
//            // specs.add((root, cq, cb) -> cb.equal(root.get("reviewStatus"), ReviewStatus.PENDING));
//        }
//        if (subjectId != null) specs.add((root, cq, cb) -> cb.equal(root.get("subjectId"), subjectId));
//        if (subjIds != null)   specs.add((root, cq, cb) -> root.get("subjectId").in(subjIds));
//        if (kk != null)        specs.add((root, cq, cb) -> cb.equal(root.get("kind"), kk));
//        if (fav != null) {
//            specs.add((root, cq, cb) -> cb.equal(root.get("variant"), fav));
//            if (kk == null) specs.add((root, cq, cb) -> cb.equal(root.get("kind"), "EXPORT"));
//        }
//        if (frsSet != null)    specs.add((root, cq, cb) -> root.get("reviewStatus").in(frsSet));
//        if (qq != null)        specs.add((root, cq, cb) -> cb.like(cb.lower(root.get("filename")), "%" + qq.toLowerCase() + "%"));
//        if (usrIds != null)    specs.add((root, cq, cb) -> root.get("userId").in(usrIds));
//        if (fromTs != null)    specs.add((root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromTs));
//        if (toTs != null)      specs.add((root, cq, cb) -> cb.lessThan(root.get("createdAt"), toTs));
//
//        for (var s : specs) spec = spec.and(s);
//        Page<FileArchive> rs = fileRepo.findAll(spec, p);
//
//        // ---- batch resolve names
//        Set<Long> uids = rs.getContent().stream()
//                .map(FileArchive::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
//        Map<Long, String> uploaderMap = uids.isEmpty() ? Map.of()
//                : userRepo.findAllById(uids).stream()
//                .collect(Collectors.toMap(User::getId, FileArchiveController::displayName));
//
//        Set<Long> sids = rs.getContent().stream()
//                .map(FileArchive::getSubjectId).filter(Objects::nonNull).collect(Collectors.toSet());
//        Map<Long, String> subjectMap = sids.isEmpty() ? Map.of()
//                : subjectRepo.findAllById(sids).stream()
//                .collect(Collectors.toMap(Subject::getId, s -> {
//                    String code = s.getCode() == null ? "" : s.getCode().trim();
//                    String name = s.getName() == null ? "" : s.getName().trim();
//                    return (code.isBlank() ? "" : code)
//                            + (code.isBlank() || name.isBlank() ? "" : " - ")
//                            + (name.isBlank() ? "" : name);
//                }));
//
//        Set<Long> reviewerIds = rs.getContent().stream()
//                .map(FileArchive::getReviewedById).filter(Objects::nonNull).collect(Collectors.toSet());
//        Map<Long, String> reviewerMap = reviewerIds.isEmpty() ? Map.of()
//                : userRepo.findAllById(reviewerIds).stream()
//                .collect(Collectors.toMap(User::getId, FileArchiveController::displayName));
//
//        Set<Long> faIds = rs.getContent().stream().map(FileArchive::getId).collect(Collectors.toSet());
//        Map<Long, ExamTask> linkMap = faIds.isEmpty() ? Map.of()
//                : taskRepository.findAllBySubmissionArchiveIdIn(faIds).stream()
//                .collect(Collectors.toMap(ExamTask::getSubmissionArchiveId, t -> t));
//
//        Page<FileArchiveDTO> mapped = rs.map(f -> {
//            Long uid = f.getUserId();
//            Long sid = f.getSubjectId();
//            Long rid = f.getReviewedById();
//
//            String uploaderNameSafe  = (uid != null) ? uploaderMap.getOrDefault(uid, "User #" + uid) : "";
//            String subjectNameSafe   = (sid != null) ? subjectMap.getOrDefault(sid, "") : "";
//            String reviewedByNameSafe= (rid != null) ? reviewerMap.getOrDefault(rid, "") : "";
//            ExamTask linked = linkMap.get(f.getId());
//            Long linkedTaskIdOut = (linked != null ? linked.getId() : null);
//            String linkedTaskStatus = (linked != null && linked.getStatus()!=null) ? linked.getStatus().name() : null;
//
//            return new FileArchiveDTO(
//                    f.getId(), f.getFilename(), f.getMimeType(), f.getSizeBytes(), f.getKind(),
//                    f.getSubjectId(), f.getUserId(), f.getCreatedAt(),
//                    uploaderNameSafe,
//                    subjectNameSafe,
//                    f.getVariant() == null ? null : f.getVariant().name(),
//                    f.getReviewStatus() == null ? null : f.getReviewStatus().name(),
//                    f.getReviewNote(), f.getReviewDeadline(),
//                    f.getReviewedAt(), f.getReviewedById(), reviewedByNameSafe,
//                    linkedTaskIdOut, linkedTaskStatus,
//                    parseReleaseAtFromMeta(f)
//            );
//        });
//
//        return PageDTO.from(mapped);
//    }

    @GetMapping
    public PageDTO<FileArchiveDTO> list(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String kind,      // IMPORT/EXPORT/SUBMISSION
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String uploader,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String variant,   // EXAM | PRACTICE | ANSWER
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) Long linkedTaskId,
            // [NEW] Thêm tham số view: "me" hoặc "subject"
            @RequestParam(defaultValue = "me") String view
    ) {
        size = Math.min(Math.max(size, 1), 100);
        Pageable p = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "reviewedAt", "createdAt"));

        final String qq = norm(q);
        final String kk = norm(kind);
        final String sq = norm(subject);
        final String uq = norm(uploader);
        final Instant fromTs = parseDateStart(from);
        final Instant toTs   = parseDateEnd(to);

        // ---- parse variant
        ArchiveVariant favTmp = null;
        if (variant != null && !variant.isBlank()) {
            try { favTmp = ArchiveVariant.valueOf(variant.trim().toUpperCase()); } catch (Exception ignore) {}
        }
        final ArchiveVariant fav = favTmp;

        // ---- parse reviewStatus
        Set<ReviewStatus> frsSetTmp = null;
        if (reviewStatus != null && !reviewStatus.isBlank()) {
            frsSetTmp = Arrays.stream(reviewStatus.split("[,;]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try { return ReviewStatus.valueOf(s.toUpperCase()); }
                        catch (Exception ignore) { return null; }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(ReviewStatus.class)));
        }
        final Set<ReviewStatus> frsSet = (frsSetTmp != null && !frsSetTmp.isEmpty()) ? EnumSet.copyOf(frsSetTmp) : null;

        // ---- id lists cho search theo text
        final List<Long> subjIds = (sq != null) ? subjectRepo.searchIdsByKeyword(sq) : null;
        if (subjIds != null && subjIds.isEmpty()) return PageDTO.from(Page.empty(p));

        final List<Long> usrIds  = (uq != null) ? userRepo.searchIdsByKeyword(uq)  : null;
        if (usrIds != null && usrIds.isEmpty())  return PageDTO.from(Page.empty(p));

        // [UPDATED] Truyền tham số view vào scopeSpec
        Specification<FileArchive> spec = scopeSpec(view);

        List<Specification<FileArchive>> specs = new ArrayList<>();
        if (linkedTaskId != null) {
            var optTask = taskRepository.findById(linkedTaskId);
            if (optTask.isEmpty() || optTask.get().getSubmissionArchiveId() == null) {
                return PageDTO.from(Page.empty(p));
            }
            Long archiveId = optTask.get().getSubmissionArchiveId();
            specs.add((root, cq, cb) -> cb.equal(root.get("id"), archiveId));
            // Tuỳ chọn: đảm bảo chỉ lấy SUBMISSION
            specs.add((root, cq, cb) -> cb.equal(cb.upper(root.get("kind")), "SUBMISSION"));
        }
        if (subjectId != null) specs.add((root, cq, cb) -> cb.equal(root.get("subjectId"), subjectId));
        if (subjIds != null)   specs.add((root, cq, cb) -> root.get("subjectId").in(subjIds));
        if (kk != null)        specs.add((root, cq, cb) -> cb.equal(root.get("kind"), kk));
        if (fav != null) {
            specs.add((root, cq, cb) -> cb.equal(root.get("variant"), fav));
            if (kk == null) specs.add((root, cq, cb) -> cb.equal(root.get("kind"), "EXPORT"));
        }
        if (frsSet != null)    specs.add((root, cq, cb) -> root.get("reviewStatus").in(frsSet));
        if (qq != null)        specs.add((root, cq, cb) -> cb.like(cb.lower(root.get("filename")), "%" + qq.toLowerCase() + "%"));
        if (usrIds != null)    specs.add((root, cq, cb) -> root.get("userId").in(usrIds));
        if (fromTs != null)    specs.add((root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromTs));
        if (toTs != null)      specs.add((root, cq, cb) -> cb.lessThan(root.get("createdAt"), toTs));

        for (var s : specs) spec = spec.and(s);
        Page<FileArchive> rs = fileRepo.findAll(spec, p);

        // ---- batch resolve names (Giữ nguyên logic cũ)
        Set<Long> uids = rs.getContent().stream()
                .map(FileArchive::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> uploaderMap = uids.isEmpty() ? Map.of()
                : userRepo.findAllById(uids).stream()
                .collect(Collectors.toMap(User::getId, FileArchiveController::displayName));

        Set<Long> sids = rs.getContent().stream()
                .map(FileArchive::getSubjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> subjectMap = sids.isEmpty() ? Map.of()
                : subjectRepo.findAllById(sids).stream()
                .collect(Collectors.toMap(Subject::getId, s -> {
                    String code = s.getCode() == null ? "" : s.getCode().trim();
                    String name = s.getName() == null ? "" : s.getName().trim();
                    return (code.isBlank() ? "" : code)
                            + (code.isBlank() || name.isBlank() ? "" : " - ")
                            + (name.isBlank() ? "" : name);
                }));

        Set<Long> reviewerIds = rs.getContent().stream()
                .map(FileArchive::getReviewedById).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> reviewerMap = reviewerIds.isEmpty() ? Map.of()
                : userRepo.findAllById(reviewerIds).stream()
                .collect(Collectors.toMap(User::getId, FileArchiveController::displayName));

        Set<Long> faIds = rs.getContent().stream().map(FileArchive::getId).collect(Collectors.toSet());
        Map<Long, ExamTask> linkMap = faIds.isEmpty() ? Map.of()
                : taskRepository.findAllBySubmissionArchiveIdIn(faIds).stream()
                .collect(Collectors.toMap(ExamTask::getSubmissionArchiveId, t -> t));

        Page<FileArchiveDTO> mapped = rs.map(f -> {
            Long uid = f.getUserId();
            Long sid = f.getSubjectId();
            Long rid = f.getReviewedById();

            String uploaderNameSafe  = (uid != null) ? uploaderMap.getOrDefault(uid, "User #" + uid) : "";
            String subjectNameSafe   = (sid != null) ? subjectMap.getOrDefault(sid, "") : "";
            String reviewedByNameSafe= (rid != null) ? reviewerMap.getOrDefault(rid, "") : "";
            ExamTask linked = linkMap.get(f.getId());
            Long linkedTaskIdOut = (linked != null ? linked.getId() : null);
            String linkedTaskStatus = (linked != null && linked.getStatus()!=null) ? linked.getStatus().name() : null;

            return new FileArchiveDTO(
                    f.getId(), f.getFilename(), f.getMimeType(), f.getSizeBytes(), f.getKind(),
                    f.getSubjectId(), f.getUserId(), f.getCreatedAt(),
                    uploaderNameSafe,
                    subjectNameSafe,
                    f.getVariant() == null ? null : f.getVariant().name(),
                    f.getReviewStatus() == null ? null : f.getReviewStatus().name(),
                    f.getReviewNote(), f.getReviewDeadline(),
                    f.getReviewedAt(), f.getReviewedById(), reviewedByNameSafe,
                    linkedTaskIdOut, linkedTaskStatus,
                    parseReleaseAtFromMeta(f)
            );
        });

        return PageDTO.from(mapped);
    }

    // ===== Helpers =====
    private static String norm(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }

    private static Instant parseDateStart(String yyyyMMdd) {
        if (yyyyMMdd == null || yyyyMMdd.isBlank()) return null;
        return LocalDate.parse(yyyyMMdd).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private static Instant parseDateEnd(String yyyyMMdd) {
        if (yyyyMMdd == null || yyyyMMdd.isBlank()) return null;
        return LocalDate.parse(yyyyMMdd).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private static String displayName(User u) {
        String f = u.getFirstName() == null ? "" : u.getFirstName().trim();
        String l = u.getLastName()  == null ? "" : u.getLastName().trim();
        String full1 = (f + " " + l).trim();
        String full2 = (l + " " + f).trim();
        if (!full1.isBlank()) return full1;
        if (!full2.isBlank()) return full2;
        if (u.getUsername()!=null && !u.getUsername().isBlank()) return u.getUsername();
        if (u.getEmail()!=null && !u.getEmail().isBlank()) return u.getEmail();
        return "User #" + u.getId();
    }

    // ===== CRUD nhỏ lẻ & URL helpers giữ nguyên =====
    public record UrlDTO(String url) {}

    @GetMapping("/{id}")
    public FileArchive detail(@PathVariable Long id) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureReadable(fa); // NEW
        return fa;
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(@PathVariable Long id) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureReadable(fa); // NEW
        var blob = gcs.stat(fa.getStorageKey());
        return (blob != null) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/view")
    public ResponseEntity<Void> view(@PathVariable Long id,
                                     @RequestParam(defaultValue = "5") long minutes) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureReadable(fa); // NEW
        ensureAnswerReleasedIfNeeded(fa);
        minutes = Math.max(1, Math.min(minutes, 30));
        String url = signer.signInline(fa.getStorageKey(), Duration.ofMinutes(minutes),
                fa.getFilename(), fa.getMimeType());
        return ResponseEntity.status(302).location(URI.create(url)).build();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Void> download(@PathVariable Long id,
                                         @RequestParam(defaultValue = "5") long minutes) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureReadable(fa); // NEW
        ensureAnswerReleasedIfNeeded(fa);
        minutes = Math.max(1, Math.min(minutes, 30));
        String url = signer.signAttachment(
                fa.getStorageKey(), Duration.ofMinutes(minutes), fa.getFilename(), fa.getMimeType()
        );
        return ResponseEntity.status(302).location(URI.create(url)).build();
    }

    @GetMapping("/{id}/view-url")
    public UrlDTO viewUrl(@PathVariable Long id,
                          @RequestParam(defaultValue = "5") long minutes) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureReadable(fa); // NEW
        ensureAnswerReleasedIfNeeded(fa);
        minutes = Math.max(1, Math.min(minutes, 30));
        String url = signer.signInline(fa.getStorageKey(), Duration.ofMinutes(minutes),
                fa.getFilename(), fa.getMimeType());
        return new UrlDTO(url);
    }

    @GetMapping("/{id}/download-url")
    public UrlDTO downloadUrl(@PathVariable Long id,
                              @RequestParam(defaultValue = "5") long minutes) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureReadable(fa); // NEW
        ensureAnswerReleasedIfNeeded(fa);
        minutes = Math.max(1, Math.min(minutes, 30));
        String url = signer.signAttachment(
                fa.getStorageKey(), Duration.ofMinutes(minutes), fa.getFilename(), fa.getMimeType()
        );
        return new UrlDTO(url);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String,Object>> approve(
            @PathVariable Long id,
            @RequestParam(required=false) Long reviewerId,
            @RequestParam(defaultValue="false") boolean approveTask
    ) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureModeratable(fa);
        Long rid = (reviewerId != null) ? reviewerId : currentUserIdOrNull();

        fileArchiveService.approve(id, rid); // move tmp/ -> archives/, mark APPROVED

        boolean taskApproved = false;
        Long taskId = null;

        if (!approveTask) {
            var tOpt = taskRepository.findFirstBySubmissionArchiveId(id);
            if (tOpt.isPresent()) {
                var st = tOpt.get().getStatus();
                if (st == ExamTaskStatus.SUBMITTED || st == ExamTaskStatus.RETURNED) {
                    approveTask = true;
                }
            }
        }

        if (approveTask) {
            var optTask = taskRepository.findFirstBySubmissionArchiveId(id);
            if (optTask.isPresent()) {
                var t = optTask.get();
                if (t.getStatus() == ExamTaskStatus.SUBMITTED || t.getStatus() == ExamTaskStatus.RETURNED) {
                    examTaskService.headApproveDone(rid, t.getId());
                    taskApproved = true;
                    taskId = t.getId();
                }
            }
        }

        // === NEW: nếu là SUBMISSION + EXAM -> build ANSWER ngay
        boolean answerBuilt = false;
        Long answerArchiveId = null;
        String answerError = null;
        try {
            var nowFa = fileRepo.findById(id).orElseThrow();
            if ("SUBMISSION".equalsIgnoreCase(nowFa.getKind())
                    && nowFa.getVariant() != null
                    && "EXAM".equalsIgnoreCase(nowFa.getVariant().name())) {
                var ansRaw = answerPackService.buildAndSaveAnswerFromSubmission(id, rid, null); // releaseAt=null
                var ans = finalizeAnswerArchive(ansRaw, rid);   // <-- SET reviewer + time
                answerBuilt = (ans != null);
                answerArchiveId = (ans == null ? null : ans.getId());

            }
        } catch (Exception e) {
            answerError = e.getMessage();
        }

        Map<String,Object> resp = new LinkedHashMap<>();
        resp.put("approved", true);
        resp.put("taskApproved", taskApproved);
        resp.put("taskId", taskId);
        resp.put("answerBuilt", answerBuilt);
        resp.put("answerArchiveId", answerArchiveId);
        if (answerError != null) resp.put("answerError", answerError);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id,
                                       @RequestBody Map<String, String> body,
                                       @RequestParam(required = false) Long reviewerId,
                                       @RequestParam(defaultValue = "false") boolean rejectTask) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureModeratable(fa);

        final String reason = Optional.ofNullable(body.get("reason")).orElse("").trim();
        final String rawDl  = body.get("deadline");
        final Instant deadlineTs = parseDeadlineFlexible(rawDl);

        Long rid = (reviewerId != null) ? reviewerId : currentUserIdOrNull();

        // 1) Từ chối file
        fileArchiveService.reject(id, rid, reason, deadlineTs);

        // 2) Nếu cần thì đẩy task SUBMITTED về trạng thái yêu cầu nộp lại
        if (rejectTask) {
            taskRepository.findFirstBySubmissionArchiveId(id).ifPresent(t -> {
                if (t.getStatus() == ExamTaskStatus.SUBMITTED) {
                    examTaskService.headReturnForRevision(rid, t.getId(), reason);
                }
            });
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) throws Exception {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureDeletable(fa); // NEW
        fileArchiveService.delete(id);
        return ResponseEntity.noContent().build();
    }


    /** Nhận null | yyyy-MM-dd | ISO-8601. Trả về Instant (UTC). */
    private static Instant parseDeadlineFlexible(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            if (s.contains("T")) {              // ISO-8601 có thời gian
                return Instant.parse(s);
            } else {                             // yyyy-MM-dd -> 23:59:59.999 theo TZ server
                LocalDate d = LocalDate.parse(s);
                return d.atTime(LocalTime.MAX)
                        .atZone(ZoneId.systemDefault())
                        .toInstant();
            }
        } catch (Exception e) {
            // Bạn có thể throw 400 nếu muốn chặt chẽ hơn
            return null;
        }
    }

    private Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;

        Object principal = auth.getPrincipal();
        String username = null;

        if (principal instanceof UserDetails ud) {
            username = ud.getUsername();
        } else if (principal instanceof String s) {
            // nhiều provider đặt principal = username dạng String
            username = s;
        }

        if (username == null || username.isBlank()) return null;

        return userRepo.findByUsername(username)
                .map(User::getId)
                .orElse(null);
    }

    private String currentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object p = auth.getPrincipal();
        if (p instanceof UserDetails ud) return ud.getUsername();
        if (p instanceof String s) return s;
        return null;
    }

//    private Specification<FileArchive> scopeSpec(String viewMode) {
//        var scope = scopeResolver.resolveByUsername(currentUsername());
//
//        // ADMIN: full access
//        if (scope.all) {
//            return (root, cq, cb) -> cb.conjunction();
//        }
//
//        // TEACHER: xem file của mình + file ANSWER APPROVED trong subject của họ
//        if (scope.onlyUserId != null) {
//            return (root, cq, cb) -> {
//                // Điều kiện 1: File do chính mình tạo
//                var ownFiles = cb.equal(root.get("userId"), scope.onlyUserId);
//
//                // Điều kiện 2: File ANSWER APPROVED trong subject mà teacher tham gia
//                var teacherSubjects = scope.subjectIds;
//                if (teacherSubjects != null && !teacherSubjects.isEmpty()) {
//                    var answerInMySubjects = cb.and(
//                            cb.equal(root.get("variant"), ArchiveVariant.ANSWER),
//                            cb.equal(root.get("reviewStatus"), ReviewStatus.APPROVED),
//                            root.get("subjectId").in(teacherSubjects)
//                    );
//                    return cb.or(ownFiles, answerInMySubjects);
//                }
//
//                // Nếu teacher không được assign subject nào -> chỉ thấy file của mình
//                return ownFiles;
//            };
//        }
//
//        // HEAD: xem file trong phạm vi department
//        var sids = scope.subjectIds;
//        if (sids == null || sids.isEmpty()) {
//            return (root, cq, cb) -> cb.disjunction();
//        }
//        return (root, cq, cb) -> root.get("subjectId").in(sids);
//    }

    // [UPDATED] Logic phân quyền và view
    private Specification<FileArchive> scopeSpec(String viewMode) {
        var scope = scopeResolver.resolveByUsername(currentUsername());

        // 1. ADMIN: full access
        if (scope.all) {
            return (root, cq, cb) -> cb.conjunction();
        }

        // 2. TEACHER (có userId trong scope)
        if (scope.onlyUserId != null) {
            // Nếu người dùng muốn xem "Theo học phần" (Tất cả file trong các môn mình dạy)
            if ("subject".equalsIgnoreCase(viewMode)) {
                var sids = scope.subjectIds;
                if (sids == null || sids.isEmpty()) {
                    // Không dạy môn nào -> không thấy gì
                    return (root, cq, cb) -> cb.disjunction();
                }
                // Lọc: subjectId IN (my_teaching_subjects)
                return (root, cq, cb) -> root.get("subjectId").in(sids);
            }

            // Mặc định "me": Chỉ xem file của chính mình
            return (root, cq, cb) -> cb.equal(root.get("userId"), scope.onlyUserId);
        }

        // 3. HEAD (Trưởng bộ môn)
        // HEAD xem theo phạm vi Subject thuộc Department (không dùng view="me" vì quản lý chung)
        var sids = scope.subjectIds;
        if (sids == null || sids.isEmpty()) {
            return (root, cq, cb) -> cb.disjunction();
        }
        return (root, cq, cb) -> root.get("subjectId").in(sids);
    }

    // FileArchiveController
    private void ensureReadable(FileArchive fa) {
        var scope = scopeResolver.resolveByUsername(currentUsername());

        // ADMIN: full access
        if (scope.all) return;

        // TEACHER: đọc mọi file trong các môn họ dạy
        if (scope.onlyUserId != null) {
            var sids = scope.subjectIds;
            if (sids != null && sids.contains(fa.getSubjectId())) return;
            // fallback: vẫn cho file của chính mình (nếu vì lí do nào đó subjectIds trống)
            if (Objects.equals(fa.getUserId(), scope.onlyUserId)) return;
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        // HEAD: trong phạm vi department (subjectIds)
        var sids = scope.subjectIds;
        if (sids == null || !sids.contains(fa.getSubjectId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private void ensureModeratable(FileArchive fa) {
        // ADMIN full; HEAD trong phạm vi dept; TEACHER không được
        var username = currentUsername();
        var userOpt = userRepo.findByUsername(username);
        if (userOpt.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var user = userOpt.get();

        boolean isAdmin = user.getUserRoles().stream().anyMatch(ur -> ur.getRole().getRoleName() == RoleType.ADMIN);
        if (isAdmin) return;

        boolean isHead = user.getUserRoles().stream().anyMatch(ur -> ur.getRole().getRoleName() == RoleType.HEAD);
        if (!isHead) throw new ResponseStatusException(HttpStatus.FORBIDDEN);

        var scope = scopeResolver.resolveByUsername(username);
        var sids = scope.subjectIds;
        if (sids == null || !sids.contains(fa.getSubjectId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    // Bỏ qua gate nếu không phải ANSWER, hoặc nếu user là ADMIN/HEAD (cùng scope)
    private void ensureAnswerReleasedIfNeeded(FileArchive fa) {
        if (fa.getVariant() == null || !"ANSWER".equalsIgnoreCase(fa.getVariant().name())) return;

        // ADMIN/HEAD bypass (vẫn giữ scope check)
        var userOpt = userRepo.findByUsername(currentUsername());
        if (userOpt.isPresent()) {
            var user = userOpt.get();
            boolean isAdmin = user.getUserRoles().stream().anyMatch(ur -> ur.getRole().getRoleName() == RoleType.ADMIN);
            boolean isHead  = user.getUserRoles().stream().anyMatch(ur -> ur.getRole().getRoleName() == RoleType.HEAD);
            if (isAdmin) return;
            if (isHead) {
                // HEAD phải nằm trong scope môn học
                var scope = scopeResolver.resolveByUsername(currentUsername());
                if (scope.subjectIds != null && scope.subjectIds.contains(fa.getSubjectId())) return;
            }
        }

        // Parse releaseAt từ metaJson (ISO-8601)
        Instant releaseAt = parseReleaseAtFromMeta(fa);
        if (releaseAt == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Đáp án chưa được đặt lịch mở (releaseAt=null).");
        if (Instant.now().isBefore(releaseAt))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chưa đến thời gian mở đáp án.");
    }

    private Instant parseReleaseAtFromMeta(FileArchive fa) {
        try {
            String meta = fa.getMetaJson();
            if (meta == null || meta.isBlank()) return null;
            Map<String, Object> m = om.readValue(meta, new TypeReference<>() {});
            Object v = m.get("releaseAt");
            if (v == null) return null;
            if (v instanceof String s && !s.isBlank()) {
                // Hỗ trợ cả "yyyy-MM-ddTHH:mm:ssZ" hoặc "yyyy-MM-dd"
                try {
                    return Instant.parse(s);
                } catch (DateTimeParseException e) {
                    // nếu chỉ có yyyy-MM-dd -> mặc định 00:00:00 theo TZ hệ thống
                    var d = LocalDate.parse(s);
                    return d.atStartOfDay(ZoneId.systemDefault()).toInstant();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    @PatchMapping("/{id}/release-at")
    public ReleaseAtDTO setReleaseAt(@PathVariable Long id, @RequestBody Map<String, String> body) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureModeratable(fa); // HEAD/ADMIN + scope

        String raw = body == null ? null : body.get("releaseAt"); // cho phép null để xoá
        Instant ts = null;
        if (raw != null && !raw.isBlank()) {
            try {
                // Ưu tiên ISO-8601, fallback yyyy-MM-dd (00:00:00 TZ server)
                ts = raw.contains("T")
                        ? Instant.parse(raw)
                        : LocalDate.parse(raw).atStartOfDay(ZoneId.systemDefault()).toInstant();
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Định dạng releaseAt không hợp lệ.");
            }
        }

        var saved = fileArchiveService.updateReleaseAt(id, ts);
        // Lấy lại giá trị đã lưu (string trong meta)
        Instant savedTs = parseReleaseAtFromMeta(saved);
        return new ReleaseAtDTO(saved.getId(), savedTs == null ? null : savedTs.toString());
    }

    @GetMapping("/{id}/release-at")
    public ReleaseAtDTO getReleaseAt(@PathVariable Long id) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureModeratable(fa);
        Instant ts = parseReleaseAtFromMeta(fa);
        return new ReleaseAtDTO(fa.getId(), ts == null ? null : ts.toString());
    }

    @PostMapping("/upload-answer")
    public ResponseEntity<FileArchiveDTO> uploadAnswer(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long subjectId
    ) throws Exception {
        // 1) Lấy user hiện tại
        var username = currentUsername();
        var userOpt = userRepo.findByUsername(username);
        if (userOpt.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var user = userOpt.get();

        // 2) Kiểm tra quyền: TEACHER/HEAD/ADMIN trong scope môn học
        boolean isAdmin = user.getUserRoles().stream()
                .anyMatch(ur -> ur.getRole().getRoleName() == RoleType.ADMIN);

        if (!isAdmin) {
            // Kiểm tra scope (HEAD hoặc TEACHER đều phải có môn học trong scope)
            var scope = scopeResolver.resolveByUsername(username);
            if (scope.subjectIds == null || !scope.subjectIds.contains(subjectId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Bạn không có quyền upload đáp án cho môn học này");
            }
        }

        // 3) Validate file
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File không được rỗng");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "answer_manual_" + System.currentTimeMillis() + ".zip";
        }

        // 4) Tạo metadata (không có releaseAt)
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("variant", "ANSWER");
        meta.put("format", "MANUAL_UPLOAD"); // đánh dấu là upload thủ công
        meta.put("uploadedBy", user.getId());
        meta.put("uploadedAt", Instant.now().toString());
        // releaseAt sẽ được set sau qua PATCH /{id}/release-at

        // 5) Lưu vào file_archive - mặc định APPROVED (có gate releaseAt bảo vệ)
        byte[] fileBytes = file.getBytes();
        FileArchive saved = fileArchiveService.save(
                "EXPORT",
                subjectId,
                user.getId(),
                filename,
                file.getContentType() != null ? file.getContentType() : "application/zip",
                fileBytes,
                meta
        );

        // 6) Reload để lấy đầy đủ thông tin
        saved = fileRepo.findById(saved.getId()).orElseThrow();

        // 7) Build DTO để trả về
        String uploaderName = displayName(user);
        String subjectName = subjectRepo.findById(subjectId)
                .map(s -> {
                    String code = s.getCode() == null ? "" : s.getCode().trim();
                    String name = s.getName() == null ? "" : s.getName().trim();
                    return (code.isBlank() ? "" : code)
                            + (code.isBlank() || name.isBlank() ? "" : " - ")
                            + (name.isBlank() ? "" : name);
                })
                .orElse("");

        FileArchiveDTO dto = new FileArchiveDTO(
                saved.getId(), saved.getFilename(), saved.getMimeType(),
                saved.getSizeBytes(), saved.getKind(),
                saved.getSubjectId(), saved.getUserId(), saved.getCreatedAt(),
                uploaderName, subjectName,
                saved.getVariant() == null ? null : saved.getVariant().name(),
                saved.getReviewStatus() == null ? null : saved.getReviewStatus().name(),
                saved.getReviewNote(), saved.getReviewDeadline(),
                saved.getReviewedAt(), saved.getReviewedById(), uploaderName,
                null, null,
                null // releaseAt = null ban đầu
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/{submissionArchiveId}/regenerate-answer")
    public ResponseEntity<Map<String, Object>> regenerateAnswer(
            @PathVariable Long submissionArchiveId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        // 1) Kiểm tra submission tồn tại
        var submission = fileRepo.findById(submissionArchiveId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy submission"));

        // 2) Validate là SUBMISSION + EXAM + APPROVED
        if (!"SUBMISSION".equalsIgnoreCase(submission.getKind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File này không phải submission");
        }

        if (submission.getVariant() != ArchiveVariant.EXAM) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Submission không phải loại EXAM");
        }

        if (submission.getReviewStatus() != ReviewStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Submission chưa được duyệt, không thể sinh đáp án");
        }

        // 3) Kiểm tra quyền (phải là HEAD/ADMIN trong scope)
        ensureModeratable(submission);

        // 4) Parse releaseAt từ body (optional)
        String releaseAtStr = (body != null) ? body.get("releaseAt") : null;
        Instant releaseAtTs = null;
        if (releaseAtStr != null && !releaseAtStr.isBlank()) {
            try {
                releaseAtTs = releaseAtStr.contains("T")
                        ? Instant.parse(releaseAtStr)
                        : LocalDate.parse(releaseAtStr).atStartOfDay(ZoneId.systemDefault()).toInstant();
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Định dạng releaseAt không hợp lệ");
            }
        }

        // 5) Lấy userId hiện tại
        Long actorUserId = currentUserIdOrNull();
        if (actorUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Không xác định được người dùng hiện tại");
        }

        // 6) Sinh đáp án
        try {
            FileArchive answerArchive = finalizeAnswerArchive(
                    answerPackService.buildAndSaveAnswerFromSubmission(
                            submissionArchiveId,
                            actorUserId,
                            releaseAtTs
                    ),
                    actorUserId // HEAD/ADMIN đang thao tác
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Sinh đáp án thành công");
            response.put("answerArchiveId", answerArchive.getId());
            response.put("filename", answerArchive.getFilename());
            response.put("releaseAt", releaseAtTs == null ? null : releaseAtTs.toString());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException | IllegalStateException e) {
            // Lỗi validation từ AnswerPackService
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);

        } catch (Exception e) {
            // Lỗi khác (IO, parsing, v.v.)
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("error", "Lỗi khi sinh đáp án: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private void ensureDeletable(FileArchive fa) {
        var username = currentUsername();
        var userOpt = userRepo.findByUsername(username);
        if (userOpt.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var user = userOpt.get();

        boolean isAdmin = user.getUserRoles().stream()
                .anyMatch(ur -> ur.getRole().getRoleName() == RoleType.ADMIN);
        if (isAdmin) return; // ADMIN full access

        boolean isHead = user.getUserRoles().stream()
                .anyMatch(ur -> ur.getRole().getRoleName() == RoleType.HEAD);

        if (isHead) {
            var scope = scopeResolver.resolveByUsername(username);
            var sids = scope.subjectIds;
            if (sids == null || !sids.contains(fa.getSubjectId()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);

            // HEAD có thể xóa cả file ANSWER (kể cả APPROVED)
            return;
        }

        // TEACHER: chỉ xoá của mình
        if (!Objects.equals(fa.getUserId(), user.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);

        // TEACHER không được xóa EXPORT APPROVED (trừ ANSWER - vì ANSWER có gate releaseAt)
        if ("EXPORT".equalsIgnoreCase(fa.getKind())
                && fa.getReviewStatus() == ReviewStatus.APPROVED
                && fa.getVariant() != ArchiveVariant.ANSWER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Không thể xóa file EXPORT đã được duyệt");
        }
    }

    private FileArchive finalizeAnswerArchive(FileArchive answer, Long reviewerId) {
        if (answer == null) return null;
        // Nếu chưa có status thì set APPROVED
        if (answer.getReviewStatus() == null) {
            answer.setReviewStatus(ReviewStatus.APPROVED);
        }
        answer.setReviewedAt(Instant.now());
        if (reviewerId != null) {
            answer.setReviewedById(reviewerId);
        }
        return fileRepo.save(answer);
    }

}
