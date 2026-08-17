package com.harucut.media.repository;

import com.harucut.media.entity.ComposeJob;
import com.harucut.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComposeJobRepository extends JpaRepository<ComposeJob, Long> {

    // 폴링 조회 — 소유자 조건이 쿼리에 있어 남의 것과 없는 것이 똑같이 empty다 (미디어와 같은 404 규칙)
    Optional<ComposeJob> findByIdAndUser(Long id, User user);

    // 멱등 조회 — 같은 key의 재시도에게 기존 Job을 돌려준다
    Optional<ComposeJob> findByUserAndIdempotencyKey(User user, String idempotencyKey);
}
