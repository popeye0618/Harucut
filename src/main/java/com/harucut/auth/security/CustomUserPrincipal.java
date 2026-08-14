package com.harucut.auth.security;

import com.harucut.user.entity.User;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

@Getter
public class CustomUserPrincipal implements UserDetails {

    private final Long id;
    private final String publicId;
    private final String email;
    private final String password;
    private final UserRole userRole;
    private final UserStatus userStatus;

    public CustomUserPrincipal(User user) {
        this.id = user.getId();
        this.publicId = user.getPublicId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.userRole = user.getUserRole();
        this.userStatus = user.getUserStatus();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return new AuthenticatedUser(publicId, userRole, userStatus).authorities();
    }

    @Override
    public String getUsername() {
        return email;
    }
}
