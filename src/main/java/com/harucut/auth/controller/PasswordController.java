package com.harucut.auth.controller;

import com.harucut.auth.dto.*;
import com.harucut.auth.password.PasswordChangeService;
import com.harucut.auth.password.PasswordResetService;
import com.harucut.auth.security.CustomUserPrincipal;
import com.harucut.common.response.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/harucut")
public class PasswordController {

    private final PasswordChangeService passwordChangeService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/reset/password/code")
    public Response<Void> sendResetCode(@Valid @RequestBody SendResetCodeRequest request) {
        passwordResetService.sendResetCode(request.email());
        return Response.ok();
    }

    @PostMapping("/reset/password/verification")
    public Response<ResetTokenResponse> verifyResetCode(@Valid @RequestBody VerifyResetCodeRequest request) {
        String resetToken = passwordResetService.verifyResetCode(request.email(), request.code());
        return Response.ok(new ResetTokenResponse(resetToken));
    }

    @PatchMapping("/reset/password")
    public Response<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.resetToken(), request.newPassword());
        return Response.ok();
    }

    @PatchMapping("/change/password")
    public Response<Void> changePassword(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        passwordChangeService.changePassword(
                principal.getPublicId(), request.oldPassword(), request.newPassword());
        return Response.ok();
    }
}
