package com.exam.examserver.service;

import com.exam.examserver.dto.exam.*;
import com.exam.examserver.model.exam.Question;

import java.util.List;

public interface QuizService {
    List<QuizDTO> getAllBySubject(Long subjectId);
    QuizDTO getById(Long quizId, Long subjectId); // Thêm subjectId
    QuizDTO create(Long subjectId, CreateQuizDTO payload, Long creatorUserId);
    QuizDTO update(Long quizId, Long subjectId, CreateQuizDTO payload); // Thêm subjectId
    void delete(Long quizId);
    List<QuestionDTO> getQuestionsByQuiz(Long quizId, boolean hideAnswer);
    List<QuizQuestionDTO> addQuestionsToQuiz(Long quizId, Long subjectId, AddQuizQuestionsDTO payload, Long userId);
    void removeQuestionFromQuiz(Long quizId, Long questionId);
    QuizDTO updateQuestions(Long quizId, Long subjectId, List<AddQuizQuestionDTO> questions, Long userId);
    Long saveQuestion(CreateQuestionDTO dto, Long subjectId, Long userId);
    Question findDuplicateQuestion(QuestionDTO dto);
}
