package com.eventcart.auth.config;

import com.eventcart.auth.jwt.JwtAuthenticationFilter;
import com.eventcart.auth.jwt.JwtTokenProvider;
import com.eventcart.common.exception.ErrorCode;
import com.eventcart.common.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider tokenProvider,
                                                   ObjectMapper objectMapper) throws Exception {
        http
                // 토큰 방식이라 세션·CSRF·폼 로그인이 필요 없다
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // 누구나: 회원가입, 로그인, 상품·이벤트 조회
                        .requestMatchers(HttpMethod.POST, "/api/members", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/events/**").permitAll()
                        // 관리자만: 상품 등록, 이벤트 생성, 이벤트 상품 등록
                        .requestMatchers(HttpMethod.POST, "/api/products", "/api/events", "/api/events/*/products")
                        .hasRole("ADMIN")
                        .requestMatchers("/error").permitAll()
                        // 나머지(장바구니, 주문)는 로그인 필요
                        .anyRequest().authenticated())

                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, objectMapper, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, objectMapper, ErrorCode.FORBIDDEN)))

                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 401·403도 다른 API 에러와 같은 {code, message} 형식으로 응답한다. */
    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode code)
            throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code, code.getMessage()));
    }
}
