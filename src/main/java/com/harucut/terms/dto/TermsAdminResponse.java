package com.harucut.terms.dto;

import com.harucut.terms.entity.CurrentTermsVersion;
import com.harucut.terms.entity.Terms;
import com.harucut.terms.entity.TermsVersion;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "약관 (관리자용 — 비활성 포함)")
public record TermsAdminResponse(

        @Schema(description = "개정·비활성화에 쓰는 내부 ID. **공개 API 는 `code` 를 쓴다**", example = "1")
        Long termsId,

        @Schema(description = "약관 코드", example = "tos")
        String code,

        @Schema(description = "제목", example = "이용약관")
        String title,

        @Schema(description = "필수 동의 여부", example = "true")
        boolean required,

        @Schema(description = "활성 여부. `false` 면 공개 목록과 동의 API 에서 빠진다", example = "true")
        boolean active,

        @Schema(description = "최신 버전 번호", example = "2")
        int latestVersion,

        @Schema(description = "최신 버전 본문", example = "제1조 (목적) ...")
        String content
) {
    public static TermsAdminResponse from(CurrentTermsVersion current) {
        Terms terms = current.getTerms();
        TermsVersion version = current.getTermsVersion();
        return new TermsAdminResponse(terms.getId(), terms.getCode(), terms.getTitle(),
                terms.isRequired(), terms.isActive(), version.getVersion(), version.getContent());
    }
}
