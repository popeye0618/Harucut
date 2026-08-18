package com.harucut.coupon.entity;

import com.harucut.subscription.enums.PlanTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Coupon")
class CouponTest {

    private static final LocalDateTime VALID_UNTIL = LocalDateTime.of(2026, 12, 31, 23, 59, 59);

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("생성하면 활성 상태고 사용 수는 0이다")
        void startsActiveWithZeroCount() {
            Coupon coupon = coupon("WELCOME-PRO", 100);

            assertThat(coupon.isActive()).isTrue();
            assertThat(coupon.getRedeemedCount()).isZero();
        }

        @Test
        @DisplayName("생성하면 publicId가 12자로 채워진다")
        void publicIdIsFilled() {
            assertThat(coupon("WELCOME-PRO", 100).getPublicId()).hasSize(12);
        }

        /*
         * 정규화가 입구(create)에 있어야 DB에 소문자 코드가 존재할 수 없다.
         * 조회 쪽만 정규화하면 대소문자만 다른 두 코드가 저장되는 구멍이 남는다.
         */
        @Test
        @DisplayName("코드는 공백을 걷어내고 대문자로 저장된다")
        void codeIsNormalized() {
            Coupon coupon = coupon("  welcome-pro  ", 100);

            assertThat(coupon.getCode()).isEqualTo("WELCOME-PRO");
        }
    }

    @Test
    @DisplayName("normalizeCode는 공백 제거 + 대문자 변환이다")
    void normalizeCode() {
        assertThat(Coupon.normalizeCode("  welcome-pro-2026 ")).isEqualTo("WELCOME-PRO-2026");
    }

    @Test
    @DisplayName("deactivate하면 비활성이 된다")
    void deactivate() {
        Coupon coupon = coupon("WELCOME-PRO", 100);

        coupon.deactivate();

        assertThat(coupon.isActive()).isFalse();
    }

    @Nested
    @DisplayName("isRedeemable")
    class IsRedeemable {

        @Test
        @DisplayName("활성이고 마감이 없으면 언제든 사용 가능하다")
        void activeWithoutDeadline() {
            Coupon coupon = Coupon.create("무기한", "FOREVER", PlanTier.PLUS, null, null);

            assertThat(coupon.isRedeemable(LocalDateTime.of(2030, 1, 1, 0, 0))).isTrue();
        }

        @Test
        @DisplayName("비활성이면 마감 전이어도 사용 불가다")
        void inactiveIsNotRedeemable() {
            Coupon coupon = coupon("WELCOME-PRO", 100);
            coupon.deactivate();

            assertThat(coupon.isRedeemable(VALID_UNTIL.minusDays(1))).isFalse();
        }

        @Test
        @DisplayName("마감 전이면 사용 가능하다")
        void beforeDeadline() {
            assertThat(coupon("WELCOME-PRO", 100).isRedeemable(VALID_UNTIL.minusNanos(1))).isTrue();
        }

        /*
         * validUntil은 "까지"라서 정각 포함이다.
         * 정각=만료인 구독 periodEnd와 다른 규칙임을 여기 박아둔다.
         */
        @Test
        @DisplayName("마감 시각 정각에는 아직 사용 가능하다 — 경계 포함")
        void atDeadlineIsStillRedeemable() {
            assertThat(coupon("WELCOME-PRO", 100).isRedeemable(VALID_UNTIL)).isTrue();
        }

        @Test
        @DisplayName("마감이 지나면 사용 불가다")
        void afterDeadline() {
            assertThat(coupon("WELCOME-PRO", 100).isRedeemable(VALID_UNTIL.plusNanos(1))).isFalse();
        }
    }

    private Coupon coupon(String code, Integer maxRedemptions) {
        return Coupon.create("가입 축하 PRO", code, PlanTier.PRO, maxRedemptions, VALID_UNTIL);
    }
}
