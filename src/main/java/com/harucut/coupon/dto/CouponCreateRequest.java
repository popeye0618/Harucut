package com.harucut.coupon.dto;

import com.harucut.subscription.enums.PlanTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CouponCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 32) String code,
        @NotNull PlanTier grantTier,
        @Positive Integer maxRedemptions,
        LocalDateTime validUntil
) {
}