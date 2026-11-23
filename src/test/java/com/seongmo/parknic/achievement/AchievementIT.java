package com.seongmo.parknic.achievement;

import com.jayway.jsonpath.JsonPath;
import com.seongmo.parknic.common.time.Kst;
import com.seongmo.parknic.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 업적 지급(PARKS, STREAK), 도감, 통계. 기준 시각은 2026-11-06(금) 12:00 KST. */
class AchievementIT extends IntegrationTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    long user;

    @BeforeEach
    void setUp() {
        user = createActiveUser("walker");
    }

    // ---------------- 업적 지급 ----------------

    @Test
    void 서로_다른_공원_10곳째_배지에서_PARKS_10이_지급되고_다음_배지에는_다시_나오지_않는다() throws Exception {
        for (int i = 1; i <= 9; i++) {
            // 하루씩 건너뛰어서 연속 일수 업적(STREAK_7)과 섞이지 않게 한다
            badge(user, createPark("공원" + i, 37.0 + i * 0.05, 127.0, 200), TODAY.minusDays(2L * i));
        }
        long tenth = createPark("공원10", 38.0, 127.0, 200);
        long eleventh = createPark("공원11", 38.1, 127.0, 200);

        // 업적 행이 없던 사용자라서 첫 완료에서 FIRST_BADGE도 함께 소급 지급된다 (임계값 오름차순)
        assertThat(codes(checkInAndComplete(user, tenth, 38.0, 127.0)))
                .containsExactly("FIRST_BADGE", "PARKS_10");

        assertThat(codes(checkInAndComplete(user, eleventh, 38.1, 127.0))).isEmpty();
        assertThat(count("SELECT count(*) FROM user_achievements WHERE achievement_cd = 'PARKS_10'")).isEqualTo(1);
    }

    @Test
    void 같은_공원에서_여러_날_받아도_방문한_공원은_1곳이라_PARKS_10은_지급되지_않는다() throws Exception {
        long park = createPark("단골공원", 37.5, 127.0, 200);
        for (int i = 1; i <= 9; i++) {
            badge(user, park, TODAY.minusDays(2L * i));      // 하루씩 건너뜀: 연속 업적과 섞이지 않게
        }

        assertThat(codes(checkInAndComplete(user, park, 37.5, 127.0))).containsExactly("FIRST_BADGE");
        assertThat(count("SELECT count(*) FROM badges")).isEqualTo(10);
    }

    @Test
    void 어제까지_6일_연속이면_오늘_완료에서_STREAK_7이_지급된다() throws Exception {
        long park = createPark("연속공원", 37.5, 127.0, 200);
        for (int i = 1; i <= 6; i++) {
            badge(user, park, TODAY.minusDays(i));
        }

        assertThat(codes(checkInAndComplete(user, park, 37.5, 127.0))).contains("STREAK_7");
    }

    @Test
    void 연속이_5일이면_STREAK_7이_지급되지_않는다() throws Exception {
        long park = createPark("연속공원", 37.5, 127.0, 200);
        for (int i = 1; i <= 5; i++) {
            badge(user, park, TODAY.minusDays(i));
        }

        assertThat(codes(checkInAndComplete(user, park, 37.5, 127.0))).doesNotContain("STREAK_7");
    }

    @Test
    void 이미_받은_업적은_다시_지급되지_않는다() throws Exception {
        long park = createPark("연속공원", 37.5, 127.0, 200);
        for (int i = 1; i <= 6; i++) {
            badge(user, park, TODAY.minusDays(i));
        }
        jdbc.sql("INSERT INTO user_achievements (user_id, achievement_cd, achieved_at) VALUES (:u, 'STREAK_7', now()), (:u, 'FIRST_BADGE', now())")
                .param("u", user).update();

        assertThat(codes(checkInAndComplete(user, park, 37.5, 127.0))).isEmpty();
        assertThat(count("SELECT count(*) FROM user_achievements")).isEqualTo(2);
    }

    @Test
    void 완료가_실패하면_업적도_생기지_않는다() throws Exception {
        long park = createPark("연속공원", 37.5, 127.0, 200);
        badge(user, park, TODAY.minusDays(1));

        // 시작은 되지만 20분 전에 완료하면 400
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/check-ins")
                        .header("Authorization", bearer(user))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"parkId\":%d,\"lat\":37.5,\"lng\":127.0}".formatted(park)))
                .andExpect(status().isOk());
        long sessionId = jdbc.sql("SELECT id FROM checkin_sessions").query(Long.class).single();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/check-ins/{id}/complete", sessionId)
                        .header("Authorization", bearer(user))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"pausedSeconds\":0}"))
                .andExpect(status().isBadRequest());

        assertThat(count("SELECT count(*) FROM user_achievements")).isZero();
    }

    // ---------------- 도감 ----------------

    @Test
    void 도감의_유형별_개수는_서로_다른_공원_수이고_달성_시각은_KST다() throws Exception {
        long neighborhoodA = createPark("근린A", 37.5, 127.0, 200);
        long neighborhoodB = createPark("근린B", 37.6, 127.0, 200);
        long children = createPark("어린이", 37.7, 127.0, 200);
        jdbc.sql("UPDATE parks SET park_type = 'CHILDREN' WHERE id = :id").param("id", children).update();
        badge(user, neighborhoodA, TODAY.minusDays(1));
        badge(user, neighborhoodA, TODAY.minusDays(2));      // 같은 공원 두 번째 배지
        badge(user, neighborhoodB, TODAY.minusDays(3));
        badge(user, children, TODAY.minusDays(4));
        jdbc.sql("INSERT INTO user_achievements (user_id, achievement_cd, achieved_at) VALUES (:u, 'FIRST_BADGE', '2026-11-01T01:00:00Z')")
                .param("u", user).update();

        mockMvc.perform(get("/api/users/me/collection").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBadges").value(4))
                .andExpect(jsonPath("$.visitedParks").value(3))
                .andExpect(jsonPath("$.types.length()").value(2))
                .andExpect(jsonPath("$.types[0].parkType").value("NEIGHBORHOOD"))
                .andExpect(jsonPath("$.types[0].count").value(2))
                .andExpect(jsonPath("$.types[1].parkType").value("CHILDREN"))
                .andExpect(jsonPath("$.types[1].count").value(1))
                .andExpect(jsonPath("$.achievements.length()").value(6))
                .andExpect(jsonPath("$.achievements[0].code").value("FIRST_BADGE"))
                .andExpect(jsonPath("$.achievements[0].achievedAt").value("2026-11-01T10:00:00+09:00"))
                .andExpect(jsonPath("$.achievements[0].label").value("첫"))
                .andExpect(jsonPath("$.achievements[2].code").value("PARKS_10"))
                .andExpect(jsonPath("$.achievements[2].achievedAt").value(nullValue(), Object.class))
                .andExpect(jsonPath("$.achievements[2].progress").value(3))
                .andExpect(jsonPath("$.achievements[2].target").value(10));
    }

    @Test
    void 도감의_진행도는_목표를_넘지_않게_잘린다() throws Exception {
        for (int i = 1; i <= 11; i++) {
            badge(user, createPark("공원" + i, 37.0 + i * 0.05, 127.0, 200), TODAY.minusDays(i));
        }

        mockMvc.perform(get("/api/users/me/collection").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.achievements[2].code").value("PARKS_10"))
                .andExpect(jsonPath("$.achievements[2].progress").value(10))
                .andExpect(jsonPath("$.achievements[3].code").value("PARKS_20"))
                .andExpect(jsonPath("$.achievements[3].progress").value(11))
                .andExpect(jsonPath("$.achievements[3].target").value(20));
    }

    // ---------------- 통계 ----------------

    @Test
    void 통계의_연속_일수는_어제까지_이어졌으면_인정하고_최고_기록은_과거의_구간도_본다() throws Exception {
        long park = createPark("통계공원", 37.5, 127.0, 200);
        // 10-20, 10-21 두 날(과거), 11-03, 11-04, 11-05 세 날(어제까지), 오늘은 아직 없음
        for (String day : List.of("2026-10-20", "2026-10-21", "2026-11-03", "2026-11-04", "2026-11-05")) {
            badge(user, park, LocalDate.parse(day));
        }

        mockMvc.perform(get("/api/users/me/stats").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBadges").value(5))
                .andExpect(jsonPath("$.visitedParks").value(1))
                .andExpect(jsonPath("$.currentStreak").value(3))
                .andExpect(jsonPath("$.bestStreak").value(3));
    }

    @Test
    void 이번_주_시상대에_오른_공원_수만_통계에_들어간다() throws Exception {
        long topPark = createPark("1위공원", 37.5, 127.0, 200);
        long fourthPark = createPark("4위공원", 37.6, 127.0, 200);
        long lastWeekPark = createPark("지난주공원", 37.7, 127.0, 200);
        badge(user, topPark, LocalDate.parse("2026-11-03"));
        badge(user, fourthPark, LocalDate.parse("2026-11-03"));
        badge(user, lastWeekPark, LocalDate.parse("2026-10-30"));      // 지난 주
        // 4위공원에는 이 사용자보다 배지가 많은 사용자가 3명 있다
        for (int i = 1; i <= 3; i++) {
            long rival = createActiveUser("rival" + i);
            badge(rival, fourthPark, LocalDate.parse("2026-11-02"));
            badge(rival, fourthPark, LocalDate.parse("2026-11-04"));
        }

        mockMvc.perform(get("/api/users/me/stats").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podiumParks").value(1));
    }

    @Test
    void 배지가_없으면_통계가_모두_0이다() throws Exception {
        mockMvc.perform(get("/api/users/me/stats").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.totalBadges").value(0))
                .andExpect(jsonPath("$.visitedParks").value(0))
                .andExpect(jsonPath("$.currentStreak").value(0))
                .andExpect(jsonPath("$.bestStreak").value(0))
                .andExpect(jsonPath("$.podiumParks").value(0));
    }

    // ---- 도우미 ----

    private void badge(long userId, long parkId, LocalDate day) {
        Instant at = LocalDateTime.of(day, java.time.LocalTime.NOON).atZone(Kst.ZONE).toInstant();
        createBadge(userId, parkId, day, at);
    }

    private List<String> codes(ResultActions complete) throws Exception {
        String body = complete.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new ArrayList<>(JsonPath.<List<String>>read(body, "$.newAchievements[*].code"));
    }

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }
}
