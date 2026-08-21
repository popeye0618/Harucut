package com.harucut.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 네컷 합성 전용 풀 — 한 건이 메모리 ~100MB(6000×4000 캔버스)를 무는 작업이라
     * 동시 실행을 2개로 제한한다. 넘치는 요청은 큐에서 기다린다.
     * (운영이 Lambda로 가면 이 제한의 의미는 "동시 Lambda 호출 대기 스레드 수"로 바뀐다)
     *
     * <p><b>큐가 가득 차면 거부된다.</b> 그 {@code RejectedExecutionException} 이 터지는 곳이
     * {@code AFTER_COMMIT} 리스너 안이라 <b>요청은 이미 202 를 받고 나간 뒤</b>이고, 거부는
     * 응답에 실리지 않는다. 그래서 거부된 Job 은 {@code startedAt} 이 비어 있는 PENDING 으로
     * 남고 {@code ComposeRerunScheduler} 가 그것을 다시 집어간다 — <b>이 풀은 유실 지점이
     * 아니라 처리량 조절 장치다.</b> 같은 이유로 종료 시 큐를 버리지 않도록
     * {@code waitForTasksToCompleteOnShutdown} 을 켠다 (기본값은 {@code shutdownNow()} 후
     * 남은 작업을 전부 취소한다 — 배포 때마다 큐가 통째로 날아간다).
     *
     * <p>값을 밖으로 뺀 이유는 환경마다 달라야 할 값이기 때문이다.
     * 기본값은 종전 하드코딩 값과 같게 두어 프로퍼티가 없을 때 동작이 바뀌지 않는다.
     */
    @Bean
    public ThreadPoolTaskExecutor composeTaskExecutor(
            @Value("${compose.pool.core-size:2}") int coreSize,
            @Value("${compose.pool.max-size:2}") int maxSize,
            @Value("${compose.pool.queue-capacity:100}") int queueCapacity) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("compose-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
