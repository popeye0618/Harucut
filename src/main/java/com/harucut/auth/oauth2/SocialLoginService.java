package com.harucut.auth.oauth2;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.user.entity.User;
import com.harucut.user.enums.Provider;
import com.harucut.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private static final String MISSING_USER_INFO = "missing_required_user_info";
    private static final String CONCURRENT_REGISTRATION = "concurrent_registration";

    private final UserRepository userRepository;

    @Transactional
    public AuthenticatedUser resolve(String registrationId, Map<String, Object> attributes) {
        Provider provider = Provider.from(registrationId);
        ProviderUser providerUser = ProviderUser.from(provider, attributes);

        requireIdentity(providerUser);

        User user = userRepository.findByProviderAndProviderId(provider, providerUser.providerId())
                .orElseGet(() -> register(providerUser));

        return new AuthenticatedUser(user.getPublicId(), user.getUserRole(), user.getUserStatus());
    }

    private void requireIdentity(ProviderUser providerUser) {
        if (providerUser.providerId() == null) {
            throw missing(providerUser, "제공자 식별자(providerId)가 없다");
        }
    }

    private User register(ProviderUser providerUser) {
        User user = User.socialUser(providerUser.provider(), providerUser.providerId(), providerUser.email(), providerUser.username());

        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("[social-login] 가입 중 제약 위반. provider={}, providerId={}",
                    providerUser.provider(), providerUser.providerId(), e);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(CONCURRENT_REGISTRATION), e.getMessage(), e
            );
        }
    }

    private OAuth2AuthenticationException missing(ProviderUser providerUser, String reason) {
        log.warn("[social-login] 필수 정보 누락. provider={}, providerId={}, reason={}",
                providerUser.provider(), providerUser.providerId(), reason);
        return new OAuth2AuthenticationException(new OAuth2Error(MISSING_USER_INFO), reason);
    }
}
