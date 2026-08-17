package com.harucut.subscription.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FrameLimitTest {

    @Nested
    @DisplayName("무제한")
    class UnlimitedTest {

        private final FrameLimit frameLimit = new FrameLimit.Unlimited();

        @Test
        @DisplayName("몇 개를 쓰고 있든 허용한다")
        void allowsAnyCount() {
            assertThat(frameLimit.allows(0)).isTrue();
            assertThat(frameLimit.allows(Integer.MAX_VALUE)).isTrue();
        }

        @Test
        @DisplayName("상한과 잔여는 무제한을 뜻하는 -1이다")
        void reportsUnlimitedAsMinusOne() {
            assertThat(frameLimit.maxOrUnlimited()).isEqualTo(-1);
            assertThat(frameLimit.remainingFrom(100)).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("제한")
    class LimitedTest {

        private final FrameLimit frameLimit = new FrameLimit.Limited(3);

        @Test
        @DisplayName("상한 미만이면 허용하고 상한에 닿으면 막는다")
        void allowsOnlyBelowMax() {
            assertThat(frameLimit.allows(2)).isTrue();
            assertThat(frameLimit.allows(3)).isFalse();
            assertThat(frameLimit.allows(4)).isFalse();
        }

        @Test
        @DisplayName("상한을 그대로 알려준다")
        void reportsMax() {
            assertThat(frameLimit.maxOrUnlimited()).isEqualTo(3);
        }

        @Test
        @DisplayName("잔여는 상한에서 사용량을 뺀 값이다")
        void remainingIsMaxMinusUsed() {
            assertThat(frameLimit.remainingFrom(1)).isEqualTo(2);
        }

        @Test
        @DisplayName("사용량이 상한을 넘어도 잔여는 음수가 되지 않는다")
        void remainingNeverGoesNegative() {
            assertThat(frameLimit.remainingFrom(5)).isZero();
        }

        @Test
        @DisplayName("상한이 음수면 만들 수 없다")
        void rejectsNegativeMax() {
            assertThatThrownBy(() -> new FrameLimit.Limited(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("상한 0은 합법이며 아무것도 허용하지 않는 제한이 된다")
        void acceptsZeroMax() {
            FrameLimit none = new FrameLimit.Limited(0);

            assertThat(none.maxOrUnlimited()).isZero();
            assertThat(none.allows(0)).isFalse();
            assertThat(none.remainingFrom(0)).isZero();
        }
    }
}
