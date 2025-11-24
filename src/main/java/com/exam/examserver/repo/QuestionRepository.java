package com.exam.examserver.repo;

import com.exam.examserver.enums.Difficulty;
import com.exam.examserver.enums.QuestionLabel;
import com.exam.examserver.model.exam.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {

    // (giữ lại các helper khác vẫn dùng)
    List<Question> findByDifficulty(Difficulty difficulty);

    @Query("SELECT q FROM Question q WHERE q.content = :content")
    Question findFirstByContent(@Param("content") String content);

    // OLD (comment): lấy theo id không lọc thùng rác
    // @EntityGraph(attributePaths = {"labels", "createdBy"})
    // List<Question> findByIdIn(Collection<Long> ids);

    // NEW: luôn lọc isDeleted=false
    @EntityGraph(attributePaths = {"labels", "createdBy"})
    @Query("select q from Question q where q.id in :ids and q.isDeleted = false")
    List<Question> findByIdIn(@Param("ids") Collection<Long> ids);

    // Clones (paging)
    // OLD (comment): không lọc thùng rác
    /*
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
    */

    // NEW: chỉ lấy clones còn hoạt động
    @EntityGraph(attributePaths = {"labels", "createdBy"})
    @Query(value = """
       SELECT q FROM Question q
       WHERE q.parent.id = :parentId and q.isDeleted = false
       ORDER BY q.cloneIndex ASC
       """,
            countQuery = """
       SELECT COUNT(q) FROM Question q
       WHERE q.parent.id = :parentId and q.isDeleted = false
       """)
    Page<Question> findClonesByParentId(@Param("parentId") Long parentId, Pageable pageable);

    @Query("select coalesce(max(q.cloneIndex), 0) from Question q where q.parent.id = :parentId")
    Integer findMaxCloneIndexByParentId(@Param("parentId") Long parentId);

    // ===== COUNTS (giữ nguyên) =====
    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
    long countByCreatedAtGreaterThanEqual(LocalDateTime from);
    long countByCreatedAtLessThan(LocalDateTime to);

    long countByDifficulty(Difficulty difficulty);
    long countByDifficultyAndCreatedAtBetween(Difficulty difficulty, LocalDateTime from, LocalDateTime to);
    long countByDifficultyAndCreatedAtGreaterThanEqual(Difficulty difficulty, LocalDateTime from);
    long countByDifficultyAndCreatedAtLessThan(Difficulty difficulty, LocalDateTime to);

    long countByIsDeletedFalse();

    long countByIsDeletedFalseAndCreatedAtBetween(LocalDateTime from, LocalDateTime to);
    long countByIsDeletedFalseAndCreatedAtGreaterThanEqual(LocalDateTime from);
    long countByIsDeletedFalseAndCreatedAtLessThan(LocalDateTime to);

    long countByIsDeletedFalseAndDifficulty(Difficulty difficulty);
    long countByIsDeletedFalseAndDifficultyAndCreatedAtBetween(Difficulty difficulty, LocalDateTime from, LocalDateTime to);
    long countByIsDeletedFalseAndDifficultyAndCreatedAtGreaterThanEqual(Difficulty difficulty, LocalDateTime from);
    long countByIsDeletedFalseAndDifficultyAndCreatedAtLessThan(Difficulty difficulty, LocalDateTime to);

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

    // OLD (comment) duplicate signature from above; left for reference
    // List<Question> findByIdIn(List<Long> ids);

    boolean existsBySubjectIdAndQuestionCode(Long subjectId, String questionCode);
    boolean existsBySubjectIdAndQuestionCodeIgnoreCase(Long subjectId, String questionCode);

    // NEW (tuỳ chính sách reuse code): kiểm tra trùng code chỉ trong các bản chưa xoá
    boolean existsBySubjectIdAndQuestionCodeAndIsDeletedFalse(Long subjectId, String questionCode);
    boolean existsBySubjectIdAndQuestionCodeIgnoreCaseAndIsDeletedFalse(Long subjectId, String questionCode);

    @Modifying
    @Query("update Question q set q.questionCode=:code where q.id=:id")
    int updateQuestionCode(@Param("id") Long id, @Param("code") String code);

    // OLD (comment): chưa lọc q.isDeleted
    /*
    @Query("""
    select q.id
    from Question q
      join QuestionMeta qm on qm.questionId = q.id
    where q.subject.id = :subjectId
      and qm.status = com.exam.examserver.enums.RecordStatus.APPROVED
      and (:chapter is null or qm.chapter = :chapter)
      and (
        :labelsEmpty = true or
        exists (
          select 1 from Question qq join qq.labels lb
          where qq.id = q.id and lb in :labels
        )
      )
  """)
    List<Long> findApprovedIdsByScopeAndLabels(@Param("subjectId") Long subjectId,
                                               @Param("chapter") Integer chapter,
                                               @Param("labels") Collection<QuestionLabel> labels,
                                               @Param("labelsEmpty") boolean labelsEmpty);
    */

    // NEW: chỉ trả id câu APPROVED mà chưa bị xoá
    @Query("""
    select q.id
    from Question q
      join QuestionMeta qm on qm.questionId = q.id
    where q.isDeleted = false
      and q.subject.id = :subjectId
      and qm.status = com.exam.examserver.enums.RecordStatus.APPROVED
      and (:chapter is null or qm.chapter = :chapter)
      and (
        :labelsEmpty = true or
        exists (
          select 1 from Question qq join qq.labels lb
          where qq.id = q.id and lb in :labels
        )
      )
  """)
    List<Long> findApprovedIdsByScopeAndLabels(@Param("subjectId") Long subjectId,
                                               @Param("chapter") Integer chapter,
                                               @Param("labels") Collection<QuestionLabel> labels,
                                               @Param("labelsEmpty") boolean labelsEmpty);

    /** Id các câu đang ở thùng rác và thuộc subject cho phép purge. */
    @Query("""
        select q.id from Question q
        where q.subject.id = :subjectId and q.isDeleted = true and q.id in :ids
    """)
    List<Long> findTrashIdsOfSubject(@Param("subjectId") Long subjectId, @Param("ids") Collection<Long> ids);

    /** Lấy id các clone 1 bậc (child) của danh sách parentIds. */
    @Query("select q.id from Question q where q.parent.id in :parentIds")
    List<Long> findChildIds(@Param("parentIds") Collection<Long> parentIds);

    /** Xoá theo batch (JPQL) – trả về số bản ghi. */
    @Modifying
    @Query("delete from Question q where q.id in :ids")
    int deleteByIdIn(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query(value = "delete from question_labels where question_id in (:ids)", nativeQuery = true)
    void deleteLabelsByQuestionIds(@Param("ids") Collection<Long> ids);

    List<Question> findAllByQuestionCodeIn(Collection<String> codes);

    @Query("""
       select q from Question q
       where q.isDeleted = false
         and lower(q.questionCode) in :codesLower
    """)
    List<Question> findAllByQuestionCodeLowerIn(@Param("codesLower") Collection<String> codesLower);

    Optional<Question> findBySubjectIdAndQuestionCodeIgnoreCase(Long subjectId, String questionCode);
}
