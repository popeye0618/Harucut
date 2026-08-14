package com.harucut.user.controller;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.Response;
import com.harucut.user.dto.ChangeUsernameRequest;
import com.harucut.user.dto.UserInfoResponse;
import com.harucut.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/user")
public class UserController {

    private final UserService userService;

    @GetMapping("/info")
    public Response<UserInfoResponse> info(@AuthenticationPrincipal AuthenticatedUser principal) {
        UserInfoResponse response = userService.getUserInfo(principal.publicId());

        return Response.ok(response);
    }

    @PatchMapping("/change/username")
    public Response<Void> changeUsername(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody @Valid ChangeUsernameRequest request
    ) {
        userService.changeUsername(principal.publicId(), request.username());
        return Response.ok();
    }
}
