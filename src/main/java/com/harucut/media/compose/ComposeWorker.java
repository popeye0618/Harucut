package com.harucut.media.compose;

import com.harucut.media.service.ComposeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;

// 합성의 백그라운드 실행. AFTER_COMMIT이라 Job 행이 보이는 상태에서만 시작하고,
// @Async라 요청 스레드를 즉시 놓아준다. 실행(다운로드·그리기·업로드)은 트랜잭션 밖 —
// 몇 초짜리 작업이 DB 커넥션을 물고 있으면 안 된다. DB 기록은 ComposeService의
// completeJob/failJob이 각자 새 트랜잭션에서 한다
@Slf4j
@Component
public class ComposeWorker {

    private final ComposeExecutor composeExecutor;
    private final ComposeService composeService;
    private final Duration staleAfter;

    public ComposeWorker(ComposeExecutor composeExecutor, ComposeService composeService,
                         @Value("${compose.stale-after:5m}")Duration staleAfter) {
        this.composeExecutor = composeExecutor;
        this.composeService = composeService;
        this.staleAfter = staleAfter;
    }

    @Async("composeTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ComposeRequestedEvent event) {
        execute(event);
    }

    // 재실행 스케줄러가 부르는 문. 이벤트가 아니라 직접 호출이라 트랜잭션 리스너를 안 탄다.
    // 큐가 가득 차면 TaskRejectedException 이 호출자(스케줄러) 스레드로 그대로 올라간다 —
    // 제출은 호출자 스레드에서 일어나기 때문이다. 스케줄러가 그걸 보고 멈춘다
    @Async("composeTaskExecutor")
    public void rerun(ComposeRequestedEvent event) {
        execute(event);
    }

    private void execute(ComposeRequestedEvent event) {
        if (!composeService.claim(event.jobId(), staleAfter)) {
            log.debug("이미 실행 중이거나 끝난 Job — 건너뛴다: jobId={}", event.jobId());
            return;
        }
        try {
            composeExecutor.execute(event.spec(), event.sourceKeys(),
                    event.resultKey(), event.thumbnailKey());
            composeService.completeJob(event.jobId(), event.resultKey(), event.thumbnailKey());
        } catch (Exception e) {
            log.error("네컷 합성 실패: jobId={}", event.jobId(), e);
            markFailed(event.jobId(), e.getMessage());
        }
    }

    // 실패 기록마저 실패하면 로그만 남는다 — Job은 PENDING으로 남고, ComposeRerunScheduler가 줍는다
    private void markFailed(Long jobId, String reason) {
        try {
            composeService.failJob(jobId, reason);
        } catch (Exception e) {
            log.error("합성 실패 기록도 실패: jobId={}", jobId, e);
        }
    }
}
