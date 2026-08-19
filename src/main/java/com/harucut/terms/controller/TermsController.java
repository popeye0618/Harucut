package com.harucut.terms.controller;

import com.harucut.common.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import com.harucut.terms.dto.TermsResponse;
import com.harucut.terms.service.TermsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "약관")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/terms")
public class TermsController {

    private final TermsService termsService;

    @Operation(
            summary = "활성 약관 목록",
            description = """
                    가입 화면에서 약관을 보여줄 때 쓴다. **인증이 필요 없다.**

                    활성 약관의 **현재 버전 본문까지** 함께 내려간다 — 본문을 따로 조회하는 API 는 없다.
                    응답의 `code` 를 동의 API 에 그대로 넘긴다.

                    실패 응답이 없다. 활성 약관이 하나도 없으면 빈 배열이다.
                    """)
    @GetMapping
    public Response<List<TermsResponse>> getActiveTerms() {
        return Response.ok(termsService.getActiveTerms());
    }
}
