package com.harucut.terms.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.support.TermsFixtures;
import com.harucut.support.UserFixtures;
import com.harucut.terms.dto.AgreementItem;
import com.harucut.terms.dto.TermsAgreementStatusResponse;
import com.harucut.terms.dto.TermsResponse;
import com.harucut.terms.entity.Terms;
import com.harucut.terms.entity.TermsAgreement;
import com.harucut.terms.entity.TermsAgreementHistory;
import com.harucut.terms.enums.TermsAgreementStatus;
import com.harucut.terms.exception.TermsErrorCode;
import com.harucut.terms.repository.CurrentTermsVersionRepository;
import com.harucut.terms.repository.TermsAgreementHistoryRepository;
import com.harucut.terms.repository.TermsAgreementRepository;
import com.harucut.terms.repository.TermsRepository;
import com.harucut.user.entity.User;
import com.harucut.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("TermsService")
class TermsServiceTest {

    private static final String PUBLIC_ID = "AbCdEf12Gh";
    private static final Long USER_ID = 7L;

    @Mock
    private TermsRepository termsRepository;

    @Mock
    private CurrentTermsVersionRepository currentTermsVersionRepository;

    @Mock
    private TermsAgreementRepository termsAgreementRepository;

    @Mock
    private TermsAgreementHistoryRepository termsAgreementHistoryRepository;

    @Mock
    private UserRepository userRepository;

    private TermsService termsService;

    @BeforeEach
    void setUp() {
        termsService = new TermsService(termsRepository, currentTermsVersionRepository,
                termsAgreementRepository, termsAgreementHistoryRepository, userRepository);
    }

    @Nested
    @DisplayName("getActiveTerms")
    class GetActiveTerms {

        @Test
        @DisplayName("활성 약관의 현재 버전을 코드·버전·본문으로 매핑한다")
        void mapsCurrentVersions() {
            Terms tos = TermsFixtures.terms(1L, "tos", true);
            given(currentTermsVersionRepository.findAllActive())
                    .willReturn(List.of(TermsFixtures.currentVersion(tos, 2)));

            List<TermsResponse> result = termsService.getActiveTerms();

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().code()).isEqualTo("tos");
            assertThat(result.getFirst().version()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getMyAgreementStatus")
    class GetMyAgreementStatus {

        @Test
        @DisplayName("동의한 적 없으면 NOT_AGREED, agreedVersion은 null이다")
        void notAgreedWhenNoAgreement() {
            stubUser();
            Terms tos = TermsFixtures.terms(1L, "tos", true);
            given(currentTermsVersionRepository.findAllActive())
                    .willReturn(List.of(TermsFixtures.currentVersion(tos, 1)));
            given(termsAgreementRepository.findAllByUserId(USER_ID)).willReturn(List.of());

            TermsAgreementStatusResponse result = termsService.getMyAgreementStatus(PUBLIC_ID).getFirst();

            assertThat(result.status()).isEqualTo(TermsAgreementStatus.NOT_AGREED);
            assertThat(result.agreedVersion()).isNull();
            assertThat(result.latestVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("최신 버전에 동의했으면 AGREED다")
        void agreedWhenLatestVersion() {
            stubUser();
            Terms tos = TermsFixtures.terms(1L, "tos", true);
            given(currentTermsVersionRepository.findAllActive())
                    .willReturn(List.of(TermsFixtures.currentVersion(tos, 2)));
            given(termsAgreementRepository.findAllByUserId(USER_ID))
                    .willReturn(List.of(TermsFixtures.agreement(USER_ID, tos, 2, true)));

            TermsAgreementStatusResponse result = termsService.getMyAgreementStatus(PUBLIC_ID).getFirst();

            assertThat(result.status()).isEqualTo(TermsAgreementStatus.AGREED);
            assertThat(result.agreedVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("v1 동의 후 v2로 개정되면 NEEDS_RECONSENT, agreedVersion=1, latestVersion=2다")
        void needsReconsentAfterRevision() {
            stubUser();
            Terms tos = TermsFixtures.terms(1L, "tos", true);
            given(currentTermsVersionRepository.findAllActive())
                    .willReturn(List.of(TermsFixtures.currentVersion(tos, 2)));
            given(termsAgreementRepository.findAllByUserId(USER_ID))
                    .willReturn(List.of(TermsFixtures.agreement(USER_ID, tos, 1, true)));

            TermsAgreementStatusResponse result = termsService.getMyAgreementStatus(PUBLIC_ID).getFirst();

            assertThat(result.status()).isEqualTo(TermsAgreementStatus.NEEDS_RECONSENT);
            assertThat(result.agreedVersion()).isEqualTo(1);
            assertThat(result.latestVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("철회 상태면 NOT_AGREED이고 agreedVersion은 null이다")
        void notAgreedWhenWithdrawn() {
            stubUser();
            Terms tos = TermsFixtures.terms(1L, "tos", true);
            given(currentTermsVersionRepository.findAllActive())
                    .willReturn(List.of(TermsFixtures.currentVersion(tos, 1)));
            given(termsAgreementRepository.findAllByUserId(USER_ID))
                    .willReturn(List.of(TermsFixtures.agreement(USER_ID, tos, 1, false)));

            TermsAgreementStatusResponse result = termsService.getMyAgreementStatus(PUBLIC_ID).getFirst();

            assertThat(result.status()).isEqualTo(TermsAgreementStatus.NOT_AGREED);
            assertThat(result.agreedVersion()).isNull();
        }

        @Test
        @DisplayName("응답 기준은 활성 약관 목록이다 — 안 건드린 약관도 응답에 나온다")
        void untouchedTermsStillListed() {
            stubUser();
            Terms tos = TermsFixtures.terms(1L, "tos", true);
            Terms marketing = TermsFixtures.terms(2L, "marketing", false);
            given(currentTermsVersionRepository.findAllActive())
                    .willReturn(List.of(TermsFixtures.currentVersion(tos, 1), TermsFixtures.currentVersion(marketing, 1)));
            given(termsAgreementRepository.findAllByUserId(USER_ID))
                    .willReturn(List.of(TermsFixtures.agreement(USER_ID, tos, 1, true)));

            List<TermsAgreementStatusResponse> result = termsService.getMyAgreementStatus(PUBLIC_ID);

            assertThat(result).extracting(TermsAgreementStatusResponse::code)
                    .containsExactly("tos", "marketing");
        }

        @Test
        @DisplayName("비활성 약관에 한 동의는 응답에 나오지 않는다")
        void inactiveTermsAgreementIgnored() {
            stubUser();
            Terms tos = TermsFixtures.terms(1L, "tos", true);
            Terms inactive = TermsFixtures.terms(3L, "marketing", false);
            given(currentTermsVersionRepository.findAllActive())
                    .willReturn(List.of(TermsFixtures.currentVersion(tos, 1)));
            given(termsAgreementRepository.findAllByUserId(USER_ID))
                    .willReturn(List.of(TermsFixtures.agreement(USER_ID, inactive, 1, true)));

            List<TermsAgreementStatusResponse> result = termsService.getMyAgreementStatus(PUBLIC_ID);

            assertThat(result).extracting(TermsAgreementStatusResponse::code)
                    .containsExactly("tos");
        }

        @Test
        @DisplayName("publicId로 사용자를 찾지 못하면 GEN-031을 던진다")
        void userNotFound() {
            given(userRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> termsService.getMyAgreementStatus(PUBLIC_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("agree")
    class Agree {

        @Test
        @DisplayName("없는 코드면 TERMS-001이고 아무것도 저장되지 않는다")
        void unknownCode() {
            stubUser();
            given(termsRepository.findByCodeAndActiveTrue("nope")).willReturn(Optional.empty());

            assertThatThrownBy(() -> termsService.agree(PUBLIC_ID, List.of(new AgreementItem("nope", true))))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TermsErrorCode.TERMS_NOT_FOUND);

            then(termsAgreementRepository).shouldHaveNoInteractions();
            then(termsAgreementHistoryRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("필수 약관 철회는 TERMS-003이고 아무것도 저장되지 않는다")
        void requiredTermsCannotWithdraw() {
            stubUser();
            Terms tos = TermsFixtures.terms(1L, "tos", true);
            given(termsRepository.findByCodeAndActiveTrue("tos")).willReturn(Optional.of(tos));

            assertThatThrownBy(() -> termsService.agree(PUBLIC_ID, List.of(new AgreementItem("tos", false))))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TermsErrorCode.REQUIRED_TERMS_CANNOT_WITHDRAW);

            then(termsAgreementRepository).shouldHaveNoInteractions();
            then(termsAgreementHistoryRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("처음 동의하면 현재 버전 번호로 agreement가 insert된다")
        void insertsOnFirstAgreement() {
            stubUser();
            Terms tos = TermsFixtures.terms(1L, "tos", true);
            given(termsRepository.findByCodeAndActiveTrue("tos")).willReturn(Optional.of(tos));
            given(currentTermsVersionRepository.findByTermsId(1L))
                    .willReturn(Optional.of(TermsFixtures.currentVersion(tos, 2)));
            given(termsAgreementRepository.findByUserIdAndTermsId(USER_ID, 1L)).willReturn(Optional.empty());

            termsService.agree(PUBLIC_ID, List.of(new AgreementItem("tos", true)));

            ArgumentCaptor<TermsAgreement> captor = ArgumentCaptor.forClass(TermsAgreement.class);
            then(termsAgreementRepository).should().save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
            assertThat(captor.getValue().getAgreedVersion()).isEqualTo(2);
            assertThat(captor.getValue().isAgreed()).isTrue();
        }

        @Test
        @DisplayName("이미 행이 있으면 insert 대신 그 행이 갱신된다")
        void updatesExistingAgreement() {
            stubUser();
            Terms marketing = TermsFixtures.terms(2L, "marketing", false);
            TermsAgreement existing = TermsFixtures.agreement(USER_ID, marketing, 1, true);
            given(termsRepository.findByCodeAndActiveTrue("marketing")).willReturn(Optional.of(marketing));
            given(currentTermsVersionRepository.findByTermsId(2L))
                    .willReturn(Optional.of(TermsFixtures.currentVersion(marketing, 2)));
            given(termsAgreementRepository.findByUserIdAndTermsId(USER_ID, 2L))
                    .willReturn(Optional.of(existing));

            termsService.agree(PUBLIC_ID, List.of(new AgreementItem("marketing", false)));

            assertThat(existing.getAgreedVersion()).isEqualTo(2);
            assertThat(existing.isAgreed()).isFalse();
            then(termsAgreementRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("동의든 철회든 history에 code 스냅샷과 함께 append된다")
        void appendsHistoryWithCodeSnapshot() {
            stubUser();
            Terms marketing = TermsFixtures.terms(2L, "marketing", false);
            given(termsRepository.findByCodeAndActiveTrue("marketing")).willReturn(Optional.of(marketing));
            given(currentTermsVersionRepository.findByTermsId(2L))
                    .willReturn(Optional.of(TermsFixtures.currentVersion(marketing, 3)));
            given(termsAgreementRepository.findByUserIdAndTermsId(USER_ID, 2L)).willReturn(Optional.empty());

            termsService.agree(PUBLIC_ID, List.of(new AgreementItem("marketing", false)));

            ArgumentCaptor<TermsAgreementHistory> captor = ArgumentCaptor.forClass(TermsAgreementHistory.class);
            then(termsAgreementHistoryRepository).should().save(captor.capture());
            assertThat(captor.getValue().getCode()).isEqualTo("marketing");
            assertThat(captor.getValue().getVersion()).isEqualTo(3);
            assertThat(captor.getValue().isAgreed()).isFalse();
        }
    }

    private void stubUser() {
        User user = UserFixtures.localUser("terms@harucut.com", "encoded");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.of(user));
    }
}
