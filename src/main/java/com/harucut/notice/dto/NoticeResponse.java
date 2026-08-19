package com.harucut.notice.dto;

import com.harucut.notice.entity.Notice;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "공지 (게시된 것만)")
public record NoticeResponse(

        @Schema(description = "단건 조회에 쓰는 공개 ID", example = "aB3dE7fG9h")
        String publicId,

        @Schema(description = "제목", example = "서비스 점검 안내")
        String title,

        @Schema(description = "본문. **목록 응답에도 전문이 그대로 들어온다**",
                example = "안정적인 서비스 제공을 위해 점검을 진행합니다.")
        String content,

        @Schema(description = "상단 고정 여부. 목록은 고정된 것이 먼저 온다", example = "true")
        boolean pinned,

        @Schema(description = "게시 시각", example = "2026-07-22T10:00:00")
        LocalDateTime publishedAt
) {

    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(
                notice.getPublicId(),
                notice.getTitle(),
                notice.getContent(),
                notice.isPinned(),
                notice.getPublishedAt()
        );
    }
}
