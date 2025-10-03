package com.exam.examserver.dto.exam;

import com.exam.examserver.enums.CognitiveLevel;
import com.exam.examserver.enums.ItemNature;
import com.exam.examserver.enums.RecordStatus;
import com.exam.examserver.enums.UnitKind;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class QuestionMetaDTO {
    private Long questionId;
    private UnitKind unitKind;           // SUB_ITEM | FULL_QUESTION
    private CognitiveLevel cognitiveLevel;
    private String typeCode;
    private BigDecimal points;
    private Integer chapter;
    private Long topicId;
    private LocalDateTime lastUsedAt;
    private Long usageCount;
    private RecordStatus status;
    private ItemNature itemNature;

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public UnitKind getUnitKind() {
        return unitKind;
    }

    public void setUnitKind(UnitKind unitKind) {
        this.unitKind = unitKind;
    }

    public CognitiveLevel getCognitiveLevel() {
        return cognitiveLevel;
    }

    public void setCognitiveLevel(CognitiveLevel cognitiveLevel) {
        this.cognitiveLevel = cognitiveLevel;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public BigDecimal getPoints() {
        return points;
    }

    public void setPoints(BigDecimal points) {
        this.points = points;
    }

    public Integer getChapter() {
        return chapter;
    }

    public void setChapter(Integer chapter) {
        this.chapter = chapter;
    }

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public Long getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(Long usageCount) {
        this.usageCount = usageCount;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public void setStatus(RecordStatus status) {
        this.status = status;
    }
}
