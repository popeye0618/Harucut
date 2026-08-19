package com.harucut.terms.dto;

import com.harucut.terms.entity.CurrentTermsVersion;
import com.harucut.terms.entity.Terms;
import com.harucut.terms.entity.TermsVersion;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "약관 (활성 약관의 현재 버전)")
public record TermsResponse(

        @Schema(description = "약관 코드. **동의 API 에 이 값을 그대로 넘긴다.** 한 번 정해지면 바뀌지 않는다",
                example = "tos")
        String code,

        @Schema(description = "제목", example = "이용약관")
        String title,

        @Schema(description = "필수 동의 여부. `true` 면 동의하지 않고는 가입할 수 없고, 나중에 철회도 안 된다",
                example = "true")
        boolean required,

        @Schema(description = "현재 버전 번호. 개정될 때마다 1씩 오른다", example = "2")
        int version,

        @Schema(description = "약관 본문 전문", example = "제1조 (목적) ...")
        String content
) {

    public static TermsResponse from(CurrentTermsVersion current) {
        Terms terms = current.getTerms();
        TermsVersion version = current.getTermsVersion();
        return new TermsResponse(terms.getCode(), terms.getTitle(), terms.isRequired(),
                version.getVersion(), version.getContent());
    }
}
