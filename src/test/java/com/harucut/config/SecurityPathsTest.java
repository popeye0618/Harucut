package com.harucut.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityPaths")
class SecurityPathsTest {

    @Nested
    @DisplayName("isAuthenticatedOnly — 역할을 보지 않는 경로")
    class IsAuthenticatedOnly {

        @Test
        @DisplayName("탈퇴 요청 상태도 통과해야 하는 두 경로를 알아본다")
        void authenticatedOnlyPaths() {
            assertThat(SecurityPaths.isAuthenticatedOnly("/api/auth/status")).isTrue();
            assertThat(SecurityPaths.isAuthenticatedOnly("/api/harucut/reactivate")).isTrue();
        }

        @Test
        @DisplayName("나머지 경로는 역할을 본다")
        void othersRequireRole() {
            assertThat(SecurityPaths.isAuthenticatedOnly("/api/auth/user/info")).isFalse();
            assertThat(SecurityPaths.isAuthenticatedOnly("/api/harucut/exit")).isFalse();
        }
    }

    @Nested
    @DisplayName("isPublic — OpenAPI 경로 템플릿이 공개 경로인지")
    class IsPublic {

        @Test
        @DisplayName("정확히 일치하는 공개 경로를 찾는다")
        void exactMatch() {
            assertThat(SecurityPaths.isPublic("/api/harucut/login")).isTrue();
            assertThat(SecurityPaths.isPublic("/api/terms")).isTrue();
        }

        @Test
        @DisplayName("경로 변수 자리는 한 세그먼트로 취급되어 ** 패턴에 걸린다")
        void pathVariableMatchesWildcard() {
            assertThat(SecurityPaths.isPublic("/api/notices/{publicId}")).isTrue();
        }

        @Test
        @DisplayName("인증이 필요한 경로는 공개가 아니다")
        void securedPaths() {
            assertThat(SecurityPaths.isPublic("/api/auth/user/frame/{frameId}")).isFalse();
            assertThat(SecurityPaths.isPublic("/api/admin/notices")).isFalse();
            assertThat(SecurityPaths.isPublic("/api/auth/status")).isFalse();
        }

        @Test
        @DisplayName("공개 경로의 접두어만 같은 다른 경로는 걸리지 않는다")
        void prefixOnlyIsNotPublic() {
            // /api/terms 는 공개지만 동의 API 는 아니다
            assertThat(SecurityPaths.isPublic("/api/auth/terms/consents")).isFalse();
        }
    }
}
