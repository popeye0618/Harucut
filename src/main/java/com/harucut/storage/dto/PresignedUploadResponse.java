package com.harucut.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;

@Schema(description = "업로드용 presigned URL")
public record PresignedUploadResponse(

        @Schema(description = """
                업로드가 끝난 뒤 **도메인 API 에 넘길 값**. 파일명은 UUID 로 바뀌어 있다.
                이 key 를 등록하지 않으면 S3 에 올라간 파일은 고아로 남는다.""",
                example = "uploads/users/AbCdEf12Gh/profile/550e8400-e29b-41d4-a716-446655440000.png")
        String key,

        @Schema(description = "여기로 **PUT** 한다. 본문은 파일 바이트 그대로",
                example = "https://harucut-bucket.s3.ap-northeast-2.amazonaws.com/uploads/...?X-Amz-Signature=...")
        String uploadUrl,

        @Schema(description = """
                PUT 요청의 `Content-Type` 헤더에 **그대로** 써야 하는 실제 MIME.
                요청에 보낸 enum 이름(`PNG`)이 아니라 `image/png` 다.""",
                example = "image/png")
        String contentType,

        @Schema(description = "URL 유효 기간. ISO-8601 기간 문자열이다 (`PT24H` = 24시간)", example = "PT24H")
        Duration expiresIn
) {
}
