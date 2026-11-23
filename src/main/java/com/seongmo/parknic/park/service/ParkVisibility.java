package com.seongmo.parknic.park.service;

import com.seongmo.parknic.park.config.ReviewModeProperties;
import com.seongmo.parknic.park.entity.Park;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 심사용 공원(review_only)을 사용자에게 보여도 되는지 판단한다. REVIEW_MODE가 꺼져 있으면 심사용 공원은 없는 공원처럼 취급
@Slf4j
@Component
@RequiredArgsConstructor
public class ParkVisibility {

    private final ReviewModeProperties properties;

    @PostConstruct
    void warnIfReviewMode() {
        if (properties.mode()) {
            log.warn("REVIEW_MODE가 켜져 있습니다. 심사용 공원이 모든 사용자에게 보입니다. 심사가 끝나면 끄고 재시작하세요");
        }
    }

    public boolean isReviewMode() {
        return properties.mode();
    }

    public boolean isVisible(Park park) {
        return !park.isReviewOnly() || properties.mode();
    }
}
