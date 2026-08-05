package com.harucut.notice.controller;

import com.harucut.common.response.PageResponse;
import com.harucut.common.response.Response;
import com.harucut.notice.dto.NoticeResponse;
import com.harucut.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public Response<PageResponse<NoticeResponse>> getNotices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return Response.ok(noticeService.getPublishedNotices(page, size));
    }

    @GetMapping("/{publicId}")
    public Response<NoticeResponse> getNotice(@PathVariable String publicId) {
        return Response.ok(noticeService.getPublishedNotice(publicId));
    }
}
