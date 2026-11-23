package com.seongmo.parknic.auth.service;

import com.seongmo.parknic.auth.config.AppleProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

// Apple 서버와 통신: authorizationCode를 refresh token으로 교환, 탈퇴 때 refresh token 폐기
// 두 기능 모두 실패해도 로그인/탈퇴를 막지 않는다. 비밀값(토큰, client secret)은 로그에 남기지 않는다
@Slf4j
@Component
public class AppleAuthClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() { };

    private final AppleProperties properties;
    private final AppleClientSecretGenerator clientSecretGenerator;
    private final RestClient restClient;

    public AppleAuthClient(AppleProperties properties, AppleClientSecretGenerator clientSecretGenerator) {
        this.properties = properties;
        this.clientSecretGenerator = clientSecretGenerator;

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    // 성공하면 refresh token, 설정이 없거나 실패하면 빈 값
    public Optional<String> exchangeCode(String authorizationCode) {
        if (!clientSecretGenerator.isConfigured()) {
            return Optional.empty();
        }
        MultiValueMap<String, String> form = baseForm();
        form.add("grant_type", "authorization_code");
        form.add("code", authorizationCode);
        try {
            Map<String, Object> response = restClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JSON_MAP);
            Object refreshToken = response == null ? null : response.get("refresh_token");
            return refreshToken instanceof String token && !token.isBlank() ? Optional.of(token) : Optional.empty();
        } catch (RestClientException e) {
            log.warn("Apple 코드 교환 실패: {}", describe(e));
            return Optional.empty();
        }
    }

    public void revoke(String refreshToken) {
        if (!clientSecretGenerator.isConfigured()) {
            log.warn("Apple 서명 설정이 없어 토큰 폐기를 건너뜀");
            return;
        }
        MultiValueMap<String, String> form = baseForm();
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");
        try {
            restClient.post()
                    .uri(properties.revokeUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Apple 토큰 폐기 실패: {}", describe(e));
        }
    }

    private MultiValueMap<String, String> baseForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", clientSecretGenerator.generate());
        return form;
    }

    // 응답 본문이나 요청 내용이 섞이지 않도록 예외 종류와 상태 코드만 남긴다
    private String describe(RestClientException e) {
        if (e instanceof RestClientResponseException responseException) {
            return "HTTP " + responseException.getStatusCode().value();
        }
        return e.getClass().getSimpleName();
    }
}
