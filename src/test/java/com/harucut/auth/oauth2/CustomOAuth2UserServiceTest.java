package com.harucut.auth.oauth2;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomOAuth2UserService")
class CustomOAuth2UserServiceTest {

    private static final String PUBLIC_ID = "abc123456789";
    private static final Instant ISSUED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-01-01T01:00:00Z");

    /* 네이버는 사용자 정보가 response 안에 한 겹 들어 있다. */
    private static final Map<String, Object> ATTRIBUTES = Map.of(
            "resultcode", "00",
            "response", Map.of(
                    "id", "naver-id-1",
                    "email", "user@naver.com",
                    "nickname", "하루컷"));

    @Mock
    private SocialLoginService socialLoginService;

    @Mock
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    private CustomOAuth2UserService service;
    private OAuth2UserRequest request;

    @BeforeEach
    void setUp() {
        service = new CustomOAuth2UserService(socialLoginService);
        ReflectionTestUtils.setField(service, "delegate", delegate);
        request = new OAuth2UserRequest(clientRegistration(), accessToken());
    }

    @Test
    @DisplayName("resolve가 돌려준 사용자가 실제로 principal에 실린다")
    void attachesResolvedUser() {
        givenDelegateReturnsUser();
        givenResolved();

        OAuth2User result = service.loadUser(request);

        assertThat(result.getName()).isEqualTo(PUBLIC_ID);
    }

    @Test
    @DisplayName("권한은 제공자가 아니라 우리 DB의 role·status에서 온다")
    void authoritiesComeFromOurUser() {
        givenDelegateReturnsUser();
        givenResolved();

        OAuth2User result = service.loadUser(request);

        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(UserRole.ROLE_USER.name());
    }

    @Test
    @DisplayName("registrationId와 중첩된 제공자 속성을 그대로 넘긴다")
    void passesRegistrationIdAndAttributes() {
        givenDelegateReturnsUser();
        givenResolved();

        service.loadUser(request);

        then(socialLoginService).should().resolve("naver", ATTRIBUTES);
    }

    /*
     * 네이버는 ID 토큰을 주지 않는다. OidcUser 로 보이면 없는 ID 토큰을 꺼내려는 코드가 붙을 수 있다.
     */
    @Test
    @DisplayName("OidcUser가 아니다")
    void isNotOidcUser() {
        givenDelegateReturnsUser();
        givenResolved();

        assertThat(service.loadUser(request)).isNotInstanceOf(OidcUser.class);
    }

    private void givenDelegateReturnsUser() {
        given(delegate.loadUser(request)).willReturn(new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("PROVIDER")), ATTRIBUTES, "response"));
    }

    private void givenResolved() {
        given(socialLoginService.resolve("naver", ATTRIBUTES))
                .willReturn(new AuthenticatedUser(PUBLIC_ID, UserRole.ROLE_USER, UserStatus.ACTIVE));
    }

    private static OAuth2AccessToken accessToken() {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", ISSUED_AT, EXPIRES_AT);
    }

    private static ClientRegistration clientRegistration() {
        return ClientRegistration.withRegistrationId("naver")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/naver")
                .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                .tokenUri("https://nid.naver.com/oauth2.0/token")
                .userInfoUri("https://openapi.naver.com/v1/nid/me")
                .userNameAttributeName("response")
                .scope("email")
                .build();
    }
}
