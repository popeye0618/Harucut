package com.harucut.frame.controller;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.Response;
import com.harucut.frame.dto.FrameCreateRequest;
import com.harucut.frame.dto.FrameResponse;
import com.harucut.frame.service.FrameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/user/frame")
public class FrameController {

    private final FrameService frameService;

    @PostMapping
    public Response<FrameResponse> createFrame(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @RequestBody @Valid FrameCreateRequest request) {
        return Response.ok(frameService.createFrame(principal.publicId(), request));
    }

    @GetMapping
    public Response<List<FrameResponse>> getMyFrames(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Response.ok(frameService.getMyFrames(principal.publicId()));
    }

    @GetMapping("/{frameId}")
    public Response<FrameResponse> getFrame(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @PathVariable Long frameId) {
        return Response.ok(frameService.getFrame(principal.publicId(), frameId));
    }

    @PutMapping("/{frameId}")
    public Response<FrameResponse> updateFrame(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @PathVariable Long frameId,
                                               @RequestBody @Valid FrameCreateRequest request) {
        return Response.ok(frameService.updateFrame(principal.publicId(), frameId, request));
    }

    @DeleteMapping("/{frameId}")
    public Response<Void> deleteFrame(@AuthenticationPrincipal AuthenticatedUser principal,
                                      @PathVariable Long frameId) {
        frameService.deleteFrame(principal.publicId(), frameId);
        return Response.ok();
    }
}
