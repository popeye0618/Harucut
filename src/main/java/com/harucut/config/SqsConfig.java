package com.harucut.config;

import com.harucut.storage.config.AwsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class SqsConfig {

    @Bean
    public SqsClient sqsClient(AwsProperties properties) {
        return SqsClient.builder()
                .region(Region.of(properties.region()))
                .build();
    }
}
