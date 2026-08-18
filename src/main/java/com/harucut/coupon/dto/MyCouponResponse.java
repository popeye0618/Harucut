package com.harucut.coupon.dto;

import com.harucut.coupon.entity.UserCoupon;
import com.harucut.coupon.enums.UserCouponStatus;
import com.harucut.subscription.enums.PlanTier;

import java.time.LocalDateTime;

public record MyCouponResponse(
        String publicId,
        String couponName,
        PlanTier grantTier,
        UserCouponStatus status,
        LocalDateTime redeemedAt
) {
    public static MyCouponResponse from(UserCoupon userCoupon) {
        return new MyCouponResponse(
                userCoupon.getPublicId(),
                userCoupon.getCoupon().getName(),
                userCoupon.getCoupon().getGrantTier(),
                userCoupon.getStatus(),
                userCoupon.getRedeemedAt()
        );
    }
}
