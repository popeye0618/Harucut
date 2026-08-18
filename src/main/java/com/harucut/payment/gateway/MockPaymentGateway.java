package com.harucut.payment.gateway;

import com.harucut.payment.config.PaymentProperties;
import com.harucut.payment.gateway.dto.BillingChargeCommand;
import com.harucut.payment.gateway.dto.BillingKeyResult;
import com.harucut.payment.gateway.dto.IssueBillingKeyCommand;
import com.harucut.payment.gateway.dto.PaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "mock", matchIfMissing = true)
@RequiredArgsConstructor
public class MockPaymentGateway implements PaymentGateway {

    private static final String FAIL_MARKER = "FAIL";

    private final PaymentProperties properties;
    private final Clock clock;

    @Override
    public PgProvider provider() {
        return PgProvider.MOCK;
    }

    @Override
    public BillingKeyResult issueBillingKey(IssueBillingKeyCommand command) {
        if (command.authKey().contains(FAIL_MARKER)) {
            return BillingKeyResult.failure("MOCK_ISSUE_FAILED", "Mock billing key issuance failed.");
        }

        String billingKeyValue = "mock-bk-" + command.customerKey() + "-" + System.nanoTime();

        return BillingKeyResult.success(billingKeyValue, "**** **** **** 1234");
    }

    @Override
    public PaymentResult charge(BillingChargeCommand command) {
        if (properties.mock().failCharge() || command.billingKeyValue().contains(FAIL_MARKER)) {
            return PaymentResult.failure("MOCK_CHARGE_FAILED", "Mock charge failed.");
        }

        return PaymentResult.success("mock-tx-" + command.orderKey(), LocalDateTime.now(clock));
    }
}
