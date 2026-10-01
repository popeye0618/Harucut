package com.harucut.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.context.annotation.Configuration;

/**
 * 배치 메타데이터(JobRepository)를 DB의 BATCH_* 테이블에 기록한다.
 *
 * <p>Boot 4의 기본은 ResourcelessJobRepository — 아무것도 저장하지 않는다. 그대로 두면
 * 실행 이력이 남지 않고, 스케줄러가 기대하는 "같은 날짜 중복 기동 거절"
 * (JobInstanceAlreadyCompleteException)이 프로세스 재시작을 넘어 보장되지 않는다.
 *
 * <p>{@code @EnableBatchProcessing}이 있으면 Boot의 배치 자동구성은 물러나고,
 * {@code @EnableJdbcJobRepository}가 {@code dataSource}/{@code transactionManager} 빈으로
 * JDBC 저장소를 구성한다. 테이블 생성은 배치가 해주지 않는다 —
 * application.yaml의 spring.sql.init이 잡아 있는 스크립트로 만든다.
 */
@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository
public class BatchConfig {
}
