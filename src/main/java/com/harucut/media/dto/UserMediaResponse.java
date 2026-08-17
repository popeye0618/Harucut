package com.harucut.media.dto;

import com.harucut.media.entity.UserMedia;

import java.time.LocalDateTime;

public record UserMediaResponse(
        Long mediaId,
        String s3Key,
        String displayName,
        String thumbnailUrl,
        String viewUrl,
        String downloadUrl,
        LocalDateTime createdAt
) {

    public static UserMediaResponse of(UserMedia media, String thumbnailUrl,
                                       String viewUrl, String downloadUrl) {
        return new UserMediaResponse(media.getId(), media.getS3Key(),
                media.getDisplayName(), thumbnailUrl, viewUrl, downloadUrl, media.getCreatedAt());
    }
}
