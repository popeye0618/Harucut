package com.harucut.notice.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.common.response.PageResponse;
import com.harucut.notice.dto.NoticeResponse;
import com.harucut.notice.entity.Notice;
import com.harucut.notice.exception.NoticeErrorCode;
import com.harucut.notice.repository.NoticeRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    private static final LocalDateTime FIXED = LocalDateTime.of(2026, 7, 22, 10, 0);

    @Mock
    private NoticeRepository noticeRepository;

    private NoticeService noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeService(noticeRepository);
    }

    @Nested
    @DisplayName("getPublishedNotices")
    class GetPublishedNotices {

        @Test
        @DisplayName("page가 음수면 GEN-002를 던지고 조회하지 않는다")
        void negativePage() {
            assertThatThrownBy(() -> noticeService.getPublishedNotices(-1, 10))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

            then(noticeRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("size가 1 미만이면 GEN-002를 던지고 조회하지 않는다")
        void zeroSize() {
            assertThatThrownBy(() -> noticeService.getPublishedNotices(0, 0))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

            then(noticeRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("페이지 메타데이터를 유지한 채 DTO로 변환한다")
        void keepPageMetadata() {
            Page<Notice> page = new PageImpl<>(List.of(published("서비스 점검 안내")), PageRequest.of(0, 10), 12L);

            given(noticeRepository.findByPublishedTrueAndDeletedAtIsNullOrderByPinnedDescPublishedAtDesc(PageRequest.of(0, 10)))
                    .willReturn(page);

            PageResponse<NoticeResponse> result = noticeService.getPublishedNotices(0, 10);

            assertThat(result)
                    .extracting(PageResponse::totalElements, PageResponse::totalPages,
                            PageResponse::number, PageResponse::size)
                    .containsExactly(12L, 2, 0, 10);

            assertThat(result.content())
                    .extracting(NoticeResponse::title)
                    .containsExactly("서비스 점검 안내");
        }

        private Notice published(String title) {
            Notice notice = new Notice(title, "본문", false);
            notice.publish(FIXED);
            return notice;
        }
    }

    @Nested
    @DisplayName("getPublishedNotice")
    class GetPublishedNotice {

        @Test
        @DisplayName("조회되지 않으면 NOTICE-001을 던진다")
        void notFound() {
            given(noticeRepository.findByPublicIdAndPublishedTrueAndDeletedAtIsNull("nope"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> noticeService.getPublishedNotice("nope"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(NoticeErrorCode.NOTICE_NOT_FOUND);
        }
    }
}