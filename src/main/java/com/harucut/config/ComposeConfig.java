package com.harucut.config;

import com.harucut.media.compose.FourcutRenderer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ComposeConfig {

    // 공유 모듈(compose-core)은 스프링을 모른다 — Lambda 배포물에 스프링이 끌려가면 안 되니까.
    // 그래서 렌더러의 빈 등록은 앱이 한다
    @Bean
    public FourcutRenderer fourcutRenderer() {
        return new FourcutRenderer();
    }
}
