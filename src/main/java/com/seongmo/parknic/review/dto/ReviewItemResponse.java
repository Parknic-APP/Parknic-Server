package com.seongmo.parknic.review.dto;

public record ReviewItemResponse(
        Long id,
        Long userId,
        String nickname,
        Short characterId,
        int badgeCount,
        int rating,
        String content,
        String createdAt,
        boolean isMine
) {
}
