package com.seongmo.parknic.park;

import com.jayway.jsonpath.JsonPath;
import com.seongmo.parknic.common.time.Kst;
import com.seongmo.parknic.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주변 공원, 공원 상세, 리뷰 좋은 공원, 심사용 공원(REVIEW_MODE 꺼짐, 기본값).
 * 기준 좌표 (37.5, 127.0). 위도 0.001도는 약 111m.
 */
class ParkQueryIT extends IntegrationTest {

    static final double LAT = 37.5;
    static final double LNG = 127.0;
    static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    long viewer;

    @BeforeEach
    void setUp() {
        viewer = createActiveUser("viewer");
    }

    // ---------------- 주변 공원 ----------------

    @Test
    void 주변_공원은_반경_안만_가까운_순으로_나온다() throws Exception {
        createPark("가까운공원", LAT + 0.0018, LNG, 200);      // 약 200m
        createPark("중간공원", LAT + 0.0072, LNG, 200);        // 약 800m
        createPark("먼공원", LAT + 0.027, LNG, 200);           // 약 3km
        createPark("정중앙공원", LAT, LNG, 200);               // 0m

        String body = nearby(1000).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$.parks[*].name"))
                .containsExactly("정중앙공원", "가까운공원", "중간공원");
        List<Integer> distances = JsonPath.read(body, "$.parks[*].distanceM");
        assertThat(distances).isSorted();
        assertThat(distances.get(0)).isZero();
    }

    @Test
    void 반경은_100에서_5000으로_보정된다() throws Exception {
        createPark("정중앙공원", LAT, LNG, 200);
        createPark("근처공원", LAT + 0.0007, LNG, 200);        // 약 78m
        createPark("가까운공원", LAT + 0.0018, LNG, 200);      // 약 200m
        createPark("먼공원", LAT + 0.027, LNG, 200);           // 약 3km
        createPark("아주먼공원", LAT + 0.08, LNG, 200);        // 약 8.9km

        // 50은 100으로 올려서 78m 공원까지 보이고 200m 공원은 아직 안 보인다
        assertThat(JsonPath.<List<String>>read(nearby(50).andReturn().getResponse().getContentAsString(), "$.parks[*].name"))
                .containsExactly("정중앙공원", "근처공원");
        // 999999는 5000으로 내려서 8.9km 공원은 제외
        assertThat(JsonPath.<List<String>>read(nearby(999_999).andReturn().getResponse().getContentAsString(), "$.parks[*].name"))
                .containsExactly("정중앙공원", "근처공원", "가까운공원", "먼공원");
    }

    @Test
    void 내_배지_상태는_오늘_받음_받은_적_있음_없음으로_나온다() throws Exception {
        long today = createPark("오늘받음", LAT, LNG, 200);
        long before = createPark("예전에받음", LAT + 0.001, LNG, 200);
        createPark("없음", LAT + 0.002, LNG, 200);
        badge(viewer, today, TODAY);
        badge(viewer, today, TODAY.minusDays(1));
        badge(viewer, before, TODAY.minusDays(3));

        String body = nearby(1000).andReturn().getResponse().getContentAsString();

        assertThat(field(body, "오늘받음", "myStatus")).isEqualTo("EARNED_TODAY");
        assertThat(field(body, "오늘받음", "myBadgeCount")).isEqualTo(2);
        assertThat(field(body, "예전에받음", "myStatus")).isEqualTo("EARNED");
        assertThat(field(body, "없음", "myStatus")).isEqualTo("NONE");
        assertThat(field(body, "없음", "myBadgeCount")).isEqualTo(0);
    }

    @Test
    void 다른_사용자의_배지는_내_상태에_영향이_없다() throws Exception {
        long park = createPark("공원", LAT, LNG, 200);
        badge(createActiveUser("other"), park, TODAY);

        String body = nearby(1000).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(body, "$.parks[0].myStatus")).isEqualTo("NONE");
    }

    @Test
    void 좌표가_없거나_숫자가_아니면_400이다() throws Exception {
        mockMvc.perform(get("/api/parks/nearby").header("Authorization", bearer(viewer)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/parks/nearby").param("lat", "abc").param("lng", "127")
                        .header("Authorization", bearer(viewer)))
                .andExpect(status().isBadRequest());
    }

    // ---------------- 공원 상세 ----------------

    @Test
    void 공원_상세의_평균은_소수_첫째_자리로_반올림하고_리뷰가_없으면_null이다() throws Exception {
        long reviewed = createPark("리뷰공원", LAT, LNG, 200);
        long empty = createPark("빈공원", LAT + 0.01, LNG, 200);
        createReview(createActiveUser("a"), reviewed, 5, false);
        createReview(createActiveUser("b"), reviewed, 4, false);
        createReview(createActiveUser("c"), reviewed, 4, false);
        createReview(createActiveUser("d"), reviewed, 1, true);      // 숨김은 세지 않는다

        mockMvc.perform(get("/api/parks/{id}", reviewed).header("Authorization", bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.3))
                .andExpect(jsonPath("$.reviewCount").value(3));
        mockMvc.perform(get("/api/parks/{id}", empty).header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.averageRating").value(nullValue(), Object.class))
                .andExpect(jsonPath("$.reviewCount").value(0));
    }

    @Test
    void 없는_공원_상세는_404다() throws Exception {
        mockMvc.perform(get("/api/parks/{id}", 999_999L).header("Authorization", bearer(viewer)))
                .andExpect(status().isNotFound());
    }

    // ---------------- 리뷰 좋은 공원 ----------------

    @Test
    void 리뷰가_10개_이상이고_평균이_4_5_이상인_공원만_평균_리뷰수_순으로_나온다() throws Exception {
        List<Long> reviewers = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            reviewers.add(createActiveUser("r" + i));
        }
        long perfect = createPark("만점공원", LAT, LNG, 200);
        long many = createPark("리뷰많은공원", LAT + 0.001, LNG, 200);
        long exact = createPark("경계공원", LAT + 0.002, LNG, 200);
        long low = createPark("평균낮은공원", LAT + 0.003, LNG, 200);
        long few = createPark("리뷰적은공원", LAT + 0.004, LNG, 200);
        long hiddenHeavy = createPark("숨김많은공원", LAT + 0.005, LNG, 200);

        reviews(reviewers, perfect, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5);                  // 10개, 5.0
        reviews(reviewers, many, 5, 5, 5, 5, 5, 5, 5, 4, 4, 4, 4, 4);               // 12개, 4.58 -> 4.6
        reviews(reviewers, exact, 5, 5, 5, 5, 5, 4, 4, 4, 4, 4);                    // 10개, 정확히 4.5
        reviews(reviewers, low, 5, 5, 5, 5, 4, 4, 4, 4, 4, 4);                      // 10개, 4.4
        reviews(reviewers, few, 5, 5, 5, 5, 5, 5, 5, 5, 5);                         // 9개
        reviews(reviewers, hiddenHeavy, 5, 5, 5, 5, 5, 5, 5, 5, 5);                 // 9개
        createReview(reviewers.get(9), hiddenHeavy, 5, true);                        // 숨김 1개를 더해도 10개가 되지 않는다

        String body = topRated().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$.parks[*].name"))
                .containsExactly("만점공원", "리뷰많은공원", "경계공원");
        assertThat(JsonPath.<List<Double>>read(body, "$.parks[*].averageRating")).containsExactly(5.0, 4.6, 4.5);
        assertThat(JsonPath.<List<Integer>>read(body, "$.parks[*].reviewCount")).containsExactly(10, 12, 10);
    }

    @Test
    void 리뷰_좋은_공원에도_내_배지_상태가_나온다() throws Exception {
        List<Long> reviewers = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            reviewers.add(createActiveUser("r" + i));
        }
        long park = createPark("만점공원", LAT, LNG, 200);
        reviews(reviewers, park, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5);
        badge(viewer, park, TODAY);

        topRated()
                .andExpect(jsonPath("$.parks[0].myStatus").value("EARNED_TODAY"))
                .andExpect(jsonPath("$.parks[0].myBadgeCount").value(1));
    }

    // ---------------- 심사용 공원 (기본: 꺼짐) ----------------

    @Test
    void 심사용_공원은_꺼져_있을_때_주변_조회_상세_체크인_리뷰_좋은_공원_어디에도_나오지_않는다() throws Exception {
        List<Long> reviewers = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            reviewers.add(createActiveUser("r" + i));
        }
        long reviewOnly = createPark("심사용공원", LAT, LNG, 200);
        long normal = createPark("일반공원", LAT + 0.001, LNG, 200);
        reviews(reviewers, reviewOnly, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5);
        jdbc.sql("UPDATE parks SET review_only = TRUE WHERE id = :id").param("id", reviewOnly).update();

        assertThat(JsonPath.<List<String>>read(nearby(1000).andReturn().getResponse().getContentAsString(), "$.parks[*].name"))
                .containsExactly("일반공원");
        mockMvc.perform(get("/api/parks/{id}", reviewOnly).header("Authorization", bearer(viewer)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/check-ins").header("Authorization", bearer(viewer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkId\":%d,\"lat\":%s,\"lng\":%s}".formatted(reviewOnly, LAT, LNG)))
                .andExpect(status().isNotFound());
        assertThat(JsonPath.<List<String>>read(topRated().andReturn().getResponse().getContentAsString(), "$.parks[*].name"))
                .isEmpty();
        mockMvc.perform(get("/api/parks/{id}", normal).header("Authorization", bearer(viewer)))
                .andExpect(status().isOk());
    }

    // ---- 도우미 ----

    private ResultActions nearby(int radius) throws Exception {
        return mockMvc.perform(get("/api/parks/nearby")
                .param("lat", String.valueOf(LAT)).param("lng", String.valueOf(LNG)).param("radius", String.valueOf(radius))
                .header("Authorization", bearer(viewer)));
    }

    private ResultActions topRated() throws Exception {
        return mockMvc.perform(get("/api/parks/top-rated")
                .param("lat", String.valueOf(LAT)).param("lng", String.valueOf(LNG))
                .header("Authorization", bearer(viewer)));
    }

    /** 응답의 parks 중 이름이 같은 공원의 필드 값 */
    private Object field(String body, String parkName, String field) {
        List<Object> values = JsonPath.read(body, "$.parks[?(@.name=='" + parkName + "')]." + field);
        return values.get(0);
    }

    private void reviews(List<Long> reviewers, long parkId, int... ratings) {
        for (int i = 0; i < ratings.length; i++) {
            createReview(reviewers.get(i), parkId, ratings[i], false);
        }
    }

    private void badge(long userId, long parkId, LocalDate day) {
        Instant at = LocalDateTime.of(day, LocalTime.NOON).atZone(Kst.ZONE).toInstant();
        createBadge(userId, parkId, day, at);
    }
}
