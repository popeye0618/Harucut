package com.harucut.auth.controller;

import com.harucut.auth.dto.NaverUnlinkRequest;
import com.harucut.auth.oauth2.unlink.NaverOAuth2UnlinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NaverOAuth2UnlinkController {

    private final NaverOAuth2UnlinkService naverOAuth2UnlinkService;

    // 네이버 서버가 호출하는 웹훅 — 인증 대신 HMAC 서명으로 검증하고, 봉투 없이 204를 준다
    @PostMapping("/api/oauth2/unlink/naver")
    public ResponseEntity<Void> unlink(@RequestBody NaverUnlinkRequest request) {
        naverOAuth2UnlinkService.unlink(request);
        return ResponseEntity.noContent().build();
    }
}
