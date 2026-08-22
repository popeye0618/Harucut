package com.harucut.media.compose;

import java.util.List;

// 서버 → Lambda로 넘어가는 작업 한 건의 계약.
// 서버(LambdaComposeExecutor)가 만들고 Lambda(ComposeHandler)가 읽는다 —
// 같은 모듈에 있어서 계약이 어긋나면 런타임이 아니라 컴파일에서 깨진다.
// 버킷도 여기 실린다: 설정의 원천을 서버 한 곳으로 유지한다 (Lambda 환경변수 없음).
// thumbnailKey는 null일 수 있다 — 썸네일 도입 전 서버가 보낸 payload와의 호환
// jobId는 Lambda가 읽지 않는다. Destination 통지에 원본 payload가 그대로 실려 오므로, 결과를 어느 Job에 적을지 알아내는 데 서버가 쓴다.
public record ComposeLambdaPayload(
        String bucket,
        Long jobId,
        ComposeSpec spec,
        List<String> sourceKeys,
        String resultKey,
        String thumbnailKey
) {
}
