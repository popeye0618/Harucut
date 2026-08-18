package com.harucut.payment.batch;

import com.harucut.payment.entity.BillingKey;
import com.harucut.payment.entity.Payment;
import com.harucut.payment.entity.PaymentOrder;
import com.harucut.payment.enums.BillingKeyStatus;
import com.harucut.payment.enums.OrderStatus;
import com.harucut.payment.gateway.dto.PaymentResult;
import com.harucut.payment.repository.BillingKeyRepository;
import com.harucut.payment.repository.PaymentOrderRepository;
import com.harucut.payment.repository.PaymentRepository;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import com.harucut.user.entity.User;
import com.harucut.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RenewalChargeTransactionService {

    public record ChargeTarget(
            String orderPublicId,
            String billingKeyValue,
            String customerKey,
            PlanTier planTier,
            int amount) {}

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentRepository paymentRepository;
    private final BillingKeyRepository billingKeyRepository;
    private final UserRepository userRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;

    // "긁는 중" 도장을 찍고 커밋한다 — PG 호출은 반드시 이 커밋 이후여야 한다
    @Transactional
    public ChargeTarget markCharging(Long orderId) {
        PaymentOrder order = paymentOrderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.CREATED) {
            return null;
        }

        List<BillingKey> billingKeys = billingKeyRepository
                .findAllByUserIdAndStatus(order.getUserId(), BillingKeyStatus.ACTIVE);
        User user = userRepository.findById(order.getUserId()).orElse(null);
        if (billingKeys.isEmpty() || user == null) {
            // 준비 커밋과 청구 사이에 카드·사용자가 사라진 드문 경우 — 시도 없이 실패 확정
            order.markFailed();
            userSubscriptionRepository.findByUserId(order.getUserId())
                    .ifPresent(UserSubscription::markPastDue);
            return null;
        }

        order.markCharging();
        return new ChargeTarget(order.getPublicId(), billingKeys.get(0).getBillingKeyValue(),
                user.getPublicId(), order.getTargetTier(), order.getAmount());
    }

    // 성공이든 실패든 이 트랜잭션은 커밋된다 — 결제 이력은 어느 쪽이든 반드시 남는다
    @Transactional
    public void applyResult(Long orderId, PaymentResult result, LocalDateTime baseTime) {
        PaymentOrder order = paymentOrderRepository.findById(orderId).orElseThrow();
        Payment payment = Payment.request(order, order.getAmount());

        if (result.success() && result.pgTransactionId() != null) {
            payment.approve(result.pgTransactionId(), result.approvedAt() != null ? result.approvedAt() : baseTime);
            order.markPaid();
            paymentRepository.save(payment);
            userSubscriptionRepository.findByUserId(order.getUserId())
                    .ifPresent(subscription -> subscription.renew(baseTime, baseTime.plusMonths(1)));
            return;
        }

        payment.fail(result.failureCode(), result.failureMessage());
        order.markFailed();
        paymentRepository.save(payment);
        userSubscriptionRepository.findByUserId(order.getUserId())
                .ifPresent(UserSubscription::markPastDue);
    }
}
