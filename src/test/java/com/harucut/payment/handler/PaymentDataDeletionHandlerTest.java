package com.harucut.payment.handler;

import com.harucut.config.JpaAuditingConfig;
import com.harucut.payment.entity.BillingKey;
import com.harucut.payment.entity.PaymentOrder;
import com.harucut.payment.gateway.PgProvider;
import com.harucut.payment.repository.BillingKeyRepository;
import com.harucut.payment.repository.PaymentOrderRepository;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.support.FixedClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureJson
@Import({JpaAuditingConfig.class, FixedClockConfig.class, PaymentDataDeletionHandler.class})
@ActiveProfiles("test")
@DisplayName("PaymentDataDeletionHandler")
class PaymentDataDeletionHandlerTest {

    @Autowired
    private PaymentDataDeletionHandler handler;

    @Autowired
    private BillingKeyRepository billingKeyRepository;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Test
    @DisplayName("내 빌링키만 지워지고 다른 사용자 것은 남는다")
    void deletesOnlyMyBillingKeys() {
        billingKeyRepository.save(BillingKey.issue(1L, PgProvider.MOCK, "bk-1", "1234-****"));
        billingKeyRepository.save(BillingKey.issue(2L, PgProvider.MOCK, "bk-2", "5678-****"));
        billingKeyRepository.flush();

        handler.handleUserDeletion(1L);

        assertThat(billingKeyRepository.findAll()).singleElement()
                .satisfies(key -> assertThat(key.getUserId()).isEqualTo(2L));
    }

    @Test
    @DisplayName("결제 주문 이력은 지우지 않는다 — 회계·법적 보존")
    void keepsPaymentOrders() {
        paymentOrderRepository.save(PaymentOrder.createInitial(1L, PlanTier.PRO, 9900, "idem-1"));
        paymentOrderRepository.flush();

        handler.handleUserDeletion(1L);

        assertThat(paymentOrderRepository.findAll()).hasSize(1);
    }
}
