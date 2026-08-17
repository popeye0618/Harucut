package com.harucut.subscription.config;

import com.harucut.subscription.enums.PlanTier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "billing.pricing")
public record PlanPricingProperties(
        @DefaultValue("0") int basic,
        @DefaultValue("3900") int plus,
        @DefaultValue("9900") int pro
) {
    public int priceOf(PlanTier tier) {
        return switch (tier) {
            case BASIC -> basic;
            case PLUS -> plus;
            case PRO -> pro;
        };
    }
}
