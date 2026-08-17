package com.harucut.subscription.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetentionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 12, 0);

    @Test
    @DisplayName("무제한은 기준 시각이 없고, 아무리 오래된 것도 접근할 수 있다")
    void unlimitedHasNoCutoff() {
        Retention retention = new Retention.Unlimited();

        assertThat(retention.cutoffFrom(NOW)).isNull();
        assertThat(retention.isAccessible(NOW.minusYears(10), NOW)).isTrue();
    }

    @Test
    @DisplayName("일 단위 보관은 기준 시각이 그만큼 앞이다")
    void daysCutoffIsNowMinusDays() {
        // 기대값을 minusDays 로 계산하면 그 API 를 잘못 이해했을 때 테스트도 같이 틀린다.
        assertThat(new Retention.Days(3).cutoffFrom(NOW))
                .isEqualTo(LocalDateTime.of(2026, 8, 13, 12, 0));
    }

    @Test
    @DisplayName("월 단위 보관은 기준 시각이 그만큼 앞이다")
    void monthsCutoffIsNowMinusMonths() {
        assertThat(new Retention.Months(3).cutoffFrom(NOW))
                .isEqualTo(LocalDateTime.of(2026, 5, 16, 12, 0));
    }

    @Test
    @DisplayName("보관 기간 1일은 합법이며 하루 전이 기준 시각이 된다")
    void acceptsSingleDay() {
        assertThat(new Retention.Days(1).cutoffFrom(NOW))
                .isEqualTo(LocalDateTime.of(2026, 8, 15, 12, 0));
    }

    @Test
    @DisplayName("보관 기간 1개월은 합법이며 한 달 전이 기준 시각이 된다")
    void acceptsSingleMonth() {
        assertThat(new Retention.Months(1).cutoffFrom(NOW))
                .isEqualTo(LocalDateTime.of(2026, 7, 16, 12, 0));
    }

    @Test
    @DisplayName("기준 시각과 같으면 접근할 수 있고, 그보다 앞이면 막는다")
    void accessibilityFlipsAtCutoff() {
        Retention retention = new Retention.Days(3);

        assertThat(retention.isAccessible(NOW, NOW)).isTrue();
        assertThat(retention.isAccessible(NOW.minusDays(3), NOW)).isTrue();
        assertThat(retention.isAccessible(NOW.minusDays(3).minusNanos(1), NOW)).isFalse();
    }

    @Test
    @DisplayName("생성 시각을 모르면 막지 않는다")
    void unknownCreatedAtIsAccessible() {
        assertThat(new Retention.Days(3).isAccessible(null, NOW)).isTrue();
    }

    @Test
    @DisplayName("보관 기간은 1 이상이어야 한다")
    void rejectsNonPositivePeriod() {
        assertThatThrownBy(() -> new Retention.Days(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Retention.Months(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
