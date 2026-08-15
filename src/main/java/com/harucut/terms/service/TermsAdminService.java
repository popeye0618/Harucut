package com.harucut.terms.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.response.PageResponse;
import com.harucut.terms.dto.TermsAdminResponse;
import com.harucut.terms.dto.TermsAgreementHistoryResponse;
import com.harucut.terms.entity.CurrentTermsVersion;
import com.harucut.terms.entity.Terms;
import com.harucut.terms.entity.TermsVersion;
import com.harucut.terms.exception.TermsErrorCode;
import com.harucut.terms.repository.CurrentTermsVersionRepository;
import com.harucut.terms.repository.TermsAgreementHistoryRepository;
import com.harucut.terms.repository.TermsRepository;
import com.harucut.terms.repository.TermsVersionRepository;
import lombok.RequiredArgsConstructor;
import com.harucut.common.utils.PageRequests;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsAdminService {

    private final TermsRepository termsRepository;
    private final TermsVersionRepository termsVersionRepository;
    private final CurrentTermsVersionRepository currentTermsVersionRepository;
    private final TermsAgreementHistoryRepository termsAgreementHistoryRepository;

    // 생성 - terms, version 1, 포인터를 한 트랜잭션에서
    @Transactional
    public void createTerms(String code, String title, boolean required, String content) {
        if (termsRepository.existsByCode(code)) {
            throw new BusinessException(TermsErrorCode.TERMS_CODE_DUPLICATED);
        }

        Terms terms = termsRepository.save(Terms.create(code, title, required));
        TermsVersion first = termsVersionRepository.save(TermsVersion.first(terms, content));
        currentTermsVersionRepository.save(CurrentTermsVersion.pointTo(first));
    }

    // 개정 - 새 버전 append 후 포인터만 이동
    @Transactional
    public void reviseTerms(Long termsId, String content) {
        CurrentTermsVersion current = currentTermsVersionRepository.findByTermsId(termsId)
                .orElseThrow(() -> new BusinessException(TermsErrorCode.TERMS_NOT_FOUND));

        TermsVersion next = termsVersionRepository.save(
                TermsVersion.next(current.getTermsVersion(), content));
        current.repoint(next);
    }

    // 비활성 포함 전체 목록
    public List<TermsAdminResponse> listAllTerms() {
        return currentTermsVersionRepository.findAllWithDetails().stream()
                .map(TermsAdminResponse::from)
                .toList();
    }

    // 비활성화 (멱등). 포인터는 건드리지 않는다
    @Transactional
    public void deactivateTerms(Long termsId) {
        termsRepository.findById(termsId)
                .orElseThrow(() -> new BusinessException(TermsErrorCode.TERMS_NOT_FOUND))
                .deactivate();
    }

    // 법적 증빙 - 특정 사용자의 동의 이력. page/size 검증은 PageRequests가 한다 (노티스와 같은 패턴)
    public PageResponse<TermsAgreementHistoryResponse> getAgreementHistory(Long userId, int page, int size) {
        return PageResponse.from(
                termsAgreementHistoryRepository.findByUserIdOrderByIdDesc(userId, PageRequests.of(page, size))
                        .map(TermsAgreementHistoryResponse::from));
    }
}
