package com.harucut.auth.controller;

import com.harucut.auth.dto.SendCodeRequest;
import com.harucut.auth.dto.VerifyCodeRequest;
import com.harucut.auth.email.EmailVerificationService;
import com.harucut.common.response.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/email-auth")
public class EmailAuthController {

    private final EmailVerificationService emailVerificationService;

    @PostMapping("/code")
    public Response<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        emailVerificationService.sendVerificationCode(request.email());
        return Response.ok();
    }

    @PostMapping("/verification")
    public Response<Void> verify(@Valid @RequestBody VerifyCodeRequest request) {
        emailVerificationService.verifyCode(request.email(), request.code());
        return Response.ok();
    }
}