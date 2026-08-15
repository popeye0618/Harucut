package com.harucut.terms.controller;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.Response;
import com.harucut.terms.dto.AgreementItem;
import com.harucut.terms.dto.TermsAgreementStatusResponse;
import com.harucut.terms.service.TermsService;
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
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/terms/consents")
public class TermsAgreementController {

    private final TermsService termsService;

    @GetMapping("/me")
    public Response<List<TermsAgreementStatusResponse>> getMyAgreementStatus(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return Response.ok(termsService.getMyAgreementStatus(principal.publicId()));
    }

    // @Valid가 제네릭 인자 안에 있어야 원소가 검증된다. List 앞에 붙이면 아무것도 검증 안 한다
    @PostMapping
    public Response<Void> agree(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody List<@Valid AgreementItem> items) {
        termsService.agree(principal.publicId(), items);
        return Response.ok();
    }
}
