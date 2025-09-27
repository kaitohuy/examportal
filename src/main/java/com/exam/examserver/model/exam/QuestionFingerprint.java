package com.exam.examserver.model.exam;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "question_fp",
        indexes = {
                @Index(name = "idx_qfp_subject_root_b1", columnList = "subject_id,is_root,b1"),
                @Index(name = "idx_qfp_subject_root_b2", columnList = "subject_id,is_root,b2"),
                @Index(name = "idx_qfp_subject_root_b3", columnList = "subject_id,is_root,b3"),
                @Index(name = "idx_qfp_subject_root_b4", columnList = "subject_id,is_root,b4")
        })
public class QuestionFingerprint {

    @Id
    @Column(name = "question_id")
    private Long questionId;  // 1-1 với Question

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "is_root", nullable = false)
    private boolean isRoot;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "simhash64", nullable = false)
    private long simhash64;

    @Column(name = "crc32", nullable = false)
    private int crc32;

    @Column(name = "b1", nullable = false)
    private int b1;

    @Column(name = "b2", nullable = false)
    private int b2;

    @Column(name = "b3", nullable = false)
    private int b3;

    @Column(name = "b4", nullable = false)
    private int b4;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    // getters/setters
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
    public boolean isRoot() { return isRoot; }
    public void setRoot(boolean root) { isRoot = root; }
    public int getTokenCount() { return tokenCount; }
    public void setTokenCount(int tokenCount) { this.tokenCount = tokenCount; }
    public long getSimhash64() { return simhash64; }
    public void setSimhash64(long simhash64) { this.simhash64 = simhash64; }
    public int getCrc32() { return crc32; }
    public void setCrc32(int crc32) { this.crc32 = crc32; }
    public int getB1() { return b1; }
    public void setB1(int b1) { this.b1 = b1; }
    public int getB2() { return b2; }
    public void setB2(int b2) { this.b2 = b2; }
    public int getB3() { return b3; }
    public void setB3(int b3) { this.b3 = b3; }
    public int getB4() { return b4; }
    public void setB4(int b4) { this.b4 = b4; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
