package com.harucut.notice.repository;

import com.harucut.config.JpaAuditingConfig;
import com.harucut.config.TimeConfig;
import com.harucut.notice.entity.Notice;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditingConfig.class, TimeConfig.class})
class NoticeRepositoryTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 7, 22, 10, 0);
    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 10);

    @Autowired
    private NoticeRepository noticeRepository;

    @Test
    @DisplayName("공개 목록은 고정 공지가 먼저, 그다음 게시 최신순이다")
    void publicListOrder() {
        noticeRepository.save(published("오래된 일반", false, BASE.minusDays(2)));
        noticeRepository.save(published("최신 일반", false, BASE));
        noticeRepository.save(published("고정", true, BASE.minusDays(5)));

        assertThat(noticeRepository
                .findByPublishedTrueAndDeletedAtIsNullOrderByPinnedDescPublishedAtDesc(FIRST_PAGE)
                .getContent())
                .extracting(Notice::getTitle)
                .containsExactly("고정", "최신 일반", "오래된 일반");
    }

    @Test
    @DisplayName("미게시 공지는 공개 목록에 나오지 않는다")
    void draftExcludedFromPublicList() {
        noticeRepository.save(new Notice("초안", "본문", false));
        noticeRepository.save(published("게시됨", false, BASE));

        assertThat(noticeRepository
                .findByPublishedTrueAndDeletedAtIsNullOrderByPinnedDescPublishedAtDesc(FIRST_PAGE)
                .getContent())
                .extracting(Notice::getTitle)
                .containsExactly("게시됨");
    }

    @Test
    @DisplayName("삭제된 공지는 게시 상태여도 공개 목록에 나오지 않는다")
    void softDeletedExcludedFromPublicList() {
        Notice deleted = published("삭제됨", false, BASE);
        deleted.softDelete(BASE.plusDays(1));
        noticeRepository.save(deleted);
        noticeRepository.save(published("살아있음", false, BASE));

        assertThat(noticeRepository
                .findByPublishedTrueAndDeletedAtIsNullOrderByPinnedDescPublishedAtDesc(FIRST_PAGE)
                .getContent())
                .extracting(Notice::getTitle)
                .containsExactly("살아있음");
    }

    @Test
    @DisplayName("미게시 공지는 publicId로 조회되지 않는다")
    void draftNotFoundByPublicId() {
        Notice draft = noticeRepository.save(new Notice("초안", "본문", false));

        assertThat(noticeRepository.findByPublicIdAndPublishedTrueAndDeletedAtIsNull(draft.getPublicId()))
                .isEmpty();
    }

    @Test
    @DisplayName("삭제된 공지는 id로도 조회되지 않는다")
    void softDeletedNotFoundById() {
        Notice deleted = published("삭제됨", false, BASE);
        deleted.softDelete(BASE.plusDays(1));
        Notice saved = noticeRepository.save(deleted);

        assertThat(noticeRepository.findByIdAndDeletedAtIsNull(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("관리자 목록에는 미게시 공지도 나온다")
    void adminListIncludesDrafts() {
        noticeRepository.save(new Notice("초안", "본문", false));
        noticeRepository.save(published("게시됨", false, BASE));

        assertThat(noticeRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(FIRST_PAGE).getContent())
                .extracting(Notice::getTitle)
                .containsExactlyInAnyOrder("초안", "게시됨");
    }

    private Notice published(String title, boolean pinned, LocalDateTime publishedAt) {
        Notice notice = new Notice(title, "본문", pinned);
        notice.publish(publishedAt);
        return notice;
    }
}