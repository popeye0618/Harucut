package com.harucut.storage.controller;

import com.harucut.auth.cookie.CookieManager;
import com.harucut.auth.security.CustomAccessDeniedHandler;
import com.harucut.auth.security.CustomAuthenticationEntryPoint;
import com.harucut.auth.service.JwtTokenService;
import com.harucut.config.SecurityConfig;
import com.harucut.storage.dto.PresignedUploadResponse;
import com.harucut.storage.enums.ContentType;
import com.harucut.storage.enums.UploadType;
import com.harucut.storage.service.FileStorageService;
import com.harucut.support.FixedClockConfig;
import com.harucut.support.SecurityBeansMockSupport;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(FileController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtTokenService.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("FileController")
class FileControllerTest extends SecurityBeansMockSupport {

    private static final String UPLOAD_URI = "/api/auth/user/files/presigned-upload";
    private static final String IMG_URI = "/api/auth/user/files/presigned-img";
    private static final String DELETE_URI = "/api/auth/user/files/delete";

    private static final String PUBLIC_ID = "AbCdEf12Gh";
    private static final String OTHER_PUBLIC_ID = "ZzYyXx98Ww";
    private static final String MY_KEY = "uploads/users/" + PUBLIC_ID + "/profile/a1b2c3.png";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private FileStorageService fileStorageService;

    @Nested
    @DisplayName("POST /api/auth/user/files/presigned-upload")
    class PresignedUpload {

        @Test
        @DisplayName("정상 요청은 200이고 expiresIn이 PT24H 문자열로 직렬화된다")
        void success() {
            given(fileStorageService.generatePresignedUploadUrl(
                    UploadType.PROFILE, "profile.png", ContentType.PNG, 123456L, PUBLIC_ID))
                    .willReturn(new PresignedUploadResponse(
                            MY_KEY, "https://upload.example", "image/png", Duration.ofHours(24)));

            assertThat(post("""
                    {"type":"PROFILE","filename":"profile.png","contentType":"PNG","fileSize":123456}"""))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.data.key", k -> assertThat(k).isEqualTo(MY_KEY))
                    .hasPathSatisfying("$.data.uploadUrl", u -> assertThat(u).isEqualTo("https://upload.example"))
                    .hasPathSatisfying("$.data.contentType", c -> assertThat(c).isEqualTo("image/png"))
                    .hasPathSatisfying("$.data.expiresIn", e -> assertThat(e).isEqualTo("PT24H"));
        }

        @Test
        @DisplayName("fileSize가 정확히 10MB면 통과한다 — 상한 경계")
        void fileSizeAtMax() {
            given(fileStorageService.generatePresignedUploadUrl(
                    UploadType.PROFILE, "profile.png", ContentType.PNG, 10_485_760L, PUBLIC_ID))
                    .willReturn(new PresignedUploadResponse(
                            MY_KEY, "https://upload.example", "image/png", Duration.ofHours(24)));

            assertThat(post("""
                    {"type":"PROFILE","filename":"profile.png","contentType":"PNG","fileSize":10485760}"""))
                    .hasStatusOk();
        }

        @Test
        @DisplayName("fileSize가 10MB + 1바이트면 GEN-003이고 서비스가 호출되지 않는다")
        void fileSizeJustOverMax() {
            assertThat(post("""
                    {"type":"PROFILE","filename":"profile.png","contentType":"PNG","fileSize":10485761}"""))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-003"))
                    .hasPathSatisfying("$.data[0].field", f -> assertThat(f).isEqualTo("fileSize"));

            then(fileStorageService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("fileSize가 0이면 GEN-003이고 서비스가 호출되지 않는다")
        void zeroFileSize() {
            assertThat(post("""
                    {"type":"PROFILE","filename":"profile.png","contentType":"PNG","fileSize":0}"""))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-003"));

            then(fileStorageService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("fileSize가 없으면 GEN-003이고 서비스가 호출되지 않는다")
        void missingFileSize() {
            assertThat(post("""
                    {"type":"PROFILE","filename":"profile.png","contentType":"PNG"}"""))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-003"))
                    .hasPathSatisfying("$.data[0].field", f -> assertThat(f).isEqualTo("fileSize"));

            then(fileStorageService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("filename이 없으면 GEN-003이고 서비스가 호출되지 않는다")
        void missingFilename() {
            assertThat(post("""
                    {"type":"PROFILE","contentType":"PNG","fileSize":123456}"""))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-003"))
                    .hasPathSatisfying("$.data[0].field", f -> assertThat(f).isEqualTo("filename"));

            then(fileStorageService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("contentType이 없으면 GEN-006이 아니라 GEN-003 + 필드명이다 — Kotlin과 다른 지점")
        void missingContentType() {
            assertThat(post("""
                    {"type":"PROFILE","filename":"profile.png","fileSize":123456}"""))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-003"))
                    .hasPathSatisfying("$.data[0].field", f -> assertThat(f).isEqualTo("contentType"));

            then(fileStorageService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("제거된 FOURCUT_PHOTO 타입을 보내면 GEN-006이고 서비스가 호출되지 않는다")
        void removedFourcutType() {
            assertThat(post("""
                    {"type":"FOURCUT_PHOTO","filename":"result.png","contentType":"PNG","fileSize":123456}"""))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-006"));

            then(fileStorageService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("토큰이 없으면 401 AUTH-010이고 서비스가 호출되지 않는다")
        void unauthenticated() {
            assertThat(mockMvc.post().uri(UPLOAD_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"type":"PROFILE","filename":"profile.png","contentType":"PNG","fileSize":123456}"""))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("AUTH-010"));

            then(fileStorageService).shouldHaveNoInteractions();
        }

        private MockMvcTester.MockMvcRequestBuilder post(String json) {
            return mockMvc.post().uri(UPLOAD_URI)
                    .cookie(accessCookie())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json);
        }
    }

    @Nested
    @DisplayName("GET /api/auth/user/files/presigned-img")
    class PresignedImg {

        @Test
        @DisplayName("내 prefix의 key면 200과 조회 URL을 반환한다")
        void ownKey() {
            given(fileStorageService.generatePresignedGetUrl(MY_KEY)).willReturn("https://signed.example");

            assertThat(mockMvc.get().uri(IMG_URI).param("key", MY_KEY).cookie(accessCookie()))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.data", d -> assertThat(d).isEqualTo("https://signed.example"));
        }

        @Test
        @DisplayName("다른 사용자 prefix의 key면 403 GEN-021이고 서비스가 호출되지 않는다")
        void foreignKey() {
            String foreignKey = "uploads/users/" + OTHER_PUBLIC_ID + "/profile/a.png";

            assertThat(mockMvc.get().uri(IMG_URI).param("key", foreignKey).cookie(accessCookie()))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-021"));

            then(fileStorageService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("key 파라미터가 없으면 GEN-004이고 서비스가 호출되지 않는다")
        void missingKeyParam() {
            assertThat(mockMvc.get().uri(IMG_URI).cookie(accessCookie()))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-004"));

            then(fileStorageService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("토큰이 없으면 401 AUTH-010이고 서비스가 호출되지 않는다")
        void unauthenticated() {
            assertThat(mockMvc.get().uri(IMG_URI).param("key", MY_KEY))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("AUTH-010"));

            then(fileStorageService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("DELETE /api/auth/user/files/delete — 제거된 엔드포인트")
    class RemovedDeleteEndpoint {

        @Test
        @DisplayName("범용 삭제 API는 존재하지 않는다 — 404 GEN-031")
        void deleteEndpointGone() {
            assertThat(mockMvc.delete().uri(DELETE_URI).param("key", MY_KEY).cookie(accessCookie()))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-031"));

            then(fileStorageService).shouldHaveNoInteractions();
        }
    }

    private Cookie accessCookie() {
        return new Cookie(CookieManager.ACCESS_TOKEN, jwtTokenService
                .createAccessToken(PUBLIC_ID, UserRole.ROLE_USER, UserStatus.ACTIVE).value());
    }
}
