package com.harucut.auth.dto;

import com.harucut.common.utils.Emails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 20) String username,
        @NotBlank @Size(min = 8, max = 20) String password
) {
    public RegisterRequest {
        email = Emails.normalize(email);
    }
}