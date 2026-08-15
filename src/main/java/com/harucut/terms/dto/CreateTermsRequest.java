package com.harucut.terms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTermsRequest(
        @NotBlank(message = "약관 코드는 필수입니다.")
        @Pattern(regexp = "^[a-z0-9-]{1,50}$", message = "약관 코드는 소문자·숫자·하이픈 1~50자여야 합니다.")
        String code,

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
        String title,

        @NotNull(message = "필수 동의 여부는 필수입니다.")
        Boolean required,

        @NotBlank(message = "본문은 필수입니다.")
        String content
) {
}
