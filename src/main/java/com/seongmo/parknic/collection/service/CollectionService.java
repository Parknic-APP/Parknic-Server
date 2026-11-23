package com.seongmo.parknic.collection.service;

import com.seongmo.parknic.achievement.service.AchievementService;
import com.seongmo.parknic.checkin.repository.BadgeRepository;
import com.seongmo.parknic.collection.dto.CollectionResponse;
import com.seongmo.parknic.collection.repository.CollectionQueryRepository;
import com.seongmo.parknic.common.time.Kst;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CollectionService {

    private final Clock clock;
    private final BadgeRepository badgeRepository;
    private final CollectionQueryRepository collectionQueryRepository;
    private final AchievementService achievementService;

    @Transactional(readOnly = true)
    public CollectionResponse getCollection(Long userId) {
        List<CollectionResponse.AchievementItem> achievements =
                achievementService.getStatuses(userId, Kst.today(clock)).stream()
                        .map(a -> new CollectionResponse.AchievementItem(
                                a.code(),
                                a.name(),
                                a.label(),
                                a.achievedAt() == null ? null : Kst.format(a.achievedAt()),
                                a.progress(),
                                a.target()))
                        .toList();

        return new CollectionResponse(
                (int) badgeRepository.countByUserId(userId),
                (int) badgeRepository.countDistinctParkByUserId(userId),
                collectionQueryRepository.countVisitedParksByType(userId),
                achievements);
    }
}
