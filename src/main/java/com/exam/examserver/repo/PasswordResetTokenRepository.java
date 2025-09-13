package com.exam.examserver.repo;

import com.exam.examserver.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findFirstByTokenHashAndUsedFalse(String tokenHash);

    @Modifying
    @Query("""
           delete from PasswordResetToken t
           where t.used = true or t.expiresAt < :now
           """)
    void deleteAllExpiredOrUsed(Instant now);
}