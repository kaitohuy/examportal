package com.exam.examserver.dto.exam;

import java.math.BigDecimal;

public class BundleItemDTO {
    private Long id;
    private Long questionId;
    private Integer orderIndex;
    private BigDecimal pointsOverride; // nullable
    private String note;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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


