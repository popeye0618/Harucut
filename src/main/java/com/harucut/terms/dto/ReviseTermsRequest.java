package com.harucut.terms.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviseTermsRequest(
        @NotBlank(message = "본문은 필수입니다.")
        String content
) {

}
