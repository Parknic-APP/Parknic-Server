package com.seongmo.parknic.checkin.service;

import com.seongmo.parknic.achievement.service.AchievementService;
import com.seongmo.parknic.checkin.config.CheckinProperties;
import com.seongmo.parknic.checkin.dto.*;
import com.seongmo.parknic.checkin.entity.Badge;
import com.seongmo.parknic.checkin.entity.CheckinFailReason;
import com.seongmo.parknic.checkin.entity.CheckinSession;
import com.seongmo.parknic.checkin.entity.CheckinStatus;
import com.seongmo.parknic.checkin.repository.BadgeRepository;
import com.seongmo.parknic.checkin.repository.CheckinSessionRepository;
import com.seongmo.parknic.common.error.BusinessException;
import com.seongmo.parknic.common.error.ErrorCode;
import com.seongmo.parknic.common.geo.GeoDistance;
import com.seongmo.parknic.common.time.Kst;
import com.seongmo.parknic.park.entity.Park;
import com.seongmo.parknic.park.repository.ParkRepository;
import com.seongmo.parknic.ranking.dto.MyRankingResponse;
import com.seongmo.parknic.ranking.dto.WeeklyStanding;
import com.seongmo.parknic.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private static final String IN_PROGRESS_UNIQUE_INDEX = "uq_checkin_in_progress";
    private static final String BADGE_UNIQUE_CONSTRAINT = "uq_badges_user_park_date";

    private final CheckinSessionRepository checkinSessionRepository;
    private final ParkRepository parkRepository;
    private final BadgeRepository badgeRepository;
    private final CheckinProperties checkinProperties;
    private final RankingService rankingService;
    private final AchievementService achievementService;
    private final Clock clock;

    @Transactional
    public CheckinSessionResponse start(Long userId, StartCheckinRequest request) {
        Park park = parkRepository.findById(request.parkId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        double distance = GeoDistance.meters(
                request.lat(), request.lng(), park.getLatitude(), park.getLongitude());
        if (distance > park.getRadiusM()) {
            throw new BusinessException(ErrorCode.CHECKIN_OUT_OF_RANGE);
        }

        // 이미 체크인이 진행중인지 검사
        if (checkinSessionRepository.existsByUserIdAndStatus(userId, CheckinStatus.IN_PROGRESS)) {
            throw new BusinessException(ErrorCode.CHECKIN_ALREADY_IN_PROGRESS);
        }

        // 같은 공원에서 오늘 배지를 받았는지 검사
        if (badgeRepository.existsByUserIdAndParkIdAndEarnedDate(userId, park.getId(), Kst.today(clock))) {
            throw new BusinessException(ErrorCode.BADGE_ALREADY_EARNED_TODAY);
        }


        CheckinSession session = CheckinSession.start(userId, park.getId(), clock.instant());
        try {
            // 커밋을 기다리지 않고 INSERT를 지금 보내서, 동시 요청이 만든 UNIQUE 위반을 이 안에서 잡도록 함
            checkinSessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            String reason = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
            if (reason != null && reason.contains(IN_PROGRESS_UNIQUE_INDEX)) {
                throw new BusinessException(ErrorCode.CHECKIN_ALREADY_IN_PROGRESS);
            }
            throw e;
        }
        return CheckinSessionResponse.of(session, park);
    }

    @Transactional(readOnly = true)
    public CheckinSessionResponse getCurrent(Long userId) {
        CheckinSession session = checkinSessionRepository
                .findByUserIdAndStatus(userId, CheckinStatus.IN_PROGRESS)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        // 세션의 parkId는 FK라 공원이 없을 수 있음. 없다면 데이터 이상이므로 500으로 드러나게 함
        Park park = parkRepository.findById(session.getParkId())
                .orElseThrow(() -> new IllegalStateException("세션의 공원이 없음 sessionId = " + session.getId()));
        return CheckinSessionResponse.of(session, park);
    }

    @Transactional
    public void cancel(Long userId, Long sessionId) {
        findOwnedInProgress(userId, sessionId).cancel(clock.instant());
    }

    @Transactional
    public void fail(Long userId, Long sessionId, FailCheckinRequest request) {
        findOwnedInProgress(userId, sessionId).fail(request.reason(), clock.instant());
    }

    // 오늘 00:00(KST) 이전에 시작했는데 아직 진행 중인 세션을 모두 실패(MIDNIGHT) 처리. 여러 번 실행해도 결과가 같다
    @Transactional
    public int failExpiredSessions() {
        Instant midnight = Kst.startOfDay(Kst.today(clock));
        int failed = checkinSessionRepository.failExpired(
                midnight, midnight, CheckinStatus.IN_PROGRESS, CheckinStatus.FAILED, CheckinFailReason.MIDNIGHT);
        log.info("자정 체크인 정리: {}건", failed);
        return failed;
    }

    private CheckinSession findOwnedInProgress(Long userId, Long sessionId) {
        CheckinSession session = checkinSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (session.getStatus() != CheckinStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return session;
    }

    @Transactional
    public CompleteCheckinResponse complete(Long userId, Long sessionId, CompleteCheckinRequest request) {
        CheckinSession session = findOwnedInProgress(userId, sessionId);
        Instant now = clock.instant();

        if (!Kst.dateOf(session.getStartedAt()).equals(Kst.dateOf(now))) {
            throw new BusinessException(ErrorCode.CHECKIN_EXPIRED_MIDNIGHT);
        }

        // 세션의 parkId는 FK라 공원이 없을 수 없음. 없다면 데이터 이상이므로 500으로 드러나게 함
        Park park = parkRepository.findById(session.getParkId())
                .orElseThrow(() -> new IllegalStateException("세션의 공원이 없음 sessionId = " + session.getId()));

        if (request.lat() != null && request.lng() != null) {
            double distance = GeoDistance.meters(
                    request.lat(), request.lng(), park.getLatitude(), park.getLongitude());
            if (distance > park.getRadiusM()) {
                throw new BusinessException(ErrorCode.CHECKIN_OUT_OF_RANGE);
            }
        }

        long elapsedSeconds = Duration.between(session.getStartedAt(), now).getSeconds();
        long pausedSeconds = request.pausedSeconds();
        if (pausedSeconds > elapsedSeconds) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (elapsedSeconds - pausedSeconds < checkinProperties.requiredSeconds()) {
            throw new BusinessException(ErrorCode.CHECKIN_TOO_EARLY);
        }

        LocalDate today = Kst.dateOf(now);

        // 이번 배지를 받기 전의 순위 (rankChange 계산용)
        WeeklyStanding before = rankingService.getStanding(userId, park.getId(), today);

        Badge badge = Badge.earn(userId, park.getId(), today, now);
        try {
            // 커밋을 기다리지 않고 INSERT를 지금 보내서, 동시 요청이 만든 UNIQUE 위반을 이 안에서 잡도록 함
            badgeRepository.saveAndFlush(badge);
        } catch (DataIntegrityViolationException e) {
            String reason = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
            if (reason != null && reason.contains(BADGE_UNIQUE_CONSTRAINT)) {
                throw new BusinessException(ErrorCode.BADGE_ALREADY_EARNED_TODAY);
            }
            throw e;
        }
        session.complete(now);

        // 방금 저장한 배지가 포함된 순위
        WeeklyStanding after = rankingService.getStanding(userId, park.getId(), today);

        // 방금 저장한 배지가 포함된 개수로 새 업적 지급
        List<CompleteCheckinResponse.AchievementInfo> newAchievements =
                achievementService.grantNewAchievements(userId, now).stream()
                        .map(a -> new CompleteCheckinResponse.AchievementInfo(a.getCode(), a.getName()))
                        .toList();

        return new CompleteCheckinResponse(
                new CompleteCheckinResponse.BadgeInfo(
                        park.getId(), park.getName(), park.getParkType(), now.atZone(Kst.ZONE).toOffsetDateTime()),
                newAchievements,
                toWeeklyRankingInfo(before, after),
                true);
    }

    private CompleteCheckinResponse.WeeklyRankingInfo toWeeklyRankingInfo(WeeklyStanding before, WeeklyStanding after) {
        // 방금 배지를 받았으므로 after.me()는 항상 있음
        MyRankingResponse me = after.me();

        // 이번 주 첫 배지면 비교할 이전 순위가 없음
        int rankChange = before.me() == null ? 0 : before.me().rank() - me.rank();

        // 동점이면 먼저 도달한 사람이 위라서 3위와 같은 개수로는 넘을 수 없음
        int badgesToPodium = me.rank() <= RankingService.PODIUM_SIZE
                ? 0
                : after.thirdBadgeCount() - me.badgeCount() + 1;

        return new CompleteCheckinResponse.WeeklyRankingInfo(me.rank(), me.badgeCount(), rankChange, badgesToPodium);
    }
}
