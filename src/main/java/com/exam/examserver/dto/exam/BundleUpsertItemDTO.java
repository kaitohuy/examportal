package com.exam.examserver.dto.exam;

import java.math.BigDecimal;

public class BundleUpsertItemDTO {
    private Long questionId;
    private Integer orderIndex;
    private BigDecimal pointsOverride; // optional
    private String note;               // optional

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public BigDecimal getPointsOverride() {
        return pointsOverride;
    }

    public void setPointsOverride(BigDecimal pointsOverride) {
        this.pointsOverride = pointsOverride;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
