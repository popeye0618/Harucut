package com.harucut.media.compose;

import java.util.List;

// 합성 실행 위치의 갈아끼우는 지점 — 로컬·테스트는 인프로세스, 운영은 Lambda(6단계).
// 계약: 원본과 스펙으로 결과 PNG를 만들어 resultKey에 올려놓는다. 실패는 예외로 알린다.
// DB는 모른다 — Job 상태 기록은 호출자(ComposeWorker → ComposeService) 몫이다
public interface ComposeExecutor {

    void execute(ComposeSpec spec, List<String> sourceKeys, String resultKey);
}
