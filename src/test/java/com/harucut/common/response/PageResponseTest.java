package com.harucut.common.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    @DisplayName("빈 페이지는 totalElements 0, content 비어 있음")
    void emptyPage() {
        PageImpl<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L);

        assertThat(PageResponse.from(page))
                .extracting(PageResponse::content, PageResponse::totalElements, PageResponse::totalPages, PageResponse::number, PageResponse::size)
                .containsExactly(List.of(), 0L, 0, 0, 10);
    }

    @Test
    @DisplayName("마지막 페이지는 content가 size보다 적을 수 있다")
    void lastPage() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(4, 10), 42L);

        assertThat(PageResponse.from(page))
                .extracting(PageResponse::totalElements, PageResponse::totalPages,
                        PageResponse::number, PageResponse::size)
                .containsExactly(42L, 5, 4, 10);
        assertThat(PageResponse.from(page).content()).hasSize(2);
    }
}