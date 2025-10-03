// src/main/java/com/exam/examserver/model/exam/QuestionIssue.java
package com.exam.examserver.model.exam;

import com.exam.examserver.enums.IssueStatus;
import com.exam.examserver.model.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "question_issue",
        uniqueConstraints = @UniqueConstraint(name = "uq_qissue_question", columnNames = "question_id")
)
public class QuestionIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 1–1 với question, UNIQUE, CASCADE khi xóa question
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Question question;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IssueStatus status = IssueStatus.OPEN;

    @Lob
    @Column(nullable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flagged_by_id", nullable = false)
    private User flaggedBy;

    @Column(nullable = false)
    private LocalDateTime flaggedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cleared_by_id")
    private User clearedBy;

    private LocalDateTime clearedAt;

    // getters/setters
    public Long getId() { return id; }
    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }
    public IssueStatus getStatus() { return status; }
    public void setStatus(IssueStatus status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public User getFlaggedBy() { return flaggedBy; }
    public void setFlaggedBy(User flaggedBy) { this.flaggedBy = flaggedBy; }
    public LocalDateTime getFlaggedAt() { return flaggedAt; }
    public void setFlaggedAt(LocalDateTime flaggedAt) { this.flaggedAt = flaggedAt; }
    public User getClearedBy() { return clearedBy; }
    public void setClearedBy(User clearedBy) { this.clearedBy = clearedBy; }
    public LocalDateTime getClearedAt() { return clearedAt; }
    public void setClearedAt(LocalDateTime clearedAt) { this.clearedAt = clearedAt; }
}
