package com.harucut.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "프로필 이미지 변경 요청")
public record ChangeProfileImageRequest(

        @NotBlank(message = "s3Key는 필수입니다.")
        @Schema(description = """
                업로드 API 가 돌려준 `key`. **presigned URL 전체를 넣어도 서버가 key 로 정규화한다.**
                반드시 본인 경로(`uploads/users/{내 publicId}/...`)여야 한다.""",
                example = "uploads/users/AbCdEf12Gh/profile/550e8400-e29b-41d4-a716-446655440000.jpg",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String s3Key
) {
}
