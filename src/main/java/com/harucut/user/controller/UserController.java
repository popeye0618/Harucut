package com.harucut.user.controller;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.Response;
import com.harucut.config.openapi.ApiErrors;
import com.harucut.subscription.dto.SubscriptionUsageResponse;
import com.harucut.subscription.service.SubscriptionUsageService;
import com.harucut.user.dto.ChangeProfileImageRequest;
import com.harucut.user.dto.ChangeUsernameRequest;
import com.harucut.user.dto.UserInfoResponse;
import com.harucut.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "내 정보")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/user")
public class UserController {

    private final UserService userService;
    private final SubscriptionUsageService subscriptionUsageService;

    @Operation(
            summary = "내 정보 조회",
            description = """
                    프로필 화면과 헤더에 쓰는 값 묶음이다.

                    **`id` 는 12자 문자열(publicId)이다.** 숫자 PK 가 아니므로 숫자로 파싱하지 말 것.

                    **`profileUrl` 은 24시간짜리 presigned URL 이다.** S3 key 가 아니라 바로 `<img src>` 에
                    넣을 수 있는 주소이고, 대신 **오래 저장해 두면 만료돼 깨진다.**

                    `planTier` 는 실제로 적용 중인 등급이다. 구독 조회·사용량 API 와 같은 기준을 쓰므로
                    화면마다 다른 등급이 보이는 일은 없다.
                    """)
    @ApiErrors("GEN-031: 토큰은 유효한데 그 계정이 사라짐 (탈퇴 완료 등)")
    @GetMapping("/info")
    public Response<UserInfoResponse> info(@AuthenticationPrincipal AuthenticatedUser principal) {
        UserInfoResponse response = userService.getUserInfo(principal.publicId());

        return Response.ok(response);
    }

    @Operation(
            summary = "닉네임 변경",
            description = """
                    ⚠️ **쿼리 파라미터가 아니라 JSON 바디로 받는다.** 기존 서버는 `?username=...` 이었다.

                    바뀐 김에 검증 실패 코드도 달라졌다 — `GEN-002` 가 아니라 **`GEN-003`** 이고,
                    `data` 배열에 어느 필드가 왜 틀렸는지 들어온다.

                    중복 검사는 없다. 같은 닉네임을 여러 사람이 쓸 수 있다.
                    """)
    @ApiErrors("GEN-031: 토큰은 유효한데 그 계정이 사라짐")
    @PatchMapping("/change/username")
    public Response<Void> changeUsername(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody @Valid ChangeUsernameRequest request
    ) {
        userService.changeUsername(principal.publicId(), request.username());
        return Response.ok();
    }

    @Operation(
            summary = "프로필 이미지 변경",
            description = """
                    **먼저 업로드해야 한다.** 순서는 이렇다.

                    1. `POST /api/auth/user/files/presigned-upload` 에 `type: PROFILE` 로 URL 을 받는다
                    2. 받은 URL 로 S3 에 직접 PUT 한다
                    3. 응답의 `key` 를 이 API 에 넘긴다

                    **본인 경로의 key 만 받는다.** 남의 key 를 넣으면 403 이다.
                    presigned URL 을 통째로 넣어도 서버가 key 로 정규화하므로, URL 로 감싸 우회할 수는 없다.

                    바꾸기 전 이미지의 S3 객체는 지우지 않는다 — 이전 `profileUrl` 이 만료 전까지는 계속 열린다.
                    """)
    @ApiErrors({
            "GEN-021: 남의 경로(`uploads/users/{다른 publicId}/...`)를 지정함",
            "GEN-031: 토큰은 유효한데 그 계정이 사라짐"
    })
    @PatchMapping("/change/profile-image")
    public Response<Void> changeProfileImage(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody @Valid ChangeProfileImageRequest request
    ) {
        userService.changeProfileImage(principal.publicId(), request.s3Key());
        return Response.ok();
    }

    @Operation(
            summary = "요금제 사용량",
            description = """
                    프레임을 몇 개까지 보관할 수 있고 지금 몇 개를 쓰고 있는지.
                    "프레임 만들기" 버튼을 막을지 판단하는 데 쓴다.

                    ⚠️ **무제한을 `-1` 로 표현한다.** `frameRetentionUnlimited` 를 먼저 보고,
                    `true` 면 `-1` 두 값을 개수로 해석하지 말 것. 한도를 이미 넘긴 경우
                    남은 개수는 음수가 아니라 `0` 으로 보정된다.
                    """)
    @ApiErrors("GEN-031: 토큰은 유효한데 그 계정이 사라짐")
    @GetMapping("/subscription/usage")
    public Response<SubscriptionUsageResponse> subscriptionUsage(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Response.ok(subscriptionUsageService.getUsage(principal.publicId()));
    }
}
