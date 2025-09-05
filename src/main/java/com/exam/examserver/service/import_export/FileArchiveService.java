package com.exam.examserver.service.import_export;

import com.exam.examserver.enums.ArchiveVariant;
import com.exam.examserver.enums.ReviewStatus;
import com.exam.examserver.model.exam.FileArchive;
import com.exam.examserver.repo.FileArchiveRepository;
import com.exam.examserver.storage.FileArchiveStorage;
import com.exam.examserver.storage.GcsObjectHelper;
import com.exam.examserver.storage.GcsSignedUrl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.Map;
import java.util.UUID;

@Service
public class FileArchiveService {

    private final FileArchiveStorage storage;
    private final FileArchiveRepository fileRepo;
    private final GcsSignedUrl signer;
    private final GcsObjectHelper gcs;
    private final ObjectMapper om = new ObjectMapper();

    public FileArchiveService(FileArchiveStorage storage,
                              FileArchiveRepository fileRepo,
                              GcsSignedUrl signer,
                              GcsObjectHelper gcs) {
        this.storage = storage;
        this.fileRepo = fileRepo;
        this.signer = signer;
        this.gcs = gcs;
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

    /** Lưu PENDING vào tmp/archives/... (khi user cần duyệt). */
    public FileArchive savePendingExport(Long subjectId, Long userId,
                                         String filename, String mimeType,
                                         byte[] data, Map<String, Object> meta) throws Exception {
        String key = "tmp/archives/" + UUID.randomUUID() + "_" + filename;
        gcs.putBytes(key, mimeType == null ? "application/octet-stream" : mimeType, data);

        FileArchive fa = new FileArchive();
        fa.setKind("EXPORT");
        fa.setSubjectId(subjectId);
        fa.setUserId(userId);
        fa.setFilename(filename);
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
        return fileRepo.save(fa);
    }

    /** Lưu record trỏ đến key đã có sẵn. */
    public FileArchive saveExistingByKey(String kind, Long subjectId, Long userId,
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
        return fileRepo.save(fa);
    }

    public String signUrl(Long id, Duration ttl) {
        FileArchive fa = fileRepo.findById(id).orElseThrow();
        return signer.sign(fa.getStorageKey(), ttl);
    }

    public void delete(Long id) throws Exception {
        FileArchive fa = fileRepo.findById(id).orElseThrow();
        storage.delete(fa.getStorageKey());
        fileRepo.deleteById(id);
    }

    // ================= Moderation =================
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
    }

    public void reject(Long id, Long reviewerId, String reason, Instant deadlineUtc) {
        FileArchive f = fileRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        f.setReviewStatus(ReviewStatus.REJECTED);
        f.setReviewNote((reason == null || reason.isBlank()) ? null : reason.trim());
        f.setReviewedAt(Instant.now());
        f.setReviewedById(reviewerId);

        f.setReviewDeadline(deadlineUtc); // Entity nên để kiểu Instant
        fileRepo.save(f);
    }
}
