package com.seongmo.parknic.auth;

import com.jayway.jsonpath.JsonPath;
import com.seongmo.parknic.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "parknic.dev-login.enabled=true")
class AuthIT extends IntegrationTest {

    @Test
    void 처음_로그인하면_사용자가_만들어지고_isNewUser가_true다() throws Exception {
        login("{\"token\":\"dev:local:simulator\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.isNewUser").value(true));
    }

    @Test
    void 같은_이름으로_다시_로그인해도_사용자는_하나이고_isNewUser는_true다() throws Exception {
        login("{\"token\":\"dev:local:simulator\"}").andExpect(status().isOk());
        login("{\"token\":\"dev:local:simulator\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true));

        assertThat(countUsers()).isEqualTo(1);
    }

    @Test
    void 받은_토큰으로_보호된_경로를_호출하면_인증을_통과한다() throws Exception {
        String body = login("{\"token\":\"dev:local:simulator\"}")
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/anything").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void 토큰이_비어_있으면_400이다() throws Exception {
        login("{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        login("{\"token\":\"\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void dev로_시작하지_않는_토큰은_401이다() throws Exception {
        login("{\"token\":\"abc\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private ResultActions login(String json) throws Exception {
        return mockMvc.perform(post("/api/auth/login/apple")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private int countUsers() {
        return jdbc.sql("SELECT count(*) FROM users").query(Integer.class).single();
    }
}