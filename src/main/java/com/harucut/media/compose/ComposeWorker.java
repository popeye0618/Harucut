package com.harucut.media.compose;

import com.harucut.media.service.ComposeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 합성의 백그라운드 실행. AFTER_COMMIT이라 Job 행이 보이는 상태에서만 시작하고,
// @Async라 요청 스레드를 즉시 놓아준다. 실행(다운로드·그리기·업로드)은 트랜잭션 밖 —
// 몇 초짜리 작업이 DB 커넥션을 물고 있으면 안 된다. DB 기록은 ComposeService의
// completeJob/failJob이 각자 새 트랜잭션에서 한다
@Slf4j
@Component
@RequiredArgsConstructor
public class ComposeWorker {

    private final ComposeExecutor composeExecutor;
    private final ComposeService composeService;

    @Async("composeTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ComposeRequestedEvent event) {
        try {
            composeExecutor.execute(event.spec(), event.sourceKeys(), event.resultKey());
            composeService.completeJob(event.jobId(), event.resultKey());
        } catch (Exception e) {
            log.error("네컷 합성 실패: jobId={}", event.jobId(), e);
            markFailed(event.jobId(), e.getMessage());
        }
    }

    // 실패 기록마저 실패하면 로그만 남는다 — Job은 PENDING으로 남고, 재실행(Phase 12)이 줍는다
    private void markFailed(Long jobId, String reason) {
        try {
            composeService.failJob(jobId, reason);
        } catch (Exception e) {
            log.error("합성 실패 기록도 실패: jobId={}", jobId, e);
        }
    }
}
