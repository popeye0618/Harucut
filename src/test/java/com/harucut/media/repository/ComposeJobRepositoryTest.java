package com.harucut.media.repository;

import com.harucut.config.JpaAuditingConfig;
import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.enums.FrameType;
import com.harucut.media.compose.ComposeSpec;
import com.harucut.media.converter.ComposeSpecConverter;
import com.harucut.media.entity.ComposeJob;
import com.harucut.media.enums.ComposeStatus;
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
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureJson
@Import({JpaAuditingConfig.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("ComposeJobRepository")
class ComposeJobRepositoryTest {

    private static final List<String> FOUR_KEYS = List.of(
            "uploads/users/abc/fourcuts/sources/1.jpg",
            "uploads/users/abc/fourcuts/sources/2.jpg",
            "uploads/users/abc/fourcuts/sources/3.jpg",
            "uploads/users/abc/fourcuts/sources/4.jpg");

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ComposeJobRepository composeJobRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("저장 후 재조회하면 스펙 스냅샷까지 그대로 돌아온다 — 왕복 무손실")
    void roundTrip() {
        User user = persistUser("owner@harucut.com");
        ComposeJob saved = em.persist(ComposeJob.create(user, 7L, "idem-1", FOUR_KEYS, spec()));
        em.flush();
        em.clear();

        ComposeJob found = composeJobRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(ComposeStatus.PENDING);
        assertThat(found.getFrameId()).isEqualTo(7L);
        assertThat(found.sourceKeys()).containsExactlyElementsOf(FOUR_KEYS);
        assertThat(found.getSpec()).isEqualTo(spec());
        assertThat(found.getCreatedAt()).isEqualTo(FixedClockConfig.FIXED_NOW);
    }

    @Nested
    @DisplayName("멱등 unique — (user, idempotencyKey)")
    class IdempotencyUnique {

        @Test
        @DisplayName("같은 사용자가 같은 key로 두 번 저장하면 DB가 거부한다")
        void duplicateRejected() {
            User user = persistUser("owner@harucut.com");
            em.persist(ComposeJob.create(user, 7L, "same-key", FOUR_KEYS, spec()));
            em.flush();

            assertThatThrownBy(() -> {
                composeJobRepository.save(ComposeJob.create(user, 7L, "same-key", FOUR_KEYS, spec()));
                composeJobRepository.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("다른 사용자면 같은 key라도 저장된다 — 멱등의 범위는 사용자 단위다")
        void sameKeyDifferentUserAllowed() {
            User first = persistUser("first@harucut.com");
            User second = persistUser("second@harucut.com");
            em.persist(ComposeJob.create(first, 7L, "same-key", FOUR_KEYS, spec()));
            em.flush();

            ComposeJob saved = composeJobRepository
                    .save(ComposeJob.create(second, 7L, "same-key", FOUR_KEYS, spec()));
            composeJobRepository.flush();

            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("findByUserAndIdempotencyKey가 같은 key의 재시도에게 기존 Job을 돌려준다")
        void findByIdempotencyKey() {
            User user = persistUser("owner@harucut.com");
            ComposeJob job = em.persist(ComposeJob.create(user, 7L, "retry-key", FOUR_KEYS, spec()));
            em.flush();
            em.clear();

            assertThat(composeJobRepository.findByUserAndIdempotencyKey(user, "retry-key"))
                    .map(ComposeJob::getId).hasValue(job.getId());
            assertThat(composeJobRepository.findByUserAndIdempotencyKey(user, "unknown-key"))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("findByIdAndUser")
    class FindByIdAndUser {

        @Test
        @DisplayName("내 작업이면 찾는다")
        void mine() {
            User user = persistUser("owner@harucut.com");
            ComposeJob job = em.persist(ComposeJob.create(user, 7L, "idem-1", FOUR_KEYS, spec()));
            em.flush();
            em.clear();

            assertThat(composeJobRepository.findByIdAndUser(job.getId(), user)).isPresent();
        }

        @Test
        @DisplayName("남의 작업은 빈 결과다 — 없는 것과 구분되지 않는다")
        void others() {
            User owner = persistUser("owner@harucut.com");
            User stranger = persistUser("stranger@harucut.com");
            ComposeJob job = em.persist(ComposeJob.create(owner, 7L, "idem-1", FOUR_KEYS, spec()));
            em.flush();
            em.clear();

            assertThat(composeJobRepository.findByIdAndUser(job.getId(), stranger)).isEmpty();
        }

        @Test
        @DisplayName("없는 id도 빈 결과다")
        void missing() {
            User user = persistUser("owner@harucut.com");
            em.flush();

            assertThat(composeJobRepository.findByIdAndUser(999L, user)).isEmpty();
        }
    }

    @Nested
    @DisplayName("깨진 스펙 JSON")
    class CorruptSpec {

        @Test
        @DisplayName("스펙 JSON이 깨져 있으면 크게 실패한다 — 깨진 스펙으로 그리는 척하지 않는다")
        void corruptSpecFailsLoud() {
            ComposeSpecConverter converter = new ComposeSpecConverter(objectMapper);

            assertThatThrownBy(() -> converter.convertToEntityAttribute("{broken"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("탈퇴 삭제 쿼리")
    class DeletionQueries {

        @Test
        @DisplayName("결과 키 조회는 내 완료 작업 것만 나온다 — 미완료(null)는 빠진다")
        void resultKeyProjection() {
            User mine = persistUser("mine@harucut.com");
            User other = persistUser("other@harucut.com");
            ComposeJob done = em.persist(ComposeJob.create(mine, 7L, "idem-1", FOUR_KEYS, spec()));
            done.complete("uploads/results/r1.png", 11L);
            em.persist(ComposeJob.create(mine, 7L, "idem-2", FOUR_KEYS, spec()));
            ComposeJob othersDone = em.persist(ComposeJob.create(other, 7L, "idem-1", FOUR_KEYS, spec()));
            othersDone.complete("uploads/results/r2.png", 12L);
            em.flush();

            assertThat(composeJobRepository.findResultKeysByUserId(mine.getId()))
                    .containsExactly("uploads/results/r1.png");
        }

        @Test
        @DisplayName("벌크 삭제는 내 작업만 지운다")
        void bulkDeleteMineOnly() {
            User mine = persistUser("mine@harucut.com");
            User other = persistUser("other@harucut.com");
            em.persist(ComposeJob.create(mine, 7L, "idem-1", FOUR_KEYS, spec()));
            em.persist(ComposeJob.create(other, 7L, "idem-1", FOUR_KEYS, spec()));
            em.flush();

            composeJobRepository.deleteByUserId(mine.getId());

            assertThat(composeJobRepository.findAll()).hasSize(1);
        }
    }

    // ── fixtures ──────────────────────────────

    private User persistUser(String email) {
        return em.persist(User.localUser(email, "encoded", "닉네임"));
    }

    private static ComposeSpec spec() {
        return new ComposeSpec(2000, 6000,
                new BackgroundAttributes.Color("#FFE4E1"),
                FrameType.CLASSIC.getLayout().slots(),
                List.of(true, false, false, false),
                List.of(new ComposeSpec.Layer(
                        "uploads/users/abc/components/sticker.png",
                        120.5, 220.0, 360.0, 480.0, 1.0, 45.0, 3, 1.0)));
    }
}
