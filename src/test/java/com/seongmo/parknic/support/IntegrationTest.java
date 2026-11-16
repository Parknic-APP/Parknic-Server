package com.seongmo.parknic.support;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 통합 테스트 부모 클래스. 진짜 PostgreSQL(Testcontainers)에서 Flyway 마이그레이션까지 적용한 뒤 돈다.
 * 컨테이너는 JVM당 한 번만 띄워 모든 테스트가 공유한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
public abstract class IntegrationTest {

    /** 테스트의 기준 시각: 2026-11-06 12:00 KST */
    public static final Instant START = Instant.parse("2026-11-06T03:00:00Z");

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("parknic.jwt.secret", () -> "test-secret-test-secret-test-secret-1234");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetState() {
        // 시드(characters, achievements)는 남기고 사용자·공원 계열만 비운다
        jdbc.sql("TRUNCATE TABLE users, parks RESTART IDENTITY CASCADE").update();
        clock.set(START);
    }
}
