package com.harucut.terms.repository;

import com.harucut.terms.entity.TermsVersion;
import org.springframework.data.jpa.repository.JpaRepository;

// 저장 전용. 버전을 "찾는" 일은 전부 CurrentTermsVersion 포인터가 대신한다
public interface TermsVersionRepository extends JpaRepository<TermsVersion, Long> {
}
