package com.exam.examserver.dto.exam;

import java.util.List;

public class CreateQuizDTO {
    private String title;
    private String description;
    private Integer maxScore;
    private Integer timeLimitMinutes;
    // Danh sách questionId theo thứ tự bạn muốn
    private List<AddQuizQuestionDTO> questions;
    // + getter/setter

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }

    public Integer getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public void setTimeLimitMinutes(Integer timeLimitMinutes) {
        this.timeLimitMinutes = timeLimitMinutes;
    }

    public List<AddQuizQuestionDTO> getQuestions() {
        return questions;
    }

    public void setQuestions(List<AddQuizQuestionDTO> questions) {
        this.questions = questions;
    }
}
