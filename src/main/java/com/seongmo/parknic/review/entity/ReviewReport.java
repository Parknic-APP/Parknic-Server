package com.seongmo.parknic.review.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "review_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReport {

    private static final String STATUS_PENDING = "PENDING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private ReviewReport(Long reviewId, Long reporterId, ReportReason reason, Instant now) {
        this.reviewId = reviewId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.status = STATUS_PENDING;
        this.createdAt = now;
    }

    public static ReviewReport report(Long reviewId, Long reporterId, ReportReason reason, Instant now) {
        return new ReviewReport(reviewId, reporterId, reason, now);
    }
}
