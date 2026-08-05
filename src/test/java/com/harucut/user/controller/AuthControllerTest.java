package com.harucut.user.controller;

import com.harucut.config.SecurityConfig;
import com.harucut.user.dto.RegisterRequest;
import com.harucut.user.service.RegisterService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private RegisterService registerService;

    @Nested
    @DisplayName("POST /api/harucut/register")
    class Register {

        @Test
        @DisplayName("정상 요청은 200 GEN-000을 반환한다")
        void success() {
            assertThat(post(body("user@harucut.com", "하루컷", "password123")))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-000"));

            then(registerService).should().register(any(RegisterRequest.class));
        }

        @Test
        @DisplayName("비밀번호가 7자면 GEN-003이고 서비스가 호출되지 않는다")
        void passwordTooShort() {
            assertThat(post(body("user@harucut.com", "하루컷", "pass123")))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-003"))
                    .hasPathSatisfying("$.data[0].field", f -> assertThat(f).isEqualTo("password"));

            then(registerService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("비밀번호가 21자면 GEN-003이다")
        void passwordTooLong() {
            assertThat(post(body("user@harucut.com", "하루컷", "a".repeat(21))))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-003"));

            then(registerService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("비밀번호가 8자면 통과한다")
        void passwordAtMinBoundary() {
            assertThat(post(body("user@harucut.com", "하루컷", "12345678")))
                    .hasStatusOk();
        }

        @Test
        @DisplayName("이메일 형식이 아니면 GEN-003이다")
        void invalidEmail() {
            assertThat(post(body("not-an-email", "하루컷", "password123")))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-003"))
                    .hasPathSatisfying("$.data[0].field", f -> assertThat(f).isEqualTo("email"));

            then(registerService).shouldHaveNoInteractions();
        }
    }

    private MockMvcTester.MockMvcRequestBuilder post(String json) {
        return mockMvc.post().uri("/api/harucut/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private String body(String email, String username, String password) {
        return """
                {"email":"%s","username":"%s","password":"%s"}
                """.formatted(email, username, password);
    }
}