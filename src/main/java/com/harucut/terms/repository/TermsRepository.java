package com.harucut.terms.repository;

import com.harucut.terms.entity.Terms;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TermsRepository extends JpaRepository<Terms, Long> {

    // 관리자 생성 시 코드 중복 검사 -> TERMS-002
    boolean existsByCode(String code);

    // 동의/철회 시 약관 조회. 비활성 약관은 없는 것과 같게 취급 -> TERMS-001
    Optional<Terms> findByCodeAndActiveTrue(String code);
}
