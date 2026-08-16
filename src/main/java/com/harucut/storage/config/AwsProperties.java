package com.harucut.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloud.aws")
public record AwsProperties(
        String region,
        S3 s3
) {
    public record S3(
            String bucket
    ) {
    }
}
