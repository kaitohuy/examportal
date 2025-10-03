package com.exam.examserver.repo;

import com.exam.examserver.model.exam.QuestionMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface QuestionMetaRepository extends JpaRepository<QuestionMeta, Long>, JpaSpecificationExecutor<QuestionMeta> {
}
