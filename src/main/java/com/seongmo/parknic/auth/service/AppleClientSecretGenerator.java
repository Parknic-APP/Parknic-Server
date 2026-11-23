package com.seongmo.parknic.auth.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.seongmo.parknic.auth.config.AppleProperties;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

// Apple 토큰 교환/폐기 요청에 쓰는 client secret(ES256 JWT)을 만든다
@Component
public class AppleClientSecretGenerator {

    private static final String AUDIENCE = "https://appleid.apple.com";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final AppleProperties properties;
    private final Clock clock;
    private final ECPrivateKey privateKey;

    public AppleClientSecretGenerator(AppleProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        // 설정이 있으면 시작할 때 키를 읽어서, 키 형식이 틀리면 서버가 바로 실패하도록 한다
        this.privateKey = properties.signingConfigured() ? parse(properties.privateKey()) : null;
    }

    public boolean isConfigured() {
        return privateKey != null;
    }

    // 요청마다 새로 만들고 5분만 유효하게 한다
    public String generate() {
        if (privateKey == null) {
            throw new IllegalStateException("Apple 서명 설정이 없습니다");
        }
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.teamId())
                .subject(properties.clientId())
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(TTL)))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(properties.keyId()).build(), claims);
        try {
            jwt.sign(new ECDSASigner(privateKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("Apple client secret 서명 실패", e);
        }
        return jwt.serialize();
    }

    // .p8(PKCS#8 PEM) 내용. 환경변수로 줄바꿈이 문자 그대로 "\n"로 들어오는 경우도 받는다
    private static ECPrivateKey parse(String pem) {
        try {
            String base64 = pem.replace("\\n", "\n")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            // 키 내용이 로그에 남지 않도록 원인 메시지는 붙이지 않는다
            throw new IllegalStateException("Apple 개인키(parknic.apple.private-key) 형식이 올바르지 않습니다");
        }
    }
}
