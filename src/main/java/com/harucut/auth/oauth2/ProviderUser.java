package com.harucut.auth.oauth2;

import com.harucut.user.enums.Provider;

import java.util.Map;

public record ProviderUser(
        Provider provider,
        String providerId,
        String email,
        String username
) {
    private static final int MAX_USERNAME_LENGTH = 20;

    public ProviderUser {
        username = resolveUsername(username, email);
    }

    public static ProviderUser from(Provider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case GOOGLE -> new ProviderUser(provider,
                    string(attributes, "sub"),
                    string(attributes, "email"),
                    string(attributes, "name"));

            case KAKAO -> new ProviderUser(provider,
                    string(attributes, "sub"),
                    string(attributes, "email"),
                    string(attributes, "nickname"));

            case NAVER -> {
                Map<?, ?> response = nested(attributes, "response");
                yield new ProviderUser(provider,
                        string(response, "id"),
                        string(response, "email"),
                        string(response, "nickname"));
            }

            case APPLE -> throw new IllegalArgumentException("APPLE 로그인은 아직 지원하지 않는다");
            case HARUCUT -> throw new IllegalArgumentException("HARUCUT은 소셜 제공자가 아니다");
        };
    }

    private static String resolveUsername(String nickname, String email) {
        String resolved = (nickname != null && !nickname.isBlank())
                ? nickname
                : localPart(email);

        if (resolved == null) {
            return null;
        }

        return resolved.length() <= MAX_USERNAME_LENGTH
                ? resolved
                : resolved.substring(0, MAX_USERNAME_LENGTH);
    }

    private static String localPart(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static String string(Map<?, ?> attributes, String key) {
        return attributes.get(key) instanceof String value ? value : null;
    }

    private static Map<?, ?> nested(Map<String, Object> attributes, String key) {
        return attributes.get(key) instanceof Map<?, ?> nested ? nested : Map.of();
    }
}
