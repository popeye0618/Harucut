package com.harucut.auth.controller;

import com.harucut.auth.dto.LoginRequest;
import com.harucut.auth.dto.LoginResponse;
import com.harucut.auth.dto.LoginResult;
import com.harucut.auth.service.LoginService;
import com.harucut.common.response.Response;
import com.harucut.auth.dto.RegisterRequest;
import com.harucut.auth.service.RegisterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/harucut")
public class AuthController {

    private final RegisterService registerService;
    private final LoginService loginService;

    @PostMapping("/register")
    public Response<Void> register(@Valid @RequestBody RegisterRequest request) {
        registerService.register(request);
        return Response.ok();
    }

    @PostMapping("/login")
    public ResponseEntity<Response<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginService.login(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.accessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, result.refreshTokenCookie().toString())
                .body(Response.ok(new LoginResponse(result.userStatus())));
    }
}
