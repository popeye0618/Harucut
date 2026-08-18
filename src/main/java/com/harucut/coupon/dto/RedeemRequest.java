package com.harucut.coupon.dto;

import jakarta.validation.constraints.NotBlank;

public record RedeemRequest(
        @NotBlank String code
) {
}