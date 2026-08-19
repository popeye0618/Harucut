package com.harucut.notice.dto;

import com.harucut.notice.entity.Notice;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "공지 (관리자용 — 미게시 포함)")
public record NoticeAdminResponse(

        @Schema(description = "수정·삭제·게시에 쓰는 내부 ID. **공개 API 의 publicId 와 다르다**", example = "1")
        Long noticeId,

        @Schema(description = "공개 API 가 쓰는 ID", example = "aB3dE7fG9h")
        String publicId,

        @Schema(description = "제목", example = "서비스 점검 안내")
        String title,

        @Schema(description = "본문", example = "안정적인 서비스 제공을 위해 점검을 진행합니다.")
        String content,

        @Schema(description = "상단 고정 여부", example = "false")
        boolean pinned,

        @Schema(description = "게시 여부. false 면 공개 목록에 안 나온다", example = "false")
        boolean published,

        @Schema(description = "게시 시각. 미게시면 null", example = "2026-07-22T10:00:00")
        LocalDateTime publishedAt
) {
    public static NoticeAdminResponse from(Notice notice) {
        return new NoticeAdminResponse(
                notice.getId(),
                notice.getPublicId(),
                notice.getTitle(),
                notice.getContent(),
                notice.isPinned(),
                notice.isPublished(),
                notice.getPublishedAt()
        );
    }
}
