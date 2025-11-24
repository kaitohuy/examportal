package com.exam.examserver.repo;

import com.exam.examserver.enums.IssueStatus;
import com.exam.examserver.model.exam.QuestionIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionIssueRepository extends JpaRepository<QuestionIssue, Long> {

    Optional<QuestionIssue> findByQuestionId(Long questionId);

    boolean existsByQuestionIdAndStatus(Long questionId, IssueStatus status);

    // QuestionIssueRepository
    List<QuestionIssue> findByQuestionIdInAndStatus(Collection<Long> ids, IssueStatus status);

    @Modifying
    @Query("delete from QuestionIssue qi where qi.question.id in :ids")
    int deleteByQuestionIdIn(@Param("ids") Collection<Long> ids);
}
