package com.seongmo.parknic.achievement.service;

import com.seongmo.parknic.achievement.entity.Achievement;
import com.seongmo.parknic.achievement.repository.AchievementRepository;
import com.seongmo.parknic.achievement.repository.UserAchievementRepository;
import com.seongmo.parknic.checkin.repository.BadgeRepository;
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

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final BadgeRepository badgeRepository;

    // 이번 배지로 새로 달성한 업적을 저장하고 돌려줌. 배지가 저장(flush)된 뒤에 호출해야 방금 배지가 개수에 포함됨
    @Transactional
    public List<Achievement> grantNewAchievements(Long userId, Instant now) {
        long totalBadges = badgeRepository.countByUserId(userId);
        long visitedParks = badgeRepository.countDistinctParkByUserId(userId);

        List<Achievement> granted = new ArrayList<>();
        for (Achievement achievement : achievementRepository.findAllByOrderByThresholdAsc()) {
            if (isMet(achievement, totalBadges, visitedParks)
                    && userAchievementRepository.insertIfAbsent(userId, achievement.getCode(), now)) {
                granted.add(achievement);
            }
        }
        return granted;
    }

    private boolean isMet(Achievement achievement, long totalBadges, long visitedParks) {
        String code = achievement.getCode();
        if (FIRST_BADGE.equals(code)) {
            return totalBadges >= achievement.getThreshold();
        }
        if (code.startsWith(PARKS_PREFIX)) {
            return visitedParks >= achievement.getThreshold();
        }
        // STREAK_7 등 아직 구현하지 않은 업적
        return false;
    }
}
