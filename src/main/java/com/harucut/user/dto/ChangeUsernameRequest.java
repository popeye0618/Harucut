package com.harucut.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeUsernameRequest(
        @NotBlank @Size(max = 20) String username
) {
    public ChangeUsernameRequest {
        username = (username == null) ? null : username.strip();
    }
}
