package com.exam.examserver.controller;

import com.exam.examserver.config.ScopeResolver;
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
import com.exam.examserver.service.import_export.FileArchiveService;
import com.exam.examserver.storage.GcsObjectHelper;
import com.exam.examserver.storage.GcsSignedUrl;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
@CrossOrigin("*")
public class FileArchiveController {

    private final FileArchiveService service;
    private final FileArchiveRepository fileRepo;
    private final UserRepository userRepo;
    private final SubjectRepository subjectRepo;
    private final GcsSignedUrl signer;
    private final GcsObjectHelper gcs;
    private final ScopeResolver scopeResolver;
    private final ExamTaskRepository taskRepository;
    private final ExamTaskService examTaskService;

    public FileArchiveController(FileArchiveService service,
                                 FileArchiveRepository fileRepo,
                                 UserRepository userRepo,
                                 SubjectRepository subjectRepo,
                                 GcsSignedUrl signer,
                                 GcsObjectHelper gcs, ScopeResolver scopeResolver, ExamTaskRepository taskRepository, ExamTaskService examTaskService) {
        this.service = service;
        this.fileRepo = fileRepo;
        this.userRepo = userRepo;
        this.subjectRepo = subjectRepo;
        this.signer = signer;
        this.gcs = gcs;
        this.scopeResolver = scopeResolver;
        this.taskRepository = taskRepository;
        this.examTaskService = examTaskService;
    }

    // ================= LIST + FILTER =================
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
            @RequestParam(required = false) String variant,   // EXAM | PRACTICE
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) Long linkedTaskId // <-- NEW
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

        // ---- parse variant (final cho lambda)
        ArchiveVariant favTmp = null;
        if (variant != null && !variant.isBlank()) {
            try { favTmp = ArchiveVariant.valueOf(variant.trim().toUpperCase()); } catch (Exception ignore) {}
        }
        final ArchiveVariant fav = favTmp;

        // ---- parse reviewStatus: hỗ trợ CSV như "APPROVED,REJECTED"
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

        Specification<FileArchive> spec = scopeSpec();
        List<Specification<FileArchive>> specs = new ArrayList<>();
        if (linkedTaskId != null) {
            var optTask = taskRepository.findById(linkedTaskId);
            if (optTask.isEmpty() || optTask.get().getSubmissionArchiveId() == null) {
                return PageDTO.from(Page.empty(p)); // chưa nộp gì -> rỗng
            }
            Long archiveId = optTask.get().getSubmissionArchiveId();
            specs.add((root, cq, cb) -> cb.equal(root.get("id"), archiveId));
            // tuỳ chọn, “chắc kèo”:
            specs.add((root, cq, cb) -> cb.equal(cb.upper(root.get("kind")), "SUBMISSION"));
            // nếu muốn tự ép Pending ở BE khi có linkedTaskId (không bắt buộc):
            // specs.add((root, cq, cb) -> cb.equal(root.get("reviewStatus"), ReviewStatus.PENDING));
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

        // ---- batch resolve names
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
                    linkedTaskId, linkedTaskStatus
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
        minutes = Math.max(1, Math.min(minutes, 30));
        String url = signer.signAttachment(
                fa.getStorageKey(), Duration.ofMinutes(minutes), fa.getFilename(), fa.getMimeType()
        );
        return new UrlDTO(url);
    }

    // FileArchiveController
    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String,Object>> approve(
            @PathVariable Long id,
            @RequestParam(required=false) Long reviewerId,
            @RequestParam(defaultValue="false") boolean approveTask
    ) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ensureModeratable(fa);
        Long rid = (reviewerId != null) ? reviewerId : currentUserIdOrNull();

        service.approve(id, rid);

        boolean taskApproved = false;
        Long taskId = null;
        if (approveTask) {
            var opt = taskRepository.findFirstBySubmissionArchiveId(id);
            if (opt.isPresent()) {
                var t = opt.get();
                if (t.getStatus() == ExamTaskStatus.SUBMITTED
                        || t.getStatus() == ExamTaskStatus.RETURNED) {
                    examTaskService.headApproveDone(rid, t.getId()); // inject examTaskService
                    taskApproved = true;
                    taskId = t.getId();
                }
            }
        }
        return ResponseEntity.ok(Map.of("approved", true, "taskApproved", taskApproved, "taskId", taskId));
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
        service.reject(id, rid, reason, deadlineTs);

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
        service.delete(id);
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

    private Specification<FileArchive> scopeSpec() {
        var scope = scopeResolver.resolveByUsername(currentUsername());
        if (scope.all) return (root, cq, cb) -> cb.conjunction();
        if (scope.onlyUserId != null) {
            return (root, cq, cb) -> cb.equal(root.get("userId"), scope.onlyUserId);
        }
        var sids = scope.subjectIds;
        if (sids == null || sids.isEmpty()) {
            return (root, cq, cb) -> cb.disjunction(); // không match gì
        }
        return (root, cq, cb) -> root.get("subjectId").in(sids);
    }

    private void ensureReadable(FileArchive fa) {
        var scope = scopeResolver.resolveByUsername(currentUsername());
        if (scope.all) return;
        if (scope.onlyUserId != null) {
            if (!Objects.equals(fa.getUserId(), scope.onlyUserId))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return;
        }
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

    private void ensureDeletable(FileArchive fa) {
        var username = currentUsername();
        var userOpt = userRepo.findByUsername(username);
        if (userOpt.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var user = userOpt.get();

        boolean isAdmin = user.getUserRoles().stream().anyMatch(ur -> ur.getRole().getRoleName() == RoleType.ADMIN);
        if (isAdmin) return;

        boolean isHead = user.getUserRoles().stream().anyMatch(ur -> ur.getRole().getRoleName() == RoleType.HEAD);
        if (isHead) {
            var scope = scopeResolver.resolveByUsername(username);
            var sids = scope.subjectIds;
            if (sids == null || !sids.contains(fa.getSubjectId()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return;
        }

        // TEACHER: chỉ xoá của mình; EXPORT đã APPROVED thì không cho xoá
        if (!Objects.equals(fa.getUserId(), user.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if ("EXPORT".equalsIgnoreCase(fa.getKind())
                && fa.getReviewStatus() == ReviewStatus.APPROVED)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
