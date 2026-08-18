package com.harucut.auth.controller;

import com.harucut.auth.dto.NaverUnlinkRequest;
import com.harucut.config.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import com.harucut.auth.oauth2.unlink.NaverOAuth2UnlinkService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "소셜 연동 해제")
@RestController
@RequiredArgsConstructor
public class NaverOAuth2UnlinkController {

    private final NaverOAuth2UnlinkService naverOAuth2UnlinkService;

    // 네이버 서버가 호출하는 웹훅 — 인증 대신 HMAC 서명으로 검증하고, 봉투 없이 204를 준다
    @Operation(
            summary = "네이버 연동 해제 웹훅",
            description = """
                    **네이버 서버가 호출한다. 프론트가 부를 API 가 아니다.**
                    사용자가 네이버 쪽에서 하루컷 연동을 끊으면 네이버가 이 주소로 알려준다.

                    인증 대신 본문의 HMAC 서명으로 진위를 검증하고, 해당 사용자를 탈퇴 요청 처리한다.

                    **성공 응답이 204 이고 공통 봉투를 쓰지 않는다** — 이 프로젝트에서 유일한 예외다.
                    외부 시스템이 규약을 정한 자리라 거기에 맞췄다.
                    """)
    @PostMapping("/api/oauth2/unlink/naver")
    @ApiErrors({
            "GEN-031: 그 식별자에 해당하는 사용자가 없음",
            "AUTH-091: 서명 검증 실패 또는 복호화 실패"
    })
    public ResponseEntity<Void> unlink(@RequestBody NaverUnlinkRequest request) {
        naverOAuth2UnlinkService.unlink(request);
        return ResponseEntity.noContent().build();
    }
}
