package com.harucut.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String resetToken,
        @NotBlank @Size(min = 8, max = 20) String newPassword
) {
    public ResetPasswordRequest {
        resetToken = resetToken == null ? null : resetToken.trim();
    }
}
