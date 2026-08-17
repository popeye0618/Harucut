package com.harucut.media.compose;

import java.util.List;

// 합성 요청 커밋 후 실행 스레드로 넘어가는 payload — 실행에 필요한 전부를 담아
// 워커가 Job을 다시 읽지 않고 바로 실행할 수 있다 (DB 기록 시점에만 Job을 읽는다)
public record ComposeRequestedEvent(
        Long jobId,
        ComposeSpec spec,
        List<String> sourceKeys,
        String resultKey,
        String thumbnailKey
) {
}
