package com.seongmo.parknic.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(nullable = false)
    private boolean used;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    private RefreshToken(Long userId, UUID familyId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.used = false;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(Long userId, UUID familyId, String tokenHash, Instant expiresAt) {
        return new RefreshToken(userId, familyId, tokenHash, expiresAt);
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
