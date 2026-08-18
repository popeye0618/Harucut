package com.harucut.subscription.dto;

import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.policy.FrameLimit;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "요금제 사용량")
public record SubscriptionUsageResponse(

        @Schema(description = "실제로 적용 중인 요금제", example = "PLUS")
        PlanTier planTier,

        @Schema(description = "프레임 동시 보관 한도. **무제한이면 `-1`** (BASIC 0 / PLUS 3 / PRO -1)",
                example = "3")
        int frameRetentionLimit,

        @Schema(description = "지금 보관 중인 프레임 수", example = "1")
        int frameRetentionUsedCount,

        @Schema(description = "더 만들 수 있는 개수. **무제한이면 `-1`**, 한도를 넘겼어도 음수가 아니라 `0`",
                example = "2")
        int frameRetentionRemainingCount,

        @Schema(description = "무제한 여부. `true` 면 위 두 `-1` 을 개수로 읽지 말 것", example = "false")
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
