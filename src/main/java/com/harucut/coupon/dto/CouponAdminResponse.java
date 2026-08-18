package com.harucut.coupon.dto;

import com.harucut.coupon.entity.Coupon;
import com.harucut.subscription.enums.PlanTier;

import java.time.LocalDateTime;

public record CouponAdminResponse(
        String publicId,
        String name,
        String code,
        PlanTier grantTier,
        Integer maxRedemptions,
        LocalDateTime validUntil,
        boolean active,
        int redeemedCount
) {
    public static CouponAdminResponse from(Coupon coupon) {
        return new CouponAdminResponse(
                coupon.getPublicId(),
                coupon.getName(),
                coupon.getCode(),
                coupon.getGrantTier(),
                coupon.getMaxRedemptions(),
                coupon.getValidUntil(),
                coupon.isActive(),
                coupon.getRedeemedCount()
        );
    }
}