package com.seongmo.parknic.ranking.service;

import com.seongmo.parknic.common.error.BusinessException;
import com.seongmo.parknic.common.error.ErrorCode;
import com.seongmo.parknic.common.time.Kst;
import com.seongmo.parknic.park.repository.ParkRepository;
import com.seongmo.parknic.ranking.dto.MyRankingResponse;
import com.seongmo.parknic.ranking.dto.RankingEntryResponse;
import com.seongmo.parknic.ranking.dto.RankingResponse;
import com.seongmo.parknic.ranking.repository.RankingQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RankingService {

    private static final int PODIUM_SIZE = 3;
    private static final int MAX_RANK = 20;

    private final ParkRepository parkRepository;
    private final RankingQueryRepository rankingQueryRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RankingResponse getWeeklyRanking(Long userId, Long parkId, LocalDate week) {
        if (!parkRepository.existsById(parkId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 그 주의 월~일요일 (week가 없으면 오늘이 속한 주)
        LocalDate base = (week != null) ? week : Kst.today(clock);
        LocalDate weekStart = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        List<RankingEntryResponse> rows =
                rankingQueryRepository.findWeekly(parkId, userId, weekStart, weekEnd, MAX_RANK);

        List<RankingEntryResponse> podium = rows.stream()
                .filter(r -> r.rank() <= PODIUM_SIZE)
                .toList();
        List<RankingEntryResponse> others = rows.stream()
                .filter(r -> r.rank() > PODIUM_SIZE && r.rank() <= MAX_RANK)
                .toList();
        MyRankingResponse me = rows.stream()
                .filter(r -> r.userId().equals(userId))
                .findFirst()
                .map(r -> new MyRankingResponse(r.rank(), r.badgeCount()))
                .orElse(null);

        return new RankingResponse(weekStart, weekEnd, podium, others, me);
    }
}
