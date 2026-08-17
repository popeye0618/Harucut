package com.harucut.storage.event;

import com.harucut.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// S3 삭제는 롤백이 없으므로 DB가 진실로 확정된 뒤에만 실행한다.
// 실패의 최악은 고아 파일(스토리지 비용) — 사용자에게 보이는 장애가 아니다. (Phase 8 결정)
@Slf4j
@Component
@RequiredArgsConstructor
public class S3DeleteListener {

    private final FileStorageService fileStorageService;

    // 트랜잭션 밖에서 발행된 이벤트는 (fallbackExecution 기본 false) 리스너가 아예 안 불린다 —
    // 발행하는 서비스 메서드는 반드시 @Transactional 안이어야 한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(S3DeleteEvent event) {
        for (String key : event.keys()) {
            try {
                fileStorageService.delete(key);
            } catch (RuntimeException e) {
                // key 단위로 삼킨다: 하나가 실패해도 나머지는 지우고, 예외를 새 나가게 하지 않는다.
                // 커밋 후 예외가 전파되면 "저장은 됐는데 500"이 된다
                log.error("S3 삭제 실패 — 고아 파일로 남는다: {}", key, e);
            }
        }
    }
}
