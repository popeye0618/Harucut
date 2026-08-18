package com.harucut.auth.dto;

public record NaverUnlinkRequest(
        String clientId,
        String encryptUniqueId,
        String timestamp,
        String signature
) {
}
