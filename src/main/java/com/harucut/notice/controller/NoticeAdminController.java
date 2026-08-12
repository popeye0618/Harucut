package com.harucut.notice.controller;

import com.harucut.common.response.PageResponse;
import com.harucut.common.response.Response;
import com.harucut.notice.dto.CreateNoticeRequest;
import com.harucut.notice.dto.NoticeAdminResponse;
import com.harucut.notice.dto.UpdateNoticeRequest;
import com.harucut.notice.service.NoticeAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/notices")
@PreAuthorize("hasRole('ADMIN')")
public class NoticeAdminController {

    private final NoticeAdminService noticeAdminService;

    @PostMapping
    public Response<Void> createNotice(@RequestBody @Valid CreateNoticeRequest request) {
        noticeAdminService.createNotice(request);
        return Response.ok();
    }

    @PatchMapping("/{noticeId}/publish")
    public Response<Void> publishNotice(@PathVariable Long noticeId) {
        noticeAdminService.publishNotice(noticeId);
        return Response.ok();
    }

    @PatchMapping("/{noticeId}")
    public Response<Void> updateNotice(@PathVariable Long noticeId, @RequestBody @Valid UpdateNoticeRequest request) {
        noticeAdminService.updateNotice(noticeId, request);
        return Response.ok();
    }

    @PatchMapping("/{noticeId}/unpublish")
    public Response<Void> unPublishNotice(@PathVariable Long noticeId) {
        noticeAdminService.unPublishNotice(noticeId);
        return Response.ok();
    }

    @DeleteMapping("/{noticeId}")
    public Response<Void> deleteNotice(@PathVariable Long noticeId) {
        noticeAdminService.deleteNotice(noticeId);
        return Response.ok();
    }

    @GetMapping
    public Response<PageResponse<NoticeAdminResponse>> listAllNotice(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return Response.ok(noticeAdminService.listAllNotice(page, size));
    }

}
