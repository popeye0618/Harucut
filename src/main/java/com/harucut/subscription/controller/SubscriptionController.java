package com.harucut.subscription.controller;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.Response;
import com.harucut.config.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import com.harucut.subscription.dto.SubscriptionResponse;
import com.harucut.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "구독")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Operation(
            summary = "내 구독 조회",
            description = """
                    ⚠️ **`planTier` 와 `status` 의 기준이 다르다.**
                    `planTier` 는 지금 실제로 적용되는 등급이고, `status` 는 DB 에 저장된 진행 상태다.

                    결제 주기가 밤 10시에 끝났는데 강등 배치는 새벽 1시 30분에 돈다.
                    그 사이 3시간 반 동안 `status` 는 `ACTIVE` 인데 `planTier` 는 `BASIC` 으로 내려온다.
                    **권한을 판단할 때는 언제나 `planTier` 를 본다.** 그래야 내 정보·사용량 API 와 값이 맞는다.

                    BASIC 사용자는 결제 주기가 없어 `currentPeriodStart`·`currentPeriodEnd` **키 자체가 없다.**
                    null 체크가 아니라 필드 존재 체크로 다룰 것.
                    """)
    @ApiErrors({
            "SUBS-004: 구독 행이 없음 (정상 가입 흐름에서는 생기지 않는다)",
            "GEN-031: 토큰은 유효한데 그 계정이 사라짐"
    })
    @GetMapping
    public Response<SubscriptionResponse> mySubscription(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Response.ok(subscriptionService.getMySubscription(principal.publicId()));
    }

    @Operation(
            summary = "자동갱신 해지",
            description = """
                    요청 본문이 없다.

                    **즉시 강등되지 않는다.** `autoRenew` 가 꺼지고 상태가 `CANCELED` 로 바뀔 뿐,
                    이미 결제한 주기(`currentPeriodEnd`)까지는 유료 등급이 그대로 유지된다.
                    **환불도 없다.** 해지 버튼 문구를 "즉시 해지"가 아니라
                    "다음 결제부터 중단"으로 쓰는 편이 맞다.

                    두 가지 이유로 거절될 수 있고 **의미가 다르다.**
                    `SUBS-005` 는 이미 해지 예약된 상태(다시 눌렀다),
                    `SUBS-006` 은 애초에 끊을 자동갱신이 없는 경우(무료·쿠폰 구독)다.
                    후자는 해지 버튼 자체를 안 보여주는 게 맞다.
                    """)
    @ApiErrors({
            "SUBS-005: 이미 해지 예약된 구독",
            "SUBS-006: 끊을 자동갱신이 없음 (BASIC 또는 쿠폰으로 받은 GRANTED 구독)",
            "SUBS-004: 구독 행이 없음",
            "GEN-031: 토큰은 유효한데 그 계정이 사라짐"
    })
    @PostMapping("/cancel")
    public Response<Void> cancel(@AuthenticationPrincipal AuthenticatedUser principal) {
        subscriptionService.cancelAutoRenew(principal.publicId());
        return Response.ok();
    }
}
