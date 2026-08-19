package com.harucut.payment.controller;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.PageResponse;
import com.harucut.common.response.Response;
import com.harucut.config.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import com.harucut.payment.dto.PaymentHistoryResponse;
import com.harucut.payment.dto.SubscribeRequest;
import com.harucut.payment.service.PaymentHistoryService;
import com.harucut.payment.service.PaymentService;
import com.harucut.subscription.dto.SubscriptionResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "결제")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentHistoryService paymentHistoryService;

    @Operation(
            summary = "구독 결제",
            description = """
                    카드 등록(빌링키 발급) → 첫 청구 → 구독 활성화를 한 번에 한다.
                    성공하면 **구독 조회와 같은 형태**의 응답이 돌아온다.

                    ⚠️ **요청 본문이 기존 서버와 다르다.** `customerKey` 를 더 이상 받지 않고
                    (로그인한 사용자로 서버가 정한다), 대신 **`idempotencyKey` 가 필수**다.

                    **실패 응답의 HTTP 상태를 구분해서 처리할 것.**

                    | 상태 | 뜻 | 안내 |
                    |---|---|---|
                    | **402** `PAY-002` | 카드가 거절됨 | "다른 카드로 시도" |
                    | **502** `PAY-001` | PG 서버 문제 | "잠시 후 다시 시도" |
                    | 409 `PAY-003` | 이미 유료 구독 중 | 결제 화면을 열지 않는 게 맞다 |
                    | 409 `PAY-006` | 같은 키가 처리 중이거나 잘못 재사용됨 | 잠시 후 구독 조회로 결과 확인 |

                    402 는 사용자가 조치할 수 있고 502 는 아니다. **같은 문구를 쓰지 말 것.**

                    개발 중 실패를 재현하려면 `authKey` 에 `FAIL` 을 넣으면 502,
                    로그인 사용자의 publicId 가 customerKey 라 청구 실패는 `payment.mock.fail-charge` 로 만든다.
                    """)
    @ApiErrors({
            "PAY-007: planTier 가 BASIC (무료라 결제 대상이 아님)",
            "PAY-001: 빌링키 발급 실패 — PG 쪽 문제, 재시도 안내",
            "PAY-002: 청구 실패 — 카드 문제, 다른 카드 안내",
            "PAY-003: 이미 유료 요금제를 구독 중",
            "PAY-006: 같은 idempotencyKey 의 주문이 아직 처리 중이거나, 다른 사용자·요금제에 재사용됨",
            "SUBS-004: 결제는 됐는데 구독 행을 찾지 못함 (정상 흐름에서는 생기지 않는다)",
            "GEN-031: 토큰은 유효한데 그 계정이 사라짐"
    })
    @PostMapping("/subscribe")
    public Response<SubscriptionResponse> subscribe(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @Valid @RequestBody SubscribeRequest request) {
        return Response.ok(paymentService.subscribe(principal.publicId(), request));
    }

    @Operation(
            summary = "결제 내역",
            description = """
                    **기존 서버에 없던 API 다.** 내 결제 주문을 최신순으로 돌려준다.

                    첫 구독 결제(`INITIAL`)와 정기결제 갱신(`RENEWAL`)이 함께 나오고,
                    ⚠️ **실패한 주문도 그대로 남는다.** 성공만 보여주려면 `status == "PAID"` 로 거를 것.

                    영수증·환불 API 는 아직 없다. 금액과 시각을 보여주는 용도다.
                    """)
    @ApiErrors({
            "GEN-002: page 가 0 미만이거나 size 가 1 미만",
            "GEN-031: 토큰은 유효한데 그 계정이 사라짐"
    })
    @GetMapping
    public Response<PageResponse<PaymentHistoryResponse>> history(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Parameter(description = "페이지 번호 (0부터)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (1 이상)", example = "10") @RequestParam(defaultValue = "10") int size
    ) {
        return Response.ok(paymentHistoryService.getMyHistory(principal.publicId(), page, size));
    }
}
