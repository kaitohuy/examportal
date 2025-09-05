package com.exam.examserver.dto.exam;

public class AddQuizQuestionDTO {
    private Long questionId;
    private Integer orderIndex;

    // Getters & setters

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
}
