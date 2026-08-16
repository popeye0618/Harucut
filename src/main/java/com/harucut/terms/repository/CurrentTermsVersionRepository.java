package com.harucut.terms.repository;

import com.harucut.terms.entity.CurrentTermsVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CurrentTermsVersionRepository extends JpaRepository<CurrentTermsVersion, Long> {

    // 공개 목록: 활성 약관의 현재 버전
    @Query("""
            SELECT c FROM CurrentTermsVersion c
            JOIN FETCH c.terms t
            JOIN FETCH c.termsVersion v
            WHERE t.active = true
            ORDER BY t.id ASC
            """)
    List<CurrentTermsVersion> findAllActive();

    // 관리자 목록: 비활성 포함 전체
    @Query("""
            SELECT c FROM CurrentTermsVersion c
            JOIN FETCH c.terms t
            JOIN FETCH c.termsVersion v
            ORDER BY t.id ASC
            """)
    List<CurrentTermsVersion> findAllWithDetails();

    // 동의/개정: 특정 약관의 현재 버전. 버전 번호가 필요하니 함께 로드한다
    @Query("""
            SELECT c FROM CurrentTermsVersion c
            JOIN FETCH c.termsVersion v
            WHERE c.terms.id = :termsId
            """)
    Optional<CurrentTermsVersion> findByTermsId(@Param("termsId") Long termsId);
}
