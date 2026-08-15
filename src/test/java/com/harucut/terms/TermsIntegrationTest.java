package com.harucut.terms;

import com.harucut.common.exception.BusinessException;
import com.harucut.support.UserFixtures;
import com.harucut.terms.dto.AgreementItem;
import com.harucut.terms.dto.TermsAgreementStatusResponse;
import com.harucut.terms.entity.TermsVersion;
import com.harucut.terms.enums.TermsAgreementStatus;
import com.harucut.terms.exception.TermsErrorCode;
import com.harucut.terms.repository.CurrentTermsVersionRepository;
import com.harucut.terms.repository.TermsAgreementHistoryRepository;
import com.harucut.terms.repository.TermsAgreementRepository;
import com.harucut.terms.repository.TermsRepository;
import com.harucut.terms.repository.TermsVersionRepository;
import com.harucut.terms.service.TermsAdminService;
import com.harucut.terms.service.TermsService;
import com.harucut.user.entity.User;
import com.harucut.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 트랜잭션 경계와 DB 제약이 실제로 동작하는지 본다. 테스트 자체는 트랜잭션을 열지 않는다 -
// 열면 롤백 검증(서비스 트랜잭션이 실제로 롤백되는가)이 테스트 트랜잭션에 가려 무의미해진다
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("약관 통합")
class TermsIntegrationTest {

    @Autowired
    private TermsService termsService;

    @Autowired
    private TermsAdminService termsAdminService;

    @Autowired
    private TermsRepository termsRepository;

    @Autowired
    private TermsVersionRepository termsVersionRepository;

    @Autowired
    private CurrentTermsVersionRepository currentTermsVersionRepository;

    @Autowired
    private TermsAgreementRepository termsAgreementRepository;

    @Autowired
    private TermsAgreementHistoryRepository termsAgreementHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    private String publicId;

    @BeforeEach
    void setUp() {
        termsAgreementHistoryRepository.deleteAllInBatch();
        termsAgreementRepository.deleteAllInBatch();
        currentTermsVersionRepository.deleteAllInBatch();
        termsVersionRepository.deleteAllInBatch();
        termsRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        User user = userRepository.save(UserFixtures.localUser("terms@harucut.com", "encoded"));
        publicId = user.getPublicId();
    }

    @Test
    @DisplayName("개정하면 version 2가 생기고 version 1은 내용 그대로 남아 있다")
    void revisionKeepsOldVersion() {
        termsAdminService.createTerms("tos", "이용약관", true, "v1 본문");
        Long termsId = termsId("tos");

        termsAdminService.reviseTerms(termsId, "v2 본문");

        List<TermsVersion> versions = termsVersionRepository.findAll();
        assertThat(versions).hasSize(2);
        assertThat(versions).extracting(TermsVersion::getVersion).containsExactlyInAnyOrder(1, 2);
        assertThat(versions.stream().filter(v -> v.getVersion() == 1).findFirst().orElseThrow()
                .getContent()).isEqualTo("v1 본문");
        assertThat(currentTermsVersionRepository.findByTermsId(termsId).orElseThrow()
                .getTermsVersion().getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("동의/철회를 5번 반복하면 history는 5행 쌓이고 agreement는 1행뿐이다")
    void historyAccumulatesWhileAgreementStaysSingle() {
        termsAdminService.createTerms("marketing", "마케팅 수신 동의", false, "본문");

        for (int i = 0; i < 5; i++) {
            termsService.agree(publicId, List.of(new AgreementItem("marketing", i % 2 == 0)));
        }

        assertThat(termsAgreementHistoryRepository.count()).isEqualTo(5);
        assertThat(termsAgreementRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("철회 후 재동의하면 AGREED로 돌아온다")
    void reagreeAfterWithdrawal() {
        termsAdminService.createTerms("marketing", "마케팅 수신 동의", false, "본문");
        termsService.agree(publicId, List.of(new AgreementItem("marketing", true)));
        termsService.agree(publicId, List.of(new AgreementItem("marketing", false)));

        termsService.agree(publicId, List.of(new AgreementItem("marketing", true)));

        TermsAgreementStatusResponse status = termsService.getMyAgreementStatus(publicId).getFirst();
        assertThat(status.status()).isEqualTo(TermsAgreementStatus.AGREED);
    }

    @Test
    @DisplayName("개정하면 별도 갱신 작업 없이 기존 동의자가 NEEDS_RECONSENT가 된다")
    void revisionFlipsStatusToNeedsReconsent() {
        termsAdminService.createTerms("tos", "이용약관", true, "v1 본문");
        termsService.agree(publicId, List.of(new AgreementItem("tos", true)));

        termsAdminService.reviseTerms(termsId("tos"), "v2 본문");

        TermsAgreementStatusResponse status = termsService.getMyAgreementStatus(publicId).getFirst();
        assertThat(status.status()).isEqualTo(TermsAgreementStatus.NEEDS_RECONSENT);
        assertThat(status.agreedVersion()).isEqualTo(1);
        assertThat(status.latestVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("3개 항목 중 마지막이 실패하면 앞의 2개도 롤백된다")
    void allOrNothing() {
        termsAdminService.createTerms("tos", "이용약관", true, "본문");
        termsAdminService.createTerms("privacy", "개인정보 처리방침", true, "본문");

        assertThatThrownBy(() -> termsService.agree(publicId, List.of(
                new AgreementItem("tos", true),
                new AgreementItem("privacy", true),
                new AgreementItem("no-such-terms", true))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TermsErrorCode.TERMS_NOT_FOUND);

        assertThat(termsAgreementRepository.count()).isZero();
        assertThat(termsAgreementHistoryRepository.count()).isZero();
    }

    @Test
    @DisplayName("비활성화된 약관에 동의하면 TERMS-001이다")
    void inactiveTermsRejected() {
        termsAdminService.createTerms("marketing", "마케팅 수신 동의", false, "본문");
        termsAdminService.deactivateTerms(termsId("marketing"));

        assertThatThrownBy(() -> termsService.agree(publicId,
                List.of(new AgreementItem("marketing", true))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TermsErrorCode.TERMS_NOT_FOUND);
    }

    private Long termsId(String code) {
        return termsRepository.findAll().stream()
                .filter(terms -> terms.getCode().equals(code))
                .findFirst().orElseThrow()
                .getId();
    }
}
