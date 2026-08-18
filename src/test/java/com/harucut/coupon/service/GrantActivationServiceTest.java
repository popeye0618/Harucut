package com.harucut.coupon.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.coupon.entity.Coupon;
import com.harucut.coupon.entity.UserCoupon;
import com.harucut.coupon.enums.UserCouponStatus;
import com.harucut.coupon.exception.CouponErrorCode;
import com.harucut.coupon.repository.UserCouponRepository;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.enums.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("GrantActivationService")
class GrantActivationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long USER_COUPON_ID = 42L;
    // 배치가 도는 새벽 시각 — 예약 활성화의 실제 호출 시점이다
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 1, 0);

    @Mock
    private UserCouponRepository userCouponRepository;

    private GrantActivationService service;

    @BeforeEach
    void setUp() {
        service = new GrantActivationService(userCouponRepository);
    }

    @Test
    @DisplayName("예약된 쿠폰의 tier로 GRANTED가 되고 주기는 now부터 한 달이다")
    void activatesGrantWithCouponTier() {
        UserSubscription subscription = reservedSubscription();
        givenReservedUserCoupon();

        service.activate(subscription, NOW);

        assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.PLUS);
        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.GRANTED);
        assertThat(subscription.getCurrentPeriodStart()).isEqualTo(NOW);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(NOW.plusMonths(1));
    }

    @Test
    @DisplayName("사용 이력이 RESERVED에서 REDEEMED로 바뀐다")
    void marksHistoryRedeemed() {
        UserSubscription subscription = reservedSubscription();
        UserCoupon userCoupon = givenReservedUserCoupon();

        service.activate(subscription, NOW);

        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.REDEEMED);
    }

    @Test
    @DisplayName("구독의 예약 칸이 비워진다 — 다음 쿠폰을 받을 수 있는 상태가 된다")
    void clearsReservation() {
        UserSubscription subscription = reservedSubscription();
        givenReservedUserCoupon();

        service.activate(subscription, NOW);

        assertThat(subscription.getReservedUserCouponId()).isNull();
    }

    @Test
    @DisplayName("예약이 가리키는 이력이 없으면 COUPON-001이고 구독은 그대로다")
    void missingHistoryFails() {
        UserSubscription subscription = reservedSubscription();
        given(userCouponRepository.findById(USER_COUPON_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate(subscription, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);

        assertThat(subscription.getReservedUserCouponId()).isEqualTo(USER_COUPON_ID);
        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    // PRO를 결제해 쓰던 사용자가 PLUS 쿠폰을 예약해둔 상황
    private UserSubscription reservedSubscription() {
        UserSubscription subscription = UserSubscription.createBasic(USER_ID);
        subscription.activatePaid(PlanTier.PRO, NOW.minusMonths(1), NOW);
        subscription.reserveGrant(USER_COUPON_ID);
        return subscription;
    }

    private UserCoupon givenReservedUserCoupon() {
        Coupon coupon = Coupon.create("PLUS 한 달", "GRANT-PLUS", PlanTier.PLUS, null, null);
        UserCoupon userCoupon = UserCoupon.reserved(coupon, USER_ID, NOW.minusDays(15));
        given(userCouponRepository.findById(USER_COUPON_ID)).willReturn(Optional.of(userCoupon));
        return userCoupon;
    }
}
