package com.seongmo.parknic.ranking;

import com.seongmo.parknic.common.time.Kst;
import com.seongmo.parknic.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 공원별 주간 랭킹(월~일, KST). 기준 시각 2026-11-06(금)이 속한 주는 11-02(월) ~ 11-08(일). */
class RankingIT extends IntegrationTest {

    long parkId;

    @BeforeEach
    void setUp() {
        parkId = createPark("랭킹공원", 37.5, 127.0, 200);
    }

    @Test
    void 배지가_많은_순이고_같으면_그_개수에_먼저_도달한_사람이_위다() throws Exception {
        long u1 = createActiveUser("u1");
        long u2 = createActiveUser("u2");
        long u3 = createActiveUser("u3");
        badge(u3, "2026-11-02T10:00");
        badge(u3, "2026-11-03T10:00");
        badge(u3, "2026-11-04T10:00");
        badge(u1, "2026-11-02T10:00");
        badge(u1, "2026-11-04T10:00");   // 두 번째 배지를 11-04에 받음
        badge(u2, "2026-11-02T10:00");
        badge(u2, "2026-11-05T10:00");   // 두 번째 배지를 11-05에 받음

        ranking(bearer(u1), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-11-02"))
                .andExpect(jsonPath("$.weekEnd").value("2026-11-08"))
                .andExpect(jsonPath("$.podium.length()").value(3))
                .andExpect(jsonPath("$.podium[0].nickname").value("u3"))
                .andExpect(jsonPath("$.podium[0].badgeCount").value(3))
                .andExpect(jsonPath("$.podium[1].nickname").value("u1"))
                .andExpect(jsonPath("$.podium[2].nickname").value("u2"))
                .andExpect(jsonPath("$.me.rank").value(2))
                .andExpect(jsonPath("$.me.badgeCount").value(2));
    }

    @Test
    void 일요일_23시_59분_배지는_그_주이고_월요일_0시_배지는_다음_주다() throws Exception {
        long u1 = createActiveUser("u1");
        long u2 = createActiveUser("u2");
        badge(u1, "2026-11-08T23:59:59");
        badge(u2, "2026-11-09T00:00:00");

        ranking(bearer(u1), null)
                .andExpect(jsonPath("$.podium.length()").value(1))
                .andExpect(jsonPath("$.podium[0].nickname").value("u1"));
        ranking(bearer(u1), "2026-11-08")
                .andExpect(jsonPath("$.weekStart").value("2026-11-02"))
                .andExpect(jsonPath("$.podium[0].nickname").value("u1"));
        ranking(bearer(u1), "2026-11-09")
                .andExpect(jsonPath("$.weekStart").value("2026-11-09"))
                .andExpect(jsonPath("$.weekEnd").value("2026-11-15"))
                .andExpect(jsonPath("$.podium.length()").value(1))
                .andExpect(jsonPath("$.podium[0].nickname").value("u2"))
                .andExpect(jsonPath("$.me").value(nullValue(), Object.class));
    }

    @Test
    void 탈퇴한_사용자는_랭킹에서_사라지고_아래_사람이_올라온다() throws Exception {
        long u1 = createActiveUser("u1");
        long u2 = createActiveUser("u2");
        long u3 = createActiveUser("u3");
        badge(u1, "2026-11-02T10:00");
        badge(u1, "2026-11-03T10:00");
        badge(u2, "2026-11-04T10:00");
        badge(u3, "2026-11-05T10:00");

        mockMvc.perform(delete("/api/users/me").header("Authorization", bearer(u1)))
                .andExpect(status().isOk());

        ranking(bearer(u2), null)
                .andExpect(jsonPath("$.podium.length()").value(2))
                .andExpect(jsonPath("$.podium[0].nickname").value("u2"))
                .andExpect(jsonPath("$.podium[0].rank").value(1))
                .andExpect(jsonPath("$.podium[1].nickname").value("u3"))
                .andExpect(jsonPath("$.me.rank").value(1));
    }

    @Test
    void 내가_21등이어도_목록은_20등까지이고_me에는_내_순위가_나온다() throws Exception {
        long viewer = 0;
        for (int i = 1; i <= 21; i++) {
            long user = createActiveUser("u" + i);
            // 번호가 클수록 늦게 받아서 순위가 낮다
            badge(user, "2026-11-03T10:%02d".formatted(i));
            viewer = user;
        }

        ranking(bearer(viewer), null)
                .andExpect(jsonPath("$.podium.length()").value(3))
                .andExpect(jsonPath("$.others.length()").value(17))
                .andExpect(jsonPath("$.others[16].rank").value(20))
                .andExpect(jsonPath("$.me.rank").value(21))
                .andExpect(jsonPath("$.me.badgeCount").value(1));
    }

    @Test
    void 이번_주_배지가_없는_사용자는_me가_null이다() throws Exception {
        long u1 = createActiveUser("u1");
        long viewer = createActiveUser("viewer");
        badge(u1, "2026-11-03T10:00");

        ranking(bearer(viewer), null)
                .andExpect(jsonPath("$.podium.length()").value(1))
                .andExpect(jsonPath("$.me").value(nullValue(), Object.class));
    }

    @Test
    void 지난_주_배지는_이번_주_랭킹에_들어가지_않는다() throws Exception {
        long u1 = createActiveUser("u1");
        badge(u1, "2026-11-01T10:00");   // 일요일, 지난 주

        ranking(bearer(u1), null)
                .andExpect(jsonPath("$.podium.length()").value(0))
                .andExpect(jsonPath("$.me").value(nullValue(), Object.class));
        ranking(bearer(u1), "2026-10-28")
                .andExpect(jsonPath("$.weekStart").value("2026-10-26"))
                .andExpect(jsonPath("$.podium.length()").value(1));
    }

    // ---- 도우미 ----

    /** KST 기준 시각에 이 공원의 배지를 받은 것으로 넣는다. earned_date는 같은 시각의 KST 날짜. */
    private void badge(long userId, String kstDateTime) {
        LocalDateTime local = LocalDateTime.parse(kstDateTime);
        Instant at = local.atZone(Kst.ZONE).toInstant();
        LocalDate date = local.toLocalDate();
        createBadge(userId, parkId, date, at);
    }

    private ResultActions ranking(String authorization, String week) throws Exception {
        var request = get("/api/parks/{id}/rankings", parkId).header("Authorization", authorization);
        if (week != null) {
            request = request.param("week", week);
        }
        return mockMvc.perform(request);
    }
}
