package com.harucut.terms.handler;

import com.harucut.config.JpaAuditingConfig;
import com.harucut.support.FixedClockConfig;
import com.harucut.terms.entity.Terms;
import com.harucut.terms.entity.TermsAgreement;
import com.harucut.terms.entity.TermsAgreementHistory;
import com.harucut.terms.repository.TermsAgreementHistoryRepository;
import com.harucut.terms.repository.TermsAgreementRepository;
import com.harucut.terms.repository.TermsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureJson
@Import({JpaAuditingConfig.class, FixedClockConfig.class, TermsAgreementDeletionHandler.class})
@ActiveProfiles("test")
@DisplayName("TermsAgreementDeletionHandler")
class TermsAgreementDeletionHandlerTest {

    @Autowired
    private TermsAgreementDeletionHandler handler;

    @Autowired
    private TermsRepository termsRepository;

    @Autowired
    private TermsAgreementRepository termsAgreementRepository;

    @Autowired
    private TermsAgreementHistoryRepository termsAgreementHistoryRepository;

    @Test
    @DisplayName("동의 현재 상태는 내 것만 지워진다")
    void deletesOnlyMyAgreements() {
        Terms terms = termsRepository.save(Terms.create("tos", "이용약관", true));
        termsAgreementRepository.save(TermsAgreement.of(1L, terms, 1, true));
        termsAgreementRepository.save(TermsAgreement.of(2L, terms, 1, true));
        termsAgreementRepository.flush();

        handler.handleUserDeletion(1L);

        assertThat(termsAgreementRepository.findAll()).singleElement()
                .satisfies(a -> assertThat(a.getUserId()).isEqualTo(2L));
    }

    @Test
    @DisplayName("동의 이력도 내 것만 지워진다")
    void deletesOnlyMyHistory() {
        termsAgreementHistoryRepository.save(TermsAgreementHistory.of(1L, "tos", 1, true));
        termsAgreementHistoryRepository.save(TermsAgreementHistory.of(2L, "tos", 1, true));
        termsAgreementHistoryRepository.flush();

        handler.handleUserDeletion(1L);

        assertThat(termsAgreementHistoryRepository.findAll()).singleElement()
                .satisfies(h -> assertThat(h.getUserId()).isEqualTo(2L));
    }
}
