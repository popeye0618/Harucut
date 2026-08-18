package com.harucut.subscription.entity;

import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.enums.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserSubscriptionTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime PERIOD_START = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime PERIOD_END = LocalDateTime.of(2026, 9, 1, 0, 0);

    @Nested
    @DisplayName("createBasic")
    class CreateBasic {

        @Test
        @DisplayName("가입 기본 구독은 BASIC/ACTIVE, 주기 없음, 자동갱신 없음이다")
        void initialState() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);

            assertThat(subscription.getUserId()).isEqualTo(USER_ID);
            assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.BASIC);
            assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(subscription.getCurrentPeriodStart()).isNull();
            assertThat(subscription.getCurrentPeriodEnd()).isNull();
            assertThat(subscription.isAutoRenew()).isFalse();
        }
    }

    @Nested
    @DisplayName("effectiveTier")
    class EffectiveTier {

        @Test
        @DisplayName("BASIC은 언제 물어도 BASIC이다")
        void basicIsAlwaysBasic() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);

            assertThat(subscription.effectiveTier(LocalDateTime.of(2026, 8, 16, 12, 0)))
                    .isEqualTo(PlanTier.BASIC);
        }

        @Test
        @DisplayName("유료 tier에 만료일이 없으면 tier를 그대로 반환한다")
        void paidWithoutPeriodEndKeepsTier() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);
            subscription.activatePaid(PlanTier.PRO, PERIOD_START, null);

            assertThat(subscription.effectiveTier(LocalDateTime.of(2030, 1, 1, 0, 0)))
                    .isEqualTo(PlanTier.PRO);
        }

        @Test
        @DisplayName("만료 직전에는 유료 tier가 유지된다")
        void beforePeriodEndKeepsTier() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);
            subscription.activatePaid(PlanTier.PRO, PERIOD_START, PERIOD_END);

            assertThat(subscription.effectiveTier(PERIOD_END.minusNanos(1)))
                    .isEqualTo(PlanTier.PRO);
        }

        @Test
        @DisplayName("만료 시각 정각은 이미 BASIC이다 — 경계 포함")
        void atPeriodEndFallsToBasic() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);
            subscription.activatePaid(PlanTier.PRO, PERIOD_START, PERIOD_END);

            assertThat(subscription.effectiveTier(PERIOD_END)).isEqualTo(PlanTier.BASIC);
        }

        @Test
        @DisplayName("만료 후에는 배치가 강등하기 전이라도 BASIC이다")
        void afterPeriodEndFallsToBasic() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);
            subscription.activatePaid(PlanTier.PRO, PERIOD_START, PERIOD_END);

            assertThat(subscription.effectiveTier(PERIOD_END.plusNanos(1)))
                    .isEqualTo(PlanTier.BASIC);
        }

        @Test
        @DisplayName("해지 예약 상태여도 만료 전에는 유료 tier가 유지된다 — 환불 없음 정책")
        void canceledKeepsTierUntilPeriodEnd() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);
            subscription.activatePaid(PlanTier.PLUS, PERIOD_START, PERIOD_END);
            subscription.cancelAutoRenew();

            assertThat(subscription.effectiveTier(PERIOD_END.minusNanos(1)))
                    .isEqualTo(PlanTier.PLUS);
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class Transitions {

        @Test
        @DisplayName("activatePaid는 tier와 주기를 설정하고 ACTIVE, 자동갱신을 켠다")
        void activatePaid() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);

            subscription.activatePaid(PlanTier.PLUS, PERIOD_START, PERIOD_END);

            assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.PLUS);
            assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(subscription.getCurrentPeriodStart()).isEqualTo(PERIOD_START);
            assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(PERIOD_END);
            assertThat(subscription.isAutoRenew()).isTrue();
        }

        @Test
        @DisplayName("cancelAutoRenew는 CANCELED로 바꾸되 tier와 주기는 남긴다")
        void cancelAutoRenew() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);
            subscription.activatePaid(PlanTier.PLUS, PERIOD_START, PERIOD_END);

            subscription.cancelAutoRenew();

            assertThat(subscription.isAutoRenew()).isFalse();
            assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELED);
            assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.PLUS);
            assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(PERIOD_END);
        }

        @Test
        @DisplayName("markPastDue는 상태만 연체로 바꾸고 tier·주기·자동갱신은 그대로 둔다")
        void markPastDue() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);
            subscription.activatePaid(PlanTier.PLUS, PERIOD_START, PERIOD_END);

            subscription.markPastDue();

            assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
            assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.PLUS);
            assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(PERIOD_END);
            assertThat(subscription.isAutoRenew()).isTrue();
        }

        @Test
        @DisplayName("renew는 주기를 갱신하고 ACTIVE로 되돌리되 자동갱신 의사는 건드리지 않는다")
        void renewAfterPastDue() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);
            subscription.activatePaid(PlanTier.PLUS, PERIOD_START, PERIOD_END);
            subscription.markPastDue();

            LocalDateTime newStart = LocalDateTime.of(2026, 9, 1, 0, 0);
            LocalDateTime newEnd = LocalDateTime.of(2026, 10, 1, 0, 0);
            subscription.renew(newStart, newEnd);

            assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(subscription.getCurrentPeriodStart()).isEqualTo(newStart);
            assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(newEnd);
            assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.PLUS);
            assertThat(subscription.isAutoRenew()).isTrue();
        }

        @Test
        @DisplayName("expireToFree는 BASIC 불변식(주기 null, 자동갱신 없음)을 복원한다")
        void expireToFree() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);
            subscription.activatePaid(PlanTier.PRO, PERIOD_START, PERIOD_END);

            subscription.expireToFree();

            assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.BASIC);
            assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
            assertThat(subscription.getCurrentPeriodStart()).isNull();
            assertThat(subscription.getCurrentPeriodEnd()).isNull();
            assertThat(subscription.isAutoRenew()).isFalse();
        }

        @Test
        @DisplayName("activateGrant는 GRANTED로 tier와 주기를 설정하되 자동갱신은 켜지 않는다")
        void activateGrant() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);

            subscription.activateGrant(PlanTier.PLUS, PERIOD_START, PERIOD_END);

            assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.PLUS);
            assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.GRANTED);
            assertThat(subscription.getCurrentPeriodStart()).isEqualTo(PERIOD_START);
            assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(PERIOD_END);
            assertThat(subscription.isAutoRenew()).isFalse();
        }
    }

    @Nested
    @DisplayName("grant 예약")
    class GrantReservation {

        @Test
        @DisplayName("reserveGrant는 예약된 UserCoupon의 PK를 기억한다")
        void reserveGrant() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);

            subscription.reserveGrant(10L);

            assertThat(subscription.getReservedUserCouponId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("clearReservedGrant는 예약을 비운다")
        void clearReservedGrant() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);
            subscription.reserveGrant(10L);

            subscription.clearReservedGrant();

            assertThat(subscription.getReservedUserCouponId()).isNull();
        }
    }
}
