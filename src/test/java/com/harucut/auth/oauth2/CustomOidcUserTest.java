package com.harucut.auth.oauth2;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomOidcUser")
class CustomOidcUserTest {

    /*
     * OidcUserService 는 반환 타입이 OidcUser 여야 한다.
     * 이게 깨지면 구글·카카오 로그인이 통째로 실패한다.
     */
    @Test
    @DisplayName("OidcUser로 인식된다")
    void isOidcUser() {
        assertThat(principal()).isInstanceOf(OidcUser.class);
    }

    @Test
    @DisplayName("ID 토큰을 위임체에서 그대로 꺼내준다")
    void idTokenIsDelegated() {
        assertThat(principal().getIdToken().getTokenValue()).isEqualTo("id-token-value");
    }

    @Test
    @DisplayName("클레임도 위임체 것을 그대로 넘긴다")
    void claimsAreDelegated() {
        assertThat(principal().getClaims())
                .containsEntry(IdTokenClaimNames.SUB, "provider-sub-1");
    }

    @Test
    @DisplayName("getName()은 여기서도 제공자 sub가 아니라 publicId다")
    void nameIsPublicId() {
        assertThat(principal().getName()).isEqualTo("public-id-1");
    }

    @Test
    @DisplayName("권한은 위임체가 아니라 AuthenticatedUser에서 온다")
    void authoritiesComeFromOurUser() {
        assertThat(principal().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    private CustomOidcUser principal() {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token-value")
                .claim(IdTokenClaimNames.SUB, "provider-sub-1")
                .build();

        OidcUser delegate = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_DELEGATE_ONLY")), idToken);

        return new CustomOidcUser(delegate,
                new AuthenticatedUser("public-id-1", UserRole.ROLE_USER, UserStatus.ACTIVE));
    }
}
