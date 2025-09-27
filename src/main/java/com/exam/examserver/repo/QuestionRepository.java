package com.exam.examserver.repo;

import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.model.exam.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {
    // (giữ lại các helper khác vẫn dùng)
    List<Question> findByDifficulty(Difficulty difficulty);

    @Query("SELECT q FROM Question q WHERE q.content = :content")
    Question findFirstByContent(@Param("content") String content);

    @EntityGraph(attributePaths = {"labels", "createdBy"})
    List<Question> findByIdIn(Collection<Long> ids);

    // Clones (vẫn dùng paging version)
    @EntityGraph(attributePaths = {"labels", "createdBy"})
    @Query(value = """
       SELECT q FROM Question q
       WHERE q.parent.id = :parentId
       ORDER BY q.cloneIndex ASC
       """,
            countQuery = """
       SELECT COUNT(q) FROM Question q
       WHERE q.parent.id = :parentId
       """)
    Page<Question> findClonesByParentId(@Param("parentId") Long parentId, Pageable pageable);

    @Query("select coalesce(max(q.cloneIndex), 0) from Question q where q.parent.id = :parentId")
    Integer findMaxCloneIndexByParentId(@Param("parentId") Long parentId);

    // ===== COUNTS (giữ nguyên vì có thể dùng chỗ khác) =====
    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
    long countByCreatedAtGreaterThanEqual(LocalDateTime from);
    long countByCreatedAtLessThan(LocalDateTime to);

    long countByDifficulty(Difficulty difficulty);
    long countByDifficultyAndCreatedAtBetween(Difficulty difficulty, LocalDateTime from, LocalDateTime to);
    long countByDifficultyAndCreatedAtGreaterThanEqual(Difficulty difficulty, LocalDateTime from);
    long countByDifficultyAndCreatedAtLessThan(Difficulty difficulty, LocalDateTime to);

    @Query("select count(q) from Question q join q.labels l where l = :label")
    long countByLabel(@Param("label") QuestionLabel label);

    @Query("select count(q) from Question q join q.labels l where l = :label and q.createdAt >= :from and q.createdAt < :to")
    long countByLabelBetween(@Param("label") QuestionLabel label,
                             @Param("from") LocalDateTime from,
                             @Param("to")   LocalDateTime to);

    @Query("select count(q) from Question q join q.labels l where l = :label and q.createdAt >= :from")
    long countByLabelFrom(@Param("label") QuestionLabel label,
                          @Param("from") LocalDateTime from);

    @Query("select count(q) from Question q join q.labels l where l = :label and q.createdAt < :to")
    long countByLabelTo(@Param("label") QuestionLabel label,
                        @Param("to")   LocalDateTime to);

}
