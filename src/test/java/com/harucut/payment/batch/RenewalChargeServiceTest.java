package com.harucut.payment.batch;

import com.harucut.payment.batch.RenewalChargeTransactionService.ChargeTarget;
import com.harucut.payment.gateway.PaymentGateway;
import com.harucut.payment.gateway.dto.BillingChargeCommand;
import com.harucut.payment.gateway.dto.PaymentResult;
import com.harucut.subscription.enums.PlanTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("RenewalChargeService")
class RenewalChargeServiceTest {

    private static final Long ORDER_ID = 10L;
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2031, 1, 10, 0, 0);

    @Mock
    private RenewalChargeTransactionService transactionService;

    @Mock
    private PaymentGateway paymentGateway;

    private RenewalChargeService service;

    @BeforeEach
    void setUp() {
        service = new RenewalChargeService(transactionService, paymentGateway);
    }

    @Test
    @DisplayName("도장이 안 찍혔으면(null) PG를 부르지 않는다")
    void noTargetMeansNoPgCall() {
        given(transactionService.markCharging(ORDER_ID)).willReturn(null);

        service.charge(ORDER_ID, BASE_TIME);

        then(paymentGateway).shouldHaveNoInteractions();
        then(transactionService).should(never()).applyResult(anyLong(), any(), any());
    }

    @Test
    @DisplayName("도장이 찍혔으면 재료 그대로 청구하고 결과를 반영 트랜잭션에 넘긴다")
    void chargesWithTargetAndAppliesResult() {
        given(transactionService.markCharging(ORDER_ID))
                .willReturn(new ChargeTarget("ord-pub-1", "bk-1", "cust-pub-1", PlanTier.PLUS, 3900));
        PaymentResult result = PaymentResult.success("tx-1", BASE_TIME.plusHours(2));
        given(paymentGateway.charge(any(BillingChargeCommand.class))).willReturn(result);

        service.charge(ORDER_ID, BASE_TIME);

        ArgumentCaptor<BillingChargeCommand> captor = ArgumentCaptor.captor();
        then(paymentGateway).should().charge(captor.capture());
        BillingChargeCommand command = captor.getValue();
        assertThat(command.billingKeyValue()).isEqualTo("bk-1");
        assertThat(command.orderKey()).isEqualTo("ord-pub-1");
        assertThat(command.amount()).isEqualTo(3900);
        assertThat(command.orderName()).isEqualTo("PLUS 구독 갱신");
        assertThat(command.customerKey()).isEqualTo("cust-pub-1");
        then(transactionService).should().applyResult(ORDER_ID, result, BASE_TIME);
    }
}
