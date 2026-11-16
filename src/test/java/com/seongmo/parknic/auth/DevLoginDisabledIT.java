package com.seongmo.parknic.auth;

import com.seongmo.parknic.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "parknic.dev-login.enabled=false")
public class DevLoginDisabledIT extends IntegrationTest {

    @Test
    void 개발용_로그인이_꺼져_있으면_dev_토큰도_401이고_사용자를_만들지_않는다() throws Exception {
        mockMvc.perform(post("/api/auth/login/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"dev:local:simulator\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        int users = jdbc.sql("SELECT count(*) FROM users").query(Integer.class).single();
        assertThat(users).isZero();
    }
}
