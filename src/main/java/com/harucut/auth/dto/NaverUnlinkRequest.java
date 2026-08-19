package com.harucut.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "네이버 연동 해제 웹훅 본문. **네이버 서버가 보낸다 — 프론트가 만들 요청이 아니다**")
public record NaverUnlinkRequest(

        @Schema(description = "네이버 애플리케이션 클라이언트 ID")
        String clientId,

        @Schema(description = "암호화된 사용자 식별자")
        String encryptUniqueId,

        @Schema(description = "요청 시각 (epoch millis)")
        String timestamp,

        @Schema(description = "HMAC 서명. 이 값으로 요청 진위를 검증한다")
        String signature
) {
}
