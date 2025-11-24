package com.exam.examserver.repo;

import com.exam.examserver.model.exam.BundleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BundleItemRepository extends JpaRepository<BundleItem, Long>, JpaSpecificationExecutor<BundleItem> {

    // NEW: chỉ item active
    @Query("""
       select bi from BundleItem bi
       where bi.bundle.id = :bundleId and bi.isDeleted = false
       order by bi.orderIndex asc
    """)
    List<BundleItem> findActiveByBundleIdOrderByOrderIndexAsc(@Param("bundleId") Long bundleId);

    // NEW: chỉ tính các liên kết còn hoạt động
    @Query("select distinct bi.bundle.id from BundleItem bi where bi.question.id = :qid and bi.isDeleted = false")
    List<Long> findActiveBundleIdsByQuestionId(@Param("qid") Long questionId);

    @Query("SELECT COUNT(bi) FROM BundleItem bi WHERE bi.bundle.id = :bundleId AND bi.isDeleted = false")
    Long countByBundleId(@Param("bundleId") Long bundleId);

    // NEW: đếm item active (dùng khi cần kiểm tra bundle còn trống hay không)
    @Query("select count(bi) from BundleItem bi where bi.bundle.id = :bundleId and bi.isDeleted = false")
    long countActiveByBundleId(@Param("bundleId") Long bundleId);

    // === Helpers khác giữ nguyên ===
    @Query("""
       select bi.question.id as questionId, b.instructions as instructions
       from BundleItem bi join bi.bundle b
       where bi.question.id in :qids
         and bi.isDeleted = false
         and b.instructions is not null
         and length(trim(b.instructions)) > 0
    """)
    List<QuestionBundleStemView> findStemsByQuestionIds(@Param("qids") Collection<Long> qids);

    // NEW: chỉ tính item chưa bị soft-delete
    @Query("""
        select distinct bi.bundle.id
        from BundleItem bi
        where bi.question.id = :questionId
          and bi.isDeleted = false
        """)
    List<Long> findBundleIdsByQuestionId(@Param("questionId") Long questionId);

    // NEW: list questionId trong 1 bundle (active only) — dùng lại ở service fingerprint
    @Query("""
       select bi.question.id
       from BundleItem bi
       where bi.bundle.id = :bundleId and bi.isDeleted = false
       order by bi.orderIndex asc
    """)
    List<Long> findActiveQuestionIdsInBundle(@Param("bundleId") Long bundleId);

    /**
     * HARD DELETE tất cả item (kể cả isDeleted=false/true) tham chiếu tới các questionId.
     */
    @Modifying
    @Query("delete from BundleItem bi where bi.question.id in :qids")
    void hardDeleteByQuestionIds(@Param("qids") Collection<Long> qids);
}
