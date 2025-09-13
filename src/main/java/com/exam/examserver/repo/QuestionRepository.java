package com.exam.examserver.repo;

import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.model.exam.Question;
import com.exam.examserver.model.exam.Quiz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    // Danh sách chính: CHỈ bản gốc (parent is null)
    @EntityGraph(attributePaths = {"labels", "createdBy"})
    @Query("SELECT q FROM Question q WHERE q.subject.id = :subjectId AND q.parent IS NULL")
    List<Question> findBySubjectId(@Param("subjectId") Long subjectId);

    List<Question> findByDifficulty(Difficulty difficulty);

    @Query("SELECT q FROM Question q WHERE q.content = :content")
    Question findFirstByContent(@Param("content") String content);

    // Lọc theo nhãn (chỉ bản gốc)
    @EntityGraph(attributePaths = {"labels", "createdBy"})
    @Query("""
           SELECT DISTINCT q FROM Question q
           LEFT JOIN q.labels l
           WHERE q.subject.id = :subjectId AND q.parent IS NULL AND l IN :labels
           """)
    List<Question> findBySubjectIdAndAnyLabelIn(@Param("subjectId") Long subjectId,
                                                @Param("labels") Set<QuestionLabel> labels);

    // Tải theo id list (kèm labels)
    @EntityGraph(attributePaths = {"labels", "createdBy"})
    List<Question> findByIdIn(Collection<Long> ids);

    // Lấy clones của 1 bản gốc
    @EntityGraph(attributePaths = {"labels", "createdBy"})
    @Query("SELECT q FROM Question q WHERE q.parent.id = :parentId ORDER BY q.cloneIndex ASC")
    List<Question> findClonesByParentId(@Param("parentId") Long parentId);

    @Query("select coalesce(max(q.cloneIndex), 0) from Question q where q.parent.id = :parentId")
    Integer findMaxCloneIndexByParentId(@Param("parentId") Long parentId);

    // Đếm theo createdAt (không null-param trong SQL)
    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
    long countByCreatedAtGreaterThanEqual(LocalDateTime from);
    long countByCreatedAtLessThan(LocalDateTime to);

    // Theo độ khó + thời gian
    long countByDifficulty(Difficulty difficulty);
    long countByDifficultyAndCreatedAtBetween(Difficulty difficulty, LocalDateTime from, LocalDateTime to);
    long countByDifficultyAndCreatedAtGreaterThanEqual(Difficulty difficulty, LocalDateTime from);
    long countByDifficultyAndCreatedAtLessThan(Difficulty difficulty, LocalDateTime to);

    // Theo nhãn (labels là ElementCollection -> dùng @Query JOIN, nhưng KHÔNG truyền null)
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
