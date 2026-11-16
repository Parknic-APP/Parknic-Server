package com.seongmo.parknic.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(length = 20)
    private String nickname;

    @Column(name = "character_id")
    private Short characterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "apple_refresh_token")
    private String appleRefreshToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private User(String provider, String providerId, UserStatus status, Instant createdAt) {
        this.provider = provider;
        this.providerId = providerId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static User pending(String provider, String providerId, Instant now) {
        return new User(provider, providerId, UserStatus.PENDING, now);
    }
}
