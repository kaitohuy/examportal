package com.exam.examserver.repo;

import com.exam.examserver.model.exam.QuestionMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface QuestionMetaRepository extends JpaRepository<QuestionMeta, Long>, JpaSpecificationExecutor<QuestionMeta> {

    @Query("select m from QuestionMeta m where m.questionId = :questionId")
    Optional<QuestionMeta> findByQuestionId(Long questionId);
}
