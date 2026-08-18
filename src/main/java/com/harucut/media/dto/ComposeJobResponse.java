package com.harucut.media.dto;

import com.harucut.media.entity.ComposeJob;
import com.harucut.media.enums.ComposeStatus;
import io.swagger.v3.oas.annotations.media.Schema;

// 폴링 응답 — PENDING이면 mediaId·failureReason이 null이라 NON_NULL 직렬화로 빠진다
@Schema(description = "합성 작업 상태")
public record ComposeJobResponse(

        @Schema(description = "작업 ID. 이 값으로 폴링한다", example = "12")
        Long jobId,

        @Schema(description = """
                `PENDING` 진행 중 (1~2초 간격으로 다시 조회) ·
                `DONE` 완료 — `mediaId` 가 함께 온다 ·
                `FAILED` 실패 — `failureReason` 이 함께 온다

                진행률이나 `RUNNING` 은 없다. 셋 중 하나다.""",
                example = "PENDING")
        ComposeStatus status,

        @Schema(description = """
                완성된 사진의 미디어 ID. **`DONE` 일 때만 실린다** — 아니면 키 자체가 없다.
                이 값으로 보관함·다운로드 API 를 그대로 쓴다.""",
                example = "1")
        Long mediaId,

        @Schema(description = "실패 사유. **`FAILED` 일 때만 실린다.** 사용자에게 그대로 보여줄 문구는 아니다",
                example = "원본 이미지를 읽을 수 없습니다")
        String failureReason
) {
    public static ComposeJobResponse from(ComposeJob job) {
        return new ComposeJobResponse(
                job.getId(), job.getStatus(), job.getMediaId(), job.getFailureReason());
    }
}
