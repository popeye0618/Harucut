package com.harucut.terms.controller;

import com.harucut.common.response.PageResponse;
import com.harucut.common.response.Response;
import com.harucut.config.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import com.harucut.terms.dto.CreateTermsRequest;
import com.harucut.terms.dto.ReviseTermsRequest;
import com.harucut.terms.dto.TermsAdminResponse;
import com.harucut.terms.dto.TermsAgreementHistoryResponse;
import com.harucut.terms.service.TermsAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "약관 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/terms")
@PreAuthorize("hasRole('ADMIN')")
public class TermsAdminController {

    private final TermsAdminService termsAdminService;

    @Operation(
            summary = "약관 생성",
            description = """
                    약관과 **버전 1** 을 함께 만든다. 만들자마자 활성 상태라 공개 목록에 바로 나온다.

                    ⚠️ **`code`·`title`·`required` 는 나중에 고칠 수 없다.** 수정 API 가 없다.
                    바꿀 수 있는 건 본문뿐이고, 그것도 개정(새 버전 추가)으로만 가능하다.
                    잘못 만들었으면 비활성화하고 새로 만드는 수밖에 없다.
                    """)
    @ApiErrors("TERMS-002: 이미 있는 코드")
    @PostMapping
    public Response<Void> createTerms(@RequestBody @Valid CreateTermsRequest request) {
        termsAdminService.createTerms(request.code(), request.title(), request.required(), request.content());
        return Response.ok();
    }

    @Operation(
            summary = "약관 개정",
            description = """
                    새 버전을 추가한다. 버전 번호는 자동으로 +1 되고 **이전 버전은 지워지지 않는다** —
                    누가 어느 버전에 동의했는지가 증빙으로 남아야 하기 때문이다.

                    **개정하는 순간 이전 버전에 동의했던 모든 사용자가 `NEEDS_RECONSENT` 가 된다.**
                    별도 갱신 작업은 없다. 상태를 저장하지 않고 조회할 때 계산하기 때문이다.
                    """)
    @ApiErrors("TERMS-001: 없는 약관")
    @PostMapping("/{termsId}/versions")
    public Response<Void> reviseTerms(@Parameter(description = "약관 ID", example = "1") @PathVariable Long termsId,
                                      @RequestBody @Valid ReviseTermsRequest request) {
        termsAdminService.reviseTerms(termsId, request.content());
        return Response.ok();
    }

    @Operation(
            summary = "약관 목록 (비활성 포함)",
            description = """
                    비활성화된 약관까지 전부 나온다. `active` 로 구분한다.
                    각 약관의 **최신 버전 본문**이 함께 실린다 — 이전 버전 본문을 보는 API 는 없다.
                    """)
    @GetMapping
    public Response<List<TermsAdminResponse>> listAllTerms() {
        return Response.ok(termsAdminService.listAllTerms());
    }

    @Operation(
            summary = "약관 비활성화",
            description = """
                    메서드는 `DELETE` 지만 **행을 지우지 않는다.** `active = false` 로 바꿀 뿐이다.
                    기존 동의 이력이 이 약관을 참조하고 있어서 지울 수 없다.

                    **멱등이다** — 이미 비활성이어도 200 이다.
                    ⚠️ **다시 활성화하는 API 는 없다.** 실수로 누르면 되돌릴 방법이 없다.
                    """)
    @ApiErrors("TERMS-001: 없는 약관")
    @DeleteMapping("/{termsId}")
    public Response<Void> deactivateTerms(@Parameter(description = "약관 ID", example = "1") @PathVariable Long termsId) {
        termsAdminService.deactivateTerms(termsId);
        return Response.ok();
    }

    // 사용자 동의 이력 (법적 증빙)
    @Operation(
            summary = "사용자 동의 이력",
            description = """
                    **기존 서버에 없던 API 다.** 특정 사용자가 언제 어느 버전에 동의·철회했는지를
                    최신순으로 돌려준다. 개인정보 동의는 법적 증빙 대상이라 이력 전체가 남아야 한다.

                    이 기록은 **추가만 되고 수정·삭제되지 않는다.** 같은 약관에 동의 → 철회 → 재동의를 했다면
                    세 행이 모두 나온다. `version` 은 그때 동의한 버전이라 지금 최신 버전과 다를 수 있다.

                    `userId` 는 내부 숫자 PK 다 — 사용자 API 가 쓰는 12자 publicId 가 아니다.
                    """)
    @ApiErrors("GEN-002: page 가 0 미만이거나 size 가 1 미만")
    @GetMapping("/consents/{userId}")
    public Response<PageResponse<TermsAgreementHistoryResponse>> getAgreementHistory(
            @Parameter(description = "사용자 내부 ID (publicId 아님)", example = "1") @PathVariable Long userId,
            @Parameter(description = "페이지 번호 (0부터)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (1 이상)", example = "10") @RequestParam(defaultValue = "10") int size) {
        return Response.ok(termsAdminService.getAgreementHistory(userId, page, size));
    }
}
