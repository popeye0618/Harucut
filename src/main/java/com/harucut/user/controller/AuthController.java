package com.harucut.user.controller;

import com.harucut.common.response.Response;
import com.harucut.user.dto.RegisterRequest;
import com.harucut.user.service.RegisterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/harucut")
public class AuthController {

    private final RegisterService registerService;

    @PostMapping("/register")
    public Response<Void> register(@Valid @RequestBody RegisterRequest request) {
        registerService.register(request);
        return Response.ok();
    }
}
