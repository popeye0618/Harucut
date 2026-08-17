package com.harucut.media.repository;

import com.harucut.config.JpaAuditingConfig;
import com.harucut.media.entity.UserMedia;
import com.harucut.support.FixedClockConfig;
import com.harucut.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Hibernate가 부팅 시 전체 엔티티의 컨버터(BackgroundConverter)를 만들며 ObjectMapper 빈을
// 요구하므로 @AutoConfigureJson이 필요하다 (frame 도메인과 무관한 테스트여도)
@DataJpaTest
@AutoConfigureJson
@Import({JpaAuditingConfig.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("UserMediaRepository")
class UserMediaRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserMediaRepository userMediaRepository;

    @Test
    @DisplayName("저장하면 id와 createdAt이 채워지고 그대로 다시 읽힌다")
    void roundTrip() {
        User user = persistUser("owner@harucut.com");
        UserMedia saved = persistMedia(user, "uploads/users/abc/fourcuts/a.png");
        em.flush();
        em.clear();

        UserMedia found = userMediaRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getS3Key()).isEqualTo("uploads/users/abc/fourcuts/a.png");
        assertThat(found.getDisplayName()).isEqualTo("이름.png");
        assertThat(found.getCreatedAt()).isEqualTo(FixedClockConfig.FIXED_NOW);
    }

    @Test
    @DisplayName("thumbnailKey는 nullable이다 — 있으면 그대로, 없으면 null로 왕복한다")
    void thumbnailKeyRoundTrip() {
        User user = persistUser("owner@harucut.com");
        UserMedia withThumb = em.persist(UserMedia.of(user,
                "uploads/users/abc/fourcuts/job-1.png",
                "uploads/users/abc/fourcuts/job-1-thumb.jpg", "이름.png"));
        UserMedia withoutThumb = persistMedia(user, "uploads/users/abc/fourcuts/job-2.png");
        em.flush();
        em.clear();

        assertThat(userMediaRepository.findById(withThumb.getId()).orElseThrow().getThumbnailKey())
                .isEqualTo("uploads/users/abc/fourcuts/job-1-thumb.jpg");
        assertThat(userMediaRepository.findById(withoutThumb.getId()).orElseThrow().getThumbnailKey())
                .isNull();
    }

    @Test
    @DisplayName("같은 s3Key를 두 번 저장하면 DB가 거부한다 — unique 제약")
    void duplicateKeyRejected() {
        User user = persistUser("owner@harucut.com");
        persistMedia(user, "uploads/dup.png");
        em.flush();

        assertThatThrownBy(() -> {
            userMediaRepository.save(UserMedia.of(user, "uploads/dup.png", "다른이름.png"));
            userMediaRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Nested
    @DisplayName("findByIdAndUser")
    class FindByIdAndUser {

        @Test
        @DisplayName("내 미디어면 찾는다")
        void mine() {
            User user = persistUser("owner@harucut.com");
            UserMedia media = persistMedia(user, "uploads/mine.png");
            em.flush();
            em.clear();

            assertThat(userMediaRepository.findByIdAndUser(media.getId(), user)).isPresent();
        }

        @Test
        @DisplayName("남의 미디어는 빈 결과다 — 없는 것과 구분되지 않는다")
        void others() {
            User owner = persistUser("owner@harucut.com");
            User stranger = persistUser("stranger@harucut.com");
            UserMedia media = persistMedia(owner, "uploads/owned.png");
            em.flush();
            em.clear();

            assertThat(userMediaRepository.findByIdAndUser(media.getId(), stranger)).isEmpty();
        }

        @Test
        @DisplayName("없는 id도 빈 결과다")
        void missing() {
            User user = persistUser("owner@harucut.com");
            em.flush();

            assertThat(userMediaRepository.findByIdAndUser(999L, user)).isEmpty();
        }
    }

    @Nested
    @DisplayName("목록 쿼리")
    class ListQueries {

        @Test
        @DisplayName("cutoff와 정확히 같은 시각은 포함, 1초 전은 제외된다 — >= 경계")
        void cutoffBoundary() {
            User user = persistUser("owner@harucut.com");
            UserMedia atCutoff = persistMedia(user, "uploads/at.png");
            UserMedia before = persistMedia(user, "uploads/before.png");
            em.flush();
            LocalDateTime cutoff = FixedClockConfig.FIXED_NOW.minusDays(3);
            setCreatedAt(atCutoff.getId(), cutoff);
            setCreatedAt(before.getId(), cutoff.minusSeconds(1));
            em.clear();

            Page<UserMedia> page = userMediaRepository
                    .findAllByUserAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                            user, cutoff, PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(UserMedia::getId)
                    .containsExactly(atCutoff.getId());
        }

        @Test
        @DisplayName("최신순으로 나온다")
        void newestFirst() {
            User user = persistUser("owner@harucut.com");
            UserMedia first = persistMedia(user, "uploads/1.png");
            UserMedia second = persistMedia(user, "uploads/2.png");
            UserMedia third = persistMedia(user, "uploads/3.png");
            em.flush();
            // FixedClock이라 셋 다 같은 시각으로 저장된다 — 정렬 검증을 위해 직접 벌린다
            setCreatedAt(first.getId(), FixedClockConfig.FIXED_NOW.minusDays(2));
            setCreatedAt(second.getId(), FixedClockConfig.FIXED_NOW.minusDays(1));
            em.clear();

            Page<UserMedia> page = userMediaRepository
                    .findAllByUserOrderByCreatedAtDesc(user, PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(UserMedia::getId)
                    .containsExactly(third.getId(), second.getId(), first.getId());
        }

        @Test
        @DisplayName("cutoff에 걸러진 행은 totalElements에서도 빠진다 — 페이지 숫자가 거짓말하지 않는다")
        void totalElementsRespectsCutoff() {
            User user = persistUser("owner@harucut.com");
            LocalDateTime cutoff = FixedClockConfig.FIXED_NOW.minusDays(3);
            for (int i = 1; i <= 5; i++) {
                UserMedia media = persistMedia(user, "uploads/" + i + ".png");
                em.flush();
                // 3개는 기간 안, 2개는 기간 밖
                LocalDateTime at = i <= 3 ? cutoff.plusDays(i) : cutoff.minusDays(i);
                setCreatedAt(media.getId(), at);
            }
            em.clear();

            Page<UserMedia> page = userMediaRepository
                    .findAllByUserAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                            user, cutoff, PageRequest.of(0, 2));

            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }
    }

    // ── helpers ──────────────────────────────

    private User persistUser(String email) {
        return em.persist(User.localUser(email, "encoded", "닉네임"));
    }

    private UserMedia persistMedia(User user, String s3Key) {
        return em.persist(UserMedia.of(user, s3Key, "이름.png"));
    }

    private void setCreatedAt(Long mediaId, LocalDateTime at) {
        em.getEntityManager()
                .createNativeQuery("update user_media set created_at = ? where media_id = ?")
                .setParameter(1, at)
                .setParameter(2, mediaId)
                .executeUpdate();
    }
}
