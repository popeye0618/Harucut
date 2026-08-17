package com.harucut.storage.service;

import com.harucut.storage.event.S3DeleteEvent;
import com.harucut.storage.util.S3Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

// "커밋 후 S3 삭제"의 공용 입구 (frame·media가 함께 쓴다). 아무것도 직접 지우지 않는다 —
// 관리 대상 key만 걸러 이벤트를 발행하고, 실제 삭제는 커밋 후 S3DeleteListener가 한다.
// 트랜잭션 밖에서 부르면 리스너가 조용히 안 불린다 (fallbackExecution=false) — 주의
@Component
@RequiredArgsConstructor
public class S3Deleter {

    private final ApplicationEventPublisher eventPublisher;

    // null·비관리 key·중복은 여기서 걸러서 받는다
    public void deleteAfterCommit(Collection<String> keys) {
        List<String> managed = keys.stream()
                .filter(S3Keys::isManagedKey)
                .distinct()
                .toList();
        if (managed.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new S3DeleteEvent(managed));
    }
}
