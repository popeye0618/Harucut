package com.harucut.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeProfileImageRequest(
        @NotBlank(message = "s3Key는 필수입니다.")
        String s3Key
) {
}
