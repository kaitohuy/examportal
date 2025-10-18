// src/main/java/.../repo/BundleFingerprintRepository.java
package com.exam.examserver.repo;

import com.exam.examserver.model.exam.BundleFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;   // <-- nhớ import

import java.util.Collection;
import java.util.List;

public interface BundleFingerprintRepository extends JpaRepository<BundleFingerprint, Long> {

    @Query("select bf.bundleId from BundleFingerprint bf where bf.subjectId = :subjectId and bf.b1 = :b")
    List<Long> findByBand1(@Param("subjectId") long subjectId, @Param("b") int b);

    @Query("select bf.bundleId from BundleFingerprint bf where bf.subjectId = :subjectId and bf.b2 = :b")
    List<Long> findByBand2(@Param("subjectId") long subjectId, @Param("b") int b);

    @Query("select bf.bundleId from BundleFingerprint bf where bf.subjectId = :subjectId and bf.b3 = :b")
    List<Long> findByBand3(@Param("subjectId") long subjectId, @Param("b") int b);

    @Query("select bf.bundleId from BundleFingerprint bf where bf.subjectId = :subjectId and bf.b4 = :b")
    List<Long> findByBand4(@Param("subjectId") long subjectId, @Param("b") int b);

    @Query("""
        select bf.bundleId, bf.simhash64
        from BundleFingerprint bf
        where bf.bundleId in :ids
        """)
    List<Object[]> findSimhashByBundleIds(@Param("ids") Collection<Long> ids);
}
