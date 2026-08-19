package com.harucut.common.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "페이지 응답")
public record PageResponse<T>(

        @Schema(description = "이 페이지의 항목들")
        List<T> content,

        @Schema(description = "전체 항목 수", example = "12")
        long totalElements,

        @Schema(description = "전체 페이지 수", example = "2")
        int totalPages,

        @Schema(description = "현재 페이지 번호 (0부터)", example = "0")
        int number,

        @Schema(description = "페이지 크기", example = "10")
        int size
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }
}
