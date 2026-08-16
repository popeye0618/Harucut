package com.harucut.subscription.dto;

import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.enums.SubscriptionStatus;

import java.time.LocalDateTime;

public record SubscriptionAdminResponse(
        PlanTier planTier,
        PlanTier effectiveTier,
        SubscriptionStatus status,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd,
        boolean autoRenew
) {

    public static SubscriptionAdminResponse of(UserSubscription subscription, PlanTier effectiveTier) {
        return new SubscriptionAdminResponse(
                subscription.getPlanTier(),
                effectiveTier,
                subscription.getSubscriptionStatus(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.isAutoRenew()
        );
    }
}