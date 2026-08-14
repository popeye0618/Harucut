package com.harucut.auth.oauth2;

import com.harucut.auth.security.AuthenticatedUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CustomOAuth2User implements OAuth2User {

    private final OAuth2User delegate;
    private final AuthenticatedUser authenticatedUser;

    public CustomOAuth2User(OAuth2User delegate, AuthenticatedUser authenticatedUser) {
        this.delegate = delegate;
        this.authenticatedUser = authenticatedUser;
    }

    public AuthenticatedUser authenticatedUser() {
        return authenticatedUser;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authenticatedUser.authorities();
    }

    @Override
    public String getName() {
        return authenticatedUser.publicId();
    }
}
