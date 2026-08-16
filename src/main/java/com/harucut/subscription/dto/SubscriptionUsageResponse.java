package com.harucut.subscription.dto;

import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.policy.FrameLimit;

public record SubscriptionUsageResponse(
        PlanTier planTier,
        int frameRetentionLimit,
        int frameRetentionUsedCount,
        int frameRetentionRemainingCount,
        boolean frameRetentionUnlimited
) {
    public static SubscriptionUsageResponse of(PlanTier effectiveTier, int usedCount) {
        FrameLimit limit = effectiveTier.getPolicy().frameRetentionLimit();
        return new SubscriptionUsageResponse(
                effectiveTier,
                limit.maxOrUnlimited(),
                usedCount,
                limit.remainingFrom(usedCount),
                limit.isUnlimited()
        );
    }
}
