package com.harucut.payment.batch;

import com.harucut.coupon.service.GrantActivationService;
import com.harucut.payment.entity.PaymentOrder;
import com.harucut.payment.enums.BillingKeyStatus;
import com.harucut.payment.enums.OrderStatus;
import com.harucut.payment.enums.OrderType;
import com.harucut.payment.repository.BillingKeyRepository;
import com.harucut.payment.repository.PaymentOrderRepository;
import com.harucut.subscription.config.PlanPricingProperties;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RenewalPreparationService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final List<OrderStatus> UNRESOLVED = List.of(OrderStatus.CREATED, OrderStatus.IN_PROGRESS);

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final BillingKeyRepository billingKeyRepository;
    private final PlanPricingProperties planPricingProperties;
    private final GrantActivationService grantActivationService;

    @Transactional
    public void prepare(Long subscriptionId, LocalDateTime baseTime) {
        UserSubscription subscription = userSubscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null) {
            return;
        }

        // 예약 쿠폰이 있으면 이번 주기는 청구 대신 grant 전환
        if (subscription.getReservedUserCouponId() != null) {
            grantActivationService.activate(subscription, baseTime);
            return;
        }

        Long userId = subscription.getUserId();

        // "모르면 다시 긁지 않는다" — 결과 미확정 주문이 있으면 새 청구 생성 금지
        if (paymentOrderRepository.existsByUserIdAndOrderTypeAndStatusIn(userId, OrderType.RENEWAL, UNRESOLVED)) {
            log.warn("[갱신 배치] subscriptionId={} 결과 미확정 주문 존재 — 건너뜀, 수동 확인 필요", subscriptionId);
            return;
        }

        String idempotencyKey = "renewal:" + subscriptionId + ":" + baseTime.format(DAY_FORMAT);

        if (paymentOrderRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;   // 오늘 이미 시도했다(실패 확정 포함) — 재시도는 내일
        }

        if (billingKeyRepository.findAllByUserIdAndStatus(userId, BillingKeyStatus.ACTIVE).isEmpty()) {
            subscription.markPastDue();
            return;
        }

        int amount = planPricingProperties.priceOf(subscription.getPlanTier());
        paymentOrderRepository.save(
                PaymentOrder.createRenewal(userId, subscription.getPlanTier(), amount, idempotencyKey));
    }
}
