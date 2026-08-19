package com.harucut.coupon.controller;

import com.harucut.common.response.Response;
import com.harucut.config.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import com.harucut.coupon.dto.CouponAdminResponse;
import com.harucut.coupon.dto.CouponCreateRequest;
import com.harucut.coupon.service.CouponAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "쿠폰 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/coupons")
@PreAuthorize("hasRole('ADMIN')")
public class CouponAdminController {

    private final CouponAdminService couponAdminService;

    @Operation(
            summary = "쿠폰 발행",
            description = """
                    만들자마자 활성 상태다.

                    ⚠️ **만든 뒤에는 아무것도 고칠 수 없다.** 이름·상한·마감일 수정 API 가 없고,
                    삭제도 없다. 잘못 만들었으면 비활성화하고 새로 발행하는 수밖에 없다.

                    `code` 는 대문자로 정규화해 저장한다. 소문자로 만들어도 대문자가 되고,
                    사용자는 대소문자 아무렇게나 입력해도 찾힌다.

                    부여 기간은 **1개월 고정**이라 요청에 넣는 자리가 없다.
                    """)
    @ApiErrors({
            "COUPON-003: grantTier 가 BASIC",
            "COUPON-002: 이미 있는 코드 (대문자 정규화 후 비교한다)"
    })
    @PostMapping
    public Response<Void> create(@Valid @RequestBody CouponCreateRequest request) {
        couponAdminService.create(request);
        return Response.ok();
    }

    @Operation(
            summary = "쿠폰 목록",
            description = """
                    비활성 쿠폰까지 전량, 최근 발행순이다.

                    `redeemedCount` 에는 **아직 시작 안 한 예약분도 포함된다** —
                    "실제로 혜택이 나간 수"보다 크거나 같다.
                    `maxRedemptions`·`validUntil` 은 무제한·무기한이면 **키 자체가 없다.**
                    """)
    @GetMapping
    public Response<List<CouponAdminResponse>> coupons() {
        return Response.ok(couponAdminService.getAll());
    }

    @Operation(
            summary = "쿠폰 비활성화",
            description = """
                    **앞으로의 신규 사용만 막는다.** 이미 사용된 쿠폰의 혜택은 회수되지 않고,
                    예약된 것도 예정대로 활성화된다.

                    ⚠️ **되돌리는 API 가 없다.** 비활성화는 편도다.
                    """)
    @ApiErrors("COUPON-001: 없는 쿠폰")
    @PatchMapping("/{publicId}/deactivate")
    public Response<Void> deactivate(
            @Parameter(description = "쿠폰 공개 ID (사용 이력 ID 아님)", example = "aB3dE7fG9h")
            @PathVariable String publicId) {
        couponAdminService.deactivate(publicId);
        return Response.ok();
    }
}
