package com.harucut.media.dto;

import com.harucut.media.entity.UserMedia;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "보관함의 사진 한 장. **URL 이 셋인데 쓰임새가 다르다**")
public record UserMediaResponse(

        @Schema(description = "미디어 ID. 다운로드·이름변경·삭제에 쓴다", example = "1")
        Long mediaId,

        @Schema(description = "S3 key. **이 값으로 이미지를 직접 가져올 수는 없다**(버킷이 비공개)",
                example = "uploads/users/AbCdEf12Gh/fourcuts/job-12.png")
        String s3Key,

        @Schema(description = "표시용 파일명. 다운로드할 때 이 이름으로 저장된다", example = "나의 기록.png")
        String displayName,

        @Schema(description = """
                **그리드·목록 미리보기용** 축소본(긴 변 512 JPEG)의 URL.
                ⚠️ **썸네일 도입 전에 만들어진 사진에는 이 필드가 아예 없다.**
                null 체크가 아니라 필드 존재 체크로 다루고, 없으면 `viewUrl` 로 대체할 것.""",
                example = "https://harucut-bucket.s3.ap-northeast-2.amazonaws.com/.../job-12-thumb.jpg?X-Amz-Signature=...")
        String thumbnailUrl,

        @Schema(description = """
                **화면에 띄울 때** 쓰는 URL(`<img src>`, 크게 보기). 원본 해상도 plain GET 이다.""",
                example = "https://harucut-bucket.s3.ap-northeast-2.amazonaws.com/.../job-12.png?X-Amz-Signature=...")
        String viewUrl,

        @Schema(description = """
                **저장 버튼**에 쓰는 URL. `Content-Disposition: attachment` 가 붙어 있어 브라우저가
                바로 저장한다. 한글 파일명도 깨지지 않는다. `<img src>` 에 넣지 말 것.""",
                example = "https://harucut-bucket.s3.ap-northeast-2.amazonaws.com/.../job-12.png?response-content-disposition=attachment...")
        String downloadUrl,

        @Schema(description = "만들어진 시각. 목록은 이 값 최신순이고, 보관 기간도 이 값 기준이다",
                example = "2026-08-03T10:20:30")
        LocalDateTime createdAt
) {
    public static UserMediaResponse of(UserMedia media, String thumbnailUrl,
                                       String viewUrl, String downloadUrl) {
        return new UserMediaResponse(media.getId(), media.getS3Key(),
                media.getDisplayName(), thumbnailUrl, viewUrl, downloadUrl, media.getCreatedAt());
    }
}
