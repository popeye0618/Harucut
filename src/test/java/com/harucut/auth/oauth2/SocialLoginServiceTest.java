package com.harucut.auth.oauth2;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.user.entity.User;
import com.harucut.user.enums.Provider;
import com.harucut.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SocialLoginServiceTest {

    @Mock
    private UserRepository userRepository;

    private SocialLoginService service;

    @BeforeEach
    void setUp() {
        service = new SocialLoginService(userRepository);
    }

    @Nested
    @DisplayName("이미 가입한 사용자")
    class ExistingUser {

        @Test
        @DisplayName("providerId로 찾은 사용자의 publicId를 돌려준다")
        void reusesUser() {
            User existing = kakaoUser();
            given(userRepository.findByProviderAndProviderId(Provider.KAKAO, "kakao-sub-1"))
                    .willReturn(Optional.of(existing));

            AuthenticatedUser result = service.resolve("kakao", kakaoAttributes());

            assertThat(result.publicId()).isEqualTo(existing.getPublicId());
        }

        @Test
        @DisplayName("다시 저장하지 않는다")
        void doesNotSaveAgain() {
            given(userRepository.findByProviderAndProviderId(Provider.KAKAO, "kakao-sub-1"))
                    .willReturn(Optional.of(kakaoUser()));

            service.resolve("kakao", kakaoAttributes());

            then(userRepository).should(never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("처음 로그인하는 사용자")
    class NewUser {

        @Test
        @DisplayName("제공자가 준 값으로 가입시킨다")
        void registersWithProviderValues() {
            givenNoKakaoUser();
            givenSaveReturnsArgument();

            service.resolve("kakao", kakaoAttributes());

            assertThat(savedUser())
                    .extracting(User::getProvider, User::getProviderId, User::getEmail, User::getUsername)
                    .containsExactly(Provider.KAKAO, "kakao-sub-1", "user@kakao.com", "하루컷");
        }

        @Test
        @DisplayName("비밀번호 없이 가입한다")
        void hasNoPassword() {
            givenNoKakaoUser();
            givenSaveReturnsArgument();

            service.resolve("kakao", kakaoAttributes());

            assertThat(savedUser().getPassword()).isNull();
        }

        @Test
        @DisplayName("네이버는 중첩된 response에서 식별자를 꺼내 가입시킨다")
        void naverNestedResponse() {
            given(userRepository.findByProviderAndProviderId(Provider.NAVER, "naver-id-1"))
                    .willReturn(Optional.empty());
            givenSaveReturnsArgument();

            service.resolve("naver", naverAttributes());

            assertThat(savedUser().getProviderId()).isEqualTo("naver-id-1");
        }
    }

    @Nested
    @DisplayName("필수 정보가 없을 때")
    class MissingRequiredInfo {

        @Test
        @DisplayName("providerId가 없으면 OAuth2AuthenticationException을 던진다")
        void missingProviderId() {
            assertThatThrownBy(() -> service.resolve("kakao", attributes(null, "user@kakao.com", "하루컷")))
                    .isInstanceOf(OAuth2AuthenticationException.class)
                    .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                    .isEqualTo("missing_required_user_info");
        }

        @Test
        @DisplayName("DB를 아예 건드리지 않는다")
        void doesNotTouchRepository() {
            assertThatThrownBy(() -> service.resolve("kakao", attributes(null, "user@kakao.com", "하루컷")))
                    .isInstanceOf(OAuth2AuthenticationException.class);

            then(userRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("동시 가입")
    class ConcurrentRegistration {

        @Test
        @DisplayName("제약 위반을 OAuth2AuthenticationException으로 바꾼다")
        void translatesConstraintViolation() {
            givenNoKakaoUser();
            given(userRepository.save(any(User.class)))
                    .willThrow(new DataIntegrityViolationException("uk_users_provider_provider_id"));

            assertThatThrownBy(() -> service.resolve("kakao", kakaoAttributes()))
                    .isInstanceOf(OAuth2AuthenticationException.class)
                    .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                    .isEqualTo("concurrent_registration");
        }
    }

    @Nested
    @DisplayName("등록되지 않은 제공자")
    class UnknownProvider {

        @Test
        @DisplayName("모르는 registrationId면 예외를 던진다")
        void unknownRegistrationId() {
            assertThatThrownBy(() -> service.resolve("line", kakaoAttributes()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private void givenNoKakaoUser() {
        given(userRepository.findByProviderAndProviderId(Provider.KAKAO, "kakao-sub-1"))
                .willReturn(Optional.empty());
    }

    private void givenSaveReturnsArgument() {
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(captor.capture());
        return captor.getValue();
    }

    private static User kakaoUser() {
        return User.socialUser(Provider.KAKAO, "kakao-sub-1", "user@kakao.com", "하루컷");
    }

    private static Map<String, Object> kakaoAttributes() {
        return attributes("kakao-sub-1", "user@kakao.com", "하루컷");
    }

    private static Map<String, Object> attributes(String sub, String email, String nickname) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", sub);
        attributes.put("email", email);
        attributes.put("nickname", nickname);
        return attributes;
    }

    private static Map<String, Object> naverAttributes() {
        Map<String, Object> response = new HashMap<>();
        response.put("id", "naver-id-1");
        response.put("email", "user@naver.com");
        response.put("nickname", "하루컷");
        return Map.of("response", response);
    }
}