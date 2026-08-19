package com.harucut.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "이메일 로그인 요청")
public record LoginRequest(

        @NotBlank @Email
        @Schema(description = "이메일", example = "user@harucut.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank
        @Schema(description = "비밀번호", example = "password123", requiredMode = Schema.RequiredMode.REQUIRED)
        String password
) {
}
