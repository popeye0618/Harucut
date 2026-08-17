package com.harucut.subscription.enums;

import com.harucut.subscription.policy.FrameLimit;
import com.harucut.subscription.policy.PlanPolicy;
import com.harucut.subscription.policy.Retention;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class PlanTierTest {

    @Test
    @DisplayName("BASIC — 프레임 보관 불가, 내역 3일")
    void basicPolicy() {
        assertThat(PlanTier.BASIC.getPolicy())
                .isEqualTo(new PlanPolicy(new FrameLimit.Limited(0), new Retention.Days(3)));
    }

    @Test
    @DisplayName("PLUS — 프레임 3개, 내역 3개월")
    void plusPolicy() {
        assertThat(PlanTier.PLUS.getPolicy())
                .isEqualTo(new PlanPolicy(new FrameLimit.Limited(3), new Retention.Months(3)));
    }

    @Test
    @DisplayName("PRO — 전부 무제한")
    void proPolicy() {
        assertThat(PlanTier.PRO.getPolicy())
                .isEqualTo(new PlanPolicy(new FrameLimit.Unlimited(), new Retention.Unlimited()));
    }

}