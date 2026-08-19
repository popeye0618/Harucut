package com.harucut.frame.controller;

import com.harucut.common.response.Response;
import com.harucut.config.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import com.harucut.frame.dto.FrameCreateRequest;
import com.harucut.frame.dto.FrameResponse;
import com.harucut.frame.service.FrameAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "시스템 프레임 관리")
@RestController
@RequestMapping("/api/admin/frames")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class FrameAdminController {

    private final FrameAdminService frameAdminService;

    @Operation(
            summary = "시스템 프레임 생성",
            description = """
                    모든 사용자에게 기본 제공되는 프레임을 만든다. 소유자가 없고,
                    **요금제 한도·보관 기간을 받지 않는다** — BASIC 사용자에게도 항상 보인다.

                    요청 본문은 사용자 프레임과 완전히 같다.
                    """)
    @ApiErrors("GEN-006: background.type 이 COLOR·IMAGE 가 아님")
    @PostMapping
    public Response<FrameResponse> createSystemFrame(@RequestBody @Valid FrameCreateRequest request) {
        return Response.ok(frameAdminService.createSystemFrame(request));
    }

    @Operation(
            summary = "시스템 프레임 목록",
            description = "시스템 프레임 전량을 최신순으로. 사용자 목록 API 뒤쪽에 붙는 것과 같은 집합이다.")
    @GetMapping
    public Response<List<FrameResponse>> listSystemFrames() {
        return Response.ok(frameAdminService.listSystemFrames());
    }

    // 동작이 전체 교체이므로 사용자 API와 같은 PUT — Kotlin의 PATCH는 메서드-동작 불일치라 통일 (wire 변경)
    @Operation(
            summary = "시스템 프레임 수정",
            description = """
                    ⚠️ **메서드가 `PATCH` 에서 `PUT` 으로 바뀌었다.** 기존 서버는 PATCH 였다.
                    PATCH 로 보내면 405 `GEN-041` 이다. 동작이 원래부터 전체 교체였고
                    사용자 API 는 이미 PUT 이라 맞췄다.

                    **사용자 프레임 ID 를 넣으면 404 다.** 관리자 API 로 남의 프레임을 건드리지 못하게 한 방어이고,
                    없는 ID 와 응답이 같다.
                    """)
    @ApiErrors({
            "FRAME-001: 없는 프레임이거나 시스템 프레임이 아님 (사용자 프레임 ID)",
            "GEN-006: background.type 이 COLOR·IMAGE 가 아님"
    })
    @PutMapping("/{frameId}")
    public Response<FrameResponse> updateSystemFrame(@Parameter(description = "시스템 프레임 ID", example = "1") @PathVariable Long frameId,
                                                     @RequestBody @Valid FrameCreateRequest request) {
        return Response.ok(frameAdminService.updateSystemFrame(frameId, request));
    }

    @Operation(
            summary = "시스템 프레임 삭제",
            description = """
                    프레임과 딸린 S3 이미지를 함께 지운다. **되돌릴 수 없고, 모든 사용자 화면에서 사라진다.**
                    사용자 프레임 ID 를 넣으면 404 다.
                    """)
    @ApiErrors("FRAME-001: 없는 프레임이거나 시스템 프레임이 아님")
    @DeleteMapping("/{frameId}")
    public Response<Void> deleteSystemFrame(@Parameter(description = "시스템 프레임 ID", example = "1") @PathVariable Long frameId) {
        frameAdminService.deleteSystemFrame(frameId);
        return Response.ok();
    }
}
