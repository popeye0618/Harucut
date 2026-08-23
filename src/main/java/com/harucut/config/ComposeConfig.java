package com.harucut.config;

import com.harucut.storage.config.AwsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;

import java.time.Duration;

@Configuration
public class ComposeConfig {

    // ⚠️ 타임아웃을 SDK 기본값에 맡기면 안 된다. 이 클라이언트의 invoke 는 AFTER_COMMIT 리스너를 통해
    //    HTTP 요청 스레드에서 실행된다 (ComposeWorker 주석 참고). 기본값은 재시도 4회 ×
    //    (연결 2초 + 소켓 30초) ≈ 128초라, Lambda 가 느려지는 순간 그 시간이 그대로 요청 스레드에 붙는다.
    //    톰캣 기본 200 스레드에서 합성이 초당 10건이면 20초 만에 스레드가 전부 잠기고,
    //    합성 장애 하나가 로그인·결제까지 멈춘다.
    //
    //    접수(EVENT 호출)는 페이로드만 넘기고 202 를 받는 수십 ms 짜리 호출이다 — 2초를 못 넘기면
    //    그건 이미 비정상이고, 기다리는 것보다 포기하는 편이 낫다. 접수에 실패해도 Job 은 PENDING 으로
    //    남아 ComposeRerunScheduler 가 다시 던지므로 유실이 아니다.
    //
    //    apiCallAttemptTimeout: 시도 1회의 상한. apiCallTimeout: 재시도까지 포함한 전체 상한.
    //    둘 다 준다 — 앞의 것만 주면 재시도 횟수만큼 곱해진다.
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RETRIES = 2;

    @Bean
    public LambdaClient lambdaClient(AwsProperties properties) {
        return LambdaClient.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(ATTEMPT_TIMEOUT)
                        .apiCallTimeout(TOTAL_TIMEOUT)
                        .retryPolicy(RetryPolicy.builder().numRetries(MAX_RETRIES).build())
                        .build())
                .build();
    }
}
