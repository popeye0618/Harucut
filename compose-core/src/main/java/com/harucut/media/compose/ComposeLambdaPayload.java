package com.harucut.media.compose;

import java.util.List;

// 서버 → Lambda로 넘어가는 작업 한 건의 계약.
// 서버(LambdaComposeExecutor)가 만들고 Lambda(ComposeHandler)가 읽는다 —
// 같은 모듈에 있어서 계약이 어긋나면 런타임이 아니라 컴파일에서 깨진다.
// 버킷도 여기 실린다: 설정의 원천을 서버 한 곳으로 유지한다 (Lambda 환경변수 없음).
// thumbnailKey는 null일 수 있다 — 썸네일 도입 전 서버가 보낸 payload와의 호환
public record ComposeLambdaPayload(
        String bucket,
        ComposeSpec spec,
        List<String> sourceKeys,
        String resultKey,
        String thumbnailKey
) {
}
