package com.harucut.subscription.enums;

import com.harucut.subscription.policy.FrameLimit;
import com.harucut.subscription.policy.PlanPolicy;
import com.harucut.subscription.policy.Retention;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class PlanTierTest {

    @Test
    @DisplayName("BASIC — 전부 무제한 (임시 통일)")
    void basicPolicy() {
        assertThat(PlanTier.BASIC.getPolicy())
                .isEqualTo(new PlanPolicy(new FrameLimit.Unlimited(), new Retention.Unlimited()));
    }

    @Test
    @DisplayName("PLUS — 전부 무제한 (임시 통일)")
    void plusPolicy() {
        assertThat(PlanTier.PLUS.getPolicy())
                .isEqualTo(new PlanPolicy(new FrameLimit.Unlimited(), new Retention.Unlimited()));
    }

    @Test
    @DisplayName("PRO — 전부 무제한")
    void proPolicy() {
        assertThat(PlanTier.PRO.getPolicy())
                .isEqualTo(new PlanPolicy(new FrameLimit.Unlimited(), new Retention.Unlimited()));
    }

}