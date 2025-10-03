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

    // getters/setters...
}
