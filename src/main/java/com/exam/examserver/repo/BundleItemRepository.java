package com.exam.examserver.repo;

import com.exam.examserver.model.exam.BundleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

// BundleItemRepository.java
@Repository
public interface BundleItemRepository extends JpaRepository<BundleItem, Long> {
    List<BundleItem> findByBundleIdOrderByOrderIndexAsc(Long bundleId);

    @Query("select distinct bi.bundle.id from BundleItem bi where bi.question.id = :qid")
    List<Long> findBundleIdsByQuestionId(@Param("qid") Long questionId);

    long countByBundleId(Long bundleId);

    @Query("""
   select bi.question.id as questionId, b.instructions as instructions
   from BundleItem bi join bi.bundle b
   where bi.question.id in :qids
     and b.instructions is not null
     and length(trim(b.instructions)) > 0
""")
    List<QuestionBundleStemView> findStemsByQuestionIds(@Param("qids") Collection<Long> qids);
}
