package com.seongmo.parknic.park.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 앱 심사 기간에만 true. 심사용 공원(review_only)을 보이게 한다
@ConfigurationProperties(prefix = "parknic.review")
public record ReviewModeProperties(@DefaultValue("false") boolean mode) {
}
