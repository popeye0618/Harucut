package com.harucut.subscription.policy;

// 요금제의 프레임 동시 보관 한도. 무제한/제한을 -1 센티널 대신 타입으로 구분한다.
// -1은 프론트 응답 계약(무제한 표현)이라 maxOrUnlimited/remainingFrom의 반환값에만 존재한다.
public sealed interface FrameLimit permits FrameLimit.Unlimited, FrameLimit.Limited {

    // currentCount개 보유 중일 때 하나 "더" 만들 수 있는가 — 상한에 닿으면 거부
    boolean allows(int currentCount);

    // API 응답용. 무제한이면 -1
    int maxOrUnlimited();

    // 남은 생성 가능 수. 강등으로 한도 초과 보유가 실존하므로 음수는 0으로 보정. 무제한이면 -1
    int remainingFrom(int used);

    record Unlimited() implements FrameLimit {

        @Override
        public boolean allows(int currentCount) {
            return true;
        }

        @Override
        public int maxOrUnlimited() {
            return -1;
        }

        @Override
        public int remainingFrom(int used) {
            return -1;
        }
    }

    record Limited(int max) implements FrameLimit {

        // 음수를 허용하면 "-1 = 무제한" 표현이 뒷문으로 돌아온다. 0은 BASIC(보관 불가)의 정당한 값
        public Limited {
            if (max < 0) {
                throw new IllegalArgumentException("max는 0 이상이어야 한다: " + max);
            }
        }

        @Override
        public boolean allows(int currentCount) {
            return currentCount < max;
        }

        @Override
        public int maxOrUnlimited() {
            return max;
        }

        @Override
        public int remainingFrom(int used) {
            return Math.max(max - used, 0);
        }
    }

    default boolean isUnlimited() {
        return this instanceof Unlimited;
    }
}
