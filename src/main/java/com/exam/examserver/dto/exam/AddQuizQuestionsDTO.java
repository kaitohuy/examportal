package com.exam.examserver.dto.exam;

import java.util.List;

public class AddQuizQuestionsDTO {
    private List<AddQuizQuestionDTO> questions;

    public List<AddQuizQuestionDTO> getQuestions() {
        return questions;
    }

    public void setQuestions(List<AddQuizQuestionDTO> questions) {
        this.questions = questions;
    }
}
