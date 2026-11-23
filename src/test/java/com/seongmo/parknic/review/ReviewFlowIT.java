package com.seongmo.parknic.review;

import com.jayway.jsonpath.JsonPath;
import com.seongmo.parknic.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 리뷰 작성, 목록, 수정/삭제, 신고의 규칙. 사용자 여럿이 얽힌 경우를 중심으로 확인한다. */
class ReviewFlowIT extends IntegrationTest {

    static final LocalDate DAY = LocalDate.of(2026, 11, 1);

    long parkId;
    long viewer;

    @BeforeEach
    void setUp() {
        parkId = createPark("리뷰공원", 37.5, 127.0, 200);
        viewer = createActiveUser("viewer");
    }

    // ---------------- 작성 ----------------

    @Test
    void 배지가_없으면_리뷰를_쓸_수_없고_배지가_생기면_쓸_수_있다() throws Exception {
        long user = createActiveUser("author");

        write(user, parkId, 5, content(20))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_BADGE_REQUIRED"));
        assertThat(countReviews()).isZero();

        giveBadge(user, parkId);
        write(user, parkId, 5, content(20)).andExpect(status().isOk());
        assertThat(countReviews()).isEqualTo(1);
    }

    @Test
    void 같은_공원에_두_번째_리뷰는_409이고_하나만_남는다() throws Exception {
        long user = createActiveUser("author");
        giveBadge(user, parkId);
        write(user, parkId, 5, content(20)).andExpect(status().isOk());

        write(user, parkId, 4, content(20))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));
        assertThat(countReviews()).isEqualTo(1);
    }

    @Test
    void 별점과_내용이_규칙을_벗어나면_400이고_경계값은_통과한다() throws Exception {
        long user = createActiveUser("author");
        giveBadge(user, parkId);

        write(user, parkId, 0, content(20)).andExpect(status().isBadRequest());
        write(user, parkId, 6, content(20)).andExpect(status().isBadRequest());
        write(user, parkId, 5, content(9)).andExpect(status().isBadRequest());
        write(user, parkId, 5, content(301)).andExpect(status().isBadRequest());
        write(user, parkId, 5, "          ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(countReviews()).isZero();

        // 10자는 통과, 300자는 다른 공원에서 통과
        write(user, parkId, 1, content(10)).andExpect(status().isOk());
        long otherPark = createPark("두번째공원", 37.6, 127.1, 200);
        giveBadge(user, otherPark);
        write(user, otherPark, 5, content(300)).andExpect(status().isOk());
    }

    @Test
    void 없는_공원에는_리뷰를_쓸_수_없다() throws Exception {
        write(createActiveUser("author"), 999_999L, 5, content(20))
                .andExpect(status().isNotFound());
    }

    // ---------------- 목록 ----------------

    @Test
    void 목록은_20개씩_최신순이고_다음_페이지와_겹치지_않는다() throws Exception {
        for (int i = 1; i <= 25; i++) {
            createReview(createActiveUser("u" + i), parkId, 5, false);
        }

        String first = list(viewer, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> firstIds = JsonPath.read(first, "$.reviews[*].id");
        String nextCursor = JsonPath.read(first, "$.nextCursor");

        assertThat(firstIds).hasSize(20).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(nextCursor).isEqualTo(String.valueOf(firstIds.get(19)));

        String second = list(viewer, nextCursor).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> secondIds = JsonPath.read(second, "$.reviews[*].id");

        assertThat(secondIds).hasSize(5);
        assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);
        assertThat(secondIds.get(0)).isLessThan(firstIds.get(19));
        assertThat(JsonPath.<Object>read(second, "$.nextCursor")).isNull();
    }

    @Test
    void 숨긴_리뷰와_내가_차단한_사용자의_리뷰는_내_목록에서만_빠진다() throws Exception {
        long visible = createActiveUser("visible");
        long hiddenAuthor = createActiveUser("hidden-author");
        long blocked = createActiveUser("blocked");
        long other = createActiveUser("other");
        createReview(visible, parkId, 5, false);
        createReview(hiddenAuthor, parkId, 5, true);
        createReview(blocked, parkId, 5, false);
        createBlock(viewer, blocked);

        assertThat(nicknames(list(viewer, null))).containsExactlyInAnyOrder("visible");
        // 차단은 한 방향: 차단하지 않은 다른 사용자에게는 보인다. 숨긴 리뷰는 누구에게도 보이지 않는다
        assertThat(nicknames(list(other, null))).containsExactlyInAnyOrder("visible", "blocked");
    }

    @Test
    void isMine과_작성자의_이_공원_배지_수가_내려온다() throws Exception {
        long mine = createActiveUser("mine");
        long theirs = createActiveUser("theirs");
        createBadge(mine, parkId, DAY, DAY.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        createBadge(mine, parkId, DAY.plusDays(1), DAY.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        giveBadge(theirs, parkId);
        createReview(mine, parkId, 5, false);
        createReview(theirs, parkId, 4, false);

        String body = list(mine, null).andReturn().getResponse().getContentAsString();
        List<String> names = JsonPath.read(body, "$.reviews[*].nickname");
        int mineIndex = names.indexOf("mine");
        int theirsIndex = names.indexOf("theirs");

        assertThat(JsonPath.<Boolean>read(body, "$.reviews[" + mineIndex + "].isMine")).isTrue();
        assertThat(JsonPath.<Integer>read(body, "$.reviews[" + mineIndex + "].badgeCount")).isEqualTo(2);
        assertThat(JsonPath.<Boolean>read(body, "$.reviews[" + theirsIndex + "].isMine")).isFalse();
        assertThat(JsonPath.<Integer>read(body, "$.reviews[" + theirsIndex + "].badgeCount")).isEqualTo(1);
    }

    @Test
    void cursor가_숫자가_아니면_400이다() throws Exception {
        list(viewer, "abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 내_리뷰_목록과_공원_상세의_개수와_평균은_숨긴_리뷰를_뺀다() throws Exception {
        long author = createActiveUser("author");
        long parkTwo = createPark("두번째공원", 37.6, 127.1, 200);
        long parkThree = createPark("세번째공원", 37.7, 127.2, 200);
        createReview(author, parkId, 5, false);
        createReview(author, parkTwo, 3, true);
        createReview(author, parkThree, 4, false);

        mockMvc.perform(get("/api/users/me/reviews").header("Authorization", bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.reviews.length()").value(2));

        mockMvc.perform(get("/api/parks/{id}", parkTwo).header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.averageRating").value(nullValue(), Object.class));
        mockMvc.perform(get("/api/parks/{id}", parkId).header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.averageRating").value(5.0));
    }

    // ---------------- 수정, 삭제 ----------------

    @Test
    void 남의_리뷰는_수정도_삭제도_404이고_바뀌지_않는다() throws Exception {
        long author = createActiveUser("author");
        long reviewId = createReview(author, parkId, 5, false);

        update(viewer, reviewId, 1, content(30)).andExpect(status().isNotFound());
        remove(viewer, reviewId).andExpect(status().isNotFound());

        assertThat(countReviews()).isEqualTo(1);
        assertThat(ratingOf(reviewId)).isEqualTo(5);
    }

    @Test
    void 내_리뷰를_수정하면_내용과_별점이_바뀐다() throws Exception {
        long author = createActiveUser("author");
        long reviewId = createReview(author, parkId, 5, false);

        update(author, reviewId, 2, content(30)).andExpect(status().isOk());

        assertThat(ratingOf(reviewId)).isEqualTo(2);
    }

    @Test
    void 숨겨진_내_리뷰는_수정할_수_없지만_삭제할_수_있다() throws Exception {
        long author = createActiveUser("author");
        long reviewId = createReview(author, parkId, 5, true);

        update(author, reviewId, 1, content(30)).andExpect(status().isNotFound());
        remove(author, reviewId).andExpect(status().isOk());

        assertThat(countReviews()).isZero();
    }

    @Test
    void 삭제하면_다시_쓸_수_있고_같은_리뷰를_두_번_삭제하면_404다() throws Exception {
        long author = createActiveUser("author");
        giveBadge(author, parkId);
        write(author, parkId, 5, content(20)).andExpect(status().isOk());
        long reviewId = jdbc.sql("SELECT id FROM reviews").query(Long.class).single();

        remove(author, reviewId).andExpect(status().isOk());
        remove(author, reviewId).andExpect(status().isNotFound());

        write(author, parkId, 3, content(20)).andExpect(status().isOk());
        assertThat(countReviews()).isEqualTo(1);
    }

    // ---------------- 신고 ----------------

    @Test
    void 서로_다른_사용자_5명이_신고하면_5번째에_숨겨지고_그_뒤_신고는_404다() throws Exception {
        long author = createActiveUser("author");
        long reviewId = createReview(author, parkId, 5, false);
        List<Long> reporters = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            reporters.add(createActiveUser("reporter" + i));
        }

        for (int i = 0; i < 4; i++) {
            report(reporters.get(i), reviewId, "SPAM").andExpect(status().isOk());
        }
        assertThat(nicknames(list(viewer, null))).containsExactly("author");

        report(reporters.get(4), reviewId, "SPAM").andExpect(status().isOk());
        assertThat(nicknames(list(viewer, null))).isEmpty();

        report(reporters.get(5), reviewId, "SPAM").andExpect(status().isNotFound());
    }

    @Test
    void 같은_사용자의_중복_신고는_409이고_내_리뷰는_신고할_수_없다() throws Exception {
        long author = createActiveUser("author");
        long reviewId = createReview(author, parkId, 5, false);

        report(viewer, reviewId, "SPAM").andExpect(status().isOk());
        report(viewer, reviewId, "ABUSE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_REPORTED"));
        assertThat(countReports()).isEqualTo(1);

        report(author, reviewId, "SPAM")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(countReports()).isEqualTo(1);
    }

    @Test
    void 허용되지_않은_신고_사유는_400이다() throws Exception {
        long reviewId = createReview(createActiveUser("author"), parkId, 5, false);

        report(viewer, reviewId, "WHATEVER").andExpect(status().isBadRequest());
        assertThat(countReports()).isZero();
    }

    // ---------------- 도우미 ----------------

    private ResultActions write(long user, long park, int rating, String content) throws Exception {
        return mockMvc.perform(post("/api/parks/{id}/reviews", park)
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":%d,\"content\":\"%s\"}".formatted(rating, content)));
    }

    private ResultActions update(long user, long reviewId, int rating, String content) throws Exception {
        return mockMvc.perform(patch("/api/reviews/{id}", reviewId)
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":%d,\"content\":\"%s\"}".formatted(rating, content)));
    }

    private ResultActions remove(long user, long reviewId) throws Exception {
        return mockMvc.perform(delete("/api/reviews/{id}", reviewId).header("Authorization", bearer(user)));
    }

    private ResultActions report(long user, long reviewId, String reason) throws Exception {
        return mockMvc.perform(post("/api/reviews/{id}/reports", reviewId)
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"%s\"}".formatted(reason)));
    }

    private ResultActions list(long user, String cursor) throws Exception {
        var request = get("/api/parks/{id}/reviews", parkId).header("Authorization", bearer(user));
        if (cursor != null) {
            request = request.param("cursor", cursor);
        }
        return mockMvc.perform(request);
    }

    private List<String> nicknames(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.reviews[*].nickname");
    }

    private void giveBadge(long user, long park) {
        createBadge(user, park, DAY, DAY.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
    }

    private static String content(int length) {
        return "가".repeat(length);
    }

    private int countReviews() {
        return jdbc.sql("SELECT count(*) FROM reviews").query(Integer.class).single();
    }

    private int countReports() {
        return jdbc.sql("SELECT count(*) FROM review_reports").query(Integer.class).single();
    }

    private int ratingOf(long reviewId) {
        return jdbc.sql("SELECT rating FROM reviews WHERE id = :id").param("id", reviewId)
                .query(Integer.class).single();
    }
}
