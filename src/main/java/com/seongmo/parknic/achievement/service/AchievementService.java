package com.seongmo.parknic.achievement.service;

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
import java.util.ArrayList;
import java.util.List;

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
        Progress progress = new Progress(
                badgeRepository.countByUserId(userId),
                badgeRepository.countDistinctParkByUserId(userId),
                StreakCalculator.current(badgeRepository.findEarnedDatesByUserId(userId), Kst.dateOf(now)));

        List<Achievement> granted = new ArrayList<>();
        for (Achievement achievement : achievementRepository.findAllByOrderByThresholdAsc()) {
            if (isMet(achievement, progress)
                    && userAchievementRepository.insertIfAbsent(userId, achievement.getCode(), now)) {
                granted.add(achievement);
            }
        }
        return granted;
    }

    private boolean isMet(Achievement achievement, Progress progress) {
        String code = achievement.getCode();
        if (FIRST_BADGE.equals(code)) {
            return progress.totalBadges() >= achievement.getThreshold();
        }
        if (code.startsWith(PARKS_PREFIX)) {
            return progress.visitedParks() >= achievement.getThreshold();
        }
        if (code.startsWith(STREAK_PREFIX)) {
            return progress.currentStreak() >= achievement.getThreshold();
        }
        return false;
    }

    // 업적 판정에 쓰는 값. 새 종류의 업적이 생기면 필드를 하나 추가
    private record Progress(long totalBadges, long visitedParks, int currentStreak) { }
}
