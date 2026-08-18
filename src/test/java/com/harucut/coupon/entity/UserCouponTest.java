package com.harucut.coupon.entity;

import com.harucut.coupon.enums.UserCouponStatus;
import com.harucut.subscription.enums.PlanTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserCoupon")
class UserCouponTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 10, 0);

    @Test
    @DisplayName("redeemed로 만들면 즉시 개시(REDEEMED) 상태다")
    void redeemedFactory() {
        UserCoupon userCoupon = UserCoupon.redeemed(coupon(), 1L, NOW);

        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.REDEEMED);
        assertThat(userCoupon.getRedeemedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("reserved로 만들면 예약(RESERVED) 상태다")
    void reservedFactory() {
        assertThat(UserCoupon.reserved(coupon(), 1L, NOW).getStatus())
                .isEqualTo(UserCouponStatus.RESERVED);
    }

    @Test
    @DisplayName("생성하면 publicId가 12자로 채워진다")
    void publicIdIsFilled() {
        assertThat(UserCoupon.redeemed(coupon(), 1L, NOW).getPublicId()).hasSize(12);
    }

    @Test
    @DisplayName("markRedeemed하면 예약이 개시 상태로 바뀐다")
    void markRedeemed() {
        UserCoupon userCoupon = UserCoupon.reserved(coupon(), 1L, NOW);

        userCoupon.markRedeemed();

        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.REDEEMED);
    }

    private Coupon coupon() {
        return Coupon.create("가입 축하 PRO", "WELCOME-PRO", PlanTier.PRO, null, null);
    }
}
