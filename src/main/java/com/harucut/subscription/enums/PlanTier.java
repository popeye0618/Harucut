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

    BASIC(new PlanPolicy(new FrameLimit.Limited(0), new Retention.Days(3))),
    PLUS(new PlanPolicy(new FrameLimit.Limited(3), new Retention.Months(3))),
    PRO(new PlanPolicy(new FrameLimit.Unlimited(), new Retention.Unlimited()));

    private final PlanPolicy policy;
}