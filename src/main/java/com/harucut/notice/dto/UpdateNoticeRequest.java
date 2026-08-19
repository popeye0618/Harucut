package com.harucut.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "공지 수정 요청")
public record UpdateNoticeRequest(

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        @Schema(description = "제목. 최대 200자", example = "서비스 점검 안내", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @NotBlank(message = "본문은 필수입니다.")
        @Schema(description = "본문", example = "안정적인 서비스 제공을 위해 점검을 진행합니다.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String content,

        @Schema(description = "상단 고정 여부. **생략하면 false 다** — 수정은 전체 교체라 고정된 공지를 수정하면서 이 필드를 빼면 고정이 풀린다", example = "false")
        boolean pinned
) {
}
