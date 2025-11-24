package com.exam.examserver.repo;

import com.exam.examserver.model.exam.QuestionMeta;
import com.exam.examserver.model.exam.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionMetaRepository extends JpaRepository<QuestionMeta, Long>, JpaSpecificationExecutor<QuestionMeta> {

    @Query("select m from QuestionMeta m where m.questionId = :questionId")
    Optional<QuestionMeta> findByQuestionId(@Param("questionId") Long questionId);

    @Query("""
        select distinct qm.typeCode
        from QuestionMeta qm
        join Question q on q.id = qm.questionId
        where q.subject.id = :subjectId
          and qm.status = com.exam.examserver.enums.RecordStatus.APPROVED
          and qm.typeCode is not null
        order by qm.typeCode asc
    """)
    List<String> findDistinctTypeCodesBySubjectApproved(@Param("subjectId") Long subjectId);
}
