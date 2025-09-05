package com.exam.examserver.repo;

import com.exam.examserver.model.exam.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {
    Optional<QuizQuestion> findByQuizIdAndQuestionId(Long quizId, Long questionId);
}
