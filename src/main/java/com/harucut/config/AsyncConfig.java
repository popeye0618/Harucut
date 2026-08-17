package com.harucut.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    // 네컷 합성 전용 풀 — 한 건이 메모리 ~100MB(6000×4000 캔버스)를 무는 작업이라
    // 동시 실행을 2개로 제한한다. 넘치는 요청은 큐에서 기다린다 — Job은 이미 PENDING으로
    // 커밋돼 있어 기다림이 유실이 되지는 않는다. (운영이 Lambda로 가면 이 제한의 의미는
    // "동시 Lambda 호출 대기 스레드 수"로 바뀐다)
    @Bean
    public ThreadPoolTaskExecutor composeTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("compose-");
        return executor;
    }
}
