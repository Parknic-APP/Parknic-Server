package com.seongmo.parknic.ranking.service;

import com.seongmo.parknic.common.error.BusinessException;
import com.seongmo.parknic.common.error.ErrorCode;
import com.seongmo.parknic.common.time.Kst;
import com.seongmo.parknic.park.repository.ParkRepository;
import com.seongmo.parknic.ranking.dto.MyRankingResponse;
import com.seongmo.parknic.ranking.dto.RankingEntryResponse;
import com.seongmo.parknic.ranking.dto.RankingResponse;
import com.seongmo.parknic.ranking.dto.WeeklyStanding;
import com.seongmo.parknic.ranking.repository.RankingQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RankingService {

    public static final int PODIUM_SIZE = 3;
    private static final int MAX_RANK = 20;

    private final ParkRepository parkRepository;
    private final RankingQueryRepository rankingQueryRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RankingResponse getWeeklyRanking(Long userId, Long parkId, LocalDate week) {
        if (!parkRepository.existsById(parkId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        LocalDate weekStart = Kst.weekStartOf(week != null ? week : Kst.today(clock));
        LocalDate weekEnd = weekStart.plusDays(6);

        List<RankingEntryResponse> rows =
                rankingQueryRepository.findWeekly(parkId, userId, weekStart, weekEnd, MAX_RANK);

        List<RankingEntryResponse> podium = rows.stream()
                .filter(r -> r.rank() <= PODIUM_SIZE)
                .toList();
        List<RankingEntryResponse> others = rows.stream()
                .filter(r -> r.rank() > PODIUM_SIZE && r.rank() <= MAX_RANK)
                .toList();

        return new RankingResponse(weekStart, weekEnd, podium, others, findMe(rows, userId));
    }

    // 체크인 완료 응답용: 그 날짜가 속한 주의 내 순위와 3위의 배지 개수
    @Transactional(readOnly = true)
    public WeeklyStanding getStanding(Long userId, Long parkId, LocalDate date) {
        LocalDate weekStart = Kst.weekStartOf(date);

        // 상위 3명과 내 행만 가져옴
        List<RankingEntryResponse> rows =
                rankingQueryRepository.findWeekly(parkId, userId, weekStart, weekStart.plusDays(6), PODIUM_SIZE);

        int thirdBadgeCount = rows.stream()
                .filter(r -> r.rank() == PODIUM_SIZE)
                .map(RankingEntryResponse::badgeCount)
                .findFirst()
                .orElse(0);

        return new WeeklyStanding(findMe(rows, userId), thirdBadgeCount);
    }

    private MyRankingResponse findMe(List<RankingEntryResponse> rows, Long userId) {
        return rows.stream()
                .filter(r -> r.userId().equals(userId))
                .findFirst()
                .map(r -> new MyRankingResponse(r.rank(), r.badgeCount()))
                .orElse(null);
    }
}
