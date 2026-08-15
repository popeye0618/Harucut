package com.harucut.terms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgreementItem(
        @NotBlank(message = "약관 코드는 필수입니다.")
        String code,

        @NotNull(message = "동의 여부는 필수입니다.")
        Boolean agreed
) {
}
