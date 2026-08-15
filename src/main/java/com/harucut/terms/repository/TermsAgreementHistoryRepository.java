package com.harucut.terms.repository;

import com.harucut.terms.entity.TermsAgreementHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsAgreementHistoryRepository extends JpaRepository<TermsAgreementHistory, Long> {

    // 관리자 이력 조회 (법적 증빙). FK가 없어서 파생 쿼리로 충분하다
    Page<TermsAgreementHistory> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
}
