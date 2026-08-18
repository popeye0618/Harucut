package com.harucut.auth.controller;

import com.harucut.auth.dto.AuthStatusResponse;
import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증 · 상태")
@RestController
@RequestMapping("/api/auth")
public class AuthStatusController {

    @Operation(
            summary = "로그인 상태 확인",
            description = """
                    소셜 로그인 콜백 페이지에서 로그인 완료를 확인하는 용도로 쓴다.
                    쿠키가 유효하면 200, 아니면 401 이다.

                    **탈퇴 요청 상태(`DELETED_REQUESTED`)에서도 200 이 나온다.** 다른 API 는 전부 403 인데
                    여기만 열어둔 이유는, 프론트가 상태를 읽어 복구 안내 화면으로 보낼 수 있어야 하기 때문이다.
                    임의의 API 에서 `GEN-021` 을 받으면 이 API 로 상태를 확인할 것 — 로그아웃시키면 안 된다.

                    값은 토큰에서 읽는다(DB 조회 없음). 즉 **토큰 발급 시점의 상태**다.
                    """)
    @GetMapping("/status")
    public Response<AuthStatusResponse> status(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Response.ok(new AuthStatusResponse(principal.status()));
    }
}
