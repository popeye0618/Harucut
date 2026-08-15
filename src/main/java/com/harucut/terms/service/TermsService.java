package com.harucut.terms.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.terms.dto.AgreementItem;
import com.harucut.terms.dto.TermsAgreementStatusResponse;
import com.harucut.terms.dto.TermsResponse;
import com.harucut.terms.entity.Terms;
import com.harucut.terms.entity.TermsAgreement;
import com.harucut.terms.entity.TermsAgreementHistory;
import com.harucut.terms.exception.TermsErrorCode;
import com.harucut.terms.repository.CurrentTermsVersionRepository;
import com.harucut.terms.repository.TermsAgreementHistoryRepository;
import com.harucut.terms.repository.TermsAgreementRepository;
import com.harucut.terms.repository.TermsRepository;
import com.harucut.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsService {

    private final TermsRepository termsRepository;
    private final CurrentTermsVersionRepository currentTermsVersionRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final TermsAgreementHistoryRepository termsAgreementHistoryRepository;
    private final UserRepository userRepository;

    // 공개 - 활성 약관의 현재 버전 목록
    public List<TermsResponse> getActiveTerms() {
        return currentTermsVersionRepository.findAllActive().stream()
                .map(TermsResponse::from)
                .toList();
    }

    // 내 동의 상태 조회. 응답의 기준은 agreement가 아니라 "활성 약관 목록"이다
    // 1) 내 agreement 전부를 terms_id -> 행 맵으로 뒤집는다 (약관당 1행 보장이라 이게 곧 최신 상태)
    // 2) 활성 약관마다 맵에서 내 행을 찾는다. 없으면 null -> NOT_AGREED
    // agreement 기준으로 돌면 안 건드린 약관이 응답에서 빠지고, 비활성 약관에 한 동의가 끼어든다
    public List<TermsAgreementStatusResponse> getMyAgreementStatus(String publicId) {
        Long userId = getUserId(publicId);

        Map<Long, TermsAgreement> byTermsId = termsAgreementRepository.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(a -> a.getTerms().getId(), Function.identity()));

        return currentTermsVersionRepository.findAllActive().stream()
                .map(current -> TermsAgreementStatusResponse.of(
                        current, byTermsId.get(current.getTerms().getId())))
                .toList();
    }

    @Transactional
    public void agree(String publicId, List<AgreementItem> items) {
        Long userId = getUserId(publicId);

        for(AgreementItem item : items) {
            Terms terms = termsRepository.findByCodeAndActiveTrue(item.code())
                    .orElseThrow(() -> new BusinessException(TermsErrorCode.TERMS_NOT_FOUND));

            if(!item.agreed() && terms.isRequired()) {
                throw new BusinessException(TermsErrorCode.REQUIRED_TERMS_CANNOT_WITHDRAW);
            }

            int currentVersion = currentTermsVersionRepository.findByTermsId(terms.getId())
                    .orElseThrow(() -> new BusinessException(TermsErrorCode.TERMS_NOT_FOUND))
                    .getTermsVersion().getVersion();

            termsAgreementRepository.findByUserIdAndTermsId(userId, terms.getId())
                    .ifPresentOrElse(
                            agreement -> agreement.update(currentVersion, item.agreed()),
                            () -> termsAgreementRepository.save(
                                    TermsAgreement.of(userId, terms, currentVersion, item.agreed())
                            )
                    );

            termsAgreementHistoryRepository.save(
                    TermsAgreementHistory.of(userId, terms.getCode(), currentVersion, item.agreed())
            );
        }
    }

    private Long getUserId(String publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "User not found."))
                .getId();
    }
}
