package com.seongmo.parknic.checkin.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "checkin_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckinSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "park_id", nullable = false)
    private Long parkId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckinStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    private CheckinSession(Long userId, Long parkId, Instant startedAt) {
        this.userId = userId;
        this.parkId = parkId;
        this.status = CheckinStatus.IN_PROGRESS;
        this.startedAt = startedAt;
    }

    public static CheckinSession start(Long userId, Long parkId, Instant now) {
        return new CheckinSession(userId, parkId, now);
    }

    public void cancel(Instant now) {
        this.status = CheckinStatus.CANCELED;
        this.endedAt = now;
    }
}
