package com.harucut.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "공지 생성 요청")
public record CreateNoticeRequest(

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        @Schema(description = "제목. 최대 200자", example = "서비스 점검 안내", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @NotBlank(message = "본문은 필수입니다.")
        @Schema(description = "본문", example = "안정적인 서비스 제공을 위해 점검을 진행합니다.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String content,

        @Schema(description = "상단 고정 여부. **생략하면 false 다** — 기본값이며 나중에 수정으로 바꿀 수 있다", example = "false")
        boolean pinned
) {
}
