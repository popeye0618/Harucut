# 프론트 영향 변경점 (Kotlin → Java 재구현)

> **이 문서만 프론트에 넘기면 된다.** 기존 Kotlin 서버(`harucut_ktl_be`)와 **응답이 달라지는 것**만 모은다.
> 내부 구조가 바뀌었어도 프론트가 보는 결과가 같으면 여기 적지 않는다.
>
> 각 항목은 **기존 동작 → 새 동작 → 프론트가 할 일** 순서로 적는다.
> 근거가 되는 기존 코드 위치를 반드시 남긴다 (나중에 "정말 그랬나?"를 다시 확인할 수 있게).

**최종 수정: 2026-08-12 (Phase 3-3까지 반영)**

---

## 요약

| # | 변경 | 상태 | 프론트 영향 |
|---|------|------|-------------|
| 1 | refresh 토큰을 access 자리에 넣으면 거부 | **적용됨** | 낮음 (정상 사용 시 발생 안 함) |
| 2 | `DELETED_REQUESTED` 사용자의 일반 API 403 | **예정** | **높음** |

---

## 1. refresh 토큰을 access 토큰 자리에 넣으면 401 `AUTH-011` — 적용됨

**기존 동작**

`JwtAuthenticationFilter.kt:33`은 `jwtTokenService.parse(accessToken)`만 하고 **`type` 클레임을 확인하지 않는다.**
그래서 `accessToken` 쿠키에 refresh 토큰을 넣어도 서명과 만료만 맞으면 **그대로 인증이 통과했다.**
결과적으로 access 30분짜리 보호가 refresh 14일짜리로 늘어난 셈이었다.

**새 동작**

`JwtAuthenticationFilter.authenticate()`에서 `claims.type() != TokenType.ACCESS`이면 거부한다.

```
401  { "code": "AUTH-011", "status": 401, "message": "Invalid access token." }
```

**프론트가 할 일**

정상적으로 `accessToken` 쿠키만 보내고 있다면 **아무것도 없다.**
다만 아래에 해당하면 고쳐야 한다.

- 재발급 응답의 refresh 토큰을 `accessToken` 자리에 잘못 넣고 있던 코드
- access 만료 시 refresh로 임시 대체해 재시도하던 코드

**왜 바꿨나**

의도적 수정이다. refresh 토큰은 재발급 전용이고, Redis 저장값 대조(3-4)를 거쳐야 한다.
그 검증을 우회해 14일간 API를 호출할 수 있는 경로를 막았다.

---

## 2. `DELETED_REQUESTED` 사용자의 일반 API 호출 → 403 `GEN-021` — 예정

**기존 동작**

명세(`docs/00-conventions.md` 2.5)에는 "탈퇴 요청 상태 사용자는 일반 API가 전부 403"이라고 적혀 있다.
**그러나 실제 코드는 그렇게 동작하지 않는다.** 확인한 내용:

- `SecurityConfig.kt:102-104` — 인가 규칙은 `permitAll` 2개 + `anyRequest().authenticated()`가 전부. `hasRole` 없음.
- Kotlin 전체에서 `@PreAuthorize`는 15군데뿐이고 **모두 `ADMIN` 아니면 `DELETED_REQUESTED`**. 일반 API에 `hasRole('USER')`가 붙은 곳이 하나도 없음.

`DELETED_REQUESTED` 사용자는 authority가 `ROLE_DELETED_REQUESTED` 하나뿐이지만
`authenticated()`는 "익명이 아닐 것"만 보므로 **일반 API를 전부 통과한다.**
즉 명세가 의도를 앞서 적었고, 코드는 따라가지 않은 상태다.

**새 동작**

일반 API 컨트롤러에 클래스 레벨 `@PreAuthorize("hasRole('USER')")`를 붙여 명세대로 맞춘다.

```
403  { "code": "GEN-021", "status": 403, "message": "..." }
```

**예외 — 이 두 곳은 잠그지 않는다**

| 경로 | 이유 |
|------|------|
| `GET /api/auth/status` | 프론트가 `userStatus`를 읽어 **복구 안내 화면으로 보내야** 한다 (`docs/01-auth.md:189`). 여기까지 막으면 탈퇴 취소 진입로가 사라진다. `BLOCKED`(authority 빈 목록)도 자기 상태는 읽을 수 있어야 한다 |
| `POST /api/harucut/reactivate` | `hasRole('DELETED_REQUESTED')` 전용. 탈퇴 취소 그 자체 |

**프론트가 할 일**

- 임의의 API에서 `GEN-021` 403을 받으면, `GET /api/auth/status`를 호출해 `userStatus`를 확인한다.
- `DELETED_REQUESTED`면 복구 안내 화면으로 보낸다. 로그아웃시키지 말 것 — 토큰은 여전히 유효하다.
- 기존에 `DELETED_REQUESTED` 상태로도 일반 화면이 열리던 동작에 의존하고 있었다면 그 경로를 정리해야 한다.

**적용 시점**

지금 리포지토리에 있는 컨트롤러는 `AuthController`(전부 public), `AuthStatusController`(위 예외),
`NoticeController`(GET permitAll), `NoticeAdminController`(`hasRole('ADMIN')`)뿐이라
**오늘 붙일 대상이 없다.** Phase 4 이후 사용자 API가 생기는 대로 적용하고, 이 항목을 갱신한다.

---

## 변경 없음 (확인함)

바뀌었다고 오해하기 쉬운 것들. 확인 결과 **기존과 동일**하다.

| 항목 | 확인 내용 |
|------|-----------|
| 토큰 쿠키 속성 | `HttpOnly`, `Secure`, `SameSite`, `Path=/` 동일. Kotlin `CookieManager.kt:11`도 `${cookie.secure:true}`로 기본 `true`다 |
| `GET /api/auth/status` | Kotlin에도 있고 `@PreAuthorize` 없이 `@AuthenticationPrincipal`만 받는다. 응답 형태 동일 |
| 401 에러 코드 | `AUTH-010` / `AUTH-011` / `AUTH-012` / `AUTH-020` 매핑 동일 |
| 토큰 위치 | `accessToken` 쿠키 우선, 없으면 `Authorization: Bearer` 폴백. 동일 |
| 공통 응답 봉투 | `{ code, status, message, data }`. `null` 필드는 생략. 동일 |
| public path 목록 | `SecurityConfig`의 `PUBLIC_PATHS` 15개 동일 |
| CORS | 허용 Origin·메서드·`allowCredentials` 동일 |
