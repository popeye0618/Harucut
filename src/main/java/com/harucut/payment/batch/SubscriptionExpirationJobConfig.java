package com.harucut.payment.batch;

import com.harucut.payment.config.PaymentProperties;
import com.harucut.subscription.enums.SubscriptionStatus;
import com.harucut.subscription.service.SubscriptionExpirationService;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.skip.AlwaysSkipItemSkipPolicy;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JpaCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class SubscriptionExpirationJobConfig {

    public static final String JOB_NAME = "subscriptionExpirationJob";
    public static final String RUN_DATE = "runDate";

    @Bean
    public Job subscriptionExpirationJob(JobRepository jobRepository, Step expirationStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(expirationStep)
                .build();
    }

    @Bean
    public Step expirationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaCursorItemReader<Long> expirableSubscriptionReader,
            ItemWriter<Long> expirationWriter) {
        return new StepBuilder("expirationStep", jobRepository)
                .<Long, Long>chunk(1)
                .transactionManager(transactionManager)
                .reader(expirableSubscriptionReader)
                .writer(expirationWriter)
                .faultTolerant()
                .skipPolicy(new AlwaysSkipItemSkipPolicy())
                .skipListener(new SkipListener<Long, Long>() {
                    @Override
                    public void onSkipInWrite(Long subscriptionId, Throwable t) {
                        log.warn("[만료 배치] subscriptionId={} 처리 실패 — 건너뛰고 계속", subscriptionId, t);
                    }
                })
                .build();
    }

    @Bean
    @StepScope
    public JpaCursorItemReader<Long> expirableSubscriptionReader(
            EntityManagerFactory entityManagerFactory,
            PaymentProperties paymentProperties,
            @Value("#{jobParameters['runDate']}") LocalDate runDate) {
        LocalDateTime baseTime = runDate.atStartOfDay();
        return new JpaCursorItemReaderBuilder<Long>()
                .name("expirableSubscriptionReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("""
                        select s.id from UserSubscription s
                        where s.currentPeriodEnd is not null
                            and ((s.subscriptionStatus in :immediateStatuses and s.currentPeriodEnd <= :baseTime)
                            or (s.subscriptionStatus = :pastDue and s.currentPeriodEnd <= :graceLimit))
                        """)
                .parameterValues(Map.of(
                        "immediateStatuses", List.of(SubscriptionStatus.CANCELED, SubscriptionStatus.GRANTED),
                        "pastDue", SubscriptionStatus.PAST_DUE,
                        "baseTime", baseTime,
                        "graceLimit", baseTime.minusDays(paymentProperties.graceDays())))
                .build();
    }

    @Bean
    @StepScope
    public ItemWriter<Long> expirationWriter(
            SubscriptionExpirationService subscriptionExpirationService,
            @Value("#{jobParameters['runDate']}") LocalDate runDate) {
        LocalDateTime baseTime = runDate.atStartOfDay();
        return chunk -> chunk.getItems().forEach(id -> subscriptionExpirationService.expire(id, baseTime));
    }
}
