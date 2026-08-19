package com.harucut.coupon.dto;

import com.harucut.coupon.entity.Coupon;
import com.harucut.subscription.enums.PlanTier;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "쿠폰 (관리자용)")
public record CouponAdminResponse(

        @Schema(description = "비활성화에 쓰는 쿠폰 ID", example = "aB3dE7fG9h")
        String publicId,

        @Schema(description = "관리자용 라벨", example = "가입 축하 PRO 1개월")
        String name,

        @Schema(description = "사용자가 입력하는 코드 (대문자로 저장된다)", example = "WELCOME-PRO-2026")
        String code,

        @Schema(description = "부여 등급", example = "PRO")
        PlanTier grantTier,

        @Schema(description = "사용 상한. **무제한이면 키 자체가 없다**", example = "100")
        Integer maxRedemptions,

        @Schema(description = "사용 마감. **무기한이면 키 자체가 없다**", example = "2026-12-31T23:59:59")
        LocalDateTime validUntil,

        @Schema(description = "활성 여부. `false` 면 신규 사용만 막힌다 — 이미 준 혜택은 회수되지 않는다",
                example = "true")
        boolean active,

        @Schema(description = "지금까지 쓰인 횟수. **아직 시작 안 한 예약분도 포함된다**", example = "3")
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
