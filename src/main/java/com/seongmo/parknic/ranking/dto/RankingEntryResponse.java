package com.seongmo.parknic.ranking.dto;

public record RankingEntryResponse(
        int rank,
        Long userId,
        String nickname,
        Short characterId,
        int badgeCount
) {
}
