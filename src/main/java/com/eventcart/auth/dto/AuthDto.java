package com.eventcart.auth.dto;

import jakarta.validation.constraints.NotBlank;

public final class AuthDto {

    private AuthDto() {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + "]";
        }
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

        public static TokenResponse bearer(String accessToken, long expiresIn) {
            return new TokenResponse(accessToken, "Bearer", expiresIn);
        }
    }
}
