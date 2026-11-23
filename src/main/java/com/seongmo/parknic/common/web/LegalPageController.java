package com.seongmo.parknic.common.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;

// 앱과 스토어가 URL로 쓰는 공개 페이지. 문서는 resources/legal 아래 HTML 파일
@Controller
public class LegalPageController {

    private static final MediaType HTML_UTF8 = new MediaType("text", "html", StandardCharsets.UTF_8);

    @GetMapping("/terms")
    public ResponseEntity<Resource> terms() {
        return page("terms");
    }

    @GetMapping("/privacy")
    public ResponseEntity<Resource> privacy() {
        return page("privacy");
    }

    @GetMapping("/support")
    public ResponseEntity<Resource> support() {
        return page("support");
    }

    private ResponseEntity<Resource> page(String name) {
        return ResponseEntity.ok()
                .contentType(HTML_UTF8)
                .body(new ClassPathResource("legal/" + name + ".html"));
    }
}
