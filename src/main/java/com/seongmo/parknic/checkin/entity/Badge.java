package com.seongmo.parknic.checkin.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "badges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Badge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "park_id", nullable = false)
    private Long parkId;

    @Column(name = "earned_date", nullable = false)
    private LocalDate earnedDate;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    private Badge(Long userId, Long parkId, LocalDate earnedDate, Instant earnedAt) {
        this.userId = userId;
        this.parkId = parkId;
        this.earnedDate = earnedDate;
        this.earnedAt = earnedAt;
    }

    public static Badge earn(Long userId, Long parkId, LocalDate earnedDate, Instant earnedAt) {
        return new Badge(userId, parkId, earnedDate, earnedAt);
    }
}
