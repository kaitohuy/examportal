package com.exam.examserver.repo;

import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.model.exam.QuestionBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface QuestionBundleRepository extends JpaRepository<QuestionBundle, Long>, JpaSpecificationExecutor<QuestionBundle> {

    @Query("""
        select b.id
        from QuestionBundle b
          join BundleItem bi on bi.bundle.id = b.id
          join QuestionMeta qm on qm.questionId = bi.question.id
        where b.subject.id = :subjectId
          and bi.isDeleted = false
          and qm.status = 'APPROVED'
          and (:chapter is null or qm.chapter = :chapter)
        group by b.id
        having sum(coalesce(bi.pointsOverride, qm.points)) between :minPts and :maxPts
        """)
    List<Long> findCandidateBundleIds(@Param("subjectId") Long subjectId,
                                      @Param("chapter") Integer chapter,
                                      @Param("minPts") BigDecimal minPts,
                                      @Param("maxPts") BigDecimal maxPts);

    @Query("""
        select b.id
        from QuestionBundle b
          join BundleItem bi on bi.bundle.id = b.id
          join QuestionMeta qm on qm.questionId = bi.question.id
        where b.subject.id = :subjectId
          and bi.isDeleted = false
          and qm.status = 'APPROVED'
          and (:chapter is null or qm.chapter = :chapter)
          and (
            :labelsEmpty = true or
            not exists (
              select 1 from BundleItem bix
                join Question qx on qx.id = bix.question.id
                join QuestionMeta qmx on qmx.questionId = qx.id
              where bix.bundle.id = b.id
                and bix.isDeleted = false
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
    List<Long> findCandidateBundleIdsByLabels(@Param("subjectId") Long subjectId,
                                              @Param("chapter") Integer chapter,
                                              @Param("minPts") BigDecimal minPts,
                                              @Param("maxPts") BigDecimal maxPts,
                                              @Param("labels") Collection<QuestionLabel> labels,
                                              @Param("labelsEmpty") boolean labelsEmpty);

    // OLD (comment): lấy cả question đã xoá trong bundle
    /*
    @Query("""
        select bi.question.id
        from BundleItem bi
        where bi.bundle.id = :bundleId
        order by bi.orderIndex asc
        """)
    List<Long> findQuestionIdsInBundle(@Param("bundleId") Long bundleId);
    */

    // NEW: chỉ lấy question còn hoạt động trong bundle
    @Query("""
        select bi.question.id
        from BundleItem bi
        where bi.bundle.id = :bundleId and bi.isDeleted = false
        order by bi.orderIndex asc
        """)
    List<Long> findActiveQuestionIdsInBundle(@Param("bundleId") Long bundleId);

    @Query("select b.instructions from QuestionBundle b where b.id = :id")
    String findInstructionsById(@Param("id") Long id);
}
