package com.harucut.coupon.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.coupon.dto.CouponAdminResponse;
import com.harucut.coupon.dto.CouponCreateRequest;
import com.harucut.coupon.entity.Coupon;
import com.harucut.coupon.exception.CouponErrorCode;
import com.harucut.coupon.repository.CouponRepository;
import com.harucut.subscription.enums.PlanTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponAdminService")
class CouponAdminServiceTest {

    private static final LocalDateTime VALID_UNTIL = LocalDateTime.of(2026, 12, 31, 23, 59, 59);

    @Mock
    private CouponRepository couponRepository;

    private CouponAdminService service;

    @BeforeEach
    void setUp() {
        service = new CouponAdminService(couponRepository);
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("BASIC tier는 COUPON-003이고 리포지토리까지 가지 않는다")
        void rejectsBasicTier() {
            assertThatThrownBy(() -> service.create(request(PlanTier.BASIC, "WELCOME-PRO")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CouponErrorCode.INVALID_GRANT_TIER);

            then(couponRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("이미 있는 코드는 COUPON-002이고 저장하지 않는다")
        void rejectsDuplicateCode() {
            given(couponRepository.existsByCode("WELCOME-PRO")).willReturn(true);

            assertThatThrownBy(() -> service.create(request(PlanTier.PRO, "WELCOME-PRO")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CouponErrorCode.COUPON_CODE_DUPLICATED);

            then(couponRepository).should(never()).saveAndFlush(any(Coupon.class));
        }

        // WELCOME-PRO가 있는데 소문자 welcome-pro로 만들려는 요청도 문앞에서 잡힌다
        @Test
        @DisplayName("중복 검사는 정규화된 코드로 한다")
        void checksDuplicateWithNormalizedCode() {
            given(couponRepository.existsByCode("WELCOME-PRO")).willReturn(true);

            assertThatThrownBy(() -> service.create(request(PlanTier.PRO, "  welcome-pro  ")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CouponErrorCode.COUPON_CODE_DUPLICATED);
        }

        @Test
        @DisplayName("요청 값대로 저장되고 코드는 대문자다")
        void savesNormalizedCoupon() {
            given(couponRepository.existsByCode("WELCOME-PRO")).willReturn(false);
            given(couponRepository.saveAndFlush(any(Coupon.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            service.create(new CouponCreateRequest(
                    "가입 축하 PRO", "welcome-pro", PlanTier.PRO, 100, VALID_UNTIL));

            ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
            then(couponRepository).should().saveAndFlush(captor.capture());
            Coupon saved = captor.getValue();
            assertThat(saved.getCode()).isEqualTo("WELCOME-PRO");
            assertThat(saved.getName()).isEqualTo("가입 축하 PRO");
            assertThat(saved.getGrantTier()).isEqualTo(PlanTier.PRO);
            assertThat(saved.getMaxRedemptions()).isEqualTo(100);
            assertThat(saved.getValidUntil()).isEqualTo(VALID_UNTIL);
        }

        /*
         * 관리자 둘이 같은 코드를 동시에 만든 경우 —
         * 두 번째 INSERT가 unique에 걸리고, 500이 아니라 COUPON-002로 나간다.
         */
        @Test
        @DisplayName("unique 제약에 걸린 동시 중복 생성은 COUPON-002로 변환된다")
        void translatesConstraintViolation() {
            given(couponRepository.existsByCode("WELCOME-PRO")).willReturn(false);
            given(couponRepository.saveAndFlush(any(Coupon.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate"));

            assertThatThrownBy(() -> service.create(request(PlanTier.PRO, "WELCOME-PRO")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CouponErrorCode.COUPON_CODE_DUPLICATED);
        }

        private CouponCreateRequest request(PlanTier tier, String code) {
            return new CouponCreateRequest("가입 축하", code, tier, 100, VALID_UNTIL);
        }
    }

    @Nested
    @DisplayName("목록")
    class GetAll {

        @Test
        @DisplayName("쿠폰이 사용 수를 포함한 응답으로 매핑된다")
        void mapsCouponsWithCount() {
            Coupon coupon = Coupon.create("가입 축하 PRO", "WELCOME-PRO", PlanTier.PRO, 100, VALID_UNTIL);
            ReflectionTestUtils.setField(coupon, "redeemedCount", 3);
            given(couponRepository.findAllByOrderByIdDesc()).willReturn(List.of(coupon));

            List<CouponAdminResponse> result = service.getAll();

            assertThat(result).singleElement().satisfies(response -> {
                assertThat(response.publicId()).isEqualTo(coupon.getPublicId());
                assertThat(response.code()).isEqualTo("WELCOME-PRO");
                assertThat(response.maxRedemptions()).isEqualTo(100);
                assertThat(response.redeemedCount()).isEqualTo(3);
                assertThat(response.active()).isTrue();
            });
        }

        @Test
        @DisplayName("쿠폰이 없으면 빈 목록이다")
        void emptyList() {
            given(couponRepository.findAllByOrderByIdDesc()).willReturn(List.of());

            assertThat(service.getAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("비활성화")
    class Deactivate {

        @Test
        @DisplayName("없는 publicId는 COUPON-001이다")
        void unknownCoupon() {
            given(couponRepository.findByPublicId("no-such-id")).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.deactivate("no-such-id"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);
        }

        @Test
        @DisplayName("찾은 쿠폰이 비활성이 된다")
        void deactivatesCoupon() {
            Coupon coupon = Coupon.create("가입 축하 PRO", "WELCOME-PRO", PlanTier.PRO, null, null);
            given(couponRepository.findByPublicId(coupon.getPublicId())).willReturn(Optional.of(coupon));

            service.deactivate(coupon.getPublicId());

            assertThat(coupon.isActive()).isFalse();
        }
    }
}
