package com.harucut.payment;

import com.harucut.common.exception.BusinessException;
import com.harucut.payment.dto.SubscribeRequest;
import com.harucut.payment.entity.BillingKey;
import com.harucut.payment.entity.Payment;
import com.harucut.payment.entity.PaymentOrder;
import com.harucut.payment.enums.BillingKeyStatus;
import com.harucut.payment.enums.OrderStatus;
import com.harucut.payment.enums.PaymentStatus;
import com.harucut.payment.exception.PaymentErrorCode;
import com.harucut.payment.repository.BillingKeyRepository;
import com.harucut.payment.repository.PaymentOrderRepository;
import com.harucut.payment.repository.PaymentRepository;
import com.harucut.payment.service.PaymentService;
import com.harucut.subscription.dto.SubscriptionResponse;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.enums.SubscriptionStatus;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import com.harucut.support.UserFixtures;
import com.harucut.user.entity.User;
import com.harucut.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * 실제 트랜잭션 경계를 검증하므로 테스트에 @Transactional을 붙이지 않는다.
 * REQUIRES_NEW 커밋이 진짜로 남는지가 관심사라, 테스트 트랜잭션으로 감싸면 검증이 가려진다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("구독 결제 통합")
class PaymentSubscribeFlowTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private BillingKeyRepository billingKeyRepository;

    @Test
    @DisplayName("정상 결제는 구독을 PLUS ACTIVE로 활성화하고 주기는 한 달이다")
    void successActivatesSubscription() {
        User user = newBasicUser("pay-ok@harucut.com");

        SubscriptionResponse response =
                paymentService.subscribe(user.getPublicId(), request("customer-1", "auth-1", "it-ok-1"));

        assertThat(response.planTier()).isEqualTo(PlanTier.PLUS);
        assertThat(response.autoRenew()).isTrue();
        assertThat(response.currentPeriodEnd()).isEqualTo(response.currentPeriodStart().plusMonths(1));

        UserSubscription subscription = userSubscriptionRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.PLUS);
        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("정상 결제는 주문 PAID와 결제 APPROVED 행을 남긴다")
    void successLeavesPaidRecords() {
        User user = newBasicUser("pay-rows@harucut.com");

        paymentService.subscribe(user.getPublicId(), request("customer-1", "auth-1", "it-rows-1"));

        PaymentOrder order = paymentOrderRepository.findByIdempotencyKey("it-rows-1").orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentsOf(order)).singleElement().satisfies(payment -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.getApprovedAt()).isNotNull();
        });
    }

    /*
     * 이 Phase의 백미 — 트랜잭션 분리의 증명.
     * 402가 예외로 터졌는데도 주문·결제 행이 커밋되어 남아 있어야 한다.
     * 커밋 #2가 예외 "전에" 끝나는 구조가 아니면 이 테스트는 실패한다.
     */
    @Test
    @DisplayName("청구가 실패해도 주문 FAILED와 결제 FAILED 행이 남는다")
    void chargeFailureLeavesFailedRecords() {
        User user = newBasicUser("pay-fail@harucut.com");

        assertThatThrownBy(() ->
                paymentService.subscribe(user.getPublicId(), request("customer-FAIL", "auth-1", "it-fail-1")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_FAILED);

        PaymentOrder order = paymentOrderRepository.findByIdempotencyKey("it-fail-1").orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(paymentsOf(order)).singleElement().satisfies(payment -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getApprovedAt()).isNull();
        });
        assertThat(userSubscriptionRepository.findByUserId(user.getId()).orElseThrow().getPlanTier())
                .isEqualTo(PlanTier.BASIC);
    }

    @Test
    @DisplayName("빌링키 발급이 실패하면 주문이 아예 안 생겨서 그 키를 다시 쓸 수 있다")
    void issueFailureLeavesNoOrder() {
        User user = newBasicUser("pay-noissue@harucut.com");

        assertThatThrownBy(() ->
                paymentService.subscribe(user.getPublicId(), request("customer-1", "auth-FAIL", "it-noissue-1")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.BILLING_KEY_ISSUE_FAILED);

        assertThat(paymentOrderRepository.findByIdempotencyKey("it-noissue-1")).isEmpty();
    }

    @Test
    @DisplayName("성공한 결제의 키를 재전송하면 재청구 없이 같은 형태의 응답을 받는다")
    void paidKeyReplaysWithoutNewCharge() {
        User user = newBasicUser("pay-replay@harucut.com");
        SubscribeRequest request = request("customer-1", "auth-1", "it-replay-1");
        SubscriptionResponse first = paymentService.subscribe(user.getPublicId(), request);
        long orders = paymentOrderRepository.count();
        long payments = paymentRepository.count();

        SubscriptionResponse second = paymentService.subscribe(user.getPublicId(), request);

        assertThat(second.planTier()).isEqualTo(first.planTier());
        assertThat(second.autoRenew()).isEqualTo(first.autoRenew());
        assertThat(paymentOrderRepository.count()).isEqualTo(orders);
        assertThat(paymentRepository.count()).isEqualTo(payments);
    }

    @Test
    @DisplayName("실패한 결제의 키를 재전송하면 재청구 없이 같은 402를 받는다")
    void failedKeyReplays402WithoutNewCharge() {
        User user = newBasicUser("pay-refail@harucut.com");
        SubscribeRequest request = request("customer-FAIL", "auth-1", "it-refail-1");
        assertThatThrownBy(() -> paymentService.subscribe(user.getPublicId(), request))
                .isInstanceOf(BusinessException.class);
        long orders = paymentOrderRepository.count();
        long payments = paymentRepository.count();

        assertThatThrownBy(() -> paymentService.subscribe(user.getPublicId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_FAILED);

        assertThat(paymentOrderRepository.count()).isEqualTo(orders);
        assertThat(paymentRepository.count()).isEqualTo(payments);
    }

    @Test
    @DisplayName("다시 결제하면 새 빌링키가 ACTIVE가 되고 옛 키는 DELETED가 된다")
    void newBillingKeyReplacesOldOne() {
        User user = newBasicUser("pay-rekey@harucut.com");
        assertThatThrownBy(() ->
                paymentService.subscribe(user.getPublicId(), request("customer-FAIL", "auth-1", "it-rekey-1")))
                .isInstanceOf(BusinessException.class);

        paymentService.subscribe(user.getPublicId(), request("customer-2", "auth-1", "it-rekey-2"));

        List<BillingKey> keys = billingKeyRepository.findAll().stream()
                .filter(key -> key.getUserId().equals(user.getId()))
                .toList();
        assertThat(keys).hasSize(2);
        assertThat(keys).filteredOn(key -> key.getStatus() == BillingKeyStatus.ACTIVE)
                .singleElement()
                .satisfies(key -> assertThat(key.getBillingKeyValue()).contains("customer-2"));
    }

    private User newBasicUser(String email) {
        User user = userRepository.save(UserFixtures.localUser(email, "encoded"));
        userSubscriptionRepository.save(UserSubscription.createBasic(user.getId()));
        return user;
    }

    private SubscribeRequest request(String customerKey, String authKey, String idempotencyKey) {
        return new SubscribeRequest(PlanTier.PLUS, customerKey, authKey, idempotencyKey);
    }

    private List<Payment> paymentsOf(PaymentOrder order) {
        return paymentRepository.findAll().stream()
                .filter(payment -> payment.getOrder().getId().equals(order.getId()))
                .toList();
    }
}
