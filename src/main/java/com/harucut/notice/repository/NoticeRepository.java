package com.harucut.notice.repository;

import com.harucut.notice.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    // 공개 목록 -> 게시됨, 미삭제, 고정 우선 -> 게시 최신순
    Page<Notice> findByPublishedTrueAndDeletedAtIsNullOrderByPinnedDescPublishedAtDesc(Pageable pageable);

    // 공개 공지 단건 조회
    Optional<Notice> findByPublicIdAndPublishedTrueAndDeletedAtIsNull(String publicId);

    // 관리자 목록
    Page<Notice> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    // 관리자 단건
    Optional<Notice> findByIdAndDeletedAtIsNull(Long id);
}
