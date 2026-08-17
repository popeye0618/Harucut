package com.harucut.media.dto;

import com.harucut.media.entity.UserMedia;

import java.time.LocalDateTime;

public record UserMediaResponse(
        Long mediaId,
        String s3Key,
        String displayName,
        String downloadUrl,
        LocalDateTime createdAt
) {

    public static UserMediaResponse of(UserMedia media, String downloadUrl) {
        return new UserMediaResponse(media.getId(), media.getS3Key(),
                media.getDisplayName(), downloadUrl, media.getCreatedAt());
    }
}
