package com.harucut.user.dto;

import com.harucut.user.entity.User;

public record UserInfoResponse(
        String id, // publicId
        String email,
        String username,
        String profileUrl,
        String loginPlatform,
        String planTier,
        int monthlyPrice
) {
    public static UserInfoResponse from(User user, String planTier, int monthlyPrice) {
        return new UserInfoResponse(
                user.getPublicId(),
                user.getEmail(),
                user.getUsername(),
                user.getProfileImageUrl(),
                user.getProvider().name(),
                planTier,
                monthlyPrice
        );
    }
}
