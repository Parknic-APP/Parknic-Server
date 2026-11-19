package com.seongmo.parknic.checkin.service;

import com.seongmo.parknic.checkin.dto.CheckinSessionResponse;
import com.seongmo.parknic.checkin.dto.StartCheckinRequest;
import com.seongmo.parknic.checkin.entity.CheckinSession;
import com.seongmo.parknic.checkin.entity.CheckinStatus;
import com.seongmo.parknic.checkin.repository.CheckinSessionRepository;
import com.seongmo.parknic.common.error.BusinessException;
import com.seongmo.parknic.common.error.ErrorCode;
import com.seongmo.parknic.common.geo.GeoDistance;
import com.seongmo.parknic.park.entity.Park;
import com.seongmo.parknic.park.repository.ParkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class CheckinService {

    private static final String IN_PROGRESS_UNIQUE_INDEX = "uq_checkin_in_progress";

    private final CheckinSessionRepository checkinSessionRepository;
    private final ParkRepository parkRepository;
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
        if (checkinSessionRepository.existsByUserIdAndStatus(userId, CheckinStatus.IN_PROGRESS)) {
            throw new BusinessException(ErrorCode.CHECKIN_ALREADY_IN_PROGRESS);
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
        // 세션의 parkId는 FK라 공원이 없을 수 있다. 없다면 데이터 이상이르모 500으로 드러나게 함
        Park park = parkRepository.findById(session.getParkId())
                .orElseThrow(() -> new IllegalStateException("세션의 공원이 없음 sessionId = " + session.getId()));
        return CheckinSessionResponse.of(session, park);
    }

    @Transactional
    public void cancel(Long userId, Long sessionId) {
        CheckinSession session = checkinSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (session.getStatus() != CheckinStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        session.cancel(clock.instant());
    }
}
