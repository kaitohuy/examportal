package com.exam.examserver.dto.exam;

public class QuizQuestionDTO {
    private Long id;           // id của QuizQuestion nếu cần
    private Integer orderIndex;
    private QuestionDTO question;
    // + getter/setter

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public QuestionDTO getQuestion() {
        return question;
    }

    public void setQuestion(QuestionDTO question) {
        this.question = question;
    }
}

