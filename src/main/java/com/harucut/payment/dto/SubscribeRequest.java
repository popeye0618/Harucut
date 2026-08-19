package com.harucut.payment.dto;

import com.harucut.subscription.enums.PlanTier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "구독 결제 요청")
public record SubscribeRequest(

        @NotNull
        @Schema(description = "결제할 요금제. **`BASIC` 은 보낼 수 없다**(무료라 결제 대상이 아님)",
                example = "PLUS", allowableValues = {"PLUS", "PRO"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        PlanTier planTier,

        @NotBlank
        @Schema(description = "카드 등록(빌링키 발급) 인증 후 PG 가 준 키",
                example = "auth-abc123", requiredMode = Schema.RequiredMode.REQUIRED)
        String authKey,

        @NotBlank @Size(max = 100)
        @Schema(description = """
                중복 결제 방지 키. 최대 100자. **기존 서버에 없던 필드다.**

                **결제 버튼을 누를 때마다 새로 만들고, 네트워크 재시도에는 같은 값을 다시 보낸다.**
                같은 키로 다시 오면 새로 결제하지 않고 그 주문의 결과를 돌려준다 —
                이미 결제됐으면 구독 정보, 실패했으면 `PAY-002`, 아직 처리 중이면 `PAY-006` 이다.

                ⚠️ **같은 키를 다른 요금제로 재사용하면 `PAY-006` 이다.** 요금제를 바꿔 다시 시도할 때는
                키도 새로 만들 것.

                (`customerKey` 는 더 이상 받지 않는다 — 로그인한 사용자로 서버가 정한다.)""",
                example = "550e8400-e29b-41d4-a716-446655440000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String idempotencyKey
) {
}
