package com.harucut.terms.handler;

import com.harucut.auth.service.UserDeletionHandler;
import com.harucut.terms.repository.TermsAgreementHistoryRepository;
import com.harucut.terms.repository.TermsAgreementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TermsAgreementDeletionHandler implements UserDeletionHandler {

    private final TermsAgreementRepository termsAgreementRepository;
    private final TermsAgreementHistoryRepository termsAgreementHistoryRepository;

    @Transactional
    @Override
    public void handleUserDeletion(Long userId) {
        termsAgreementRepository.deleteByUserId(userId);
        termsAgreementHistoryRepository.deleteByUserId(userId);
    }
}
