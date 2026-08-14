package com.harucut.auth.oauth2;

import com.harucut.auth.security.AuthenticatedUser;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Map;

public class CustomOidcUser extends CustomOAuth2User implements OidcUser {

    private final OidcUser oidcDelegate;

    public CustomOidcUser(OidcUser delegate, AuthenticatedUser authenticatedUser) {
        super(delegate, authenticatedUser);
        this.oidcDelegate = delegate;
    }

    @Override
    public Map<String, Object> getClaims() {
        return oidcDelegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return oidcDelegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return oidcDelegate.getIdToken();
    }
}
