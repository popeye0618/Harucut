package com.harucut.payment.entity;

import com.harucut.payment.enums.PaymentMethod;
import com.harucut.payment.enums.PaymentStatus;
import com.harucut.subscription.enums.PlanTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Payment")
class PaymentTest {

    private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 8, 18, 10, 0);

    @Test
    @DisplayName("요청 직후 상태는 REQUESTED, 방식은 BILLING_KEY다")
    void startsRequested() {
        Payment payment = payment();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.BILLING_KEY);
    }

    @Test
    @DisplayName("생성하면 publicId가 12자로 채워진다")
    void publicIdIsFilled() {
        assertThat(payment().getPublicId()).hasSize(12);
    }

    @Test
    @DisplayName("approve하면 APPROVED가 되고 승인 정보가 담긴다")
    void approveKeepsApprovalInfo() {
        Payment payment = payment();

        payment.approve("mock-tx-order-1", APPROVED_AT);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getPgTransactionId()).isEqualTo("mock-tx-order-1");
        assertThat(payment.getApprovedAt()).isEqualTo(APPROVED_AT);
    }

    @Test
    @DisplayName("fail하면 FAILED가 되고 실패 정보가 담긴다")
    void failKeepsFailureInfo() {
        Payment payment = payment();

        payment.fail("MOCK_CHARGE_FAILED", "Mock charge failed.");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("MOCK_CHARGE_FAILED");
        assertThat(payment.getFailureMessage()).isEqualTo("Mock charge failed.");
    }

    /*
     * approvedAt은 매출 통계(Phase 14)의 집계 기준이다.
     * 실패 행에 승인 시각이 남으면 실패가 매출로 잡힌다.
     */
    @Test
    @DisplayName("실패한 결제에는 승인 시각이 없다")
    void failedPaymentHasNoApprovedAt() {
        Payment payment = payment();

        payment.fail("MOCK_CHARGE_FAILED", "Mock charge failed.");

        assertThat(payment.getApprovedAt()).isNull();
    }

    private Payment payment() {
        PaymentOrder order = PaymentOrder.createInitial(1L, PlanTier.PLUS, 3900, "idem-1");
        return Payment.request(order, 3900);
    }
}
