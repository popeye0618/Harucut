package com.harucut.media.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "네컷 합성 요청")
public record ComposeRequest(

        @NotNull
        @Schema(description = """
                합성에 쓸 프레임 ID. 내 프레임이거나 시스템 프레임이어야 한다.
                **단건 조회와 같은 관문을 통과한다** — 보관 기간이 지났거나 한도 밖으로 밀려나
                조회가 막힌 프레임은 합성도 안 된다.""",
                example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
        Long frameId,

        @NotNull @Size(min = 4, max = 4)
        @Schema(description = """
                원본 사진 4장의 S3 key. **정확히 4개**이고, **촬영 순서가 곧 슬롯 순서**다.

                업로드 API 에 `type: FOURCUT_SOURCE` 로 올린 뒤 받은 key 를 쓴다.
                본인 경로가 아니면 403 이다 — URL 로 감싸도 서버가 key 로 정규화하므로 우회되지 않는다.

                ⚠️ **필터(흑백·밝게 등)는 올리기 전에 픽셀에 구워 넣어야 한다.** 서버는 필터를 모른다.""",
                example = "[\"uploads/users/AbCdEf12Gh/fourcuts/sources/a.png\","
                        + "\"uploads/users/AbCdEf12Gh/fourcuts/sources/b.png\","
                        + "\"uploads/users/AbCdEf12Gh/fourcuts/sources/c.png\","
                        + "\"uploads/users/AbCdEf12Gh/fourcuts/sources/d.png\"]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotBlank String> sourceKeys,

        @NotBlank @Size(max = 64)
        @Schema(description = """
                중복 합성 방지용 키. 최대 64자.

                **합성 버튼을 누를 때마다 새로 만들고, 네트워크 재시도에는 같은 값을 다시 보낸다.**
                같은 키로 다시 오면 새로 합성하지 않고 **기존 작업의 상태를 그대로** 돌려준다
                (더블클릭도 여기서 걸린다).""",
                example = "550e8400-e29b-41d4-a716-446655440000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String idempotencyKey
) {
}
