package com.harucut.media.controller;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.Response;
import com.harucut.media.dto.ComposeJobResponse;
import com.harucut.media.dto.ComposeRequest;
import com.harucut.media.service.ComposeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/user/media/compose")
public class ComposeController {

    private final ComposeService composeService;

    // 202: 접수했고 결과는 나중이다 — 프론트는 jobId로 폴링한다
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Response<ComposeJobResponse> requestCompose(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody @Valid ComposeRequest request) {
        return Response.ok(composeService.requestCompose(principal.publicId(), request));
    }

    @GetMapping("/{jobId}")
    public Response<ComposeJobResponse> getJob(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @PathVariable Long jobId) {
        return Response.ok(composeService.getJob(principal.publicId(), jobId));
    }
}
