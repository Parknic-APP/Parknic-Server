package com.seongmo.parknic.review.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "park_id", nullable = false)
    private Long parkId;

    @Column(nullable = false)
    private short rating;

    @Column(nullable = false, length = 300)
    private String content;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Review(Long userId, Long parkId, short rating, String content, Instant now) {
        this.userId = userId;
        this.parkId = parkId;
        this.rating = rating;
        this.content = content;
        this.hidden = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Review write(Long userId, Long parkId, int rating, String content, Instant now) {
        return new Review(userId, parkId, (short) rating, content, now);
    }

    public void update(int rating, String content, Instant now) {
        this.rating = (short) rating;
        this.content = content;
        this.updatedAt = now;
    }

    public void hide() {
        this.hidden = true;
    }

}
