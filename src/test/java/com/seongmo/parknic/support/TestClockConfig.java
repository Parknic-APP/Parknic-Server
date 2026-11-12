package com.seongmo.parknic.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 앱의 Clock 대신 MutableClock을 주입한다. 이름이 겹치면 빈 덮어쓰기 오류가 나므로 메서드 이름을 다르게 둔다. */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(IntegrationTest.START);
    }
}
