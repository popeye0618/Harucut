package com.harucut.notice.dto;

import com.harucut.notice.entity.Notice;

import java.time.LocalDateTime;

public record NoticeResponse(
        String publicId,
        String title,
        String content,
        boolean pinned,
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
