package com.exam.examserver.model;

import com.exam.examserver.model.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class PasswordResetToken {
    @Id
    @GeneratedValue
    Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    User user;
    @Column(nullable=false, length=64) String tokenHash; // SHA-256
    @Column(nullable=false)
    Instant expiresAt;
    @Column(nullable=false) boolean used = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }
}

