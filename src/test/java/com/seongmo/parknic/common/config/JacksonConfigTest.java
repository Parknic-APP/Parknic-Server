package com.seongmo.parknic.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import(JacksonConfigTest.SampleController.class)
public class JacksonConfigTest {

    @Autowired
    MockMvc mockMvc;

    record Sample(String name, Double averageRating) {}

    @RestController
    static class SampleController {
        @GetMapping("/test/null-field")
        Sample nullField() {
            return new Sample("보라매공원", null);
        }
    }

    @Test
    void null_필드도_키가_응답에_포함된다() throws Exception {
        mockMvc.perform(get("/test/null-field"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").hasJsonPath())
                .andExpect(jsonPath("$.averageRating").value(nullValue(), Object.class));
    }
}
