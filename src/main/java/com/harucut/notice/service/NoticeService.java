package com.harucut.notice.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.response.PageResponse;
import com.harucut.common.utils.PageRequests;
import com.harucut.notice.dto.NoticeResponse;
import com.harucut.notice.entity.Notice;
import com.harucut.notice.exception.NoticeErrorCode;
import com.harucut.notice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public PageResponse<NoticeResponse> getPublishedNotices(int page, int size) {
        Pageable pageable = PageRequests.of(page, size);

        Page<NoticeResponse> notices = noticeRepository.findByPublishedTrueAndDeletedAtIsNullOrderByPinnedDescPublishedAtDesc(pageable)
                .map(NoticeResponse::from);

        return PageResponse.from(notices);
    }

    public NoticeResponse getPublishedNotice(String publicId) {
        Notice notice = noticeRepository.findByPublicIdAndPublishedTrueAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND));

        return NoticeResponse.from(notice);
    }
}
