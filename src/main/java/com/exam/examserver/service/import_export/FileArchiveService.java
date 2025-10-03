package com.exam.examserver.service.import_export;

import com.exam.examserver.enums.ArchiveVariant;
import com.exam.examserver.enums.ReviewStatus;
import com.exam.examserver.model.exam.FileArchive;
import com.exam.examserver.repo.FileArchiveRepository;
import com.exam.examserver.repo.SubjectRepository;
import com.exam.examserver.repo.UserRepository;
import com.exam.examserver.service.impl.NotificationService;
import com.exam.examserver.storage.FileArchiveStorage;
import com.exam.examserver.storage.GcsObjectHelper;
import com.exam.examserver.storage.GcsSignedUrl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class FileArchiveService {

    private final FileArchiveStorage storage;
    private final FileArchiveRepository fileRepo;
    private final GcsSignedUrl signer;
    private final GcsObjectHelper gcs;
    private final ObjectMapper om = new ObjectMapper();
    private final NotificationService notif;
    private final SubjectRepository subjectRepo;
    private final UserRepository userRepo;

    public FileArchiveService(FileArchiveStorage storage,
                              FileArchiveRepository fileRepo,
                              GcsSignedUrl signer,
                              GcsObjectHelper gcs, NotificationService notif, SubjectRepository subjectRepo, UserRepository userRepo) {
        this.storage = storage;
        this.fileRepo = fileRepo;
        this.signer = signer;
        this.gcs = gcs;
        this.notif = notif;
        this.subjectRepo = subjectRepo;
        this.userRepo = userRepo;
    }

    /** Lưu trực tiếp vào archives/... (APPROVED). */
    public FileArchive save(String kind, Long subjectId, Long userId,
                            String filename, String mimeType, byte[] data,
                            Map<String, Object> meta) throws Exception {

        var put = storage.put(data, mimeType, filename);

        FileArchive fa = new FileArchive();
        fa.setKind(kind);
        fa.setSubjectId(subjectId);
        fa.setUserId(userId);
        fa.setFilename(filename);
        fa.setMimeType(mimeType == null ? "application/octet-stream" : mimeType);
        fa.setSizeBytes(data.length);
        fa.setSha256(DigestUtils.sha256Hex(data));
        fa.setStorage("GCS");
        fa.setStorageKey(put.storageKey());
        fa.setPublicUrl(put.publicUrl());
        fa.setMetaJson(meta == null ? "{}" : om.writeValueAsString(meta));

        if ("EXPORT".equalsIgnoreCase(kind) && meta != null) {
            String v = String.valueOf(meta.getOrDefault("variant", "")).toUpperCase();
            if ("EXAM".equals(v))      fa.setVariant(ArchiveVariant.EXAM);
            else if ("PRACTICE".equals(v)) fa.setVariant(ArchiveVariant.PRACTICE);

            String fmt = String.valueOf(meta.getOrDefault("format","")).toUpperCase();
            if ("PDF".equals(fmt) || "DOCX".equals(fmt) || "WORD".equals(fmt)) {
                fa.setExportFormat("WORD".equals(fmt) ? "DOCX" : fmt);
            }
        }
        return fileRepo.save(fa);
    }

    /**
     * Lưu PENDING vào tmp/archives/... (khi user cần duyệt).
     */
    @Transactional
    public void savePendingExport(Long subjectId, Long userId,
                                  String filename, String mimeType,
                                  byte[] data, Map<String, Object> meta) throws Exception {
        String safeName = sanitizeFilename(filename);
        String key = "tmp/" + UUID.randomUUID() + "_" + safeName;

        gcs.putBytes(key, mimeType == null ? "application/octet-stream" : mimeType, data);

        FileArchive fa = new FileArchive();
        fa.setKind("EXPORT");
        fa.setSubjectId(subjectId);
        fa.setUserId(userId);
        fa.setFilename(safeName);
        fa.setMimeType(mimeType == null ? "application/octet-stream" : mimeType);
        fa.setSizeBytes(data.length);
        fa.setSha256(DigestUtils.sha256Hex(data));
        fa.setStorage("GCS");
        fa.setStorageKey(key);
        fa.setPublicUrl("");
        fa.setMetaJson(meta == null ? "{}" : om.writeValueAsString(meta));

        if (meta != null) {
            String v = String.valueOf(meta.getOrDefault("variant", "")).toUpperCase();
            if ("EXAM".equals(v))      fa.setVariant(ArchiveVariant.EXAM);
            else if ("PRACTICE".equals(v)) fa.setVariant(ArchiveVariant.PRACTICE);

            String fmt = String.valueOf(meta.getOrDefault("format","")).toUpperCase();
            if ("PDF".equals(fmt) || "DOCX".equals(fmt) || "WORD".equals(fmt)) {
                fa.setExportFormat("WORD".equals(fmt) ? "DOCX" : fmt);
            }
        }

        fa.setReviewStatus(ReviewStatus.PENDING);
        fa.setSubmittedAt(Instant.now());
        fileRepo.save(fa);

        // NEW: tạo notification
        notifyHeadOnPending(subjectId, userId, safeName);
        // (tuỳ chọn) báo cho chính GV rằng đã gửi thành công
        notif.create(userId, "Đã gửi file chờ duyệt",
                "Bạn đã gửi \"" + safeName + "\" để chờ duyệt.", Instant.now().plus(Duration.ofDays(7)));
    }

    // helper mới — loại bỏ mọi path trong tên file để không lồng thư mục
    private static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "file.bin";
        String s = filename.replace('\\','/');
        int p = s.lastIndexOf('/');
        String base = (p >= 0) ? s.substring(p + 1) : s;
        return base.replaceAll("[\\r\\n]", "");
    }


    /**
     * Lưu record trỏ đến key đã có sẵn.
     */
    public void saveExistingByKey(String kind, Long subjectId, Long userId,
                                  String filename, String mimeType,
                                  String storageKey, Map<String, Object> meta) throws Exception {
        var blob = gcs.stat(storageKey);
        long size = (blob != null ? blob.getSize() : 0L);
        String ct = (mimeType != null ? mimeType : (blob != null ? blob.getContentType() : "application/octet-stream"));

        FileArchive fa = new FileArchive();
        fa.setKind(kind);
        fa.setSubjectId(subjectId);
        fa.setUserId(userId);
        fa.setFilename(filename);
        fa.setMimeType(ct);
        fa.setSizeBytes(size);
        fa.setSha256(null);
        fa.setStorage("GCS");
        fa.setStorageKey(storageKey);
        fa.setPublicUrl("");
        fa.setMetaJson(meta == null ? "{}" : om.writeValueAsString(meta));

        if ("EXPORT".equalsIgnoreCase(kind) && fa.getReviewStatus() == null) {
            fa.setReviewStatus(storageKey.startsWith("tmp/") ? ReviewStatus.PENDING : ReviewStatus.APPROVED);
        }
        fileRepo.save(fa);
    }

    public String signUrl(Long id, Duration ttl) {
        FileArchive fa = fileRepo.findById(id).orElseThrow();
        return signer.sign(fa.getStorageKey(), ttl);
    }

    @Transactional
    public void delete(Long id) throws Exception {
        FileArchive fa = fileRepo.findById(id).orElseThrow();
        storage.delete(fa.getStorageKey());
        fileRepo.deleteById(id);

        if (fa.getReviewStatus() == ReviewStatus.APPROVED && fa.getUserId() != null) {
            notif.create(
                    fa.getUserId(),
                    "File đã bị xoá",
                    "File \"" + fa.getFilename() + "\" (đã được duyệt) đã bị xoá khỏi hệ thống.",
                    Instant.now().plus(Duration.ofDays(30))
            );
        }
    }

    @Transactional
    public void approve(Long id, Long reviewerId) {
        FileArchive fa = fileRepo.findById(id).orElseThrow();
        if (fa.getReviewStatus() != ReviewStatus.PENDING) return;

        String fromKey = fa.getStorageKey();
        String fileName = fromKey.substring(fromKey.lastIndexOf('/') + 1);
        String toKey = "archives/" + fileName;

        gcs.copyAndDelete(fromKey, toKey);

        fa.setStorageKey(toKey);
        fa.setReviewStatus(ReviewStatus.APPROVED);
        fa.setReviewedAt(Instant.now());
        if (reviewerId != null) fa.setReviewedById(reviewerId);

        fileRepo.save(fa);

        notifyTeacherOnApproved(fa, reviewerId);
    }

    @Transactional
    public void reject(Long id, Long reviewerId, String reason, Instant deadlineUtc) {
        FileArchive f = fileRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        f.setReviewStatus(ReviewStatus.REJECTED);
        f.setReviewNote((reason == null || reason.isBlank()) ? null : reason.trim());
        f.setReviewedAt(Instant.now());
        if (reviewerId != null) f.setReviewedById(reviewerId);
        f.setReviewDeadline(deadlineUtc);

        fileRepo.save(f);

        notifyTeacherOnRejected(f, reviewerId);
    }

    private String displayName(Long userId) {
        return userRepo.findById(userId)
                .map(u -> {
                    String f = Optional.ofNullable(u.getFirstName()).orElse("").trim();
                    String l = Optional.ofNullable(u.getLastName()).orElse("").trim();
                    String full1 = (f + " " + l).trim();
                    String full2 = (l + " " + f).trim();
                    if (!full1.isBlank()) return full1;
                    if (!full2.isBlank()) return full2;
                    if (u.getUsername()!=null && !u.getUsername().isBlank()) return u.getUsername();
                    if (u.getEmail()!=null && !u.getEmail().isBlank()) return u.getEmail();
                    return "User #" + u.getId();
                })
                .orElse("User #" + userId);
    }

    private void notifyHeadOnPending(Long subjectId, Long uploaderId, String filename) {
        Long headId = subjectRepo.findHeadUserIdBySubjectId(subjectId);
        if (headId == null) return; // khoa chưa có head, bỏ qua

        String upName = displayName(uploaderId);
        String title = "Có file chờ duyệt";
        String msg = "Giáo viên " + upName + " gửi file \"" + filename + "\" chờ duyệt.";
        notif.create(headId, title, msg, null); // không đặt expiresAt -> lưu vô thời hạn
    }

    private void notifyTeacherOnApproved(FileArchive fa, Long reviewerId) {
        if (fa.getUserId() == null) return;
        String rvName = (reviewerId != null) ? displayName(reviewerId) : "HEAD";
        String title = "File đã được duyệt";
        String msg = "File \"" + fa.getFilename() + "\" đã được duyệt bởi " + rvName + ".";
        notif.create(fa.getUserId(), title, msg, Instant.now().plus(Duration.ofDays(30)));
    }

    private void notifyTeacherOnRejected(FileArchive fa, Long reviewerId) {
        if (fa.getUserId() == null) return;
        String rvName = (reviewerId != null) ? displayName(reviewerId) : "HEAD";
        String reason = Optional.ofNullable(fa.getReviewNote()).orElse("(không có)");
        String dl = (fa.getReviewDeadline() != null)
                ? " Hạn xử lý: " + fa.getReviewDeadline().toString()
                : "";
        String title = "File bị từ chối";
        String msg = "File \"" + fa.getFilename() + "\" bị từ chối bởi " + rvName +
                ". Lý do: " + reason + "." + dl;
        notif.create(fa.getUserId(), title, msg, Instant.now().plus(Duration.ofDays(14)));
    }
}
