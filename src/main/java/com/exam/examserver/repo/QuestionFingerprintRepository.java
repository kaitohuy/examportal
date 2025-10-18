package com.exam.examserver.repo;

import com.exam.examserver.model.exam.QuestionFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionFingerprintRepository extends JpaRepository<QuestionFingerprint, Long> {

    // OLD (comment): không lọc câu trong thùng rác
    /*
    @Query("""
        SELECT qf.questionId
        FROM QuestionFingerprint qf
        WHERE qf.subjectId = :subjectId AND qf.isRoot = true AND qf.b1 = :b
    """)
    List<Long> findByBand1(long subjectId, int b);

    @Query("""
        SELECT qf.questionId
        FROM QuestionFingerprint qf
        WHERE qf.subjectId = :subjectId AND qf.isRoot = true AND qf.b2 = :b
    """)
    List<Long> findByBand2(long subjectId, int b);

    @Query("""
        SELECT qf.questionId
        FROM QuestionFingerprint qf
        WHERE qf.subjectId = :subjectId AND qf.isRoot = true AND qf.b3 = :b
    """)
    List<Long> findByBand3(long subjectId, int b);

    @Query("""
        SELECT qf.questionId
        FROM QuestionFingerprint qf
        WHERE qf.subjectId = :subjectId AND qf.isRoot = true AND qf.b4 = :b
    """)
    List<Long> findByBand4(long subjectId, int b);
    */

    // NEW: join sang Question để lọc isDeleted=false
    @Query("""
        SELECT qf.questionId
        FROM QuestionFingerprint qf
          JOIN Question q ON q.id = qf.questionId
        WHERE qf.subjectId = :subjectId AND qf.isRoot = true AND qf.b1 = :b
          AND q.isDeleted = false
    """)
    List<Long> findByBand1(@Param("subjectId") long subjectId, @Param("b") int b);

    @Query("""
        SELECT qf.questionId
        FROM QuestionFingerprint qf
          JOIN Question q ON q.id = qf.questionId
        WHERE qf.subjectId = :subjectId AND qf.isRoot = true AND qf.b2 = :b
          AND q.isDeleted = false
    """)
    List<Long> findByBand2(@Param("subjectId") long subjectId, @Param("b") int b);

    @Query("""
        SELECT qf.questionId
        FROM QuestionFingerprint qf
          JOIN Question q ON q.id = qf.questionId
        WHERE qf.subjectId = :subjectId AND qf.isRoot = true AND qf.b3 = :b
          AND q.isDeleted = false
    """)
    List<Long> findByBand3(@Param("subjectId") long subjectId, @Param("b") int b);

    @Query("""
        SELECT qf.questionId
        FROM QuestionFingerprint qf
          JOIN Question q ON q.id = qf.questionId
        WHERE qf.subjectId = :subjectId AND qf.isRoot = true AND qf.b4 = :b
          AND q.isDeleted = false
    """)
    List<Long> findByBand4(@Param("subjectId") long subjectId, @Param("b") int b);
}
