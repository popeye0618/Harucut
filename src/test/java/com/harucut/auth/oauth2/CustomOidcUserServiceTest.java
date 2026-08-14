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
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomOidcUserService")
class CustomOidcUserServiceTest {

    private static final String PUBLIC_ID = "abc123456789";
    private static final Instant ISSUED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-01-01T01:00:00Z");
    private static final Map<String, Object> CLAIMS = Map.of(
            IdTokenClaimNames.SUB, "kakao-sub-1",
            "email", "user@kakao.com",
            "nickname", "하루컷");

    @Mock
    private SocialLoginService socialLoginService;

    @Mock
    private OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    private CustomOidcUserService service;
    private OidcUserRequest request;

    @BeforeEach
    void setUp() {
        service = new CustomOidcUserService(socialLoginService);
        ReflectionTestUtils.setField(service, "delegate", delegate);
        request = new OidcUserRequest(clientRegistration(), accessToken(), idToken());
    }

    /*
     * 이 테스트가 없어서 버그가 났었다. resolve() 를 호출만 하고 반환값을 버린 채
     * CustomOidcUser(oidcUser, null) 을 돌려주면, Spring 이 곧바로 getAuthorities() 를 불러
     * NPE 가 난다. 구글·카카오가 OIDC 이므로 소셜 로그인 주 경로가 통째로 500 이 된다.
     */
    @Test
    @DisplayName("resolve가 돌려준 사용자가 실제로 principal에 실린다")
    void attachesResolvedUser() {
        givenDelegateReturnsOidcUser();
        givenResolved();

        OidcUser result = service.loadUser(request);

        assertThat(result.getName()).isEqualTo(PUBLIC_ID);
    }

    @Test
    @DisplayName("권한은 제공자가 아니라 우리 DB의 role·status에서 온다")
    void authoritiesComeFromOurUser() {
        givenDelegateReturnsOidcUser();
        givenResolved();

        OidcUser result = service.loadUser(request);

        assertThat(result.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(UserRole.ROLE_USER.name());
    }

    @Test
    @DisplayName("registrationId와 제공자 속성을 그대로 넘긴다")
    void passesRegistrationIdAndAttributes() {
        givenDelegateReturnsOidcUser();
        givenResolved();

        service.loadUser(request);

        then(socialLoginService).should().resolve("kakao", CLAIMS);
    }

    /*
     * OidcUserService 자리에 꽂히므로 반환 타입이 OidcUser 여야 한다.
     * OAuth2User 만 만족하는 객체를 돌려주면 Spring 이 ClassCastException 을 낸다.
     */
    @Test
    @DisplayName("OidcUser를 돌려준다")
    void returnsOidcUser() {
        givenDelegateReturnsOidcUser();
        givenResolved();

        assertThat(service.loadUser(request)).isInstanceOf(CustomOidcUser.class);
    }

    @Test
    @DisplayName("ID 토큰은 위임체 것을 그대로 들고 간다")
    void keepsIdToken() {
        givenDelegateReturnsOidcUser();
        givenResolved();

        OidcUser result = service.loadUser(request);

        assertThat(result.getIdToken().getTokenValue()).isEqualTo("id-token-value");
    }

    private void givenDelegateReturnsOidcUser() {
        given(delegate.loadUser(request)).willReturn(
                new DefaultOidcUser(List.of(new SimpleGrantedAuthority("PROVIDER")), idToken()));
    }

    private void givenResolved() {
        given(socialLoginService.resolve("kakao", CLAIMS))
                .willReturn(new AuthenticatedUser(PUBLIC_ID, UserRole.ROLE_USER, UserStatus.ACTIVE));
    }

    private static OidcIdToken idToken() {
        return new OidcIdToken("id-token-value", ISSUED_AT, EXPIRES_AT, CLAIMS);
    }

    private static OAuth2AccessToken accessToken() {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", ISSUED_AT, EXPIRES_AT);
    }

    private static ClientRegistration clientRegistration() {
        return ClientRegistration.withRegistrationId("kakao")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/kakao")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kauth.kakao.com/oidc/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .scope("openid")
                .build();
    }

}
