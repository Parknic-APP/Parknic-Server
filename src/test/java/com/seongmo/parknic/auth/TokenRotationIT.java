package com.seongmo.parknic.auth;

import com.jayway.jsonpath.JsonPath;
import com.seongmo.parknic.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** refresh 토큰 재발급(회전, 재사용 거절, 만료)과 로그아웃. */
@TestPropertySource(properties = "parknic.dev-login.enabled=true")
class TokenRotationIT extends IntegrationTest {

    @Test
    void 재발급하면_새_refresh_토큰이_오고_이전_토큰은_사용된_것으로_표시된다() throws Exception {
        Tokens first = login("rotator");

        String body = reissue(first.refresh()).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String second = JsonPath.read(body, "$.refreshToken");

        assertThat(second).isNotEqualTo(first.refresh());
        assertThat(count("SELECT count(*) FROM refresh_tokens")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM refresh_tokens WHERE used")).isEqualTo(1);
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + JsonPath.<String>read(body, "$.accessToken")))
                .andExpect(status().isOk());
    }

    @Test
    void 이미_쓴_refresh_토큰을_다시_쓰면_401이다() throws Exception {
        Tokens first = login("rotator");
        reissue(first.refresh()).andExpect(status().isOk());

        reissue(first.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void 재사용을_거절해도_정상적으로_받은_새_토큰은_계속_쓸_수_있다_토큰_계열_폐기는_아직_없다() throws Exception {
        Tokens first = login("rotator");
        String second = JsonPath.read(reissue(first.refresh()).andReturn().getResponse().getContentAsString(),
                "$.refreshToken");

        reissue(first.refresh()).andExpect(status().isUnauthorized());

        // 현재 동작을 고정한 테스트: 탈취가 의심되는 재사용이어도 같은 계열의 새 토큰을 막지는 않는다
        reissue(second).andExpect(status().isOk());
    }

    @Test
    void 만료된_refresh_토큰은_401이다() throws Exception {
        Tokens tokens = login("rotator");

        clock.advance(Duration.ofDays(30).plusSeconds(1));

        reissue(tokens.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void 알_수_없는_토큰은_401이고_빈_값은_400이다() throws Exception {
        reissue("not-a-real-token").andExpect(status().isUnauthorized());
        reissue("").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 로그아웃하면_그_사용자의_refresh_토큰으로_재발급할_수_없다() throws Exception {
        Tokens tokens = login("rotator");

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + tokens.access()))
                .andExpect(status().isOk());

        reissue(tokens.refresh()).andExpect(status().isUnauthorized());
        assertThat(count("SELECT count(*) FROM refresh_tokens")).isZero();
    }

    @Test
    void 로그아웃해도_다른_사용자의_refresh_토큰은_그대로다() throws Exception {
        Tokens leaving = login("leaving");
        Tokens staying = login("staying");

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + leaving.access()))
                .andExpect(status().isOk());

        reissue(staying.refresh()).andExpect(status().isOk());
    }

    @Test
    void 로그아웃한_뒤에도_이미_받은_액세스_토큰은_만료될_때까지_유효하다_알려진_한계() throws Exception {
        Tokens tokens = login("rotator");

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + tokens.access()))
                .andExpect(status().isOk());

        // 액세스 토큰은 서버에 저장하지 않으므로 로그아웃으로 무효화되지 않는다 (유효 시간 30분)
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tokens.access()))
                .andExpect(status().isOk());
    }

    // ---- 도우미 ----

    private record Tokens(String access, String refresh) {
    }

    private Tokens login(String name) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"dev:local:%s\"}".formatted(name)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
    }

    private ResultActions reissue(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }
}
