package com.harucut.media.controller;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.PageResponse;
import com.harucut.common.response.Response;
import com.harucut.media.dto.DisplayNameUpdateRequest;
import com.harucut.media.dto.UserMediaResponse;
import com.harucut.media.service.UserMediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/user/media")
public class UserMediaController {

    private final UserMediaService userMediaService;

    @GetMapping
    public Response<PageResponse<UserMediaResponse>> getMyMedia(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Response.ok(userMediaService.getMyMedia(principal.publicId(), page, size));
    }

    @GetMapping("/{mediaId}/download-url")
    public Response<String> getDownloadUrl(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @PathVariable Long mediaId) {
        return Response.ok(userMediaService.getDownloadUrl(principal.publicId(), mediaId));
    }

    @PatchMapping("/{mediaId}/display-name")
    public Response<UserMediaResponse> updateDisplayName(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long mediaId,
            @RequestBody @Valid DisplayNameUpdateRequest request) {
        return Response.ok(userMediaService.updateDisplayName(
                principal.publicId(), mediaId, request.displayName()));
    }

    @DeleteMapping("/{mediaId}")
    public Response<Void> deleteMedia(@AuthenticationPrincipal AuthenticatedUser principal,
                                      @PathVariable Long mediaId) {
        userMediaService.deleteMedia(principal.publicId(), mediaId);
        return Response.ok();
    }
}
