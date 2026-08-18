package com.harucut.terms.controller;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.Response;
import com.harucut.config.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import com.harucut.terms.dto.AgreementItem;
import com.harucut.terms.dto.TermsAgreementStatusResponse;
import com.harucut.terms.service.TermsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// URL은 명세(consents)를 따르고, 내부 이름은 agreement를 쓴다
@Tag(name = "약관")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/terms/consents")
public class TermsAgreementController {

    private final TermsService termsService;

    @Operation(
            summary = "내 동의 상태",
            description = """
                    **활성 약관 전체**가 기준이다. 내가 한 번도 건드리지 않은 약관도 `NOT_AGREED` 로 나온다.
                    비활성화된 약관에 예전에 동의했더라도 목록에 끼지 않는다.

                    `NEEDS_RECONSENT` 는 동의한 뒤 약관이 개정됐다는 뜻이다.
                    ⚠️ **서버는 재동의를 강제하지 않는다** — 재동의하지 않아도 다른 API 는 전부 정상 동작한다.
                    재동의 모달을 띄울지는 프론트가 정한다.

                    `agreedVersion` 은 동의한 적이 없거나 철회했으면 **키 자체가 없다.**
                    """)
    @ApiErrors("GEN-031: 토큰은 유효한데 그 계정이 사라짐")
    @GetMapping("/me")
    public Response<List<TermsAgreementStatusResponse>> getMyAgreementStatus(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return Response.ok(termsService.getMyAgreementStatus(principal.publicId()));
    }

    // @Valid가 제네릭 인자 안에 있어야 원소가 검증된다. List 앞에 붙이면 아무것도 검증 안 한다
    @Operation(
            summary = "약관 동의 · 철회",
            description = """
                    ⚠️ **요청 본문의 최상위가 배열이다.** 객체로 감싸 보내면 `GEN-006` 이다.

                    ```json
                    [ { "code": "tos", "agreed": true }, { "code": "marketing", "agreed": false } ]
                    ```

                    **전부 아니면 전무다.** 세 번째 항목에서 실패하면 앞의 두 개도 저장되지 않는다.
                    부분 성공이 없으므로, 실패하면 사용자가 고른 값을 그대로 두고 다시 보내면 된다.

                    **필수 약관은 철회할 수 없다**(`TERMS-003`). 정책상 탈퇴로만 가능하므로
                    프론트가 탈퇴 안내로 연결해야 한다 — 서버는 그 안내를 주지 않는다.

                    항목 검증에 실패하면 `GEN-002` 다. **어느 항목이 왜 틀렸는지는 알려주지 않는다** —
                    본문이 배열이라 필드 경로를 담는 `GEN-003` 형식을 쓸 수 없다. 보내기 전에 프론트가 검증할 것.
                    """)
    @ApiErrors({
            "GEN-002: 항목의 code 가 비었거나 agreed 가 없음",
            "GEN-006: 최상위가 배열이 아님",
            "TERMS-003: 필수 약관을 철회하려 함",
            "TERMS-001: 없거나 비활성화된 약관 코드",
            "GEN-031: 토큰은 유효한데 그 계정이 사라짐"
    })
    @PostMapping
    public Response<Void> agree(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody List<@Valid AgreementItem> items) {
        termsService.agree(principal.publicId(), items);
        return Response.ok();
    }
}
