package com.harucut.config;

import com.harucut.storage.config.AwsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;

@Configuration
public class ComposeConfig {

    @Bean
    public LambdaClient lambdaClient(AwsProperties properties) {
        return LambdaClient.builder()
                .region(Region.of(properties.region()))
                .build();
    }
}
