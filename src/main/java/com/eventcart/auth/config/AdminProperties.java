package com.eventcart.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 앱 시작 시 만들 관리자 계정. email이 비어 있으면 만들지 않는다. */
@ConfigurationProperties("app.admin")
public record AdminProperties(String email, String password) {

    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }

    @Override
    public String toString() {
        return "AdminProperties[email=" + email + "]";
    }
}
