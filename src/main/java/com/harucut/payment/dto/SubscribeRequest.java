package com.harucut.payment.dto;

import com.harucut.subscription.enums.PlanTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubscribeRequest(
        @NotNull PlanTier planTier,
        @NotBlank String authKey,
        @NotBlank @Size(max = 100) String idempotencyKey
) {
}
