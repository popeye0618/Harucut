package com.harucut.config;

import com.harucut.storage.config.AwsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class SqsConfig {

    // ⚠️ 타임아웃을 재정의하려면 ComposeResultConsumer.WAIT_SECONDS(20)보다 길어야 한다.
    //    이 소비자는 롱폴링이라 메시지가 없으면 응답이 20초까지 늦게 온다 — 정상이다.
    //    소켓/API 호출 타임아웃을 그보다 짧게 잡으면 모든 폴링이 타임아웃 예외로 끝나고,
    //    소비자는 5초 백오프 후 재시도를 반복한다. 로그만 보면 "가끔 느린 것"처럼 보여서
    //    원인을 찾기 어렵다. 지금은 SDK 기본값(소켓 30초)이라 10초 여유가 있다
    @Bean
    public SqsClient sqsClient(AwsProperties properties) {
        return SqsClient.builder()
                .region(Region.of(properties.region()))
                .build();
    }
}
