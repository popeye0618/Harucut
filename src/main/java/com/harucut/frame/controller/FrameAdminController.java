package com.harucut.frame.controller;

import com.harucut.common.response.Response;
import com.harucut.frame.dto.FrameCreateRequest;
import com.harucut.frame.dto.FrameResponse;
import com.harucut.frame.service.FrameAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/frames")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class FrameAdminController {

    private final FrameAdminService frameAdminService;

    @PostMapping
    public Response<FrameResponse> createSystemFrame(@RequestBody @Valid FrameCreateRequest request) {
        return Response.ok(frameAdminService.createSystemFrame(request));
    }

    @GetMapping
    public Response<List<FrameResponse>> listSystemFrames() {
        return Response.ok(frameAdminService.listSystemFrames());
    }

    // 동작이 전체 교체이므로 사용자 API와 같은 PUT — Kotlin의 PATCH는 메서드-동작 불일치라 통일 (wire 변경)
    @PutMapping("/{frameId}")
    public Response<FrameResponse> updateSystemFrame(@PathVariable Long frameId,
                                                     @RequestBody @Valid FrameCreateRequest request) {
        return Response.ok(frameAdminService.updateSystemFrame(frameId, request));
    }

    @DeleteMapping("/{frameId}")
    public Response<Void> deleteSystemFrame(@PathVariable Long frameId) {
        frameAdminService.deleteSystemFrame(frameId);
        return Response.ok();
    }
}
