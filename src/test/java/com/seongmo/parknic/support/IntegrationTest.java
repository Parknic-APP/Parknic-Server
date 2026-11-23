package com.seongmo.parknic.support;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import com.seongmo.parknic.common.security.AccessTokenService;
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
        // 개발 PC의 application-local.yaml(체크인 시간을 줄여 둠)에 테스트가 영향받지 않도록 실제 규칙(20분)으로 고정
        registry.add("parknic.checkin.required-seconds", () -> "1200");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected AccessTokenService accessTokenService;

    @BeforeEach
    void resetState() {
        // 시드(characters, achievements)는 남기고 사용자·공원 계열만 비운다
        jdbc.sql("TRUNCATE TABLE users, parks RESTART IDENTITY CASCADE").update();
        clock.set(START);
    }

    // ---- 테스트 데이터 도우미: 로그인 흐름을 거치지 않고 필요한 행을 바로 만든다 ----

    /** 프로필까지 끝난(ACTIVE) 사용자를 만들고 id를 돌려준다. */
    protected long createActiveUser(String nickname) {
        return jdbc.sql("""
                        INSERT INTO users (provider, provider_id, nickname, character_id, status, created_at)
                        VALUES ('TEST', :providerId, :nickname, 1, 'ACTIVE', now())
                        RETURNING id
                        """)
                .param("providerId", "test-" + nickname)
                .param("nickname", nickname)
                .query(Long.class)
                .single();
    }

    /** 그 사용자의 액세스 토큰(Authorization 헤더 값)을 돌려준다. */
    protected String bearer(long userId) {
        return "Bearer " + accessTokenService.issue(userId);
    }

    protected long createPark(String name, double latitude, double longitude, int radiusM) {
        return jdbc.sql("""
                        INSERT INTO parks (manage_no, name, park_type, address, latitude, longitude, area_m2, radius_m, review_only)
                        VALUES (:manageNo, :name, 'NEIGHBORHOOD', '', :latitude, :longitude, 0, :radiusM, FALSE)
                        RETURNING id
                        """)
                .param("manageNo", "test-" + name)
                .param("name", name)
                .param("latitude", latitude)
                .param("longitude", longitude)
                .param("radiusM", radiusM)
                .query(Long.class)
                .single();
    }

    protected void createBadge(long userId, long parkId, LocalDate earnedDate, Instant earnedAt) {
        jdbc.sql("""
                        INSERT INTO badges (user_id, park_id, earned_date, earned_at)
                        VALUES (:userId, :parkId, :earnedDate, :earnedAt)
                        """)
                .param("userId", userId)
                .param("parkId", parkId)
                .param("earnedDate", earnedDate)
                .param("earnedAt", OffsetDateTime.ofInstant(earnedAt, ZoneOffset.UTC))
                .update();
    }

    /** 리뷰를 직접 넣고 id를 돌려준다. 배지 보유 같은 작성 규칙은 거치지 않는다. */
    protected long createReview(long userId, long parkId, int rating, boolean hidden) {
        return jdbc.sql("""
                        INSERT INTO reviews (user_id, park_id, rating, content, hidden, created_at, updated_at)
                        VALUES (:userId, :parkId, :rating, '테스트 리뷰입니다. 열 글자가 넘습니다', :hidden, now(), now())
                        RETURNING id
                        """)
                .param("userId", userId)
                .param("parkId", parkId)
                .param("rating", rating)
                .param("hidden", hidden)
                .query(Long.class)
                .single();
    }

    /** blocker가 blocked를 차단한 상태로 만든다. */
    protected void createBlock(long blockerId, long blockedId) {
        jdbc.sql("INSERT INTO user_blocks (blocker_id, blocked_id, created_at) VALUES (:blocker, :blocked, now())")
                .param("blocker", blockerId)
                .param("blocked", blockedId)
                .update();
    }

    protected void createReport(long reviewId, long reporterId) {
        jdbc.sql("""
                        INSERT INTO review_reports (review_id, reporter_id, reason, status, created_at)
                        VALUES (:reviewId, :reporterId, 'SPAM', 'PENDING', now())
                        """)
                .param("reviewId", reviewId)
                .param("reporterId", reporterId)
                .update();
    }
}
