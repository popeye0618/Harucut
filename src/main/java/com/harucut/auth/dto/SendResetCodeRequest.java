package com.harucut.auth.dto;

import com.harucut.common.utils.Emails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendResetCodeRequest(
        @NotBlank @Email @Size(max = 255) String email
) {
    public SendResetCodeRequest {
        email = Emails.normalize(email);
    }
}
