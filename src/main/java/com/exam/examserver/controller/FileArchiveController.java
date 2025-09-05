package com.exam.examserver.controller;

import com.exam.examserver.dto.importing.FileArchiveDTO;
import com.exam.examserver.dto.importing.PageDTO;
import com.exam.examserver.enums.ArchiveVariant;
import com.exam.examserver.enums.ReviewStatus;
import com.exam.examserver.model.exam.FileArchive;
import com.exam.examserver.model.exam.Subject;
import com.exam.examserver.model.user.User;
import com.exam.examserver.repo.FileArchiveRepository;
import com.exam.examserver.repo.SubjectRepository;
import com.exam.examserver.repo.UserRepository;
import com.exam.examserver.service.import_export.FileArchiveService;
import com.exam.examserver.storage.GcsObjectHelper;
import com.exam.examserver.storage.GcsSignedUrl;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.*;
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

    public FileArchiveController(FileArchiveService service,
                                 FileArchiveRepository fileRepo,
                                 UserRepository userRepo,
                                 SubjectRepository subjectRepo,
                                 GcsSignedUrl signer,
                                 GcsObjectHelper gcs) {
        this.service = service;
        this.fileRepo = fileRepo;
        this.userRepo = userRepo;
        this.subjectRepo = subjectRepo;
        this.signer = signer;
        this.gcs = gcs;
    }

    // ================= LIST + FILTER =================
    @GetMapping
    public PageDTO<FileArchiveDTO> list(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String kind,      // IMPORT/EXPORT
            @RequestParam(required = false) String q,         // filename
            @RequestParam(required = false) String subject,   // subject name/code
            @RequestParam(required = false) String uploader,  // username/full name
            @RequestParam(required = false) String from,      // yyyy-MM-dd
            @RequestParam(required = false) String to,        // yyyy-MM-dd
            @RequestParam(required = false) String variant,   // EXAM | PRACTICE
            @RequestParam(required = false) String reviewStatus // "APPROVED", "REJECTED" hoặc "APPROVED,REJECTED"
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

        // ---- dynamic specs
        List<Specification<FileArchive>> specs = new ArrayList<>();
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

        Specification<FileArchive> spec = specs.stream().reduce(Specification::and).orElse(null);
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

        Page<FileArchiveDTO> mapped = rs.map(f -> {
            Long uid = f.getUserId();
            Long sid = f.getSubjectId();
            Long rid = f.getReviewedById();

            String uploaderNameSafe  = (uid != null) ? uploaderMap.getOrDefault(uid, "User #" + uid) : "";
            String subjectNameSafe   = (sid != null) ? subjectMap.getOrDefault(sid, "") : "";
            String reviewedByNameSafe= (rid != null) ? reviewerMap.getOrDefault(rid, "") : "";

            return new FileArchiveDTO(
                    f.getId(), f.getFilename(), f.getMimeType(), f.getSizeBytes(), f.getKind(),
                    f.getSubjectId(), f.getUserId(), f.getCreatedAt(),
                    uploaderNameSafe,
                    subjectNameSafe,
                    f.getVariant() == null ? null : f.getVariant().name(),
                    f.getReviewStatus() == null ? null : f.getReviewStatus().name(),
                    f.getReviewNote(), f.getReviewDeadline(),
                    f.getReviewedAt(), f.getReviewedById(), reviewedByNameSafe
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
    @GetMapping("/{id}")
    public FileArchive detail(@PathVariable Long id) {
        return fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(@PathVariable Long id) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var blob = gcs.stat(fa.getStorageKey());
        return (blob != null) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/view")
    public ResponseEntity<Void> view(@PathVariable Long id,
                                     @RequestParam(defaultValue = "5") long minutes) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        minutes = Math.max(1, Math.min(minutes, 30));
        String url = signer.signInline(fa.getStorageKey(), Duration.ofMinutes(minutes),
                fa.getFilename(), fa.getMimeType());
        return ResponseEntity.status(302).location(URI.create(url)).build();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Void> download(@PathVariable Long id,
                                         @RequestParam(defaultValue = "5") long minutes) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        minutes = Math.max(1, Math.min(minutes, 30));
        String url = signer.signAttachment(
                fa.getStorageKey(), Duration.ofMinutes(minutes), fa.getFilename(), fa.getMimeType()
        );
        return ResponseEntity.status(302).location(URI.create(url)).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) throws Exception {
        if (!fileRepo.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record UrlDTO(String url) {}

    @GetMapping("/{id}/view-url")
    public UrlDTO viewUrl(@PathVariable Long id,
                          @RequestParam(defaultValue = "5") long minutes) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        minutes = Math.max(1, Math.min(minutes, 30));
        String url = signer.signInline(fa.getStorageKey(), Duration.ofMinutes(minutes),
                fa.getFilename(), fa.getMimeType());
        return new UrlDTO(url);
    }

    @GetMapping("/{id}/download-url")
    public UrlDTO downloadUrl(@PathVariable Long id,
                              @RequestParam(defaultValue = "5") long minutes) {
        var fa = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        minutes = Math.max(1, Math.min(minutes, 30));
        String url = signer.signAttachment(
                fa.getStorageKey(), Duration.ofMinutes(minutes), fa.getFilename(), fa.getMimeType()
        );
        return new UrlDTO(url);
    }

    // ================= Moderation =================
    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long id,
                                        @RequestParam(required = false) Long reviewerId) {
        service.approve(id, reviewerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id,
                                       @RequestBody Map<String, String> body,
                                       @RequestParam(required = false) Long reviewerId) {
        final String reason   = Optional.ofNullable(body.get("reason")).orElse("").trim();
        final String rawDl    = body.get("deadline"); // có thể là ISO (2025-09-04T08:00:00Z) hoặc yyyy-MM-dd

        // Parse linh hoạt: ISO -> Instant; yyyy-MM-dd -> end-of-day theo TZ server -> Instant
        final Instant deadlineTs = parseDeadlineFlexible(rawDl);
        service.reject(id, reviewerId, reason, deadlineTs);
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
}
