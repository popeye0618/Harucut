package com.harucut.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloud.aws")
public record AwsProperties(
        String region,
        S3 s3,
        // compose.executor=lambda일 때만 읽는다 — 로컬(인프로세스)은 없어도 된다
        Lambda lambda
) {
    public record S3(
            String bucket
    ) {
    }

    public record Lambda(
            String composeFunction
    ) {
    }
}
