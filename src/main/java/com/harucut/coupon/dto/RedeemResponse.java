package com.harucut.coupon.dto;

import com.harucut.subscription.enums.PlanTier;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "쿠폰 사용 결과. **`applied` 에 따라 안내 문구가 달라져야 한다**")
public record RedeemResponse(

        @Schema(description = """
                `true` **지금 바로 적용됐다** (무료 사용자였음) ·
                `false` **현재 구독이 끝난 뒤에 시작하도록 예약됐다** (이미 유료 사용자였음)

                예약은 손해를 막는 장치다 — 돈 내고 쓰는 PRO 위에 PLUS 쿠폰을 덮어써 강등시키지 않는다.
                ⚠️ **`false` 를 실패로 처리하지 말 것.** 200 이고 쿠폰은 정상 등록됐다.""",
                example = "true")
        boolean applied,

        @Schema(description = "이 쿠폰이 주는 등급", example = "PRO")
        PlanTier grantTier,

        @Schema(description = """
                적용 시작 시각. `applied=false` 면 **현재 결제 주기가 끝나는 시각**이다.

                ⚠️ 예약분의 실제 활성화는 새벽 배치가 한다. 주기가 끝난 직후 잠깐(최대 하루 반)
                BASIC 으로 보일 수 있다.""",
                example = "2026-08-03T10:00:00")
        LocalDateTime startsAt,

        @Schema(description = "적용 종료 시각. 항상 `startsAt` + 1개월이다 — 쿠폰마다 기간을 정할 수 없다",
                example = "2026-09-03T10:00:00")
        LocalDateTime endsAt
) {
}
