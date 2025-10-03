// src/main/java/com/exam/examserver/repo/QuestionBundleRepository.java
package com.exam.examserver.repo;

import com.exam.examserver.model.exam.QuestionBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface QuestionBundleRepository extends JpaRepository<QuestionBundle, Long>, JpaSpecificationExecutor<QuestionBundle> {

    // Lấy các bundle ứng viên theo subject + chapter (của các item) + tổng điểm
    @Query("""
        select b.id
        from QuestionBundle b
          join BundleItem bi on bi.bundle.id = b.id
          join QuestionMeta qm on qm.questionId = bi.question.id
        where b.subject.id = :subjectId
          and qm.status = 'APPROVED'
          and (:chapter is null or qm.chapter = :chapter)
        group by b.id
        having sum(coalesce(bi.pointsOverride, qm.points)) between :minPts and :maxPts
        """)
    List<Long> findCandidateBundleIds(Long subjectId, Integer chapter,
                                      BigDecimal minPts, BigDecimal maxPts);

    // Lấy danh sách questionId trong bundle theo thứ tự
    @Query("""
        select bi.question.id
        from BundleItem bi
        where bi.bundle.id = :bundleId
        order by bi.orderIndex asc
        """)
    List<Long> findQuestionIdsInBundle(Long bundleId);

    // (tuỳ) Tính tổng điểm bundle (nếu muốn tách riêng)
    @Query("""
        select sum(coalesce(bi.pointsOverride, qm.points))
        from BundleItem bi
          join QuestionMeta qm on qm.questionId = bi.question.id
        where bi.bundle.id = :bundleId
        """)
    BigDecimal sumPointsOfBundle(Long bundleId);
}

