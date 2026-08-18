package com.harucut.auth.controller;

import com.harucut.auth.cookie.CookieManager;
import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.auth.service.UserExitService;
import com.harucut.common.response.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/harucut")
public class UserExitController {

    private final UserExitService userExitService;
    private final CookieManager cookieManager;

    @DeleteMapping("/exit")
    public ResponseEntity<Response<Void>> exit(@AuthenticationPrincipal AuthenticatedUser principal) {
        userExitService.requestExit(principal.publicId());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expired(CookieManager.ACCESS_TOKEN))
                .header(HttpHeaders.SET_COOKIE, expired(CookieManager.REFRESH_TOKEN))
                .body(Response.ok());
    }

    @PostMapping("/reactivate")
    @PreAuthorize("hasRole('DELETED_REQUESTED')")
    public Response<Void> reactivate(@AuthenticationPrincipal AuthenticatedUser principal) {
        userExitService.reActivate(principal.publicId());
        return Response.ok();
    }

    private String expired(String name) {
        return cookieManager.createExpiredCookie(name).toString();
    }
}