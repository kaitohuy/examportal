package com.exam.examserver.dto.exam;

import com.exam.examserver.enums.CognitiveLevel;
import com.exam.examserver.enums.RecordStatus;
import com.exam.examserver.enums.UnitKind;

import java.math.BigDecimal;

public class QuestionMetaUpsertDTO {
    private UnitKind unitKind;                 // required
    private CognitiveLevel cognitiveLevel;     // optional
    private String typeCode;                   // optional
    private BigDecimal points;                 // required (default 1.00)
    private Integer chapter;                   // optional
    private Long topicId;                      // optional
    private RecordStatus status;               // required (default DRAFT)
    private String problemType;

    public String getProblemType() {
        return problemType;
    }

    public void setProblemType(String problemType) {
        this.problemType = problemType;
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

    public Integer getChapter() {
        return chapter;
    }

    public void setChapter(Integer chapter) {
        this.chapter = chapter;
    }

    public BigDecimal getPoints() {
        return points;
    }

    public void setPoints(BigDecimal points) {
        this.points = points;
    }

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public void setStatus(RecordStatus status) {
        this.status = status;
    }
}
