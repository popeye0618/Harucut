package com.harucut.notice.controller;

import com.harucut.common.response.PageResponse;
import com.harucut.common.response.Response;
import com.harucut.config.openapi.ApiErrors;
import com.harucut.notice.dto.NoticeResponse;
import com.harucut.notice.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "공지")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    @Operation(
            summary = "공지 목록",
            description = """
                    게시된 공지만 내려간다. 초안은 나오지 않는다.

                    **정렬** 고정(`pinned`)된 것이 먼저, 그다음 게시 최신순.
                    **본문(`content`)이 목록에도 전문 그대로 들어온다.** 목록에서 자를 필요가 있으면 프론트가 자른다.
                    """)
    @ApiErrors("GEN-002: page 가 0 미만이거나 size 가 1 미만")
    @GetMapping
    public Response<PageResponse<NoticeResponse>> getNotices(
            @Parameter(description = "페이지 번호 (0부터)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기 (1 이상)", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        return Response.ok(noticeService.getPublishedNotices(page, size));
    }

    @Operation(
            summary = "공지 단건",
            description = """
                    목록의 `publicId` 로 조회한다.

                    아직 게시되지 않은 공지도 **404** 다. 403 을 주면 "그 ID 의 초안이 존재한다"는 사실이 새기 때문에,
                    없는 것과 초안인 것을 구분하지 않는다. 프론트는 둘을 같게 처리하면 된다.
                    """)
    @ApiErrors("NOTICE-001: 없는 공지이거나, 있어도 아직 게시되지 않은 공지")
    @GetMapping("/{publicId}")
    public Response<NoticeResponse> getNotice(
            @Parameter(description = "공지 공개 ID (12자)", example = "aB3dE7fG9h")
            @PathVariable String publicId
    ) {
        return Response.ok(noticeService.getPublishedNotice(publicId));
    }
}
