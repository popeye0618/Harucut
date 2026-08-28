package com.harucut.subscription.enums;

import com.harucut.subscription.policy.FrameLimit;
import com.harucut.subscription.policy.PlanPolicy;
import com.harucut.subscription.policy.Retention;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 가격은 여기 넣지 않는다 — billing.pricing.* 설정(PlanPricingProperties)이 단일 원천
@Getter
@RequiredArgsConstructor
public enum PlanTier {

    // 임시로 세 요금제의 한도를 전부 무제한으로 통일 — 기능 차이는 없고 가격 차이만 남는다
    BASIC(new PlanPolicy(new FrameLimit.Unlimited(), new Retention.Unlimited())),
    PLUS(new PlanPolicy(new FrameLimit.Unlimited(), new Retention.Unlimited())),
    PRO(new PlanPolicy(new FrameLimit.Unlimited(), new Retention.Unlimited()));

    private final PlanPolicy policy;
}