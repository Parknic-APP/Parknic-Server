package com.seongmo.parknic.checkin;

import com.jayway.jsonpath.JsonPath;
import com.seongmo.parknic.checkin.service.CheckinService;
import com.seongmo.parknic.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 체크인 시작, 완료, 취소/실패, 자정 정리의 규칙을 시간 경계까지 확인한다. 기준 시각은 2026-11-06 12:00 KST. */
class CheckinFlowIT extends IntegrationTest {

    private static final double LAT = 37.5;
    private static final double LNG = 127.0;
    private static final int RADIUS_M = 200;

    @Autowired
    CheckinService checkinService;

    long userId;
    long parkId;

    @BeforeEach
    void setUp() {
        userId = createActiveUser("walker");
        parkId = createPark("테스트공원", LAT, LNG, RADIUS_M);
    }

    @Test
    void 반경_안에서_시작하면_진행_중_세션이_만들어진다() throws Exception {
        start(userId, parkId, LAT, LNG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parkId").value(parkId))
                .andExpect(jsonPath("$.radiusM").value(RADIUS_M));

        assertThat(countSessions("IN_PROGRESS")).isEqualTo(1);
    }

    @Test
    void 반경_밖에서_시작하면_400이고_세션이_생기지_않는다() throws Exception {
        // 위도 0.01도는 약 1.1km
        start(userId, parkId, LAT + 0.01, LNG)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHECKIN_OUT_OF_RANGE"));

        assertThat(countSessions(null)).isZero();
    }

    @Test
    void 진행_중인데_다시_시작하면_409다() throws Exception {
        start(userId, parkId, LAT, LNG).andExpect(status().isOk());

        start(userId, parkId, LAT, LNG)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECKIN_ALREADY_IN_PROGRESS"));

        assertThat(countSessions(null)).isEqualTo(1);
    }

    @Test
    void 없는_공원이면_404다() throws Exception {
        start(userId, 999_999L, LAT, LNG)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void 체크인은_20분이_되기_1초_전에는_너무_이르고_20분이_되면_배지를_받는다() throws Exception {
        long sessionId = startOk();

        clock.advance(Duration.ofSeconds(1199));
        complete(userId, sessionId, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHECKIN_TOO_EARLY"));
        assertThat(countSessions("IN_PROGRESS")).isEqualTo(1);
        assertThat(countBadges()).isZero();

        clock.advance(Duration.ofSeconds(1));
        complete(userId, sessionId, 0).andExpect(status().isOk());
        assertThat(countSessions("COMPLETED")).isEqualTo(1);
        assertThat(countBadges()).isEqualTo(1);
    }

    @Test
    void 반경_밖에서_보낸_시간은_20분에서_빼고_판정한다() throws Exception {
        long sessionId = startOk();

        // 25분에서 5분을 빼면 정확히 20분이어야 통과한다. 1초 모자라면 거절
        clock.advance(Duration.ofSeconds(1499));
        complete(userId, sessionId, 300)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHECKIN_TOO_EARLY"));

        clock.advance(Duration.ofSeconds(1));
        complete(userId, sessionId, 300).andExpect(status().isOk());
    }

    @Test
    void 멈춘_시간이_경과_시간보다_길면_400이다() throws Exception {
        long sessionId = startOk();
        clock.advance(Duration.ofSeconds(60));

        complete(userId, sessionId, 120)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 자정이_지나서_완료하면_CHECKIN_EXPIRED_MIDNIGHT다() throws Exception {
        long sessionId = startOk();

        // 2026-11-07 00:00 KST
        clock.set(Instant.parse("2026-11-06T15:00:00Z"));

        complete(userId, sessionId, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHECKIN_EXPIRED_MIDNIGHT"));
        assertThat(countBadges()).isZero();
    }

    @Test
    void 같은_공원에서_같은_날_다시_시작하면_BADGE_ALREADY_EARNED_TODAY다() throws Exception {
        long sessionId = startOk();
        clock.advance(Duration.ofMinutes(20));
        complete(userId, sessionId, 0).andExpect(status().isOk());

        start(userId, parkId, LAT, LNG)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BADGE_ALREADY_EARNED_TODAY"));

        assertThat(countBadges()).isEqualTo(1);
    }

    @Test
    void 첫_배지에는_FIRST_BADGE가_지급되고_다음_공원의_배지에는_새_업적이_없다() throws Exception {
        long secondPark = createPark("두번째공원", LAT + 0.02, LNG, RADIUS_M);

        long first = startOk();
        clock.advance(Duration.ofMinutes(20));
        complete(userId, first, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newAchievements.length()").value(1))
                .andExpect(jsonPath("$.newAchievements[0].code").value("FIRST_BADGE"));

        long second = JsonPath.<Number>read(
                start(userId, secondPark, LAT + 0.02, LNG).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(), "$.id").longValue();
        clock.advance(Duration.ofMinutes(20));
        complete(userId, second, 0, LAT + 0.02, LNG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newAchievements.length()").value(0));

        assertThat(countBadges()).isEqualTo(2);
    }

    @Test
    void 첫_완료의_주간_랭킹은_1위_배지_1개_변동_없음이다() throws Exception {
        long sessionId = startOk();
        clock.advance(Duration.ofMinutes(20));

        complete(userId, sessionId, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyRanking.rank").value(1))
                .andExpect(jsonPath("$.weeklyRanking.badgeCount").value(1))
                .andExpect(jsonPath("$.weeklyRanking.rankChange").value(0))
                .andExpect(jsonPath("$.weeklyRanking.badgesToPodium").value(0))
                .andExpect(jsonPath("$.canWriteReview").value(true));
    }

    @Test
    void 취소하거나_실패한_뒤에는_다시_시작할_수_있다() throws Exception {
        long first = startOk();
        mockMvc.perform(post("/api/check-ins/{id}/cancel", first).header("Authorization", bearer(userId)))
                .andExpect(status().isOk());
        assertThat(countSessions("CANCELED")).isEqualTo(1);

        long second = startOk();
        mockMvc.perform(post("/api/check-ins/{id}/fail", second)
                        .header("Authorization", bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OUT_OF_RANGE\"}"))
                .andExpect(status().isOk());
        assertThat(countSessions("FAILED")).isEqualTo(1);

        start(userId, parkId, LAT, LNG).andExpect(status().isOk());
        assertThat(countSessions("IN_PROGRESS")).isEqualTo(1);
    }

    @Test
    void 남의_세션은_완료할_수_없다() throws Exception {
        long sessionId = startOk();
        clock.advance(Duration.ofMinutes(20));
        long stranger = createActiveUser("stranger");

        complete(stranger, sessionId, 0)
                .andExpect(status().isForbidden());
        assertThat(countBadges()).isZero();
    }

    @Test
    void 자정_정리는_어제_시작한_진행_중_세션만_실패로_바꾸고_여러_번_실행해도_결과가_같다() throws Exception {
        long other = createActiveUser("night-walker");
        long yesterdaySession = startOk();

        // 2026-11-07 00:30 KST: 어제 시작한 세션은 만료, 지금 시작하는 세션은 오늘 것
        clock.set(Instant.parse("2026-11-06T15:30:00Z"));
        start(other, parkId, LAT, LNG).andExpect(status().isOk());

        assertThat(checkinService.failExpiredSessions()).isEqualTo(1);

        assertThat(sessionStatus(yesterdaySession)).isEqualTo("FAILED");
        assertThat(jdbc.sql("SELECT fail_reason FROM checkin_sessions WHERE id = :id")
                .param("id", yesterdaySession).query(String.class).single()).isEqualTo("MIDNIGHT");
        assertThat(jdbc.sql("SELECT ended_at FROM checkin_sessions WHERE id = :id")
                .param("id", yesterdaySession)
                .query((rs, i) -> rs.getObject("ended_at", OffsetDateTime.class).toInstant())
                .single())
                .isEqualTo(Instant.parse("2026-11-06T15:00:00Z"));
        assertThat(countSessions("IN_PROGRESS")).isEqualTo(1);

        assertThat(checkinService.failExpiredSessions()).isZero();
    }

    // ---- 도우미 ----

    // 토큰은 요청할 때마다 현재 시각으로 새로 만든다 (시계를 크게 옮기면 미리 만든 토큰은 만료되기 때문)
    private ResultActions start(long user, long park, double lat, double lng) throws Exception {
        return mockMvc.perform(post("/api/check-ins")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parkId\":%d,\"lat\":%s,\"lng\":%s}".formatted(park, lat, lng)));
    }

    private long startOk() throws Exception {
        String body = start(userId, parkId, LAT, LNG).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.id").longValue();
    }

    private ResultActions complete(long user, long sessionId, int pausedSeconds) throws Exception {
        return complete(user, sessionId, pausedSeconds, LAT, LNG);
    }

    private ResultActions complete(long user, long sessionId, int pausedSeconds, double lat, double lng) throws Exception {
        return mockMvc.perform(post("/api/check-ins/{id}/complete", sessionId)
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lat\":%s,\"lng\":%s,\"pausedSeconds\":%d}".formatted(lat, lng, pausedSeconds)));
    }

    private int countSessions(String status) {
        if (status == null) {
            return jdbc.sql("SELECT count(*) FROM checkin_sessions").query(Integer.class).single();
        }
        return jdbc.sql("SELECT count(*) FROM checkin_sessions WHERE status = :status")
                .param("status", status).query(Integer.class).single();
    }

    private int countBadges() {
        return jdbc.sql("SELECT count(*) FROM badges").query(Integer.class).single();
    }

    private String sessionStatus(long sessionId) {
        return jdbc.sql("SELECT status FROM checkin_sessions WHERE id = :id")
                .param("id", sessionId).query(String.class).single();
    }
}
