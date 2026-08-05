package com.harucut.notice.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.notice.dto.CreateNoticeRequest;
import com.harucut.notice.dto.UpdateNoticeRequest;
import com.harucut.notice.entity.Notice;
import com.harucut.notice.exception.NoticeErrorCode;
import com.harucut.notice.repository.NoticeRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NoticeAdminServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 15, 30);
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    private NoticeRepository noticeRepository;

    private NoticeAdminService noticeAdminService;

    @BeforeEach
    void setUp() {
        noticeAdminService = new NoticeAdminService(noticeRepository, FIXED_CLOCK);
    }

    @Nested
    @DisplayName("publishNotice")
    class PublishNotice {

        @Test
        @DisplayName("게시하면 published가 true가 되고 publishedAt이 고정된 now로 찍힌다")
        void publish() {
            Notice notice = new Notice("서비스 점검 안내", "본문", false);

            given(noticeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(notice));

            noticeAdminService.publishNotice(1L);

            assertThat(notice.isPublished()).isTrue();
            assertThat(notice.getPublishedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("이미 게시된 공지를 다시 게시하면 publishedAt이 갱신된다")
        void republishRefreshesPublishedAt() {
            Notice notice = new Notice("서비스 점검 안내", "본문", false);
            notice.publish(NOW.minusDays(3));
            given(noticeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(notice));

            noticeAdminService.publishNotice(1L);

            assertThat(notice.getPublishedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("존재하지 않으면 NOTICE-001을 던진다")
        void notFound() {
            given(noticeRepository.findByIdAndDeletedAtIsNull(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> noticeAdminService.publishNotice(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(NoticeErrorCode.NOTICE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("deleteNotice")
    class DeleteNotice {

        @Test
        @DisplayName("삭제하면 deletedAt만 찍히고 하드 삭제하지 않는다")
        void softDelete() {
            Notice notice = new Notice("서비스 점검 안내", "본문", false);
            given(noticeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(notice));

            noticeAdminService.deleteNotice(1L);

            assertThat(notice.getDeletedAt()).isEqualTo(NOW);
            then(noticeRepository).should(never()).delete(any());
        }
    }

    @Nested
    @DisplayName("createNotice")
    class CreateNotice {

        @Test
        @DisplayName("생성된 공지는 미게시 초안 상태다")
        void savesDraft() {
            noticeAdminService.createNotice(new CreateNoticeRequest("서비스 점검 안내", "본문", true));

            ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
            then(noticeRepository).should().save(captor.capture());

            assertThat(captor.getValue())
                    .extracting(Notice::getTitle, Notice::isPinned, Notice::isPublished, Notice::getPublishedAt)
                    .containsExactly("서비스 점검 안내", true, false, null);
        }
    }

    @Nested
    @DisplayName("updateNotice")
    class UpdateNotice {

        @Test
        @DisplayName("제목·본문·고정 여부를 교체한다")
        void update() {
            Notice notice = new Notice("옛 제목", "옛 본문", false);
            given(noticeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(notice));

            noticeAdminService.updateNotice(1L, new UpdateNoticeRequest("새 제목", "새 본문", true));

            assertThat(notice)
                    .extracting(Notice::getTitle, Notice::getContent, Notice::isPinned)
                    .containsExactly("새 제목", "새 본문", true);
        }

        @Test
        @DisplayName("수정해도 게시 상태와 게시 시각은 그대로다")
        void keepsPublishState() {
            Notice notice = new Notice("옛 제목", "옛 본문", false);
            notice.publish(NOW.minusDays(3));
            given(noticeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(notice));

            noticeAdminService.updateNotice(1L, new UpdateNoticeRequest("새 제목", "새 본문", true));

            assertThat(notice.isPublished()).isTrue();
            assertThat(notice.getPublishedAt()).isEqualTo(NOW.minusDays(3));
        }
    }

    @Nested
    @DisplayName("unPublishNotice")
    class UnPublishNotice {

        @Test
        @DisplayName("게시를 취소하면 publishedAt이 null로 되돌아간다")
        void unPublish() {
            Notice notice = new Notice("서비스 점검 안내", "본문", false);
            notice.publish(NOW.minusDays(3));
            given(noticeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(notice));

            noticeAdminService.unPublishNotice(1L);

            assertThat(notice.isPublished()).isFalse();
            assertThat(notice.getPublishedAt()).isNull();
        }

        @Test
        @DisplayName("미게시 공지를 다시 게시 취소해도 그대로 미게시다")
        void unPublishDraft() {
            Notice notice = new Notice("초안", "본문", false);
            given(noticeRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(notice));

            noticeAdminService.unPublishNotice(1L);

            assertThat(notice.isPublished()).isFalse();
            assertThat(notice.getPublishedAt()).isNull();
        }
    }
}