package com.harucut.notice.controller;

import com.harucut.common.response.PageResponse;
import com.harucut.common.response.Response;
import com.harucut.config.openapi.ApiErrors;
import com.harucut.notice.dto.CreateNoticeRequest;
import com.harucut.notice.dto.NoticeAdminResponse;
import com.harucut.notice.dto.UpdateNoticeRequest;
import com.harucut.notice.service.NoticeAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "공지 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/notices")
@PreAuthorize("hasRole('ADMIN')")
public class NoticeAdminController {

    private final NoticeAdminService noticeAdminService;

    @Operation(
            summary = "공지 생성",
            description = """
                    **초안으로 저장된다.** `published = false` 라 공개 목록에는 아직 안 나온다.
                    노출하려면 게시 API 를 따로 호출해야 한다.

                    ⚠️ **응답에 생성된 공지의 식별자가 없다.** 방금 만든 공지를 이어서 다루려면
                    목록을 다시 조회해 `noticeId` 를 찾아야 한다.
                    """)
    @PostMapping
    public Response<Void> createNotice(@RequestBody @Valid CreateNoticeRequest request) {
        noticeAdminService.createNotice(request);
        return Response.ok();
    }

    @Operation(
            summary = "공지 게시",
            description = """
                    `published = true`, 게시 시각을 현재로 기록한다.

                    **이미 게시된 공지에 다시 호출하면 게시 시각이 갱신된다.** 공개 목록이 게시 최신순이라
                    맨 위로 올라온다 — "끌어올리기"로 쓸 수 있지만, 모르고 누르면 순서가 바뀐다.
                    """)
    @ApiErrors("NOTICE-001: 없는 공지")
    @PatchMapping("/{noticeId}/publish")
    public Response<Void> publishNotice(@Parameter(description = "공지 ID", example = "1") @PathVariable Long noticeId) {
        noticeAdminService.publishNotice(noticeId);
        return Response.ok();
    }

    @Operation(
            summary = "공지 수정",
            description = """
                    ⚠️ **메서드는 PATCH 지만 동작은 전체 교체다.** 세 필드를 모두 보내야 한다.

                    특히 `pinned` 를 빼면 `false` 로 덮어써진다. 고정된 공지의 제목만 고치려고
                    `title` 만 보내면 **고정이 풀린다.** 수정 전 값을 그대로 실어 보낼 것.
                    """)
    @ApiErrors("NOTICE-001: 없는 공지")
    @PatchMapping("/{noticeId}")
    public Response<Void> updateNotice(
            @Parameter(description = "공지 ID", example = "1") @PathVariable Long noticeId,
            @RequestBody @Valid UpdateNoticeRequest request
    ) {
        noticeAdminService.updateNotice(noticeId, request);
        return Response.ok();
    }

    @Operation(
            summary = "공지 게시 취소",
            description = """
                    공개 목록에서 내린다. **게시 시각도 함께 지워진다** —
                    다시 게시하면 그때가 새 게시 시각이 되므로 목록 맨 위로 올라온다.
                    """)
    @ApiErrors("NOTICE-001: 없는 공지")
    @PatchMapping("/{noticeId}/unpublish")
    public Response<Void> unPublishNotice(@Parameter(description = "공지 ID", example = "1") @PathVariable Long noticeId) {
        noticeAdminService.unPublishNotice(noticeId);
        return Response.ok();
    }

    @Operation(
            summary = "공지 삭제",
            description = """
                    소프트 삭제다. 행은 남지만 공개 목록·관리자 목록 어디에도 다시 나오지 않고,
                    **되살리는 API 는 없다.** 잠깐 내리는 것이 목적이라면 게시 취소를 쓸 것.
                    """)
    @ApiErrors("NOTICE-001: 없는 공지이거나 이미 삭제된 공지")
    @DeleteMapping("/{noticeId}")
    public Response<Void> deleteNotice(@Parameter(description = "공지 ID", example = "1") @PathVariable Long noticeId) {
        noticeAdminService.deleteNotice(noticeId);
        return Response.ok();
    }

    @Operation(
            summary = "공지 목록 (미게시 포함)",
            description = """
                    삭제된 것을 뺀 전량이다. 초안도 함께 나오므로 `published` 로 구분한다.

                    **정렬이 공개 목록과 다르다** — 여기는 생성 최신순이고, 고정 여부를 보지 않는다.
                    """)
    @ApiErrors("GEN-002: page 가 0 미만이거나 size 가 1 미만")
    @GetMapping
    public Response<PageResponse<NoticeAdminResponse>> listAllNotice(
            @Parameter(description = "페이지 번호 (0부터)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기 (1 이상)", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        return Response.ok(noticeAdminService.listAllNotice(page, size));
    }
}
