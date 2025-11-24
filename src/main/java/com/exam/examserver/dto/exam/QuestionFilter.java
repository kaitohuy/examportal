// com.exam.examserver.dto.exam.QuestionFilter.java
package com.exam.examserver.dto.exam;

import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.enums.QuestionType;

import java.time.LocalDateTime;
import java.util.Set;

public class QuestionFilter {
    private Set<QuestionLabel> labels;
    private Difficulty difficulty;
    private Integer chapter;
    private QuestionType type;
    private String createdBy;
    private LocalDateTime from;
    private LocalDateTime to;
    private String q;
    private Boolean flagged;
    private Boolean deletedOnly;

    public Boolean getDeletedOnly() {
        return deletedOnly;
    }

    public void setDeletedOnly(Boolean deletedOnly) {
        this.deletedOnly = deletedOnly;
    }

    public Boolean getFlagged() {
        return flagged;
    }

    public void setFlagged(Boolean flagged) {
        this.flagged = flagged;
    }

    public Set<QuestionLabel> getLabels() {
        return labels;
    }

    public void setLabels(Set<QuestionLabel> labels) {
        this.labels = labels;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public Integer getChapter() {
        return chapter;
    }

    public void setChapter(Integer chapter) {
        this.chapter = chapter;
    }

    public QuestionType getType() {
        return type;
    }

    public void setType(QuestionType type) {
        this.type = type;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getFrom() {
        return from;
    }

    public void setFrom(LocalDateTime from) {
        this.from = from;
    }

    public LocalDateTime getTo() {
        return to;
    }

    public void setTo(LocalDateTime to) {
        this.to = to;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    // getters/setters
    // ...
}
