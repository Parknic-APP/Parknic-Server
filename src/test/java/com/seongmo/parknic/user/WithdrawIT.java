package com.seongmo.parknic.user;

import com.jayway.jsonpath.JsonPath;
import com.seongmo.parknic.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 회원 탈퇴는 사용자 행을 실제로 지우고, 연결된 데이터(외래키 CASCADE)와 남은 토큰을 모두 처리한다. */
@TestPropertySource(properties = "parknic.dev-login.enabled=true")
class WithdrawIT extends IntegrationTest {

    static final LocalDate DAY = LocalDate.of(2026, 11, 1);

    long parkId;
    long leaver;          // 탈퇴하는 사용자 (개발용 로그인으로 만들어 로그인 토큰도 갖는다)
    long stayer;          // 남는 사용자
    String leaverAccessToken;
    String leaverRefreshToken;
    long leaversReviewId;
    long stayersReviewId;

    @BeforeEach
    void setUp() throws Exception {
        parkId = createPark("탈퇴공원", 37.5, 127.0, 200);
        stayer = createActiveUser("stayer");

        String body = mockMvc.perform(post("/api/auth/login/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"dev:local:leaver\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        leaverAccessToken = JsonPath.read(body, "$.accessToken");
        leaverRefreshToken = JsonPath.read(body, "$.refreshToken");
        leaver = jdbc.sql("SELECT id FROM users WHERE provider = 'DEV' AND provider_id = 'leaver'")
                .query(Long.class).single();

        // 탈퇴자의 데이터: 배지, 리뷰, 체크인 세션, 업적, 상대를 차단함, 상대에게 차단당함, 상대의 리뷰를 신고함
        createBadge(leaver, parkId, DAY, DAY.atStartOfDay().toInstant(ZoneOffset.UTC));
        leaversReviewId = createReview(leaver, parkId, 5, false);
        jdbc.sql("INSERT INTO checkin_sessions (user_id, park_id, status, started_at, ended_at) "
                + "VALUES (:u, :p, 'COMPLETED', now(), now())").param("u", leaver).param("p", parkId).update();
        jdbc.sql("INSERT INTO user_achievements (user_id, achievement_cd, achieved_at) VALUES (:u, 'FIRST_BADGE', now())")
                .param("u", leaver).update();
        createBlock(leaver, stayer);
        createBlock(stayer, leaver);

        // 남는 사용자의 데이터
        createBadge(stayer, parkId, DAY, DAY.atStartOfDay().toInstant(ZoneOffset.UTC));
        stayersReviewId = createReview(stayer, parkId, 3, false);
        createReport(stayersReviewId, leaver);     // 탈퇴자가 상대의 리뷰를 신고
        createReport(leaversReviewId, stayer);     // 상대가 탈퇴자의 리뷰를 신고
    }

    @Test
    void 탈퇴하면_사용자와_연결된_데이터가_모두_사라진다() throws Exception {
        assertThat(rowsOf(leaver)).as("탈퇴 전에는 데이터가 있다").containsExactly(1, 1, 1, 1, 1, 1, 1, 2);

        withdraw(leaverAccessToken).andExpect(status().isOk());

        // users, refresh_tokens, badges, checkin_sessions, user_achievements, reviews, review_reports(신고자), user_blocks
        assertThat(rowsOf(leaver)).containsOnly(0);
    }

    @Test
    void 다른_사용자의_데이터는_그대로_남고_탈퇴자와_관련된_행만_사라진다() throws Exception {
        withdraw(leaverAccessToken).andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM users WHERE id = ?", stayer)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM badges WHERE user_id = ?", stayer)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM reviews WHERE id = ?", stayersReviewId)).isEqualTo(1);
        // 탈퇴자의 리뷰에 달렸던 신고, 탈퇴자가 한 신고, 탈퇴자와 관련된 차단은 모두 없어진다
        assertThat(count("SELECT count(*) FROM review_reports", null)).isZero();
        assertThat(count("SELECT count(*) FROM user_blocks", null)).isZero();
    }

    @Test
    void 공원의_평균과_리뷰_수는_탈퇴한_사용자의_리뷰를_뺀다() throws Exception {
        mockMvc.perform(get("/api/parks/{id}", parkId).header("Authorization", bearer(stayer)))
                .andExpect(jsonPath("$.reviewCount").value(2))
                .andExpect(jsonPath("$.averageRating").value(4.0));

        withdraw(leaverAccessToken).andExpect(status().isOk());

        mockMvc.perform(get("/api/parks/{id}", parkId).header("Authorization", bearer(stayer)))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.averageRating").value(3.0));
    }

    @Test
    void 탈퇴한_사용자의_남은_액세스_토큰은_모든_API에서_401이고_500이_아니다() throws Exception {
        withdraw(leaverAccessToken).andExpect(status().isOk());

        String auth = "Bearer " + leaverAccessToken;
        mockMvc.perform(get("/api/users/me").header("Authorization", auth))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/parks/{id}", parkId).header("Authorization", auth))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/check-ins").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkId\":%d,\"lat\":37.5,\"lng\":127.0}".formatted(parkId)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/parks/{id}/reviews", parkId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"content\":\"탈퇴한 뒤에 쓰려는 리뷰입니다. 열 글자 이상\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(count("SELECT count(*) FROM checkin_sessions", null)).isZero();
    }

    @Test
    void 탈퇴한_사용자의_refresh_토큰으로_재발급할_수_없고_탈퇴를_다시_할_수_없다() throws Exception {
        withdraw(leaverAccessToken).andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(leaverRefreshToken)))
                .andExpect(status().isUnauthorized());
        withdraw(leaverAccessToken).andExpect(status().isUnauthorized());
    }

    @Test
    void 같은_계정으로_다시_로그인하면_이전_데이터가_없는_새_사용자다() throws Exception {
        withdraw(leaverAccessToken).andExpect(status().isOk());

        String body = mockMvc.perform(post("/api/auth/login/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"dev:local:leaver\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andReturn().getResponse().getContentAsString();
        long newId = jdbc.sql("SELECT id FROM users WHERE provider = 'DEV' AND provider_id = 'leaver'")
                .query(Long.class).single();

        assertThat(newId).isNotEqualTo(leaver);
        mockMvc.perform(get("/api/users/me/stats")
                        .header("Authorization", "Bearer " + JsonPath.<String>read(body, "$.accessToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBadges").value(0))
                .andExpect(jsonPath("$.visitedParks").value(0));
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + JsonPath.<String>read(body, "$.accessToken")))
                .andExpect(jsonPath("$.nickname").value(nullValue(), Object.class));
    }

    // ---- 도우미 ----

    private ResultActions withdraw(String accessToken) throws Exception {
        return mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + accessToken));
    }

    /** 이 사용자와 관련된 행 수: users, refresh_tokens, badges, checkin_sessions, user_achievements, reviews, review_reports(신고자), user_blocks(양방향). */
    private java.util.List<Integer> rowsOf(long userId) {
        return jdbc.sql("""
                        SELECT (SELECT count(*) FROM users WHERE id = :u),
                               (SELECT count(*) FROM refresh_tokens WHERE user_id = :u),
                               (SELECT count(*) FROM badges WHERE user_id = :u),
                               (SELECT count(*) FROM checkin_sessions WHERE user_id = :u),
                               (SELECT count(*) FROM user_achievements WHERE user_id = :u),
                               (SELECT count(*) FROM reviews WHERE user_id = :u),
                               (SELECT count(*) FROM review_reports WHERE reporter_id = :u),
                               (SELECT count(*) FROM user_blocks WHERE blocker_id = :u OR blocked_id = :u)
                        """)
                .param("u", userId)
                .query((rs, i) -> java.util.List.of(
                        rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                        rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8)))
                .single();
    }

    private int count(String sql, Long param) {
        var spec = jdbc.sql(sql.replace("?", ":p"));
        if (param != null) {
            spec = spec.param("p", param);
        }
        return spec.query(Integer.class).single();
    }
}
