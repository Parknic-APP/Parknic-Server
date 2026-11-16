package com.seongmo.parknic.common.security;

import com.seongmo.parknic.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityIT extends IntegrationTest {

    @Autowired
    AccessTokenService accessTokenService;

    @Autowired
    JwtProperties jwtProperties;
    
    @Test
    void 유효한_토큰이면_인증을_통과한다() throws Exception {
        String token = accessTokenService.issue(1L);

        mockMvc.perform(get("/api/anything").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void 만료된_토큰은_401과_TOKEN_EXPIRED다() throws Exception {
        String token = accessTokenService.issue(1L);
        clock.advance(jwtProperties.accessTtl().plusSeconds(1));

        mockMvc.perform(get("/api/anything").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void 엉터리_토큰은_401과_TOKEN_EXPIRED다() throws Exception {
        mockMvc.perform(get("/api/anything").header("Authorization", "Bearer abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void 토큰_없이_보호된_경로를_호출하면_401과_에러_형식이_나온다() throws Exception {
        mockMvc.perform(get("/api/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void 세션_쿠키와_Basic_인증_요청_헤더를_내려주지_않는다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/anything"))
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void 열어_둔_경로는_토큰_없이_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    // 컨트롤러가 생기면 /api/auth/login/**, /api/auth/reissue 테스트 추가
}