package com.harucut.media.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "표시 파일명 수정 요청")
public record DisplayNameUpdateRequest(

        @NotBlank @Size(max = 255)
        @Schema(description = """
                새 파일명. 최대 255자.

                **서버가 정제한 뒤 저장한다** — 경로(`../`, `foo/bar`)·따옴표·개행·제어문자를 없애고,
                **원본 파일의 확장자를 다시 붙인다.** 확장자를 빼고 `my_photo` 를 보내면
                `my_photo.png` 로 저장된다. 응답으로 최종 저장값이 돌아오니 그걸 화면에 반영할 것.""",
                example = "나의 기록", requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName
) {
}
