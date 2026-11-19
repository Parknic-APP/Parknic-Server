package com.seongmo.parknic.park.service;

import com.seongmo.parknic.common.error.BusinessException;
import com.seongmo.parknic.common.error.ErrorCode;
import com.seongmo.parknic.park.dto.ParkDetailResponse;
import com.seongmo.parknic.park.dto.ParkListResponse;
import com.seongmo.parknic.park.entity.Park;
import com.seongmo.parknic.park.repository.ParkQueryRepository;
import com.seongmo.parknic.park.repository.ParkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParkService {

    private static final int MIN_RADIUS_M = 100;
    private static final int MAX_RADIUS_M = 5000;

    private final ParkRepository parkRepository;
    private final ParkQueryRepository parkQueryRepository;

    @Transactional(readOnly = true)
    public ParkDetailResponse getPark(Long parkId) {
        Park park = parkRepository.findById(parkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        return ParkDetailResponse.from(park);
    }

    @Transactional(readOnly = true)
    public ParkListResponse getNearbyParks(double lat, double lng, int radius) {
        int radiusM = Math.max(MIN_RADIUS_M, Math.min(MAX_RADIUS_M, radius));
        return new ParkListResponse(parkQueryRepository.findNearby(lat, lng, radiusM));
    }
}
