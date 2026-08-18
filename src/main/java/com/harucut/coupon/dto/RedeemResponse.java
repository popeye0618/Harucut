package com.harucut.coupon.dto;

import com.harucut.subscription.enums.PlanTier;

import java.time.LocalDateTime;

public record RedeemResponse(
        boolean applied,
        PlanTier grantTier,
        LocalDateTime startsAt,
        LocalDateTime endsAt
) {
}
