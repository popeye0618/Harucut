package com.harucut.auth.batch;

import com.harucut.auth.service.UserExitService;
import com.harucut.user.entity.User;
import com.harucut.user.enums.UserStatus;
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
import java.util.Map;

@Slf4j
@Configuration
public class UserDeletionJobConfig {

    public static final String JOB_NAME = "userDeletionJob";
    public static final String RUN_DATE = "runDate";

    @Bean
    public Job userDeletionJob(JobRepository jobRepository, Step userDeletionStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(userDeletionStep)
                .build();
    }

    @Bean
    public Step userDeletionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaCursorItemReader<Long> expiredExitTargetReader,
            ItemWriter<Long> userDeletionWriter) {
        return new StepBuilder("userDeletionStep", jobRepository)
                .<Long, Long>chunk(1)
                .transactionManager(transactionManager)
                .reader(expiredExitTargetReader)
                .writer(userDeletionWriter)
                .faultTolerant()
                .skipPolicy(new AlwaysSkipItemSkipPolicy())
                .skipListener(new SkipListener<Long, Long>() {
                    @Override
                    public void onSkipInWrite(Long userId, Throwable t) {
                        log.warn("[탈퇴 배치] userId={} 처리 실패 — 건너뛰고 계속", userId, t);
                    }
                })
                .build();
    }

    // @StepScope: 이 빈은 부팅 때가 아니라 "스텝이 실행되는 순간" 만들어진다.
    // 그래야 그 실행의 jobParameters(runDate)를 주입받을 수 있다 — 늦은 바인딩.
    // 페이징 reader를 안 쓰는 이유: 처리하면 행이 대상 조건에서 빠져나가므로
    // 2페이지를 읽을 때 기준이 밀려 절반을 건너뛴다. 커서는 한 번 연 결과를 끝까지 흘려 읽는다.
    @Bean
    @StepScope
    public JpaCursorItemReader<Long> expiredExitTargetReader(
            EntityManagerFactory entityManagerFactory,
            @Value("#{jobParameters['runDate']}") LocalDate runDate) {
        LocalDateTime threshold = runDate.atStartOfDay().minusDays(User.DELETION_GRACE_DAYS);

        return new JpaCursorItemReaderBuilder<Long>()
                .name("expiredExitTargetReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("""
                        select u.id from User u
                        where u.userStatus = :status and u.deleteRequestedAt < :threshold
                        """)
                .parameterValues(Map.of(
                        "status", UserStatus.DELETED_REQUESTED,
                        "threshold", threshold
                ))
                .build();
    }

    @Bean
    public ItemWriter<Long> userDeletionWriter(UserExitService userExitService) {
        return chunk -> chunk.getItems().forEach(userExitService::exit);
    }
}
