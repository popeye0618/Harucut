package com.harucut.config;

import com.harucut.media.compose.FourcutRenderer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.lambda.LambdaClient;

@Configuration
public class ComposeConfig {

    // 공유 모듈(compose-core)은 스프링을 모른다 — Lambda 배포물에 스프링이 끌려가면 안 되니까.
    // 그래서 렌더러의 빈 등록은 앱이 한다
    @Bean
    public FourcutRenderer fourcutRenderer() {
        return new FourcutRenderer();
    }

    // region·자격증명은 S3와 같은 기본 체인. lambda 실행기를 켰을 때만 만든다
    @Bean
    @ConditionalOnProperty(name = "compose.executor", havingValue = "lambda")
    public LambdaClient lambdaClient() {
        return LambdaClient.create();
    }
}
