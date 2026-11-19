package com.seongmo.parknic.checkin.service;

import com.seongmo.parknic.checkin.config.CheckinProperties;
import com.seongmo.parknic.checkin.dto.*;
import com.seongmo.parknic.checkin.entity.Badge;
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
import lombok.RequiredArgsConstructor;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckinService {

    private static final String IN_PROGRESS_UNIQUE_INDEX = "uq_checkin_in_progress";
    private static final String BADGE_UNIQUE_CONSTRAINT = "uq_badges_user_park_date";

    private final CheckinSessionRepository checkinSessionRepository;
    private final ParkRepository parkRepository;
    private final BadgeRepository badgeRepository;
    private final CheckinProperties checkinProperties;
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

        Badge badge = Badge.earn(userId, park.getId(), Kst.dateOf(now), now);
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

        // 업적과 주간 랭킹을 만들기 전까지는 고정값
        return new CompleteCheckinResponse(
                new CompleteCheckinResponse.BadgeInfo(
                        park.getId(), park.getName(), park.getParkType(), now.atZone(Kst.ZONE).toOffsetDateTime()),
                List.of(),
                new CompleteCheckinResponse.WeeklyRankingInfo(1, 1, 0, 0),
                true);
    }
}
