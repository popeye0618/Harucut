package com.harucut.coupon.controller;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.Response;
import com.harucut.config.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import com.harucut.coupon.dto.MyCouponResponse;
import com.harucut.coupon.dto.RedeemRequest;
import com.harucut.coupon.dto.RedeemResponse;
import com.harucut.coupon.service.CouponService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "쿠폰")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/coupons")
public class CouponController {

    private final CouponService couponService;

    @Operation(
            summary = "쿠폰 사용",
            description = """
                    코드를 넣으면 **무료 사용자면 즉시 적용, 이미 유료 사용자면 현재 주기 뒤로 예약**된다.
                    응답의 `applied` 로 구분한다.

                    ⚠️ **`applied: false` 는 실패가 아니다.** 200 이고 쿠폰은 정상 등록됐다.
                    "지금 적용됐습니다"와 "구독이 끝나는 {startsAt}부터 적용됩니다"로 문구를 나눠야 한다.
                    예약은 돈 내고 쓰는 구독을 무료 쿠폰이 덮어써 강등시키지 않으려는 장치다.

                    부여 기간은 **항상 1개월 고정**이다. 쿠폰마다 다르게 정할 수 없다.

                    **거절 사유가 여섯 가지이고 안내가 전부 다르다.** 400 은 쿠폰 자체가 못 쓰는 상태,
                    404 는 코드가 틀린 것, 409 셋은 각각 "상한 소진" · "이미 쓴 쿠폰" · "예약이 이미 있음"이다.
                    HTTP 상태로 뭉뚱그리지 말고 코드로 분기할 것.

                    **예약 슬롯은 하나뿐이다**(`COUPON-007`). 예약이 활성화될 때까지 다른 쿠폰을 쓸 수 없다.
                    """)
    @ApiErrors({
            "COUPON-001: 없는 코드 (대소문자·공백은 서버가 맞춰주므로 진짜 오타다)",
            "COUPON-004: 비활성화됐거나 사용 마감이 지난 쿠폰",
            "COUPON-005: 전체 사용 상한에 도달",
            "COUPON-006: 이 사용자가 이미 쓴 쿠폰",
            "COUPON-007: 아직 시작 안 한 예약 쿠폰이 이미 있음 — 슬롯은 하나뿐",
            "COUPON-008: 만료 시각이 없는 유료 구독이라 예약을 걸 자리가 없음",
            "GEN-031: 토큰은 유효한데 그 계정이 사라짐"
    })
    @PostMapping("/redeem")
    public Response<RedeemResponse> redeem(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody RedeemRequest request) {
        return Response.ok(couponService.redeem(principal.publicId(), request.code()));
    }

    @Operation(
            summary = "내 쿠폰 목록",
            description = """
                    내가 쓴 쿠폰의 이력이다. 페이징이 없고 **정렬도 보장하지 않는다.**

                    `status` 가 `RESERVED` 면 아직 시작 안 한 예약분이다.
                    `redeemedAt` 은 **코드를 입력한 시각**이지 적용이 시작된 시각이 아니다 —
                    예약분은 둘이 다르므로 "사용일"로 표시하면 오해를 부른다.

                    `publicId` 는 **이 사용 이력의 ID** 이지 쿠폰 자체의 ID 가 아니다.
                    """)
    @ApiErrors("GEN-031: 토큰은 유효한데 그 계정이 사라짐")
    @GetMapping
    public Response<List<MyCouponResponse>> myCoupons(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Response.ok(couponService.getMyCoupons(principal.publicId()));
    }
}
