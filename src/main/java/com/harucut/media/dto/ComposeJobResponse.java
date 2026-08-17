package com.harucut.media.dto;

import com.harucut.media.entity.ComposeJob;
import com.harucut.media.enums.ComposeStatus;

// 폴링 응답 — PENDING이면 mediaId·failureReason이 null이라 NON_NULL 직렬화로 빠진다
public record ComposeJobResponse(
        Long jobId,
        ComposeStatus status,
        Long mediaId,
        String failureReason
) {

    public static ComposeJobResponse from(ComposeJob job) {
        return new ComposeJobResponse(
                job.getId(), job.getStatus(), job.getMediaId(), job.getFailureReason());
    }
}
