package com.harucut.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "쿠폰 사용 요청")
public record RedeemRequest(

        @NotBlank
        @Schema(description = """
                쿠폰 코드. **대소문자를 가리지 않고 앞뒤 공백도 잘라낸다** —
                `welcome-pro-2026` 으로 입력해도 찾힌다.""",
                example = "WELCOME-PRO-2026", requiredMode = Schema.RequiredMode.REQUIRED)
        String code
) {
}
