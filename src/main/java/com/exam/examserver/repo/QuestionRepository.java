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
}
