package com.harucut.payment.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobOperator;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionRenewalScheduler")
class SubscriptionRenewalSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 2, 0);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Mock
    private JobOperator jobOperator;

    @Mock
    private Job subscriptionRenewalJob;

    private SubscriptionRenewalScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        scheduler = new SubscriptionRenewalScheduler(jobOperator, subscriptionRenewalJob, clock);
    }

    @Test
    @DisplayName("오늘 날짜가 식별 파라미터로 실려 잡이 기동된다")
    void startsJobWithTodayAsRunDate() throws Exception {
        scheduler.run();

        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        then(jobOperator).should().start(eq(subscriptionRenewalJob), captor.capture());
        assertThat(captor.getValue().getLocalDate(SubscriptionRenewalJobConfig.RUN_DATE))
                .isEqualTo(LocalDate.of(2026, 8, 18));
    }

    // 서버 2대가 같은 새벽에 깨어나도 한쪽은 이 예외를 받는다 — 스케줄러는 조용히 물러난다
    @Test
    @DisplayName("이미 진행 중인 실행이 있으면 예외를 삼키고 끝난다")
    void swallowsDuplicateRun() throws Exception {
        willThrow(new JobExecutionAlreadyRunningException("이미 실행 중"))
                .given(jobOperator).start(eq(subscriptionRenewalJob), any(JobParameters.class));

        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();
    }
}
