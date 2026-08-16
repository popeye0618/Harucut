package com.harucut.storage.dto;

import java.time.Duration;

public record PresignedUploadResponse(
        String key,
        String uploadUrl,
        String contentType,
        Duration expiresIn
) {
}
