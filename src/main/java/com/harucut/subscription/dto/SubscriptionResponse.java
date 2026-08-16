package com.harucut.subscription.dto;

import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.enums.SubscriptionStatus;

import java.time.LocalDateTime;

public record SubscriptionResponse(
        PlanTier planTier,
        SubscriptionStatus status,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd,
        boolean autoRenew
) {
    public static SubscriptionResponse of(UserSubscription subscription, PlanTier effectiveTier) {
        return new SubscriptionResponse(
                effectiveTier,
                subscription.getSubscriptionStatus(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.isAutoRenew()
        );
    }
}
