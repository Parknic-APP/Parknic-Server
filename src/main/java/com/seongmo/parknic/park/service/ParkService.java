package com.seongmo.parknic.park.service;

import com.seongmo.parknic.common.error.BusinessException;
import com.seongmo.parknic.common.error.ErrorCode;
import com.seongmo.parknic.common.time.Kst;
import com.seongmo.parknic.park.dto.ParkDetailResponse;
import com.seongmo.parknic.park.dto.ParkListResponse;
import com.seongmo.parknic.park.dto.ParkMyBadge;
import com.seongmo.parknic.park.dto.ParkReviewStats;
import com.seongmo.parknic.park.dto.TopRatedParkListResponse;
import com.seongmo.parknic.park.entity.Park;
import com.seongmo.parknic.park.repository.ParkQueryRepository;
import com.seongmo.parknic.park.repository.ParkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class ParkService {

    private static final int MIN_RADIUS_M = 100;
    private static final int MAX_RADIUS_M = 5000;

    private final Clock clock;
    private final ParkRepository parkRepository;
    private final ParkQueryRepository parkQueryRepository;

    @Transactional(readOnly = true)
    public ParkDetailResponse getPark(Long userId, Long parkId) {
        Park park = parkRepository.findById(parkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        ParkMyBadge myBadge = parkQueryRepository.findMyBadge(userId, parkId, Kst.today(clock));
        ParkReviewStats stats = parkQueryRepository.findReviewStats(parkId);
        return ParkDetailResponse.of(park, myBadge, stats);
    }

    @Transactional(readOnly = true)
    public ParkListResponse getNearbyParks(Long userId, double lat, double lng, int radius) {
        int radiusM = Math.max(MIN_RADIUS_M, Math.min(MAX_RADIUS_M, radius));
        return new ParkListResponse(parkQueryRepository.findNearby(userId, Kst.today(clock), lat, lng, radiusM));
    }

    @Transactional(readOnly = true)
    public TopRatedParkListResponse getTopRatedParks(Long userId, double lat, double lng, int radius) {
        int radiusM = Math.max(MIN_RADIUS_M, Math.min(MAX_RADIUS_M, radius));
        return new TopRatedParkListResponse(
                parkQueryRepository.findTopRated(userId, Kst.today(clock), lat, lng, radiusM));
    }
}
