package com.seongmo.parknic.park.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ReviewModeProperties.class)
public class ParkConfig {
}
