package com.harucut.auth.dto;

import com.harucut.common.utils.Emails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyResetCodeRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String code
) {
    public VerifyResetCodeRequest {
        email = Emails.normalize(email);
        code = code == null ? null : code.trim();
    }
}
