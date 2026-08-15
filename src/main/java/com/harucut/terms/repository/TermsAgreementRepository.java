package com.harucut.terms.repository;

import com.harucut.terms.entity.TermsAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TermsAgreementRepository extends JpaRepository<TermsAgreement, Long> {

    // 내 동의 상태: 약관당 1행이라 행 수 = 이 사용자가 건드려본 약관 수
    @Query("""
            SELECT a FROM TermsAgreement a
            JOIN FETCH a.terms t
            WHERE a.userId = :userId
            """)
    List<TermsAgreement> findAllByUserId(@Param("userId") Long userId);

    // 동의/철회: 이 행이 있으면 update, 없으면 insert
    Optional<TermsAgreement> findByUserIdAndTermsId(Long userId, Long termsId);
}
