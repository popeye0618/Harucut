package com.harucut.media.repository;

import com.harucut.media.entity.UserMedia;
import com.harucut.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
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

    @Query("SELECT m.s3Key FROM UserMedia m WHERE m.user.id = :userId")
    List<String> findS3KeysByUserId(Long userId);

    @Query("SELECT m.thumbnailKey FROM UserMedia m WHERE m.user.id = :userId AND m.thumbnailKey IS NOT NULL")
    List<String> findThumbnailKeysByUserId(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserMedia m WHERE m.user.id = :userId")
    void deleteByUserId(Long userId);
}