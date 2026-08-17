package com.harucut.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ComposeRequest(
        @NotNull Long frameId,
        // 촬영 순서대로 4개 — 슬롯 순서와 같다
        @NotNull @Size(min = 4, max = 4) List<@NotBlank String> sourceKeys,
        // 클라이언트가 요청마다 새로 만드는 값 — 재시도는 같은 값을 다시 보낸다
        @NotBlank @Size(max = 64) String idempotencyKey
) {
}
