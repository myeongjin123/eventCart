package com.eventcart.auth.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret              HMAC-SHA 서명 키 (Base64, 디코딩 후 32바이트 이상)
 * @param accessTokenValidity 액세스 토큰 유효 기간
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration accessTokenValidity) {
}
