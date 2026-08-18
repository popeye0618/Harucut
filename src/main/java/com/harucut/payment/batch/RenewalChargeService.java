package com.harucut.payment.batch;

import com.harucut.payment.gateway.PaymentGateway;
import com.harucut.payment.gateway.dto.BillingChargeCommand;
import com.harucut.payment.gateway.dto.PaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RenewalChargeService {

    private final RenewalChargeTransactionService transactionService;
    private final PaymentGateway paymentGateway;

    public void charge(Long orderId, LocalDateTime baseTime) {
        RenewalChargeTransactionService.ChargeTarget target = transactionService.markCharging(orderId);
        if (target == null) {
            return;
        }

        PaymentResult result = paymentGateway.charge(new BillingChargeCommand(
                target.billingKeyValue(),
                target.orderPublicId(),
                target.amount(),
                target.planTier().name() + " 구독 갱신",
                target.customerKey()));

        transactionService.applyResult(orderId, result, baseTime);
    }
}
