// com.exam.examserver.model.exam
package com.exam.examserver.model.exam;

import com.exam.examserver.enums.ExamTaskStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "exam_task",
        indexes = {
                @Index(name="idx_exam_task_assigned",   columnList = "assignedToId,status,subjectId"),
                @Index(name="idx_exam_task_head_dept",  columnList = "headDepartmentId,status,subjectId"),
                @Index(name="idx_exam_task_created",    columnList = "createdAt")
        })
public class ExamTask {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tham chiếu logic (tránh fetch nặng): lưu id để join/resolve khi cần
    @Column(nullable = false)
    private Long subjectId;

    @Column(nullable = false)
    private Long headDepartmentId;     // cache sẵn khoa của môn tại thời điểm giao

    @Column(nullable = false)
    private Long assignedToId;         // giáo viên được giao

    @Column(nullable = false)
    private Long createdByHeadId;      // head user id giao nhiệm vụ

    // Metadata hiển thị
    @Column(nullable = false, length = 160)
    private String title;              // VD: "Đề thi cuối kỳ CS101 - HK1"

    @Column(columnDefinition = "text")
    private String instructions;       // mô tả tự do / lưu guideline

    // Cấu trúc đề (JSON text, linh hoạt về sau)
    @Column(columnDefinition = "text", nullable = false)
    private String structureJson;      // JSON yêu cầu: ví dụ {durationMin, maxQuestions, ...}

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamTaskStatus status = ExamTaskStatus.ASSIGNED;

    private Instant dueAt;             // hạn HEAD yêu cầu
    private Instant completedAt;       // thời điểm teacher tick xong

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    // thời điểm và ghi chú khi GV nộp bài
    private Instant submittedAt;
    private Long submissionArchiveId;      // id bản ghi file_archive tạm (PENDING, path tmp/)
    private String submissionNote;

    // báo lỗi
    private Instant reportedAt;
    private String reportNote;
    // ==== REVIEW META (HEAD) ====
    private Long reviewedById;   // user id của HEAD đã review lần gần nhất
    private String reviewNote;   // lý do từ chối / ghi chú review
    private Instant reviewedAt;  // thời điểm review gần nhất

    // ===== getters/setters =====
    public Long getReviewedById() { return reviewedById; }
    public void setReviewedById(Long reviewedById) { this.reviewedById = reviewedById; }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }


    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Long getSubmissionArchiveId() {
        return submissionArchiveId;
    }

    public void setSubmissionArchiveId(Long submissionArchiveId) {
        this.submissionArchiveId = submissionArchiveId;
    }

    public String getSubmissionNote() {
        return submissionNote;
    }

    public void setSubmissionNote(String submissionNote) {
        this.submissionNote = submissionNote;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(Instant reportedAt) {
        this.reportedAt = reportedAt;
    }

    public String getReportNote() {
        return reportNote;
    }

    public void setReportNote(String reportNote) {
        this.reportNote = reportNote;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Long getHeadDepartmentId() {
        return headDepartmentId;
    }

    public void setHeadDepartmentId(Long headDepartmentId) {
        this.headDepartmentId = headDepartmentId;
    }

    public Long getCreatedByHeadId() {
        return createdByHeadId;
    }

    public void setCreatedByHeadId(Long createdByHeadId) {
        this.createdByHeadId = createdByHeadId;
    }

    public Long getAssignedToId() {
        return assignedToId;
    }

    public void setAssignedToId(Long assignedToId) {
        this.assignedToId = assignedToId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getStructureJson() {
        return structureJson;
    }

    public void setStructureJson(String structureJson) {
        this.structureJson = structureJson;
    }

    public ExamTaskStatus getStatus() {
        return status;
    }

    public void setStatus(ExamTaskStatus status) {
        this.status = status;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

}
