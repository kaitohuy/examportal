package com.exam.examserver.model.exam;

import com.exam.examserver.enums.ArchiveVariant;
import com.exam.examserver.enums.ReviewStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "file_archive")
public class FileArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** IMPORT | EXPORT */
    @Column(length = 20, nullable = false)
    private String kind;

    private Long subjectId;
    private Long userId;

    // file info
    @Column(nullable = false) private String filename;
    @Column(nullable = false) private String mimeType;
    @Column(nullable = false) private long sizeBytes;
    @Column(length = 64) private String sha256;

    // storage
    @Column(length = 20, nullable = false) private String storage = "GCS";
    @Column(length = 512, nullable = false) private String storageKey;
    @Column(length = 1024) private String publicUrl;

    // export metadata
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ArchiveVariant variant; // null nếu IMPORT

    @Column(length = 8)
    private String exportFormat; // PDF | DOCX

    @Column(columnDefinition = "TEXT")
    private String metaJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // moderation
    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.APPROVED;

    private Instant submittedAt;
    private Instant reviewedAt;
    private Long reviewedById;

    @Column(columnDefinition = "TEXT")
    private String reviewNote;

    private Instant reviewDeadline;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (reviewStatus == null) reviewStatus = ReviewStatus.APPROVED;
    }

    // ===== getters/setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public String getStorage() { return storage; }
    public void setStorage(String storage) { this.storage = storage; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }

    public ArchiveVariant getVariant() { return variant; }
    public void setVariant(ArchiveVariant variant) { this.variant = variant; }

    public String getExportFormat() { return exportFormat; }
    public void setExportFormat(String exportFormat) { this.exportFormat = exportFormat; }

    public String getMetaJson() { return metaJson; }
    public void setMetaJson(String metaJson) { this.metaJson = metaJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(ReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public Long getReviewedById() { return reviewedById; }
    public void setReviewedById(Long reviewedById) { this.reviewedById = reviewedById; }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public Instant getReviewDeadline() { return reviewDeadline; }
    public void setReviewDeadline(Instant reviewDeadline) { this.reviewDeadline = reviewDeadline; }
}