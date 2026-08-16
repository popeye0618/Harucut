package com.harucut.subscription.policy;

import java.time.LocalDateTime;

// 요금제의 내역 보관 기간. cutoff를 저장하지 않고 매번 now에서 계산하므로
// tier가 바뀌면 즉시 반영되고, 기간이 지난 데이터도 지우지 않는다(업그레이드 시 다시 보인다).
public sealed interface Retention permits Retention.Unlimited, Retention.Days, Retention.Months {

    // 이보다 오래된 내역은 숨긴다. null = 무제한 — 목록 쿼리에서 기간 조건을 생략하라는 신호
    LocalDateTime cutoffFrom(LocalDateTime now);

    // 경계 포함: createdAt == cutoff면 접근 가능. createdAt을 모르면 막지 않는다(명세)
    default boolean isAccessible(LocalDateTime createdAt, LocalDateTime now) {
        LocalDateTime cutoff = cutoffFrom(now);
        return cutoff == null || createdAt == null || !createdAt.isBefore(cutoff);
    }

    record Unlimited() implements Retention {

        @Override
        public LocalDateTime cutoffFrom(LocalDateTime now) {
            return null;
        }
    }

    record Days(int days) implements Retention {

        public Days {
            if (days <= 0) {
                throw new IllegalArgumentException("days는 1 이상이어야 한다: " + days);
            }
        }

        @Override
        public LocalDateTime cutoffFrom(LocalDateTime now) {
            return now.minusDays(days);
        }
    }

    record Months(int months) implements Retention {

        public Months {
            if (months <= 0) {
                throw new IllegalArgumentException("months는 1 이상이어야 한다: " + months);
            }
        }

        @Override
        public LocalDateTime cutoffFrom(LocalDateTime now) {
            // minusMonths는 달력 기준(월말 클램프) — "3개월 보관"이라는 상품 문구와 일치한다
            return now.minusMonths(months);
        }
    }
}