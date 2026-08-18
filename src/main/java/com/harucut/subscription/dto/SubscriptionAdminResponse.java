package com.harucut.subscription.dto;

import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.enums.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "구독 (관리자용 — DB 값과 적용 값을 함께 준다)")
public record SubscriptionAdminResponse(

        @Schema(description = "DB 에 저장된 등급 원본. 주기가 끝나도 강등 배치 전까지는 그대로 남아 있다",
                example = "PRO")
        PlanTier planTier,

        @Schema(description = """
                **실제로 적용 중인 등급.** 사용자 API 가 보는 값이 이것이다.
                `planTier` 와 다르면 "주기는 끝났는데 배치가 아직 안 돌았다"는 뜻이다.""",
                example = "BASIC")
        PlanTier effectiveTier,

        @Schema(description = "구독 진행 상태", example = "ACTIVE")
        SubscriptionStatus status,

        @Schema(description = "현재 결제 주기 시작. BASIC 이면 키 자체가 없다", example = "2026-07-21T00:00:00")
        LocalDateTime currentPeriodStart,

        @Schema(description = "현재 결제 주기 만료. BASIC 이면 키 자체가 없다", example = "2026-08-21T00:00:00")
        LocalDateTime currentPeriodEnd,

        @Schema(description = "자동갱신 여부", example = "true")
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
