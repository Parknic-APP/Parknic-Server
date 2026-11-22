package com.seongmo.parknic.ranking.dto;

import java.time.LocalDate;
import java.util.List;

public record RankingResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        List<RankingEntryResponse> podium,
        List<RankingEntryResponse> others,
        MyRankingResponse me
) {
}
