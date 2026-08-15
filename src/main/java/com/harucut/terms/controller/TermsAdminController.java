package com.harucut.terms.controller;

import com.harucut.common.response.PageResponse;
import com.harucut.common.response.Response;
import com.harucut.terms.dto.CreateTermsRequest;
import com.harucut.terms.dto.ReviseTermsRequest;
import com.harucut.terms.dto.TermsAdminResponse;
import com.harucut.terms.dto.TermsAgreementHistoryResponse;
import com.harucut.terms.service.TermsAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/terms")
@PreAuthorize("hasRole('ADMIN')")
public class TermsAdminController {

    private final TermsAdminService termsAdminService;

    @PostMapping
    public Response<Void> createTerms(@RequestBody @Valid CreateTermsRequest request) {
        termsAdminService.createTerms(request.code(), request.title(), request.required(), request.content());
        return Response.ok();
    }

    @PostMapping("/{termsId}/versions")
    public Response<Void> reviseTerms(@PathVariable Long termsId,
                                      @RequestBody @Valid ReviseTermsRequest request) {
        termsAdminService.reviseTerms(termsId, request.content());
        return Response.ok();
    }

    @GetMapping
    public Response<List<TermsAdminResponse>> listAllTerms() {
        return Response.ok(termsAdminService.listAllTerms());
    }

    @DeleteMapping("/{termsId}")
    public Response<Void> deactivateTerms(@PathVariable Long termsId) {
        termsAdminService.deactivateTerms(termsId);
        return Response.ok();
    }

    // 사용자 동의 이력 (법적 증빙)
    @GetMapping("/consents/{userId}")
    public Response<PageResponse<TermsAgreementHistoryResponse>> getAgreementHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Response.ok(termsAdminService.getAgreementHistory(userId, page, size));
    }
}
