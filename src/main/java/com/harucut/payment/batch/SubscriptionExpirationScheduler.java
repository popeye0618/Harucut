package com.harucut.payment.batch;

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
public class SubscriptionExpirationScheduler {

    private final JobOperator jobOperator;
    private final Job subscriptionExpirationJob;
    private final Clock clock;

    // 갱신(2:00) 뒤 30분 — PAST_DUE 3일째의 마지막 재청구가 먼저 돌고, 실패한 것만 여기서 강등된다
    @Scheduled(cron = "0 30 2 * * *")
    public void run() {
        LocalDate today = LocalDate.now(clock);
        try {
            jobOperator.start(subscriptionExpirationJob, new JobParametersBuilder()
                    .addLocalDate(SubscriptionExpirationJobConfig.RUN_DATE, today)
                    .toJobParameters());
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException e) {
            log.info("[만료 배치] {} 실행이 이미 있거나 진행 중 — 중복 기동 무시", today);
        } catch (Exception e) {
            log.error("[만료 배치] 기동 실패", e);
        }
    }
}