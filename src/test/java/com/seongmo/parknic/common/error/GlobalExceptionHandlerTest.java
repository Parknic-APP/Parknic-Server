package com.seongmo.parknic.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @RestController
    static class ThrowingController {
        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("db password is secret");
        }

        @PostMapping("/test/json")
        void json(@RequestBody Map<String, Object> body) {
        }
    }

    @Test
    void 비즈니스_예외는_코드와_상태로_변환된다() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATED"));
    }

    @Test
    void 예상치_못한_예외는_내부_메시지를_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void 깨진_JSON은_INVALID_REQUEST다() throws Exception {
        mockMvc.perform(post("/test/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 지원하지_않는_메서드는_METHOD_NOT_ALLOWED다() throws Exception {
        mockMvc.perform(delete("/test/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}