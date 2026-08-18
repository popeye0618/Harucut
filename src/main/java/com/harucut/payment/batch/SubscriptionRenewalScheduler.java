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
public class SubscriptionRenewalScheduler {

    private final JobOperator jobOperator;
    private final Job subscriptionRenewalJob;
    private final Clock clock;

    @Scheduled(cron = "0 0 2 * * *")
    public void run() {
        LocalDate today = LocalDate.now(clock);
        try {
            jobOperator.start(subscriptionRenewalJob, new JobParametersBuilder()
                    .addLocalDate(SubscriptionRenewalJobConfig.RUN_DATE, today)
                    .toJobParameters());
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException e) {
            log.info("[갱신 배치] {} 실행이 이미 있거나 진행 중 — 중복 기동 무시", today);
        } catch (Exception e) {
            log.error("[갱신 배치] 기동 실패", e);
        }
    }
}
