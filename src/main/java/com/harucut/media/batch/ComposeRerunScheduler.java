package com.harucut.media.batch;

import com.harucut.media.compose.ComposeRequestedEvent;
import com.harucut.media.compose.ComposeWorker;
import com.harucut.media.service.ComposeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class ComposeRerunScheduler {

    private final ComposeService composeService;
    private final ComposeWorker composeWorker;
    private final Duration staleAfter;
    private final int batchSize;

    public ComposeRerunScheduler(ComposeService composeService, ComposeWorker composeWorker,
                                 @Value("${compose.stale-after:5m}") Duration staleAfter,
                                 @Value("${compose.rerun.batch-size:20}") int batchSize) {
        this.composeService = composeService;
        this.composeWorker = composeWorker;
        this.staleAfter = staleAfter;
        this.batchSize = batchSize;
    }

    // fixedDelay: 이전 실행이 끝난 뒤부터 센다 — 한 주기가 길어져도 겹치지 않는다
    @Scheduled(fixedDelayString = "${compose.rerun.interval:30s}")
    public void run() {
        List<ComposeRequestedEvent> stalled = composeService.findStalled(staleAfter, batchSize);

        if(stalled.isEmpty()) {
            return;
        }

        int submitted = 0;
        for (ComposeRequestedEvent event : stalled) {
            try {
                composeWorker.rerun(event);
                submitted++;
            } catch (TaskRejectedException e) {
                break;
            }
        }
        log.info("[합성 재실행] 대상 {}건 중 {}건 재투입", stalled.size(), submitted);
    }
}
