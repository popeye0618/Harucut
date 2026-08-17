package com.harucut.storage.event;

import java.util.List;

// 커밋 후 지울 S3 key 목록. 발행 시점에는 아무것도 지워지지 않는다 — S3DeleteListener가 커밋 후 실행.
public record S3DeleteEvent(List<String> keys) {

    public S3DeleteEvent {
        keys = List.copyOf(keys);  // 방어적 복사 — 발행 후 원본 리스트가 변해도 이벤트는 불변
    }
}
