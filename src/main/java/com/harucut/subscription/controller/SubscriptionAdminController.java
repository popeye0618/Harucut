package com.harucut.subscription.controller;

import com.harucut.common.response.Response;
import com.harucut.config.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import com.harucut.subscription.dto.SubscriptionAdminResponse;
import com.harucut.subscription.service.SubscriptionAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "구독 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/subscriptions")
@PreAuthorize("hasRole('ADMIN')")
public class SubscriptionAdminController {

    private final SubscriptionAdminService subscriptionAdminService;

    @Operation(
            summary = "사용자 구독 조회",
            description = """
                    사용자 API 와 달리 **DB 원본(`planTier`)과 실제 적용값(`effectiveTier`)을 함께** 준다.
                    둘이 다르면 "주기는 끝났는데 강등 배치가 아직 안 돌았다"는 뜻이다.

                    ⚠️ **기존 서버에 있던 요금제 강제 변경 API(`PATCH .../{userId}/plan`)는 없앴다.**
                    결제 없이 등급을 올리면 BASIC 사용자에게는 만료 시각이 없어 무기한 유료가 되기 때문이다.
                    등급 부여는 쿠폰으로 한다.

                    `userId` 는 내부 숫자 PK 다 — 사용자 API 가 쓰는 12자 publicId 가 아니다.
                    """)
    @ApiErrors("SUBS-004: 그 사용자의 구독 행이 없음 (없는 userId 를 넣은 경우 포함)")
    @GetMapping("/{userId}")
    public Response<SubscriptionAdminResponse> subscription(
            @Parameter(description = "사용자 내부 ID (publicId 아님)", example = "1") @PathVariable Long userId) {
        return Response.ok(subscriptionAdminService.getSubscription(userId));
    }
}