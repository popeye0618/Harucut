package com.harucut.auth.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeletionScheduler {

    private final JobOperator jobOperator;
    private final Job userDeletionJob;
    private final Clock clock;

    // 날짜를 식별 파라미터로 주므로 "하루에 JobInstance 하나"가 강제된다.
    // 같은 날 다른 인스턴스(서버 2대)가 또 띄우면 아래 예외로 거절 — 메타테이블 unique가 곧 분산 락이다.
    // 실패한 날은 같은 파라미터로 재기동하면 재시작으로 이어진다.
    @Scheduled(cron = "0 0 1 * * *")
    public void run() {
        LocalDate today = LocalDate.now(clock);
        try {
            jobOperator.start(userDeletionJob, new JobParametersBuilder()
                    .addLocalDate(UserDeletionJobConfig.RUN_DATE, today)
                    .toJobParameters());
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException e) {
            log.info("[탈퇴 배치] {} 실행이 이미 있거나 진행 중 — 중복 기동 무시", today);
        } catch (Exception e) {
            log.error("[탈퇴 배치] 기동 실패", e);
        }
    }
}
