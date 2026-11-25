package com.eventcart.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventcart.auth.jwt.JwtTokenProvider;
import com.eventcart.member.domain.Role;
import com.eventcart.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 실제 HTTP 요청처럼 필터 체인을 통과시켜 인증·인가 규칙을 검증한다. */
@IntegrationTest
class AuthApiTest {

    private static final String ADMIN_EMAIL = "admin@test.com";   // src/test/resources/application.yml
    private static final String ADMIN_PASSWORD = "admin1234!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Test
    void 가입하고_로그인하면_토큰으로_내_장바구니를_볼_수_있다() throws Exception {
        String email = signUp("password123");

        String token = login(email, "password123");

        mockMvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void 가입하면_USER_권한이고_응답에_비밀번호가_없다() throws Exception {
        mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", uniqueEmail(), "name", "홍길동", "password", "password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void 토큰_없이_장바구니에_접근하면_401() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 위조된_토큰은_401() throws Exception {
        String token = tokenProvider.createAccessToken(1L, Role.USER);
        int signatureStart = token.lastIndexOf('.') + 1;
        char flipped = token.charAt(signatureStart) == 'A' ? 'B' : 'A'; // 서명 첫 글자만 바꿔치기
        String tampered = token.substring(0, signatureStart) + flipped + token.substring(signatureStart + 1);

        mockMvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 비밀번호가_틀리면_401_INVALID_CREDENTIALS() throws Exception {
        String email = signUp("password123");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 없는_이메일도_같은_에러를_준다() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", uniqueEmail(), "password", "password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 일반_회원은_상품을_등록할_수_없다_403() throws Exception {
        String token = login(signUp("password123"), "password123");

        mockMvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "에어팟", "price", 300000))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 관리자는_상품을_등록할_수_있다() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "에어팟", "price", 300000))))
                .andExpect(status().isCreated());
    }

    @Test
    void 이벤트_목록은_로그인_없이_볼_수_있다() throws Exception {
        mockMvc.perform(get("/api/events/ongoing"))
                .andExpect(status().isOk());
    }

    private String signUp(String password) throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "name", "tester", "password", password))))
                .andExpect(status().isCreated());
        return email;
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = body.get("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@test.com";
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
