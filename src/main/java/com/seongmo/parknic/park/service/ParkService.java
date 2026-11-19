package com.seongmo.parknic.park.service;

import com.seongmo.parknic.common.error.BusinessException;
import com.seongmo.parknic.common.error.ErrorCode;
import com.seongmo.parknic.park.dto.ParkDetailResponse;
import com.seongmo.parknic.park.entity.Park;
import com.seongmo.parknic.park.repository.ParkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParkService {

    private final ParkRepository parkRepository;

    @Transactional(readOnly = true)
    public ParkDetailResponse getPark(Long parkId) {
        Park park = parkRepository.findById(parkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        return ParkDetailResponse.from(park);
    }
}
