package com.exam.examserver.repo;

import com.exam.examserver.enums.ExamTaskStatus;
import com.exam.examserver.model.exam.ExamTask;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExamTaskRepository extends JpaRepository<ExamTask, Long>, JpaSpecificationExecutor<ExamTask> {

    // Teacher xem task của mình
    Page<ExamTask> findByAssignedToIdOrderByCreatedAtDesc(Long uid, Pageable p);

    // Head xem task trong khoa mình (đã cache sẵn headDepartmentId)
    Page<ExamTask> findByHeadDepartmentIdOrderByCreatedAtDesc(Long deptId, Pageable p);

    // Thống kê/đếm nhanh (tuỳ dùng)
    long countByAssignedToIdAndStatus(Long uid, ExamTaskStatus status);

    @Query("""
      select t from ExamTask t
      where (:uid is null or t.assignedToId = :uid)
        and (:deptId is null or t.headDepartmentId = :deptId)
        and (:subjectId is null or t.subjectId = :subjectId)
        and (:status is null or t.status = :status)
        and (:from is null or t.createdAt >= :from)
        and (:to   is null or t.createdAt <  :to)
      order by t.createdAt desc
    """)
    Page<ExamTask> search(@Param("uid") Long assignedToId,
                          @Param("deptId") Long headDeptId,
                          @Param("subjectId") Long subjectId,
                          @Param("status") ExamTaskStatus status,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable p);

    Optional<ExamTask> findFirstBySubmissionArchiveId(Long submissionArchiveId);
    List<ExamTask> findAllBySubmissionArchiveIdIn(Collection<Long> ids);
}

