package com.harucut.coupon.dto;

import com.harucut.subscription.enums.PlanTier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Schema(description = "쿠폰 생성 요청")
public record CouponCreateRequest(

        @NotBlank @Size(max = 100)
        @Schema(description = "관리자용 라벨. 사용자의 쿠폰 목록에도 이 이름이 보인다. 최대 100자",
                example = "가입 축하 PRO 1개월", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @NotBlank @Size(max = 32)
        @Schema(description = """
                사용자가 입력할 코드. 최대 32자.
                **대문자로 정규화해 저장한다** — 소문자로 만들어도 대문자로 저장되고,
                사용자는 대소문자 아무렇게나 입력해도 된다.""",
                example = "WELCOME-PRO-2026", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        @NotNull
        @Schema(description = "부여할 등급. **`BASIC` 은 안 된다**(무료라 줄 것이 없음)",
                example = "PRO", allowableValues = {"PLUS", "PRO"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        PlanTier grantTier,

        @Positive
        @Schema(description = "전체 사용 횟수 상한. **생략하면 무제한.** 1 이상이어야 한다", example = "100")
        Integer maxRedemptions,

        @Schema(description = "사용 마감 시각. **생략하면 무기한**", example = "2026-12-31T23:59:59")
        LocalDateTime validUntil
) {
}
