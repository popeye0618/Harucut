package com.harucut.notice.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.response.PageResponse;
import com.harucut.common.utils.PageRequests;
import com.harucut.notice.dto.CreateNoticeRequest;
import com.harucut.notice.dto.NoticeAdminResponse;
import com.harucut.notice.dto.UpdateNoticeRequest;
import com.harucut.notice.entity.Notice;
import com.harucut.notice.exception.NoticeErrorCode;
import com.harucut.notice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class NoticeAdminService {

    private final NoticeRepository noticeRepository;
    private final Clock clock;

    public void createNotice(CreateNoticeRequest request) {
        noticeRepository.save(Notice.builder()
                .title(request.title())
                .content(request.content())
                .pinned(request.pinned())
                .build());
    }

    public void updateNotice(Long noticeId, UpdateNoticeRequest request) {
        getNotice(noticeId).update(request.title(), request.content(), request.pinned());
    }

    public void publishNotice(Long noticeId) {
        getNotice(noticeId).publish(LocalDateTime.now(clock));
    }

    public void unPublishNotice(Long noticeId) {
        getNotice(noticeId).unPublish();
    }

    public void deleteNotice(Long noticeId) {
        getNotice(noticeId).softDelete(LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public PageResponse<NoticeAdminResponse> listAllNotice(int page, int size) {
        Pageable pageable = PageRequests.of(page, size);

        Page<NoticeAdminResponse> notices = noticeRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(pageable)
                .map(NoticeAdminResponse::from);

        return PageResponse.from(notices);
    }

    private Notice getNotice(Long noticeId) {
        return noticeRepository.findByIdAndDeletedAtIsNull(noticeId)
                .orElseThrow(() -> new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND));
    }
}
