package com.harucut.media.enums;

// RUNNING은 의도적으로 없다 — 재실행 판정이 "오래된 PENDING"(시간 임계값)이라
// 실행 중인 몇 초짜리는 상태 없이도 걸러진다 (decisions.md 네컷 합성 결정 참고)
public enum ComposeStatus {
    PENDING,
    DONE,
    FAILED
}
