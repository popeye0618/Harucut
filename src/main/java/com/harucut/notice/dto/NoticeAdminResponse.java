package com.harucut.notice.dto;

import com.harucut.notice.entity.Notice;

import java.time.LocalDateTime;

public record NoticeAdminResponse(
        Long noticeId,
        String publicId,
        String title,
        String content,
        boolean pinned,
        boolean published,
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
