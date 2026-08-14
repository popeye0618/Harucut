package com.harucut.auth.oauth2;

import com.harucut.user.enums.Provider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class ProviderUserTest {

    @Nested
    @DisplayName("제공자별 매핑")
    class ProviderMapping {

        @Test
        @DisplayName("구글은 sub/email/name을 읽는다")
        void google() {
            ProviderUser user = ProviderUser.from(Provider.GOOGLE,
                    googleAttributes("google-sub-1", "user@gmail.com", "하루컷"));

            assertThat(user).isEqualTo(
                    new ProviderUser(Provider.GOOGLE, "google-sub-1", "user@gmail.com", "하루컷")
            );
        }

        @Test
        @DisplayName("카카오는 sub/email/nickname을 읽는다")
        void kakao() {
            ProviderUser user = ProviderUser.from(Provider.KAKAO,
                    kakaoAttributes("kakao-sub-1", "user@kakao.com", "하루컷"));

            assertThat(user).isEqualTo(
                    new ProviderUser(Provider.KAKAO, "kakao-sub-1", "user@kakao.com", "하루컷"));
        }

        @Test
        @DisplayName("네이버는 response 안에서 id/email/nickname을 읽는다")
        void naver() {
            ProviderUser user = ProviderUser.from(Provider.NAVER,
                    naverAttributes("naver-id-1", "user@naver.com", "하루컷"));

            assertThat(user).isEqualTo(
                    new ProviderUser(Provider.NAVER, "naver-id-1", "user@naver.com", "하루컷"));
        }
    }

    @Nested
    @DisplayName("값이 없거나 형태가 다를 때")
    class MissingOrMalformed {

        @Test
        @DisplayName("email이 없으면 null이 담긴다 — 거부는 SocialLoginService의 몫이다")
        void missingEmail() {
            ProviderUser user = ProviderUser.from(Provider.KAKAO,
                    kakaoAttributes("kakao-sub-1", null, "하루컷"));

            assertThat(user.email()).isNull();
        }

        @Test
        @DisplayName("providerId가 없어도 예외를 던지지 않는다 — 판정은 서비스에서 한다")
        void missingProviderId() {
            ProviderUser user = ProviderUser.from(Provider.GOOGLE,
                    googleAttributes(null, "user@gmail.com", "하루컷"));

            assertThat(user.providerId()).isNull();
        }

        @Test
        @DisplayName("email과 닉네임이 둘 다 없으면 username도 null이다")
        void missingEmailAndNickname() {
            ProviderUser user = ProviderUser.from(Provider.KAKAO,
                    kakaoAttributes("kakao-sub-1", null, null));

            assertThat(user.username()).isNull();
        }

        // response 키가 아예 없는 경우도 같은 분기를 탄다 (instanceof 실패 → 빈 Map)
        @Test
        @DisplayName("네이버 response가 Map이 아니면 터지지 않고 전부 null이 된다")
        void naverResponseIsNotAMap() {
            ProviderUser user = ProviderUser.from(Provider.NAVER, Map.of("response", "문자열"));

            assertThat(user).isEqualTo(
                    new ProviderUser(Provider.NAVER, null, null, null));
        }
    }

    @Nested
    @DisplayName("닉네임 정규화")
    class UsernameResolution {

        @Test
        @DisplayName("20자면 그대로 둔다")
        void atMaxLength() {
            String nickname = "가".repeat(20);

            assertThat(username(nickname, "user@gmail.com")).isEqualTo(nickname);
        }

        @Test
        @DisplayName("21자면 20자로 자른다")
        void overMaxLength() {
            assertThat(username("가".repeat(21), "user@gmail.com"))
                    .isEqualTo("가".repeat(20));
        }

        @Test
        @DisplayName("null이면 이메일 앞부분을 쓴다")
        void nullNickname() {
            assertThat(username(null, "harucut@gmail.com")).isEqualTo("harucut");
        }

        @Test
        @DisplayName("공백뿐이면 이메일 앞부분을 쓴다")
        void blankNickname() {
            assertThat(username("   ", "harucut@gmail.com")).isEqualTo("harucut");
        }

        @Test
        @DisplayName("대체한 이메일 앞부분이 20자를 넘으면 그것도 자른다")
        void fallbackIsAlsoTruncated() {
            assertThat(username(null, "a".repeat(25) + "@gmail.com"))
                    .isEqualTo("a".repeat(20));
        }

        private String username(String nickname, String email) {
            return new ProviderUser(Provider.GOOGLE, "provider-id", email, nickname).username();
        }
    }

    @Nested
    @DisplayName("지원하지 않는 제공자")
    class UnsupportedProvider {

        @Test
        @DisplayName("APPLE은 아직 지원하지 않아 예외를 던진다")
        void apple() {
            assertThatThrownBy(() -> ProviderUser.from(Provider.APPLE, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("HARUCUT은 소셜 제공자가 아니라 예외를 던진다")
        void harucut() {
            assertThatThrownBy(() -> ProviderUser.from(Provider.HARUCUT, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static Map<String, Object> googleAttributes(String sub, String email, String name) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", sub);
        attributes.put("email", email);
        attributes.put("name", name);
        return attributes;
    }

    private static Map<String, Object> kakaoAttributes(String sub, String email, String nickname) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", sub);
        attributes.put("email", email);
        attributes.put("nickname", nickname);
        return attributes;
    }

    private static Map<String, Object> naverAttributes(String id, String email, String nickname) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("email", email);
        response.put("nickname", nickname);
        return Map.of("response", response);
    }
}