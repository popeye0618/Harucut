package com.harucut.auth.controller;

import com.harucut.auth.dto.LoginRequest;
import com.harucut.auth.dto.LoginResponse;
import com.harucut.auth.dto.LoginResult;
import com.harucut.auth.dto.RegisterRequest;
import com.harucut.auth.service.LoginService;
import com.harucut.auth.service.RegisterService;
import com.harucut.common.response.Response;
import com.harucut.config.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증 · 가입 · 로그인")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/harucut")
public class AuthController {

    private final RegisterService registerService;
    private final LoginService loginService;

    @Operation(
            summary = "이메일 회원가입",
            description = """
                    **이메일 인증을 먼저 마쳐야 한다.** 인증 후 10분이 지나면 `AUTH-004` 다 — 코드 발송부터 다시.

                    **가입에 성공해도 토큰을 주지 않는다.** 이어서 로그인을 호출해야 한다.

                    계정의 유일성은 (가입 경로, 이메일) 조합이다. 같은 이메일이라도
                    구글 계정과 이메일 계정은 **서로 다른 계정**이다.
                    """)
    @ApiErrors({
            "AUTH-030: 이미 가입된 이메일",
            "AUTH-004: 이메일 인증을 안 했거나 인증 후 10분이 지남"
    })
    @PostMapping("/register")
    public Response<Void> register(@Valid @RequestBody RegisterRequest request) {
        registerService.register(request);
        return Response.ok();
    }

    @Operation(
            summary = "이메일 로그인",
            description = """
                    **토큰은 바디가 아니라 `Set-Cookie` 로 내려간다** — `accessToken`(30분), `refreshToken`(14일).
                    둘 다 httpOnly 라 JS 로 읽을 수 없고, 읽을 필요도 없다.
                    프론트는 이후 요청에 `credentials: 'include'` 만 붙이면 된다.

                    응답 바디의 `userStatus` 를 반드시 확인할 것. `DELETED_REQUESTED` 면 홈이 아니라
                    복구 안내 화면으로 보내야 한다 — 로그인 자체는 되지만 일반 API 는 전부 403 이다.
                    """)
    @ApiErrors({
            "AUTH-001: 비밀번호가 틀림",
            "AUTH-020: 그 이메일로 가입된 계정이 없음 (소셜로 가입한 계정은 여기서 안 찾힌다)",
            "AUTH-010: 그 밖의 인증 실패"
    })
    @PostMapping("/login")
    public ResponseEntity<Response<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginService.login(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.accessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, result.refreshTokenCookie().toString())
                .body(Response.ok(new LoginResponse(result.userStatus())));
    }
}
