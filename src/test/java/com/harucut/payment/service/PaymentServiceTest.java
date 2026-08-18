package com.harucut.payment.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.payment.dto.SubscribeRequest;
import com.harucut.payment.entity.PaymentOrder;
import com.harucut.payment.exception.PaymentErrorCode;
import com.harucut.payment.gateway.PaymentGateway;
import com.harucut.payment.gateway.PgProvider;
import com.harucut.payment.gateway.dto.BillingChargeCommand;
import com.harucut.payment.gateway.dto.BillingKeyResult;
import com.harucut.payment.gateway.dto.IssueBillingKeyCommand;
import com.harucut.payment.gateway.dto.PaymentResult;
import com.harucut.payment.repository.PaymentOrderRepository;
import com.harucut.payment.service.PaymentTransactionService.ChargeApplyResult;
import com.harucut.payment.service.PaymentTransactionService.CreatedOrder;
import com.harucut.subscription.config.PlanPricingProperties;
import com.harucut.subscription.dto.SubscriptionResponse;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import com.harucut.support.UserFixtures;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    private static final String PUBLIC_ID = "user-pub-001";
    private static final Long USER_ID = 1L;
    private static final String IDEM_KEY = "idem-1";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 10, 0);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentTransactionService paymentTransactionService;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentGateway, paymentTransactionService, paymentOrderRepository,
                userRepository, userSubscriptionRepository,
                new PlanPricingProperties(0, 3900, 9900),
                Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
    }

    @Nested
    @DisplayName("검증")
    class Validation {

        @Test
        @DisplayName("BASIC 요청은 PAY-007이고 아무것도 안 건드린다")
        void rejectsBasic() {
            assertThatThrownBy(() -> service.subscribe(PUBLIC_ID, request(PlanTier.BASIC)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_TARGET_PLAN);

            then(userRepository).shouldHaveNoInteractions();
            then(paymentGateway).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("없는 사용자는 GEN-031이다")
        void unknownUser() {
            given(userRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.subscribe(PUBLIC_ID, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(GlobalErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("유효한 유료 구독 중이면 PAY-003이고 PG를 부르지 않는다")
        void rejectsAlreadySubscribed() {
            givenUser();
            givenNoExistingOrder();
            given(userSubscriptionRepository.findByUserId(USER_ID))
                    .willReturn(Optional.of(activePlusSubscription()));

            assertThatThrownBy(() -> service.subscribe(PUBLIC_ID, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(PaymentErrorCode.ALREADY_SUBSCRIBED);

            then(paymentGateway).shouldHaveNoInteractions();
        }

        /*
         * DB의 planTier가 아니라 effectiveTier로 판정하는지 확인한다.
         * 만료 배치가 아직 강등하지 못한 구독이 재구독을 막으면 안 된다.
         */
        @Test
        @DisplayName("기간이 끝난 유료 구독은 재구독을 막지 않는다")
        void expiredPaidDoesNotBlock() {
            givenUser();
            givenNoExistingOrder();
            UserSubscription expired = UserSubscription.createBasic(USER_ID);
            expired.activatePaid(PlanTier.PLUS, NOW.minusMonths(2), NOW.minusMonths(1));
            given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.of(expired));
            givenIssueSucceeds();
            givenOrderCreated();
            givenChargeSucceeds();
            givenApplyReturns(ChargeApplyResult.ok(activePlusSubscription()));

            SubscriptionResponse response = service.subscribe(PUBLIC_ID, request());

            assertThat(response.planTier()).isEqualTo(PlanTier.PLUS);
        }
    }

    @Nested
    @DisplayName("멱등 재생")
    class Replay {

        /*
         * 재생의 핵심: PG를 다시 부르지 않는다. 재전송에 돈이 두 번 나가면 안 된다.
         */
        @Test
        @DisplayName("PAID 키 재전송은 현재 구독을 돌려주고 PG를 부르지 않는다")
        void paidReplaysSubscription() {
            givenUser();
            PaymentOrder order = order(USER_ID, PlanTier.PLUS);
            order.markPaid();
            givenExistingOrder(order);
            given(userSubscriptionRepository.findByUserId(USER_ID))
                    .willReturn(Optional.of(activePlusSubscription()));

            SubscriptionResponse response = service.subscribe(PUBLIC_ID, request());

            assertThat(response.planTier()).isEqualTo(PlanTier.PLUS);
            then(paymentGateway).shouldHaveNoInteractions();
            then(paymentTransactionService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("FAILED 키 재전송은 같은 402를 다시 받는다")
        void failedReplays402() {
            givenUser();
            PaymentOrder order = order(USER_ID, PlanTier.PLUS);
            order.markFailed();
            givenExistingOrder(order);

            assertThatThrownBy(() -> service.subscribe(PUBLIC_ID, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_FAILED);

            then(paymentGateway).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("CREATED 키 재전송은 처리 중이라는 뜻의 409다")
        void inFlightReplays409() {
            givenUser();
            givenExistingOrder(order(USER_ID, PlanTier.PLUS));

            assertThatThrownBy(() -> service.subscribe(PUBLIC_ID, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(PaymentErrorCode.DUPLICATE_PAYMENT);
        }

        @Test
        @DisplayName("같은 키를 다른 tier로 재사용하면 409다")
        void differentTierRejected() {
            givenUser();
            PaymentOrder order = order(USER_ID, PlanTier.PLUS);
            order.markPaid();
            givenExistingOrder(order);

            assertThatThrownBy(() -> service.subscribe(PUBLIC_ID, request(PlanTier.PRO)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(PaymentErrorCode.DUPLICATE_PAYMENT);
        }

        @Test
        @DisplayName("남의 주문 키면 재생하지 않고 409다")
        void foreignKeyRejected() {
            givenUser();
            givenExistingOrder(order(2L, PlanTier.PLUS));

            assertThatThrownBy(() -> service.subscribe(PUBLIC_ID, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(PaymentErrorCode.DUPLICATE_PAYMENT);
        }
    }

    @Nested
    @DisplayName("결제 흐름")
    class Flow {

        @Test
        @DisplayName("빌링키 발급 실패는 PAY-001이고 주문을 만들지 않는다")
        void issueFailureIs502() {
            givenUser();
            givenNoExistingOrder();
            givenBasicSubscription();
            given(paymentGateway.issueBillingKey(any(IssueBillingKeyCommand.class)))
                    .willReturn(BillingKeyResult.failure("MOCK_ISSUE_FAILED", "Mock billing key issuance failed."));

            assertThatThrownBy(() -> service.subscribe(PUBLIC_ID, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(PaymentErrorCode.BILLING_KEY_ISSUE_FAILED);

            then(paymentTransactionService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("주문 생성이 유니크 제약에 걸리면 동시 요청이므로 409다")
        void concurrentDuplicateIs409() {
            givenUser();
            givenNoExistingOrder();
            givenBasicSubscription();
            givenIssueSucceeds();
            given(paymentGateway.provider()).willReturn(PgProvider.MOCK);
            given(paymentTransactionService.createInitialOrder(eq(USER_ID), eq(PlanTier.PLUS), eq(3900),
                    eq(IDEM_KEY), eq(PgProvider.MOCK), any(BillingKeyResult.class)))
                    .willThrow(new DataIntegrityViolationException("uk_payment_order_idempotency_key"));

            assertThatThrownBy(() -> service.subscribe(PUBLIC_ID, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(PaymentErrorCode.DUPLICATE_PAYMENT);
        }

        @Test
        @DisplayName("청구 실패는 결과를 기록한 뒤 PAY-002다")
        void chargeFailureIs402() {
            givenUser();
            givenNoExistingOrder();
            givenBasicSubscription();
            givenIssueSucceeds();
            givenOrderCreated();
            given(paymentGateway.charge(any(BillingChargeCommand.class)))
                    .willReturn(PaymentResult.failure("MOCK_CHARGE_FAILED", "Mock charge failed."));
            givenApplyReturns(ChargeApplyResult.failed());

            assertThatThrownBy(() -> service.subscribe(PUBLIC_ID, request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_FAILED);

            // 402를 던지기 전에 실패 기록 커밋(applyInitialChargeResult)이 먼저 일어났다
            then(paymentTransactionService).should()
                    .applyInitialChargeResult(eq(USER_ID), eq(10L), any(PaymentResult.class), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("정상 결제는 활성화된 구독을 돌려준다")
        void successReturnsActivatedSubscription() {
            givenUser();
            givenNoExistingOrder();
            givenBasicSubscription();
            givenIssueSucceeds();
            givenOrderCreated();
            givenChargeSucceeds();
            givenApplyReturns(ChargeApplyResult.ok(activePlusSubscription()));

            SubscriptionResponse response = service.subscribe(PUBLIC_ID, request());

            assertThat(response.planTier()).isEqualTo(PlanTier.PLUS);
            assertThat(response.autoRenew()).isTrue();
            assertThat(response.currentPeriodEnd()).isEqualTo(response.currentPeriodStart().plusMonths(1));
        }

        @Test
        @DisplayName("청구 명령의 금액은 가격 설정에서, orderKey는 주문 publicId에서 온다")
        void chargeCommandIsBuiltFromOrderAndPricing() {
            givenUser();
            givenNoExistingOrder();
            givenBasicSubscription();
            givenIssueSucceeds();
            givenOrderCreated();
            givenChargeSucceeds();
            givenApplyReturns(ChargeApplyResult.ok(activePlusSubscription()));

            service.subscribe(PUBLIC_ID, request());

            ArgumentCaptor<BillingChargeCommand> captor = ArgumentCaptor.forClass(BillingChargeCommand.class);
            then(paymentGateway).should().charge(captor.capture());
            assertThat(captor.getValue().amount()).isEqualTo(3900);
            assertThat(captor.getValue().orderKey()).isEqualTo("order-pub-1");
            assertThat(captor.getValue().billingKeyValue()).isEqualTo("bk-1");
            assertThat(captor.getValue().customerKey()).isEqualTo(PUBLIC_ID);
        }

        /*
         * customerKey는 요청 DTO에 없다. 프론트 값을 믿으면 남의 customerKey로
         * 카드를 등록할 여지가 생겨서, 서버가 principal의 publicId로 정한다.
         */
        @Test
        @DisplayName("빌링키 발급의 customerKey는 요청이 아니라 principal의 publicId다")
        void customerKeyComesFromPrincipal() {
            givenUser();
            givenNoExistingOrder();
            givenBasicSubscription();
            givenIssueSucceeds();
            givenOrderCreated();
            givenChargeSucceeds();
            givenApplyReturns(ChargeApplyResult.ok(activePlusSubscription()));

            service.subscribe(PUBLIC_ID, request());

            ArgumentCaptor<IssueBillingKeyCommand> captor = ArgumentCaptor.forClass(IssueBillingKeyCommand.class);
            then(paymentGateway).should().issueBillingKey(captor.capture());
            assertThat(captor.getValue().customerKey()).isEqualTo(PUBLIC_ID);
        }
    }

    private void givenUser() {
        User user = UserFixtures.localUser("payer@harucut.com", "encoded");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.of(user));
    }

    private void givenNoExistingOrder() {
        given(paymentOrderRepository.findByIdempotencyKey(IDEM_KEY)).willReturn(Optional.empty());
    }

    private void givenExistingOrder(PaymentOrder order) {
        given(paymentOrderRepository.findByIdempotencyKey(IDEM_KEY)).willReturn(Optional.of(order));
    }

    private void givenBasicSubscription() {
        given(userSubscriptionRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(UserSubscription.createBasic(USER_ID)));
    }

    private void givenIssueSucceeds() {
        given(paymentGateway.issueBillingKey(any(IssueBillingKeyCommand.class)))
                .willReturn(BillingKeyResult.success("bk-1", "**** **** **** 1234"));
    }

    private void givenOrderCreated() {
        given(paymentGateway.provider()).willReturn(PgProvider.MOCK);
        given(paymentTransactionService.createInitialOrder(eq(USER_ID), eq(PlanTier.PLUS), eq(3900),
                eq(IDEM_KEY), eq(PgProvider.MOCK), any(BillingKeyResult.class)))
                .willReturn(new CreatedOrder(10L, "order-pub-1", "bk-1"));
    }

    private void givenChargeSucceeds() {
        given(paymentGateway.charge(any(BillingChargeCommand.class)))
                .willReturn(PaymentResult.success("mock-tx-order-pub-1", NOW));
    }

    private void givenApplyReturns(ChargeApplyResult result) {
        given(paymentTransactionService.applyInitialChargeResult(
                eq(USER_ID), eq(10L), any(PaymentResult.class), any(LocalDateTime.class)))
                .willReturn(result);
    }

    private UserSubscription activePlusSubscription() {
        UserSubscription subscription = UserSubscription.createBasic(USER_ID);
        subscription.activatePaid(PlanTier.PLUS, NOW, NOW.plusMonths(1));
        return subscription;
    }

    private PaymentOrder order(Long userId, PlanTier tier) {
        return PaymentOrder.createInitial(userId, tier, 3900, IDEM_KEY);
    }

    private SubscribeRequest request() {
        return request(PlanTier.PLUS);
    }

    private SubscribeRequest request(PlanTier tier) {
        return new SubscribeRequest(tier, "auth-1", IDEM_KEY);
    }
}
