package com.harucut.auth.security;

import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.auth.exception.CustomAuthenticationException;
import com.harucut.user.enums.Provider;
import com.harucut.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByProviderAndEmail(Provider.HARUCUT, email)
                .map(CustomUserPrincipal::new)
                .orElseThrow(() -> new CustomAuthenticationException(AuthErrorCode.USER_NOT_FOUND));
    }

    public CustomUserPrincipal loadUserByPublicId(String publicId) {
        return userRepository.findByPublicId(publicId)
                .map(CustomUserPrincipal::new)
                .orElseThrow(() -> new CustomAuthenticationException(AuthErrorCode.USER_NOT_FOUND));
    }
}
