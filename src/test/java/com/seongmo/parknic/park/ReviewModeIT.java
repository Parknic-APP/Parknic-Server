package com.seongmo.parknic.park;

import com.jayway.jsonpath.JsonPath;
import com.seongmo.parknic.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 심사 기간(REVIEW_MODE=true)에는 심사용 공원이 보이고 체크인할 수 있다. 리뷰 좋은 공원에는 계속 제외된다. */
@TestPropertySource(properties = "parknic.review.mode=true")
class ReviewModeIT extends IntegrationTest {

    static final double LAT = 37.5;
    static final double LNG = 127.0;

    long viewer;
    long reviewOnly;
    long normal;

    @BeforeEach
    void setUp() {
        viewer = createActiveUser("viewer");
        reviewOnly = createPark("심사용공원", LAT, LNG, 200);
        normal = createPark("일반공원", LAT + 0.001, LNG, 200);
        jdbc.sql("UPDATE parks SET review_only = TRUE WHERE id = :id").param("id", reviewOnly).update();
    }

    @Test
    void 켜져_있으면_심사용_공원이_일반_공원과_함께_주변_조회에_나온다() throws Exception {
        String body = mockMvc.perform(get("/api/parks/nearby")
                        .param("lat", String.valueOf(LAT)).param("lng", String.valueOf(LNG)).param("radius", "1000")
                        .header("Authorization", bearer(viewer)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$.parks[*].name")).containsExactly("심사용공원", "일반공원");
    }

    @Test
    void 켜져_있으면_심사용_공원의_상세와_체크인_시작이_된다() throws Exception {
        mockMvc.perform(get("/api/parks/{id}", reviewOnly).header("Authorization", bearer(viewer)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/check-ins").header("Authorization", bearer(viewer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkId\":%d,\"lat\":%s,\"lng\":%s}".formatted(reviewOnly, LAT, LNG)))
                .andExpect(status().isOk());
    }

    @Test
    void 켜져_있어도_리뷰_좋은_공원에는_심사용_공원이_나오지_않는다() throws Exception {
        List<Long> reviewers = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            reviewers.add(createActiveUser("r" + i));
        }
        for (long reviewer : reviewers) {
            createReview(reviewer, reviewOnly, 5, false);
            createReview(reviewer, normal, 5, false);
        }

        String body = mockMvc.perform(get("/api/parks/top-rated")
                        .param("lat", String.valueOf(LAT)).param("lng", String.valueOf(LNG))
                        .header("Authorization", bearer(viewer)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(body, "$.parks[*].name")).containsExactly("일반공원");
    }
}
