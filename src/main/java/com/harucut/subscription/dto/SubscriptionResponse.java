package com.harucut.subscription.dto;

import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.enums.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "내 구독")
public record SubscriptionResponse(

        @Schema(description = """
                **실제로 적용 중인 등급.** 결제 주기가 끝났는데 강등 배치가 아직 안 돌았으면
                `status` 가 `ACTIVE` 여도 여기는 `BASIC` 으로 내려온다.
                **권한 판정은 이 값으로 한다** — 내 정보·사용량 API 와 같은 기준이다.""",
                example = "PLUS")
        PlanTier planTier,

        @Schema(description = """
                구독의 진행 상태. `planTier` 와 달리 **DB 원본**이라 둘이 어긋나 보일 수 있다.

                `ACTIVE` 정상 구독 중 ·
                `CANCELED` 자동갱신 해지 예약(주기 끝까지는 유료) ·
                `PAST_DUE` 정기결제 실패 ·
                `EXPIRED` 만료되어 BASIC 으로 강등됨 ·
                `GRANTED` 쿠폰으로 받은 무료 구독""",
                example = "ACTIVE")
        SubscriptionStatus status,

        @Schema(description = "현재 결제 주기 시작. **BASIC 이면 키 자체가 없다**", example = "2026-07-21T00:00:00")
        LocalDateTime currentPeriodStart,

        @Schema(description = """
                현재 결제 주기 만료. **BASIC 이면 키 자체가 없다.**
                `CANCELED` 상태라면 이 시각까지는 유료 등급이 유지된다.""",
                example = "2026-08-21T00:00:00")
        LocalDateTime currentPeriodEnd,

        @Schema(description = "자동갱신 여부. `false` 면 해지 API 를 다시 부를 수 없다(`SUBS-006`)", example = "true")
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
