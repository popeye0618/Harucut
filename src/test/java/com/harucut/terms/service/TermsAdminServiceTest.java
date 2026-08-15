package com.harucut.terms.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.common.response.PageResponse;
import com.harucut.support.TermsFixtures;
import com.harucut.terms.dto.TermsAdminResponse;
import com.harucut.terms.dto.TermsAgreementHistoryResponse;
import com.harucut.terms.entity.CurrentTermsVersion;
import com.harucut.terms.entity.Terms;
import com.harucut.terms.entity.TermsAgreementHistory;
import com.harucut.terms.entity.TermsVersion;
import com.harucut.terms.exception.TermsErrorCode;
import com.harucut.terms.repository.CurrentTermsVersionRepository;
import com.harucut.terms.repository.TermsAgreementHistoryRepository;
import com.harucut.terms.repository.TermsRepository;
import com.harucut.terms.repository.TermsVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("TermsAdminService")
class TermsAdminServiceTest {

    @Mock
    private TermsRepository termsRepository;

    @Mock
    private TermsVersionRepository termsVersionRepository;

    @Mock
    private CurrentTermsVersionRepository currentTermsVersionRepository;

    @Mock
    private TermsAgreementHistoryRepository termsAgreementHistoryRepository;

    private TermsAdminService termsAdminService;

    @BeforeEach
    void setUp() {
        termsAdminService = new TermsAdminService(termsRepository, termsVersionRepository,
                currentTermsVersionRepository, termsAgreementHistoryRepository);
    }

    @Nested
    @DisplayName("createTerms")
    class CreateTerms {

        @Test
        @DisplayName("약관 생성 시 version 1이 함께 생성된다")
        void createsVersionOne() {
            given(termsRepository.existsByCode("tos")).willReturn(false);
            given(termsRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(termsVersionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            termsAdminService.createTerms("tos", "이용약관", true, "본문");

            ArgumentCaptor<TermsVersion> captor = ArgumentCaptor.forClass(TermsVersion.class);
            then(termsVersionRepository).should().save(captor.capture());
            assertThat(captor.getValue().getVersion()).isEqualTo(1);
            assertThat(captor.getValue().getContent()).isEqualTo("본문");
        }

        @Test
        @DisplayName("생성된 포인터는 방금 만든 version 1을 가리킨다")
        void pointerTargetsFirstVersion() {
            given(termsRepository.existsByCode("tos")).willReturn(false);
            given(termsRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
            given(termsVersionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            termsAdminService.createTerms("tos", "이용약관", true, "본문");

            ArgumentCaptor<CurrentTermsVersion> captor = ArgumentCaptor.forClass(CurrentTermsVersion.class);
            then(currentTermsVersionRepository).should().save(captor.capture());
            assertThat(captor.getValue().getTermsVersion().getVersion()).isEqualTo(1);
            assertThat(captor.getValue().getTerms().getCode()).isEqualTo("tos");
        }

        @Test
        @DisplayName("코드가 중복이면 TERMS-002이고 아무것도 저장되지 않는다")
        void duplicateCode() {
            given(termsRepository.existsByCode("tos")).willReturn(true);

            assertThatThrownBy(() -> termsAdminService.createTerms("tos", "이용약관", true, "본문"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TermsErrorCode.TERMS_CODE_DUPLICATED);

            then(termsVersionRepository).shouldHaveNoInteractions();
            then(currentTermsVersionRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("reviseTerms")
    class ReviseTerms {

        @Test
        @DisplayName("개정하면 version + 1이 저장되고 포인터가 새 버전으로 옮겨간다")
        void appendsNextVersionAndRepoints() {
            Terms tos = TermsFixtures.terms(1L, "tos", true);
            CurrentTermsVersion current = TermsFixtures.currentVersion(tos, 1);
            given(currentTermsVersionRepository.findByTermsId(1L)).willReturn(Optional.of(current));
            given(termsVersionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            termsAdminService.reviseTerms(1L, "개정 본문");

            ArgumentCaptor<TermsVersion> captor = ArgumentCaptor.forClass(TermsVersion.class);
            then(termsVersionRepository).should().save(captor.capture());
            assertThat(captor.getValue().getVersion()).isEqualTo(2);
            assertThat(current.getTermsVersion()).isSameAs(captor.getValue());
        }

        @Test
        @DisplayName("없는 약관을 개정하면 TERMS-001이다")
        void notFound() {
            given(currentTermsVersionRepository.findByTermsId(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> termsAdminService.reviseTerms(99L, "본문"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TermsErrorCode.TERMS_NOT_FOUND);

            then(termsVersionRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("listAllTerms")
    class ListAllTerms {

        @Test
        @DisplayName("비활성 약관도 active 플래그와 함께 나온다")
        void includesInactive() {
            Terms inactive = TermsFixtures.terms(3L, "marketing", false);
            inactive.deactivate();
            given(currentTermsVersionRepository.findAllWithDetails())
                    .willReturn(List.of(TermsFixtures.currentVersion(inactive, 1)));

            List<TermsAdminResponse> result = termsAdminService.listAllTerms();

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().active()).isFalse();
        }
    }

    @Nested
    @DisplayName("deactivateTerms")
    class DeactivateTerms {

        @Test
        @DisplayName("비활성화하면 active가 false가 된다")
        void deactivates() {
            Terms tos = TermsFixtures.terms(1L, "tos", true);
            given(termsRepository.findById(1L)).willReturn(Optional.of(tos));

            termsAdminService.deactivateTerms(1L);

            assertThat(tos.isActive()).isFalse();
        }

        @Test
        @DisplayName("이미 비활성이어도 예외 없이 그대로 비활성이다 (멱등)")
        void idempotent() {
            Terms tos = TermsFixtures.terms(1L, "tos", true);
            tos.deactivate();
            given(termsRepository.findById(1L)).willReturn(Optional.of(tos));

            termsAdminService.deactivateTerms(1L);

            assertThat(tos.isActive()).isFalse();
        }

        @Test
        @DisplayName("없는 약관이면 TERMS-001이다")
        void notFound() {
            given(termsRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> termsAdminService.deactivateTerms(99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(TermsErrorCode.TERMS_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getAgreementHistory")
    class GetAgreementHistory {

        @Test
        @DisplayName("이력이 code·version·agreed로 매핑되어 페이지로 나온다")
        void mapsHistoryPage() {
            TermsAgreementHistory history = TermsAgreementHistory.of(7L, "tos", 1, true);
            given(termsAgreementHistoryRepository.findByUserIdOrderByIdDesc(eq(7L), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(history)));

            PageResponse<TermsAgreementHistoryResponse> result =
                    termsAdminService.getAgreementHistory(7L, 0, 10);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().getFirst().code()).isEqualTo("tos");
            assertThat(result.content().getFirst().agreed()).isTrue();
        }

        @Test
        @DisplayName("page가 음수면 GEN-002이고 조회하지 않는다")
        void negativePage() {
            assertThatThrownBy(() -> termsAdminService.getAgreementHistory(7L, -1, 10))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

            then(termsAgreementHistoryRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("size가 0이면 GEN-002이고 조회하지 않는다")
        void zeroSize() {
            assertThatThrownBy(() -> termsAdminService.getAgreementHistory(7L, 0, 0))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

            then(termsAgreementHistoryRepository).shouldHaveNoInteractions();
        }
    }
}
