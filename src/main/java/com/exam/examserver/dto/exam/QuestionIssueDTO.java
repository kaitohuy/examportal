package com.exam.examserver.dto.exam;

import com.exam.examserver.enums.IssueStatus;

import java.time.LocalDateTime;

public class QuestionIssueDTO {
    private Long questionId;
    private IssueStatus status;
    private String reason;
    private Long flaggedById;
    private String flaggedByName;
    private LocalDateTime flaggedAt;
    private Long clearedById;
    private String clearedByName;
    private LocalDateTime clearedAt;
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public IssueStatus getStatus() { return status; }
    public void setStatus(IssueStatus status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getFlaggedById() { return flaggedById; }
    public void setFlaggedById(Long flaggedById) { this.flaggedById = flaggedById; }
    public String getFlaggedByName() { return flaggedByName; }
    public void setFlaggedByName(String flaggedByName) { this.flaggedByName = flaggedByName; }
    public LocalDateTime getFlaggedAt() { return flaggedAt; }
    public void setFlaggedAt(LocalDateTime flaggedAt) { this.flaggedAt = flaggedAt; }
    public Long getClearedById() { return clearedById; }
    public void setClearedById(Long clearedById) { this.clearedById = clearedById; }
    public String getClearedByName() { return clearedByName; }
    public void setClearedByName(String clearedByName) { this.clearedByName = clearedByName; }
    public LocalDateTime getClearedAt() { return clearedAt; }
    public void setClearedAt(LocalDateTime clearedAt) { this.clearedAt = clearedAt; }

}
