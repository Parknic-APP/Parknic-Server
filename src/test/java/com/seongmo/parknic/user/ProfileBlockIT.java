package com.seongmo.parknic.user;

import com.jayway.jsonpath.JsonPath;
import com.seongmo.parknic.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 프로필(닉네임, 캐릭터)과 사용자 차단/해제. */
class ProfileBlockIT extends IntegrationTest {

    long parkId;

    @BeforeEach
    void setUp() {
        parkId = createPark("프로필공원", 37.5, 127.0, 200);
    }

    // ---------------- 프로필 ----------------

    @Test
    void 프로필을_저장하면_ACTIVE가_되고_내_정보에_반영된다() throws Exception {
        long user = createPendingUser("newbie");

        saveProfile(user, "산책왕", 3).andExpect(status().isOk());

        assertThat(statusOf(user)).isEqualTo("ACTIVE");
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("산책왕"))
                .andExpect(jsonPath("$.characterId").value(3))
                .andExpect(jsonPath("$.characterName").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
    }

    @Test
    void 닉네임이_중복이면_409이고_본인의_현재_닉네임은_다시_저장할_수_있다() throws Exception {
        long first = createPendingUser("first");
        long second = createPendingUser("second");
        saveProfile(first, "초록발", 1).andExpect(status().isOk());

        saveProfile(second, "초록발", 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATED"));
        assertThat(statusOf(second)).isEqualTo("PENDING");

        // 캐릭터만 바꾸려고 같은 닉네임을 보내도 중복으로 보지 않는다
        saveProfile(first, "초록발", 5).andExpect(status().isOk());
    }

    @Test
    void 금칙어가_들어간_닉네임은_대소문자와_상관없이_400이다() throws Exception {
        long user = createPendingUser("newbie");

        saveProfile(user, "시발이", 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NICKNAME_INAPPROPRIATE"));
        saveProfile(user, "FuckYou", 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NICKNAME_INAPPROPRIATE"));
        assertThat(statusOf(user)).isEqualTo("PENDING");
    }

    @Test
    void 닉네임은_공백_없이_2자에서_10자이고_경계는_통과한다() throws Exception {
        long user = createPendingUser("newbie");

        saveProfile(user, "가", 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        saveProfile(user, "열한글자닉네임입니다요요", 1).andExpect(status().isBadRequest());
        saveProfile(user, "공 백", 1).andExpect(status().isBadRequest());

        saveProfile(user, "가나", 1).andExpect(status().isOk());
        saveProfile(user, "열글자닉네임입니다요", 1).andExpect(status().isOk());
    }

    @Test
    void 캐릭터는_1에서_8까지만_허용한다() throws Exception {
        long user = createPendingUser("newbie");

        saveProfile(user, "산책왕", 0).andExpect(status().isBadRequest());
        saveProfile(user, "산책왕", 9).andExpect(status().isBadRequest());

        saveProfile(user, "산책왕", 1).andExpect(status().isOk());
        saveProfile(user, "산책왕", 8).andExpect(status().isOk());
    }

    @Test
    void 닉네임_확인은_남이_쓰는_닉네임만_사용_불가이고_본인_닉네임은_사용_가능이다() throws Exception {
        long owner = createActiveUser("owner");
        long other = createActiveUser("other");

        check(other, "owner").andExpect(jsonPath("$.available").value(false));
        check(owner, "owner").andExpect(jsonPath("$.available").value(true));
        check(other, "새닉네임").andExpect(jsonPath("$.available").value(true));
        check(other, "시발").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NICKNAME_INAPPROPRIATE"));
        check(other, "가").andExpect(status().isBadRequest());
    }

    // ---------------- 차단 ----------------

    @Test
    void 자기_자신과_없는_사용자는_차단할_수_없다() throws Exception {
        long user = createActiveUser("blocker");

        block(user, user).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        block(user, 999_999L).andExpect(status().isNotFound());
        assertThat(countBlocks()).isZero();
    }

    @Test
    void 같은_사용자를_두_번_차단해도_행은_하나이고_해제는_여러_번_해도_200이다() throws Exception {
        long blocker = createActiveUser("blocker");
        long target = createActiveUser("target");

        block(blocker, target).andExpect(status().isOk());
        block(blocker, target).andExpect(status().isOk());
        assertThat(countBlocks()).isEqualTo(1);

        unblock(blocker, target).andExpect(status().isOk());
        unblock(blocker, target).andExpect(status().isOk());
        assertThat(countBlocks()).isZero();
        // 차단한 적 없는 사용자의 해제도 200
        unblock(blocker, createActiveUser("stranger")).andExpect(status().isOk());
    }

    @Test
    void 차단하면_그_사용자의_리뷰가_내_목록에서_사라지고_해제하면_다시_보인다() throws Exception {
        long viewer = createActiveUser("viewer");
        long author = createActiveUser("author");
        createReview(author, parkId, 5, false);

        assertThat(authors(viewer)).containsExactly("author");

        block(viewer, author).andExpect(status().isOk());
        assertThat(authors(viewer)).isEmpty();

        unblock(viewer, author).andExpect(status().isOk());
        assertThat(authors(viewer)).containsExactly("author");
    }

    // ---- 도우미 ----

    private long createPendingUser(String name) {
        return jdbc.sql("""
                        INSERT INTO users (provider, provider_id, status, created_at)
                        VALUES ('TEST', :providerId, 'PENDING', now()) RETURNING id
                        """)
                .param("providerId", "pending-" + name)
                .query(Long.class).single();
    }

    private ResultActions saveProfile(long user, String nickname, int characterId) throws Exception {
        return mockMvc.perform(patch("/api/users/me")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"%s\",\"characterId\":%d}".formatted(nickname, characterId)));
    }

    private ResultActions check(long user, String nickname) throws Exception {
        return mockMvc.perform(get("/api/users/nickname/check")
                .header("Authorization", bearer(user)).param("nickname", nickname));
    }

    private ResultActions block(long user, long target) throws Exception {
        return mockMvc.perform(post("/api/users/{id}/block", target).header("Authorization", bearer(user)));
    }

    private ResultActions unblock(long user, long target) throws Exception {
        return mockMvc.perform(delete("/api/users/{id}/block", target).header("Authorization", bearer(user)));
    }

    private List<String> authors(long viewer) throws Exception {
        String body = mockMvc.perform(get("/api/parks/{id}/reviews", parkId).header("Authorization", bearer(viewer)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.reviews[*].nickname");
    }

    private String statusOf(long user) {
        return jdbc.sql("SELECT status FROM users WHERE id = :id").param("id", user).query(String.class).single();
    }

    private int countBlocks() {
        return jdbc.sql("SELECT count(*) FROM user_blocks").query(Integer.class).single();
    }
}
