package com.harucut.payment.service;

import com.harucut.payment.entity.BillingKey;
import com.harucut.payment.entity.Payment;
import com.harucut.payment.entity.PaymentOrder;
import com.harucut.payment.enums.BillingKeyStatus;
import com.harucut.payment.gateway.PgProvider;
import com.harucut.payment.gateway.dto.BillingKeyResult;
import com.harucut.payment.gateway.dto.PaymentResult;
import com.harucut.payment.repository.BillingKeyRepository;
import com.harucut.payment.repository.PaymentOrderRepository;
import com.harucut.payment.repository.PaymentRepository;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final BillingKeyRepository billingKeyRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentRepository paymentRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;

    public record CreatedOrder(Long orderId, String orderPublicId, String billingKeyValue) {}

    public record ChargeApplyResult(boolean success, UserSubscription subscription) {
        public static ChargeApplyResult ok(UserSubscription subscription) {
            return new ChargeApplyResult(true, subscription);
        }

        public static ChargeApplyResult failed() {
            return new ChargeApplyResult(false, null);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreatedOrder createInitialOrder(Long userId, PlanTier planTier, int amount,
                                           String idempotencyKey, PgProvider provider,
                                           BillingKeyResult issued) {
        billingKeyRepository.findAllByUserIdAndStatus(userId, BillingKeyStatus.ACTIVE)
                .forEach(BillingKey::delete);

        billingKeyRepository.save(
                BillingKey.issue(userId, provider, issued.billingKeyValue(), issued.maskedCard())
        );

        PaymentOrder order = paymentOrderRepository.save(
                PaymentOrder.createInitial(userId, planTier, amount, idempotencyKey)
        );

        return new CreatedOrder(order.getId(), order.getPublicId(), issued.billingKeyValue());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChargeApplyResult applyInitialChargeResult(Long userId, Long orderId, PaymentResult chargeResult, LocalDateTime now) {
        PaymentOrder order = paymentOrderRepository.findById(orderId).orElseThrow();
        Payment payment = Payment.request(order, order.getAmount());

        if (!chargeResult.success()) {
            payment.fail(chargeResult.failureCode(), chargeResult.failureMessage());
            order.markFailed();
            paymentRepository.save(payment);
            return ChargeApplyResult.failed();
        }

        payment.approve(chargeResult.pgTransactionId(), approvedAtOrNow(chargeResult, now));
        order.markPaid();
        paymentRepository.save(payment);

        UserSubscription subscription = userSubscriptionRepository.findByUserId(userId)
                .orElseGet(() ->
                        userSubscriptionRepository.save(UserSubscription.createBasic(userId)));

        subscription.activatePaid(order.getTargetTier(), now, now.plusMonths(1));

        return ChargeApplyResult.ok(subscription);
    }

    private LocalDateTime approvedAtOrNow(PaymentResult result, LocalDateTime now) {
        return result.approvedAt() != null ? result.approvedAt() : now;
    }
}
