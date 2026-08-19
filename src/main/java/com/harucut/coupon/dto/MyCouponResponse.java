package com.harucut.coupon.dto;

import com.harucut.coupon.entity.UserCoupon;
import com.harucut.coupon.enums.UserCouponStatus;
import com.harucut.subscription.enums.PlanTier;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "내가 쓴 쿠폰 한 건")
public record MyCouponResponse(

        @Schema(description = "**사용 이력의 ID**다. 쿠폰 자체의 ID 가 아니다", example = "aB3dE7fG9h")
        String publicId,

        @Schema(description = "쿠폰 이름 (관리자가 붙인 라벨)", example = "가입 축하 PRO 1개월")
        String couponName,

        @Schema(description = "이 쿠폰이 주는 등급", example = "PRO")
        PlanTier grantTier,

        @Schema(description = "`REDEEMED` 적용 완료 · `RESERVED` 현재 구독이 끝나면 시작될 예약분",
                example = "REDEEMED")
        UserCouponStatus status,

        @Schema(description = "**코드를 입력한 시각.** 적용이 시작된 시각이 아니다 — 예약분은 둘이 다르다",
                example = "2026-07-28T10:00:00")
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
