package com.harucut.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloud.aws")
public record AwsProperties(
        String region,
        S3 s3,
        Lambda lambda,
        Sqs sqs
) {
    public record S3(
            String bucket
    ) {
    }

    public record Lambda(
            String composeFunction
    ) {
    }

    public record Sqs(String composeResultQueueUrl) {
    }
}
