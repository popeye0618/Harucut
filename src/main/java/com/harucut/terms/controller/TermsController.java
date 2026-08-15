package com.harucut.terms.controller;

import com.harucut.common.response.Response;
import com.harucut.terms.dto.TermsResponse;
import com.harucut.terms.service.TermsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/terms")
public class TermsController {

    private final TermsService termsService;

    @GetMapping
    public Response<List<TermsResponse>> getActiveTerms() {
        return Response.ok(termsService.getActiveTerms());
    }
}
