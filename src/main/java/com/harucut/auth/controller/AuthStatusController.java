package com.harucut.auth.controller;

import com.harucut.auth.dto.AuthStatusResponse;
import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.Response;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthStatusController {

    @GetMapping("/status")
    public Response<AuthStatusResponse> status(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Response.ok(new AuthStatusResponse(principal.status()));
    }
}
