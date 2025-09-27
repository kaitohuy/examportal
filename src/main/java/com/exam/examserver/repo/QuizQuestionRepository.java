package com.exam.examserver.repo;

import com.exam.examserver.model.exam.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {
    Optional<QuizQuestion> findByQuizIdAndQuestionId(Long quizId, Long questionId);
    @Modifying
    @Query("delete from QuizQuestion qq where qq.question.id in :ids")
    int deleteByQuestionIds(@Param("ids") Collection<Long> ids);

}
