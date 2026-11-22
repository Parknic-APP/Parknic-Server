package com.seongmo.parknic.ranking.controller;

import com.seongmo.parknic.common.security.CurrentUserId;
import com.seongmo.parknic.ranking.dto.RankingResponse;
import com.seongmo.parknic.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @GetMapping("/api/parks/{parkId}/rankings")
    public RankingResponse getRankings(@CurrentUserId Long userId,
                                       @PathVariable Long parkId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        return rankingService.getWeeklyRanking(userId, parkId, week);
    }
}
