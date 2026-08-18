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

    // authKey에 FAIL → 발급 실패(502 경로), DECLINE → 발급은 성공하고 그 키의 청구가 실패(402 경로).
    // DECLINE 마커는 빌링키 값에 실려 저장까지 통과한 뒤 청구 시점에 걸린다.
    private static final String ISSUE_FAIL_MARKER = "FAIL";
    private static final String CHARGE_FAIL_MARKER = "DECLINE";

    private final PaymentProperties properties;
    private final Clock clock;

    @Override
    public PgProvider provider() {
        return PgProvider.MOCK;
    }

    @Override
    public BillingKeyResult issueBillingKey(IssueBillingKeyCommand command) {
        if (command.authKey().contains(ISSUE_FAIL_MARKER)) {
            return BillingKeyResult.failure("MOCK_ISSUE_FAILED", "Mock billing key issuance failed.");
        }

        String marker = command.authKey().contains(CHARGE_FAIL_MARKER) ? CHARGE_FAIL_MARKER + "-" : "";
        String billingKeyValue = "mock-bk-" + marker + command.customerKey() + "-" + System.nanoTime();

        return BillingKeyResult.success(billingKeyValue, "**** **** **** 1234");
    }

    @Override
    public PaymentResult charge(BillingChargeCommand command) {
        if (properties.mock().failCharge() || command.billingKeyValue().contains(CHARGE_FAIL_MARKER)) {
            return PaymentResult.failure("MOCK_CHARGE_FAILED", "Mock charge failed.");
        }

        return PaymentResult.success("mock-tx-" + command.orderKey(), LocalDateTime.now(clock));
    }
}
