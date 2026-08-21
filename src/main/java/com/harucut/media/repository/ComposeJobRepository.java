package com.harucut.media.repository;

import com.harucut.media.entity.ComposeJob;
import com.harucut.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ComposeJobRepository extends JpaRepository<ComposeJob, Long> {

    // 폴링 조회 — 소유자 조건이 쿼리에 있어 남의 것과 없는 것이 똑같이 empty다 (미디어와 같은 404 규칙)
    Optional<ComposeJob> findByIdAndUser(Long id, User user);

    // 멱등 조회 — 같은 key의 재시도에게 기존 Job을 돌려준다
    Optional<ComposeJob> findByUserAndIdempotencyKey(User user, String idempotencyKey);

    @Query("SELECT j.resultKey FROM ComposeJob j WHERE j.user.id = :userId AND j.resultKey IS NOT NULL")
    List<String> findResultKeysByUserId(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ComposeJob j WHERE j.user.id = :userId")
    void deleteByUserId(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE ComposeJob j
            SET j.startedAt = :now
            WHERE j.id = :jobId
                AND j.status = ComposeStatus.PENDING
                AND (j.startedAt IS NULL OR j.startedAt < :staleBefore)
            """)
    int claim(Long jobId, LocalDateTime now, LocalDateTime staleBefore);

    @Query("""
            SELECT j FROM ComposeJob j
            JOIN FETCH j.user
            WHERE j.status = ComposeStatus.PENDING
                AND (j.startedAt IS NULL OR j.startedAt < :staleBefore)
            ORDER BY j.id
            """)
    List<ComposeJob> findStalled(LocalDateTime staleBefore, Pageable pageable);
}
