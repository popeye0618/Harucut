package com.harucut.payment.dto;

import com.harucut.payment.entity.PaymentOrder;
import com.harucut.payment.enums.OrderStatus;
import com.harucut.payment.enums.OrderType;
import com.harucut.subscription.enums.PlanTier;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "결제 내역 한 건")
public record PaymentHistoryResponse(

        @Schema(description = "주문 식별자. 문의할 때 쓰는 값이다", example = "aB3dE7fG9h")
        String orderId,

        @Schema(description = "결제한 요금제", example = "PLUS")
        PlanTier planTier,

        @Schema(description = "결제 금액(원)", example = "3900")
        int amount,

        @Schema(description = "`INITIAL` 첫 구독 결제 · `RENEWAL` 정기결제 갱신(배치가 만든 것)",
                example = "INITIAL")
        OrderType orderType,

        @Schema(description = """
                `PAID` 결제 완료 · `FAILED` 실패 ·
                `CREATED`·`IN_PROGRESS` 결과 미확정 (거의 보이지 않는다)

                ⚠️ **실패한 주문도 목록에 남는다.** 결제 성공만 보여주려면 `PAID` 로 걸러야 한다.""",
                example = "PAID")
        OrderStatus status,

        @Schema(description = "주문이 만들어진 시각. 승인 시각이 아니다", example = "2026-08-03T10:20:30")
        LocalDateTime createdAt
) {
    public static PaymentHistoryResponse from(PaymentOrder order) {
        return new PaymentHistoryResponse(
                order.getPublicId(),
                order.getTargetTier(),
                order.getAmount(),
                order.getOrderType(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
