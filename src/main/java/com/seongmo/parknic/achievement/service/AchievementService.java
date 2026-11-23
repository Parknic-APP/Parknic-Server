package com.seongmo.parknic.achievement.service;

import com.seongmo.parknic.achievement.dto.AchievementStatus;
import com.seongmo.parknic.achievement.entity.Achievement;
import com.seongmo.parknic.achievement.repository.AchievementRepository;
import com.seongmo.parknic.achievement.repository.UserAchievementRepository;
import com.seongmo.parknic.checkin.repository.BadgeRepository;
import com.seongmo.parknic.common.time.Kst;
import com.seongmo.parknic.user.service.StreakCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AchievementService {

    private static final String FIRST_BADGE = "FIRST_BADGE";
    private static final String PARKS_PREFIX = "PARKS_";
    private static final String STREAK_PREFIX = "STREAK_";

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final BadgeRepository badgeRepository;

    // 이번 배지로 새로 달성한 업적을 저장하고 돌려줌. 배지가 저장(flush)된 뒤에 호출해야 방금 배지가 개수에 포함됨
    @Transactional
    public List<Achievement> grantNewAchievements(Long userId, Instant now) {
        Progress progress = progressOf(userId, Kst.dateOf(now));

        List<Achievement> granted = new ArrayList<>();
        for (Achievement achievement : achievementRepository.findAllByOrderByThresholdAsc()) {
            if (isMet(achievement, progress)
                    && userAchievementRepository.insertIfAbsent(userId, achievement.getCode(), now)) {
                granted.add(achievement);
            }
        }
        return granted;
    }

    // 도감용: 모든 업적의 달성 시각과 진행도. 달성 시각은 한 번에 조회해서 업적마다 조회하지 않음
    @Transactional(readOnly = true)
    public List<AchievementStatus> getStatuses(Long userId, LocalDate today) {
        Progress progress = progressOf(userId, today);
        Map<String, Instant> achievedAt = userAchievementRepository.findAchievedAtByUserId(userId);

        return achievementRepository.findAllByOrderByThresholdAsc().stream()
                .map(a -> new AchievementStatus(
                        a.getCode(),
                        a.getName(),
                        a.getLabel(),
                        achievedAt.get(a.getCode()),
                        (int) Math.min(currentValue(a, progress), a.getThreshold()),
                        a.getThreshold()))
                .toList();
    }

    private Progress progressOf(Long userId, LocalDate today) {
        return new Progress(
                badgeRepository.countByUserId(userId),
                badgeRepository.countDistinctParkByUserId(userId),
                StreakCalculator.current(badgeRepository.findEarnedDatesByUserId(userId), today));
    }

    private boolean isMet(Achievement achievement, Progress progress) {
        return currentValue(achievement, progress) >= achievement.getThreshold();
    }

    // 업적 종류에 맞는 현재 값. 판정(isMet)과 진행도(getStatuses)가 같은 값을 쓴다
    private long currentValue(Achievement achievement, Progress progress) {
        String code = achievement.getCode();
        if (FIRST_BADGE.equals(code)) {
            return progress.totalBadges();
        }
        if (code.startsWith(PARKS_PREFIX)) {
            return progress.visitedParks();
        }
        if (code.startsWith(STREAK_PREFIX)) {
            return progress.currentStreak();
        }
        return 0;
    }

    // 업적 판정에 쓰는 값. 새 종류의 업적이 생기면 필드를 하나 추가
    private record Progress(long totalBadges, long visitedParks, int currentStreak) { }
}
