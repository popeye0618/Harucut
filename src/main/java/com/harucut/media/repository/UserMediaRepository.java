package com.harucut.media.repository;

import com.harucut.media.entity.UserMedia;
import com.harucut.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserMediaRepository extends JpaRepository<UserMedia, Long> {

    // 소유자 조건이 쿼리 안에 있다 — 남의 것도 없는 것도 똑같이 empty (404 통일)
    Optional<UserMedia> findByIdAndUser(Long id, User user);

    // 기간 제한이 없을 때 (PRO)
    Page<UserMedia> findAllByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    // 기간 제한이 있을 때 (BASIC/PLUS) — cutoff 이후(>=)만.
    // 페이지 개수가 맞으려면 이 필터가 쿼리 안에 있어야 한다
    Page<UserMedia> findAllByUserAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            User user, LocalDateTime cutoff, Pageable pageable);
}