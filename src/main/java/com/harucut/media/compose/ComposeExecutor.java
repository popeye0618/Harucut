package com.harucut.media.compose;

// 워커 테스트에서 목으로 대체하기 위함.
// 계약: 원본과 스펙으로 결과 PNG를 resultKey에, 썸네일 JPEG을 thumbnailKey에 올려놓는다.
// 둘 중 하나라도 실패하면 예외 — 잡 전체가 실패다 (어중간한 절반 성공 없음).
// DB는 모른다 — Job 상태 기록은 호출자(ComposeWorker → ComposeService) 몫이다
public interface ComposeExecutor {

    void execute(ComposeRequestedEvent event);
}
