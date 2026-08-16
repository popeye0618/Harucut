package com.harucut.storage.controller;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.common.response.Response;
import com.harucut.storage.dto.PresignedUploadRequest;
import com.harucut.storage.dto.PresignedUploadResponse;
import com.harucut.storage.service.FileStorageService;
import com.harucut.storage.util.S3Keys;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/user/files")
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/presigned-upload")
    public Response<PresignedUploadResponse> createPresignedUploadUrl(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody @Valid PresignedUploadRequest request
    ) {
        PresignedUploadResponse response = fileStorageService.generatePresignedUploadUrl(
                request.type(), request.filename(), request.contentType(),
                request.fileSize(), principal.publicId());

        return Response.ok(response);
    }

    @GetMapping("/presigned-img")
    public Response<String> getPresignedImgUrl(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("key") String key
    ) {
        verifyOwnership(key, principal.publicId());

        return Response.ok(fileStorageService.generatePresignedGetUrl(key));
    }

    private void verifyOwnership(String key, String publicId) {
        if (!key.startsWith(S3Keys.userRoot(publicId))) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }
}
