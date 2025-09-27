package com.exam.examserver.repo;

import com.exam.examserver.model.exam.QuestionFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuestionFingerprintRepository extends JpaRepository<QuestionFingerprint, Long> {

    // Lấy candidate theo từng band (mỗi band limit N ở service)
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
}
