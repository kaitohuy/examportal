// src/main/java/com/exam/examserver/repo/QuestionBundleRepository.java
package com.exam.examserver.repo;

import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.model.exam.QuestionBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface QuestionBundleRepository extends JpaRepository<QuestionBundle, Long>, JpaSpecificationExecutor<QuestionBundle> {

    // Ứng viên bundle theo subject/chapter/điểm (không lọc nhãn)
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

    // Ứng viên bundle có lọc nhãn: mọi item trong bundle phải có ÍT NHẤT 1 nhãn thuộc :labels
    @Query("""
        select b.id
        from QuestionBundle b
          join BundleItem bi on bi.bundle.id = b.id
          join QuestionMeta qm on qm.questionId = bi.question.id
        where b.subject.id = :subjectId
          and qm.status = 'APPROVED'
          and (:chapter is null or qm.chapter = :chapter)
          and (
            :labelsEmpty = true or
            not exists (
              select 1 from BundleItem bix
                join Question qx on qx.id = bix.question.id
                join QuestionMeta qmx on qmx.questionId = qx.id
              where bix.bundle.id = b.id
                and qmx.status = 'APPROVED'
                and (:chapter is null or qmx.chapter = :chapter)
                and not exists (
                  select 1 from Question qq join qq.labels lb
                  where qq.id = qx.id and lb in :labels
                )
            )
          )
        group by b.id
        having sum(coalesce(bi.pointsOverride, qm.points)) between :minPts and :maxPts
        """)
    List<Long> findCandidateBundleIdsByLabels(Long subjectId, Integer chapter,
                                              BigDecimal minPts, BigDecimal maxPts,
                                              Collection<QuestionLabel> labels,
                                              boolean labelsEmpty);

    // Danh sách questionId trong bundle theo thứ tự
    @Query("""
        select bi.question.id
        from BundleItem bi
        where bi.bundle.id = :bundleId
        order by bi.orderIndex asc
        """)
    List<Long> findQuestionIdsInBundle(Long bundleId);
}
