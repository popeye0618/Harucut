package com.harucut.payment.config;

import com.harucut.payment.gateway.PaymentGateway;
import com.harucut.payment.gateway.PgProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!local & !test")
public class MockGatewayGuard {

    public MockGatewayGuard(PaymentGateway paymentGateway, PaymentProperties properties) {
        if (paymentGateway.provider() != PgProvider.MOCK) {
            return;
        }
        if (!properties.mock().allowedOutsideLocal()) {
            throw new IllegalStateException("Mock payment gateway must not be active outside local/test profiles.");
        }
        log.warn("[결제] mock 게이트웨이가 허용된 채로 기동했다 — 실제 결제는 일어나지 않는다. "
                + "PG 를 붙이면 payment.mock.allowed-outside-local(PAYMENT_MOCK_ALLOWED)을 지울 것");
    }
}
