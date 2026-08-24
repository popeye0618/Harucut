# 구현 로드맵

`docs/`의 API 명세를 요구사항으로 삼아, **처음부터 새로 만드는 순서**로 정리했다.

각 단계는 **수행 → 테스트 → 체크**의 세 블록으로 되어 있다.
**체크를 통과하지 못하면 다음 단계로 넘어가지 않는다.**

---

## 작업 루프

한 단계를 시작할 때마다 이 순서를 지킨다.

```
1. 읽기    해당 docs 문서를 먼저 읽는다. 무엇을 만들지 말로 설명할 수 있어야 한다.
2. 판단    "판단할 것" 항목에 답을 정한다. 기존 구현과 다르게 갈 거면 decisions.md에 기록.
3. 수행    코드를 쓴다. 체크리스트 항목 단위로 커밋.
4. 테스트  "테스트" 블록의 항목을 테스트 코드로 증명한다. 통과할 때까지 다음으로 안 감.
5. 체크    "완료 체크"를 하나씩 확인. 하나라도 아니면 4로.
6. 회고    무엇을 배웠는지 한 줄. 막힌 부분이 있으면 그것도.
```

**테스트 없이 넘어간 단계는 나중에 반드시 되돌아온다.**
이 프로젝트의 목적은 동작하는 서버가 아니라 이해한 코드다.

### 커밋 단위

체크리스트 항목 1~3개마다 커밋. 브랜치는 `feat/{phase}-{name}` 형식.
예: `feat/p1-common-response`, `feat/p3-jwt-filter`

---

## 전체 조감도

| Phase | 내용 | 의존 | 왜 이 순서인가 |
|-------|------|------|----------------|
| **0** | 프로젝트 기반 | – | 앱이 뜨고 테스트가 도는 상태 확보 |
| **1** | 공통 기반 (응답/예외/페이징/엔티티) | 0 | 모든 도메인이 여기 의존 |
| **2** | 공지 | 1 | 순수 CRUD. Phase 1을 검증하는 시험대 |
| **3** | 인증 (가장 큼, 7단계로 분할) | 1 | 이후 모든 도메인이 "로그인한 사용자"를 전제 |
| **4** | 사용자 정보 | 3 | 인증 위에 얹는 첫 도메인 |
| **5** | 약관 | 3 | 이력 설계 연습. 다른 도메인에 영향 없음 |
| **6** | 스토리지 (S3) | 3 | 프레임/미디어/프로필의 선행 조건 |
| **7** | 구독 정책 | 3 | 프레임/미디어의 정책 판정에 필요 |
| **8** | 프레임 | 6, 7 | 서비스 핵심 도메인 |
| **9** | 미디어 | 6, 7 | 프레임과 유사. 빠르게 |
| **10** | 결제 | 7 | 가장 어렵고 가장 실무적 |
| **11** | 쿠폰 | 7, 10 | 구독/결제 위에 얹힘 |
| **12** | 배치 + 회원 탈퇴 | 8~11 | 모든 도메인의 삭제 핸들러가 필요 |
| **13** | 소셜 로그인 | 3 | 독립적. 언제 해도 되나 난이도 있음 |
| **14** | 관리자 통계 | 10 | 결제 데이터가 있어야 의미 있음 |
| **15** | 마무리 (문서/운영) | 전부 | |

> Phase 5·6·13은 앞뒤 순서를 바꿔도 된다. 나머지는 의존 관계상 순서가 강제된다.

---

# Phase 0 — 프로젝트 기반

**목표:** 앱이 뜨고, 테스트가 돌고, DB에 붙는 상태.

### 수행

- [ ] `build.gradle` 의존성 정리 — 지금 필요한 것만 남기고, 나머지는 필요할 때 추가
- [ ] `application.yaml` 프로파일 분리 (`local` / `test`)
- [ ] H2 설정 (`local`은 파일 모드 또는 인메모리, `test`는 인메모리)
- [ ] `spring.jackson.time-zone: Asia/Seoul`, `default-property-inclusion: non_null`
- [ ] `.gitignore` 확인 (`.env`, `build/`, `*.log`)
- [ ] 임시 헬스체크 컨트롤러 (`GET /ping` → `"pong"`) — Phase 1 끝나면 삭제

### 테스트

- [ ] `HarucutApplicationTests` — 컨텍스트 로딩 성공
- [ ] `GET /ping` MockMvc 테스트 통과

### 완료 체크

- [ ] `./gradlew bootRun` 으로 앱이 뜬다
- [ ] `./gradlew test` 가 초록불
- [ ] H2 콘솔에 접속해 스키마를 눈으로 확인할 수 있다
- [ ] 테스트가 로컬 DB를 건드리지 않는다 (프로파일 분리 확인)

### 판단할 것

| 질문 | 참고 |
|------|------|
| DB를 H2로 갈까, 처음부터 MySQL(Docker)로 갈까? | H2는 빠르지만 방언 차이로 나중에 터진다. 통계 쿼리(`11-admin-stats.md`)에서 특히 |
| 테스트에서 DB를 어떻게 할까? | 인메모리 H2 / Testcontainers. 후자가 정확하지만 느리다 |
| Lombok을 쓸까, Java 21 record로 갈까? | DTO는 record가 낫다. 엔티티는 record 불가(JPA 요구사항) |

---

# Phase 1 — 공통 기반

> 참고: `docs/00-conventions.md`

**목표:** 모든 도메인이 공유할 응답·예외·페이징·엔티티 뼈대.
**이 단계의 품질이 프로젝트 전체의 품질을 결정한다.** 서두르지 말 것.

### 수행

- [ ] `Response<T>` — `code` / `status` / `message` / `data`, `NON_NULL` 직렬화
- [ ] 정적 팩토리: `ok()`, `ok(data)`, `error(errorCode)`, `from(errorCode, data)`
- [ ] `ErrorCode` 인터페이스 (`code`, `httpStatus`, `message`)
- [ ] `GlobalErrorCode` enum — `docs/00-conventions.md` 6절의 GEN 표 전부
- [ ] `BusinessException` (errorCode + 선택적 custom message)
- [ ] `GlobalExceptionHandler` (`@RestControllerAdvice`)
  - [ ] `BusinessException` → 해당 코드
  - [ ] `MethodArgumentNotValidException` → `GEN-003` + `data`에 필드 목록
  - [ ] `MethodArgumentTypeMismatchException` → `GEN-005`
  - [ ] `MissingServletRequestParameterException` / `MissingRequestCookieException` → `GEN-004`
  - [ ] `HttpMessageNotReadableException` → `GEN-006`
  - [ ] `ConstraintViolationException` / `BindException` → `GEN-002`
  - [ ] `HttpRequestMethodNotSupportedException` → `GEN-041`
  - [ ] `NoResourceFoundException` → `GEN-031`
  - [ ] `AccessDeniedException` → `GEN-021`
  - [ ] `Exception` (fallback) → `GEN-091` + **error 레벨 로그**
- [ ] `FieldErrorResponse` (`field`, `message`, `rejectedValue`)
- [ ] `PageResponse<T>` + `from(Page<T>)`
- [ ] `BaseEntity` (`createdAt`, `updatedAt`) + `@EnableJpaAuditing`
- [ ] `publicId` 생성 유틸 (12자)
- [ ] `Clock` 빈 등록 (`Clock.systemDefaultZone()`)

### 테스트

- [ ] 성공 응답에 `message`, `data` 키가 **없다** (null 제외 확인)
- [ ] `BusinessException` 던지면 해당 code/status/message가 나온다
- [ ] `@Valid` 실패 시 `GEN-003` + `data` 배열에 필드명·메시지·거부값
- [ ] 필드가 2개 이상 실패하면 배열에 2개가 담긴다
- [ ] 존재하지 않는 URL → `GEN-031`
- [ ] 잘못된 HTTP 메서드 → `GEN-041`
- [ ] 깨진 JSON 본문 → `GEN-006`
- [ ] `PageResponse.from()` — 빈 페이지, 마지막 페이지 경계
- [ ] `publicId` 생성 — 길이 12, 1만 번 생성 시 중복 없음
- [ ] `BaseEntity` — 저장 시 `createdAt` 채워짐, 수정 시 `updatedAt` 갱신

> 테스트용 임시 컨트롤러(`@RestController` in test source)를 만들어 예외 핸들러를 검증한다.
> 프로덕션 코드에 테스트용 엔드포인트를 남기지 말 것.

### 완료 체크

- [ ] 위 테스트 전부 통과
- [ ] 응답 JSON이 `docs/00-conventions.md` 1절 예시와 **문자 단위로 일치**
- [ ] 예외 핸들러에 로그가 적절히 있다 (business=warn, 예상 못한 예외=error + 스택트레이스)
- [ ] `LocalDateTime.now()`를 직접 부르는 코드가 없다 (전부 `Clock` 경유)

### 판단할 것

| 질문 | 트레이드오프 |
|------|-------------|
| `status`를 바디에 중복으로 넣을까? | 프론트가 쓰면 유지. 안 쓰면 중복이라 제거 가능 |
| 성공도 `code: "GEN-000"`을 줄까? | 없어도 되지만, 있으면 클라이언트가 한 곳만 보면 된다 |
| `ErrorCode`를 도메인별 enum으로 쪼갤까, 하나로 합칠까? | 쪼개면 응집도↑ 탐색성↓. 기존은 쪼갬 |
| `Service` 인터페이스 + `Impl`을 만들까? | **구현체 하나뿐이면 불필요한 간접층.** 기존은 전부 분리했지만 재고할 것 |

---

# Phase 2 — 공지

> 참고: `docs/10-notice.md`

**목표:** Phase 1의 기반을 실제 CRUD로 검증. **가장 단순한 도메인이라 첫 타자로 적합.**
아직 Security가 없으므로 관리자 API도 일단 열어둔다 (Phase 3-7에서 잠근다).

### 수행

- [ ] `Notice` 엔티티 (`publicId`, `title`, `content`, `pinned`, `published`, `publishedAt`)
- [ ] 상태 변경 메서드를 엔티티에 (`update`, `publish(now)`, `unpublish`) — **setter 노출 금지**
- [ ] `NoticeRepository` (`findByPublicId`, `findByPublishedTrueOrderBy...`)
- [ ] `NoticeErrorCode` (`NOTICE-001`)
- [ ] 공개 서비스/컨트롤러 — 목록(페이징), 단건
- [ ] 관리자 서비스/컨트롤러 — 생성/수정/게시/게시취소/삭제/전체목록
- [ ] page/size 검증 (`page >= 0`, `size >= 1` → `GEN-002`)
- [ ] DTO ↔ 엔티티 변환 (`NoticeResponse.from(notice)` 또는 별도 매퍼)

### 테스트

**단위 (서비스)**
- [ ] 미게시 공지 단건 조회 → `NOTICE-001`
- [ ] 없는 publicId → `NOTICE-001`
- [ ] `page = -1` → `GEN-002`
- [ ] `size = 0` → `GEN-002`
- [ ] 게시 시 `published = true`, `publishedAt = 고정된 now`

**슬라이스 (컨트롤러, MockMvc)**
- [ ] 목록 응답이 `PageResponse` 구조로 나온다
- [ ] 생성 시 `title` 누락 → `GEN-003` + `data[0].field == "title"`
- [ ] `title` 201자 → `GEN-003`

**통합 (DB 포함)**
- [ ] 정렬 검증: 고정 공지가 먼저, 그다음 게시 최신순
- [ ] 미게시 공지가 공개 목록에 **안 나온다**
- [ ] 관리자 목록에는 미게시가 **나온다**

### 완료 체크

- [ ] 공개 목록/단건, 관리자 6개 엔드포인트가 명세대로 동작
- [ ] 페이지 경계값(첫 페이지, 마지막, 범위 초과) 정상
- [ ] 정렬 기준이 공개(pinned→publishedAt)와 관리자(createdAt)로 다르게 동작
- [ ] `docs/10-notice.md`의 실패 표에 있는 응답이 전부 재현된다

### 판단할 것

| 질문 | 참고 |
|------|------|
| 목록 응답에 본문 전체를 담을까? | 기존은 담는다. 본문이 길면 무겁다 → 요약 필드 분리 검토 |
| 생성 시 식별자를 반환할까? | 기존은 안 준다. `201 Created` + `Location`이 REST 정석 |
| 수정을 PATCH로 할까 PUT으로 할까? | 기존은 PATCH인데 동작은 전체 교체. **모순** |
| 삭제를 하드로 할까 소프트로 할까? | 기존은 하드. `unpublish`가 있으니 삭제가 정말 필요한가? |
| 공개 API 식별자를 publicId로 할까 id로 할까? | 기존은 이원화. 통일할지 결정 |

---

# Phase 3 — 인증

> 참고: `docs/01-auth.md`, `docs/00-conventions.md` 2절

**가장 크고 가장 배울 게 많은 구간.** 7개로 쪼갠다. **한 번에 다 하려고 하지 말 것.**

---

## 3-1. User 엔티티 + 회원가입 (이메일 인증 없이)

### 수행
- [ ] `User` 엔티티 — `provider`, `providerId`, `userRole`, `email`, `password`, `username`, `profileImageUrl`, `userStatus`, `deleteRequestedAt`, `publicId`
- [ ] `Provider` / `UserRole` / `UserStatus` enum
- [ ] `UserRepository` (`findByProviderAndEmail`, `findByPublicId`, `existsByProviderAndEmail`)
- [ ] `AuthErrorCode` enum (`docs/00-conventions.md` 6절 AUTH 표 전부)
- [ ] `PasswordEncoder` 빈 (BCrypt)
- [ ] `POST /api/harucut/register` — 중복 검사 → 인코딩 → 저장
- [ ] 최소 `SecurityConfig` — csrf/formLogin/httpBasic disable, 일단 전부 permitAll

### 테스트
- [ ] 정상 가입 → DB에 저장, 비밀번호가 **평문이 아니다**
- [ ] 중복 이메일 → `AUTH-030` 409
- [ ] 비밀번호 7자 → `GEN-003`, 21자 → `GEN-003`
- [ ] 이메일 형식 오류 → `GEN-003`
- [ ] `publicId`가 채워진다

### 완료 체크
- [ ] `curl`로 가입 → H2 콘솔에서 BCrypt 해시 확인
- [ ] 사용자 유일성이 `(provider, email)` 조합임을 이해했다

### 판단할 것
- 같은 이메일로 로컬/카카오 계정이 각각 생기는 걸 허용할까? (기존: 허용)
- 이메일 유니크 제약을 DB에 걸까? (걸면 `(provider, email)` 복합)

---

## 3-2. JWT 발급 + 쿠키 + 로그인

### 수행
- [ ] jjwt 의존성 추가
- [ ] `JwtProperties` (`secret`, `accessExpiration=30m`, `refreshExpiration=14d`)
- [ ] `JwtTokenService` — `createAccessToken`, `createRefreshToken`, `parse`
  - [ ] claims: `sub`=publicId, `iss`="Harucut", `type`=ACCESS/REFRESH, `iat`, `exp`
  - [ ] 파싱 실패를 예외로 변환: 만료→`AUTH-012`, 서명오류/형식오류→`AUTH-011`
- [ ] `CookieManager` — `createTokenCookie`, `createExpiredCookie`
- [ ] `cookie.domain` / `secure` / `same-site` 설정화
- [ ] `POST /api/harucut/login` — 인증 → 토큰 발급 → 쿠키 2개 + `{userStatus}` 반환

### 테스트
- [ ] 발급한 토큰을 파싱하면 원래 publicId가 나온다
- [ ] 만료된 토큰 파싱 → `AUTH-012` (`Clock` 조작 또는 짧은 만료로 생성)
- [ ] 서명이 다른 토큰 파싱 → `AUTH-011`
- [ ] 쓰레기 문자열 파싱 → `AUTH-011`
- [ ] access 토큰의 `type == "ACCESS"`, refresh는 `"REFRESH"`
- [ ] 로그인 성공 → `Set-Cookie` 헤더 2개, `httpOnly` 포함
- [ ] 비밀번호 불일치 → `AUTH-001` 400
- [ ] 없는 이메일 → `AUTH-020` 404

### 완료 체크
- [ ] 브라우저 개발자도구에서 쿠키 2개가 `HttpOnly`로 심어진다
- [ ] JWT를 [jwt.io](https://jwt.io)에 붙여넣어 클레임을 눈으로 확인했다
- [ ] **왜 subject에 PK가 아니라 publicId를 쓰는지 설명할 수 있다**

### 판단할 것
- 토큰을 쿠키로 줄까 응답 바디로 줄까? (쿠키=XSS 안전, 바디=CSRF 안전 + 모바일 편함)
- `secret`을 어디서 관리할까? (환경변수 필수. 코드/깃에 절대 금지)

---

## 3-3. 인증 필터 + 401/403

### 수행
- [ ] `CustomUserPrincipal implements UserDetails`
  - [ ] authority 매핑: `ACTIVE`→userRole, `DELETED_REQUESTED`→`ROLE_DELETED_REQUESTED`, 그 외→빈 목록
- [ ] `CustomUserDetailsService` (`loadUserByUsername`, `loadUserByPublicId`)
- [ ] `JwtAuthenticationFilter extends OncePerRequestFilter`
  - [ ] 쿠키 `accessToken` → 없으면 `Authorization: Bearer` 폴백
  - [ ] 파싱 성공 시 `SecurityContext` 채움
  - [ ] 파싱 실패 시 컨텍스트 clear + EntryPoint 호출 + **체인 중단**
- [ ] `CustomAuthenticationEntryPoint` — JSON 봉투로 401
- [ ] `SecurityConfig` 정식 구성
  - [ ] `SessionCreationPolicy.STATELESS`
  - [ ] `PUBLIC_PATHS` 배열 (`docs/00-conventions.md` 3절)
  - [ ] `GET /api/notices/**` permitAll
  - [ ] `anyRequest().authenticated()`
  - [ ] CORS 설정
- [ ] `GET /api/auth/status`

### 테스트
- [ ] 토큰 없이 보호 API → 401 `AUTH-010`, **JSON 봉투 형태**
- [ ] 만료 토큰 → 401 `AUTH-012`
- [ ] 위조 토큰 → 401 `AUTH-011`
- [ ] 유효 토큰 → 200
- [ ] `Authorization: Bearer` 헤더만으로도 통과
- [ ] 쿠키와 헤더가 둘 다 있으면 **쿠키 우선**
- [ ] public path는 토큰 없이 200
- [ ] `DELETED_REQUESTED` 사용자가 일반 API 호출 → 403 `GEN-021`

### 완료 체크
- [ ] 401 응답이 Spring 기본 HTML이 아니라 우리 JSON 봉투다
- [ ] 필터 순서를 그림으로 그려 설명할 수 있다
- [ ] **`DELETED_REQUESTED`가 `ROLE_USER`를 잃는 설계 의도를 설명할 수 있다**
- [ ] Phase 2의 관리자 공지 API를 `@PreAuthorize("hasRole('ADMIN')")`로 잠갔다 → 403 확인

### 판단할 것
- 토큰이 없을 때 필터에서 401을 낼까, 그냥 통과시키고 인가에서 막을까? (기존: 후자)
- 헤더 폴백을 프로덕션에도 열어둘까? (Swagger 편의 vs 공격면 확대)

---

## 3-4. Redis + Refresh Token

### 수행
- [ ] Redis 의존성 + Docker Compose (또는 로컬 설치)
- [ ] `RedisConfig`, `StringRedisTemplate`
- [ ] `RefreshTokenService` — `save`, `reissue`, `logout`
  - [ ] 키: `REFRESH_TOKEN:USER:{publicId}`, TTL 14일
  - [ ] 재발급: 파싱 → `type=REFRESH` 확인 → **저장값과 문자열 일치** 확인 → 회전
- [ ] `POST /api/harucut/reissue`
- [ ] `DELETE /api/harucut/logout` — principal 없으면 refresh 쿠키에서 publicId 복원

### 테스트
- [ ] 정상 재발급 → 새 쿠키 2개, Redis 값이 **새 refresh로 바뀜**
- [ ] 이전 refresh로 다시 재발급 시도 → `AUTH-011` (회전 검증)
- [ ] refresh 쿠키 누락 → `GEN-004` 400
- [ ] access 토큰을 refresh 자리에 넣기 → `AUTH-011` (`type` 검증)
- [ ] 로그아웃 → Redis 키 삭제 + 만료 쿠키 2개
- [ ] **토큰 없이 로그아웃 → 200** (실패하면 안 됨)
- [ ] 쓰레기 refresh 쿠키로 로그아웃 → 200 (조용히 무시)

### 완료 체크
- [ ] `redis-cli KEYS 'REFRESH_TOKEN:*'`로 키와 TTL을 눈으로 확인
- [ ] **왜 로그아웃이 public path인지 설명할 수 있다**
- [ ] 토큰 회전(rotation)이 무엇을 막는지 설명할 수 있다

### 판단할 것
- 탈취 감지(저장값 불일치) 시 해당 사용자 세션을 전부 끊을까? (기존: 안 함 → **개선 후보**)
- Redis가 죽으면 어떻게 될까? 재발급만 막힐까, 전체가 마비될까?

---

## 3-5. 이메일 인증

### 수행
- [ ] mail 스타터 + Thymeleaf, 로컬은 MailHog (Docker)
- [ ] `VerificationCodeGenerator` (6자리)
- [ ] `EmailVerificationRepository` (Redis)
  - [ ] `email:code:{email}` TTL 5분
  - [ ] `email:verified:{email}` TTL 10분
- [ ] `MailService` / `EmailVerificationService`
- [ ] 메일 템플릿 `verification-code.html`
- [ ] `POST /api/email-auth/code`, `POST /api/email-auth/verification`
- [ ] 회원가입에 `consumeVerified` 연결 → 미인증이면 `AUTH-004`

### 테스트
- [ ] 코드 발송 → Redis에 저장됨
- [ ] 정확한 코드 검증 → 성공, `email:code` 삭제, `email:verified` 생성
- [ ] 틀린 코드 → `AUTH-003`
- [ ] 대소문자 다른 코드 → **성공** (ignoreCase)
- [ ] 만료된 코드 → `AUTH-003`
- [ ] 인증 없이 가입 → `AUTH-004`
- [ ] 인증 후 가입 → 성공, `email:verified` **삭제됨**
- [ ] 같은 인증으로 두 번 가입 시도 → 두 번째는 `AUTH-004`

### 완료 체크
- [ ] MailHog UI(`localhost:8025`)에서 메일이 보인다
- [ ] 2단계 구조(코드 → 검증플래그 → 소비)의 이유를 설명할 수 있다
- [ ] 코드 검증을 원자적으로 할지(Lua/`GETDEL`) 정했다

### 판단할 것
- 코드 발송 시 이미 가입된 이메일인지 검사할까? (기존: 안 함 → 이메일 열거 방지 vs UX)
- 발송 횟수 제한(rate limit)을 걸까? **기존에는 없다. SMTP 비용 폭탄 위험**

---

## 3-6. 비밀번호 재설정 / 변경

### 수행
- [ ] `email:reset:code:{email}` (5분), `reset_token:{uuid}` (10분)
- [ ] 메일 템플릿 `password-reset-code.html`
- [ ] `POST /api/harucut/reset/password/code`
- [ ] `POST /api/harucut/reset/password/verification` → 리셋 토큰
- [ ] `PATCH /api/harucut/reset/password`
- [ ] `PATCH /api/harucut/change/password` (로그인 필요)

### 테스트
- [ ] 없는 이메일로 코드 요청 → `AUTH-020` (또는 결정한 정책대로)
- [ ] 코드 검증 성공 → UUID 리셋 토큰 반환, Redis에 email 매핑
- [ ] 리셋 토큰으로 비밀번호 변경 → 새 비밀번호로 로그인 성공
- [ ] 같은 리셋 토큰 재사용 → `AUTH-011` (1회용)
- [ ] 만료된 리셋 토큰 → `AUTH-011`
- [ ] 기존 비밀번호 틀림 → `AUTH-002`
- [ ] 소셜 계정(password=null)이 변경 시도 → 무슨 일이 일어나는지 확인

### 완료 체크
- [ ] 재설정 전 과정을 curl로 끝까지 통과
- [ ] 리셋 토큰이 1회용임이 테스트로 증명됨

### 판단할 것
- 비밀번호 변경 후 **기존 refresh token을 전부 무효화할까?** (기존: 안 함 → **개선 후보**)
- 없는 이메일에도 200을 줄까? (이메일 열거 방지 vs "메일이 안 왔어요" 문의 증가)
- 소셜 계정 전용 에러 코드를 만들까?

---

## 3-7. 정리

### 수행
- [ ] Phase 2 관리자 API에 `@PreAuthorize("hasRole('ADMIN')")` 적용 확인
- [ ] `PUBLIC_PATHS`를 `docs/00-conventions.md` 3절과 대조
- [ ] CORS 설정 확인 (`allowCredentials=true` + 구체적 origin)

### 완료 체크
- [ ] 로컬 프론트(또는 curl + `--cookie-jar`)로 **가입 → 인증 → 로그인 → 보호 API → 재발급 → 로그아웃** 전 과정 통과
- [ ] 인증 관련 테스트가 전부 초록불
- [ ] `docs/01-auth.md`의 모든 실패 케이스가 재현된다

---

# Phase 4 — 사용자 정보

> 참고: `docs/02-user.md`

**목표:** 인증 위에 얹는 첫 도메인. 작고 빠르게.
**프로필 이미지 변경은 S3(Phase 6) 이후로 미룬다.**

### 수행
- [ ] `GET /api/auth/user/info` (프로필 URL은 일단 raw key 반환 → Phase 6에서 presigned로)
- [ ] `PATCH /api/auth/user/change/username`
- [ ] (Phase 6 이후) `PATCH /api/auth/user/change/profile-image`
- [ ] (Phase 7 이후) `GET /api/auth/user/subscription/usage`

### 테스트
- [ ] 내 정보 조회 → 내 데이터만
- [ ] 닉네임 20자 초과 → `GEN-002`
- [ ] 닉네임 빈 값 → `GEN-002`
- [ ] 파라미터 누락 → `GEN-004`

### 완료 체크
- [ ] 다른 사용자의 정보가 절대 안 나온다 (principal에서만 id를 얻는지 확인)

### 판단할 것
- 닉네임을 `@RequestParam`으로 받을까 `@RequestBody`로 받을까? (기존은 param → 에러 코드가 다름)
- 닉네임 중복을 허용할까? (기존: 허용)

---

# Phase 5 — 약관

> 참고: `docs/09-terms.md`

**목표:** append-only 이력 + 파생 상태 계산. **설계 연습으로서 가치가 큼.**

### 수행
- [ ] `Terms` / `TermsVersion` / `TermsConsent` 3테이블
- [ ] `(terms_id, version)` **unique 제약**
- [ ] `TermsErrorCode`
- [ ] `GET /api/terms` (공개) — 코드별 최신 활성 버전
- [ ] `GET /api/auth/terms/consents/me` — 상태 계산
- [ ] `POST /api/auth/terms/consents` — 배열 수신, append
- [ ] 관리자: 생성 / 개정 / 목록 / 비활성화

### 테스트
- [ ] 약관 생성 → version 1 함께 생성
- [ ] 코드 중복 → `TERMS-002`
- [ ] code 패턴 위반(`TOS`, `to s`, 51자) → `GEN-003`
- [ ] 개정 → version 2 생성, **version 1 그대로 남아 있음**
- [ ] **상태 판정 3종**
  - [ ] 동의 없음 → `NOT_AGREED`
  - [ ] 최신 버전 동의 → `AGREED`
  - [ ] v1 동의 후 v2 개정 → `NEEDS_RECONSENT`, `agreedVersion=1`, `latestVersion=2`
  - [ ] 동의 후 철회 → `NOT_AGREED`, `agreedVersion=null`
  - [ ] 철회 후 재동의 → `AGREED`
- [ ] 필수 약관 철회 → `TERMS-003`
- [ ] 없는 코드 → `TERMS-001`
- [ ] 비활성 약관 코드 → `TERMS-001`
- [ ] **동의 이력이 UPDATE 없이 계속 쌓인다** (행 수로 검증)
- [ ] 3개 항목 중 마지막이 실패 → 앞의 2개도 롤백

### 완료 체크
- [ ] 동의/철회를 5번 반복해도 `terms_consent` 행이 5개 쌓인다
- [ ] **왜 상태를 컬럼에 저장하지 않고 계산하는지 설명할 수 있다**
- [ ] 개정 후 별도 갱신 작업 없이 모든 사용자가 `NEEDS_RECONSENT`가 된다

### 판단할 것
- 동의 상태 조회를 "이력 전량 로드 후 그룹핑"으로 할까, 쿼리로 최신 1건만 가져올까?
- 필수 약관 미동의 사용자를 인터셉터로 차단할까? (기존: 안 함)
- 관리자용 "사용자 동의 이력 조회"를 만들까? (**법적 증빙 시 필요**)

---

# Phase 6 — 스토리지 (S3)

> 참고: `docs/05-storage.md`

**목표:** presigned URL 발급. 프레임/미디어/프로필의 선행 조건.

### 수행
- [ ] AWS SDK v2 의존성, `S3Client` + `S3Presigner` 빈
- [ ] 로컬 개발 환경 결정 (실제 S3 버킷 / LocalStack / MinIO)
- [ ] `UploadType`, `ContentType` (MIME↔확장자 검증)
- [ ] `UploadPathStrategy` 인터페이스 + 4개 구현
- [ ] `FileStorageService` — presigned PUT / presigned GET / download URL / delete
- [ ] `normalizeToS3Key` 유틸 (URL → 순수 key)
- [ ] `Content-Disposition` 조립 (`filename` + `filename*` 둘 다)
- [ ] `POST /api/auth/user/files/presigned-upload`
- [ ] **`GET /presigned-img` / `DELETE /delete` — 소유권 검증을 넣을지 결정 후 구현**
- [ ] Phase 4의 프로필 URL을 presigned로 교체, `PATCH /change/profile-image` 완성

### 테스트
- [ ] `PROFILE` + `profile.png` + `PNG` → key가 `uploads/users/{publicId}/profile/{uuid}.png`
- [ ] 4가지 `UploadType`별 경로가 각각 맞다
- [ ] MIME `PNG` + 확장자 `.jpg` → 415 `GEN-051`
- [ ] 확장자 없는 파일명 → 415
- [ ] 지원 안 하는 확장자(`.exe`) → 415
- [ ] 대문자 확장자(`.PNG`) → 정상 (소문자 정규화)
- [ ] `normalizeToS3Key` — 전체 URL, 쿼리스트링 붙은 URL, 이미 key인 경우 전부 같은 결과
- [ ] 한글 파일명 → `Content-Disposition`에 `filename*=UTF-8''` 인코딩

### 완료 체크
- [ ] presigned URL로 실제 파일 업로드가 된다 (curl `-X PUT --upload-file`)
- [ ] presigned GET URL로 브라우저에서 이미지가 보인다
- [ ] 다운로드 URL로 받으면 **한글 파일명이 안 깨진다**
- [ ] 전략 패턴 덕분에 새 업로드 타입 추가가 클래스 1개 추가로 끝난다

### 판단할 것
| 질문 | 참고 |
|------|------|
| **`presigned-img`/`delete`에 소유권 검증을 넣을까?** | **기존은 없어서 남의 파일을 보고 지울 수 있다. 심각한 취약점** |
| 범용 delete API를 아예 없앨까? | 도메인 API가 파일 생명주기를 소유하는 편이 안전 |
| 파일 크기 제한을 어떻게 걸까? | presigned PUT은 못 건다. presigned POST + policy, 또는 버킷 정책 |
| 로컬을 LocalStack으로 갈까 실제 S3로 갈까? | LocalStack=무료/오프라인, 실제=정확 |

---

# Phase 7 — 구독 정책

> 참고: `docs/06-subscription.md`, `docs/12-domain-model.md` 4절

**목표:** 결제 없이 구독 **상태 모델과 정책**만. 프레임/미디어의 선행 조건.

### 수행
- [ ] `PlanTier` enum + `PlanPolicy`
- [ ] `Limit` sealed interface (`Unlimited` / `Limited`)
- [ ] `Retention` sealed interface (`Unlimited` / `Days` / `Months`)
- [ ] `UserSubscription` 엔티티 — `user_id` unique, `@Version` 낙관적 락
- [ ] 상태 전이 메서드 (`activatePaid`, `renew`, `cancelAutoRenew`, `expireToFree`, `activateGrant`, `changePlan`)
- [ ] **`effectiveTier(now)`** — 핵심
- [ ] `UserRegisteredEvent` + 리스너 → 가입 시 기본 BASIC 구독 생성
- [ ] `SubscriptionPolicyService` + 포트 인터페이스 (`FrameSubscriptionPolicy`, `MediaSubscriptionPolicy`)
- [ ] `SubscriptionUsageService`
- [ ] `GET /api/auth/subscriptions`, `POST /api/auth/subscriptions/cancel`
- [ ] 관리자: `GET /{userId}`, `PATCH /{userId}/plan`
- [ ] Phase 4의 `GET /subscription/usage` 완성
- [ ] `PlanPricingProperties` (`billing.pricing.*`)

### 테스트
- [ ] **`effectiveTier` 경계값** (`Clock` 고정)
  - [ ] BASIC → 항상 BASIC
  - [ ] PRO + `periodEnd = null` → PRO
  - [ ] PRO + `now < periodEnd` → PRO
  - [ ] PRO + `now == periodEnd` → **BASIC** (경계)
  - [ ] PRO + `now > periodEnd` → BASIC
- [ ] `Limit.Limited(3).allows(2)` = true, `.allows(3)` = false
- [ ] `Limit.Limited(3).remainingFrom(5)` = 0 (음수 보정)
- [ ] `Unlimited.maxOrUnlimited()` = -1
- [ ] `Retention.Days(3).isAccessible(4일 전, now)` = false
- [ ] 회원가입 → BASIC 구독 자동 생성
- [ ] 구독 없음 → `SUBS-004`
- [ ] 이미 CANCELED 상태에서 해지 → `SUBS-005`
- [ ] 사용량 응답값이 tier별로 맞다 (BASIC 0/PLUS 3/PRO -1)

### 완료 체크
- [ ] **`effectiveTier`가 왜 필요한지, 없으면 무슨 일이 생기는지 설명할 수 있다**
- [ ] 엔티티에 setter가 없고 의미 있는 메서드만 있다
- [ ] `frame`/`media` 패키지가 `subscription` 패키지를 import하지 않는 구조를 준비했다
- [ ] 모든 시간 계산이 `Clock` 경유라 테스트에서 고정된다

### 판단할 것
| 질문 | 참고 |
|------|------|
| `Limit`을 sealed로 만들까, 그냥 `int` + `-1`로 갈까? | 타입 안전 vs 단순함. 정책이 늘수록 sealed 유리 |
| `/api/auth/subscriptions`의 `planTier`를 원본으로 줄까 effectiveTier로 줄까? | **기존은 API마다 다르다 — 불일치** |
| BASIC 사용자가 `cancel`을 호출하면? | 기존은 그냥 CANCELED가 된다 → 가드 추가 검토 |
| 관리자 `changePlan`으로 BASIC에게 PRO를 주면 무기한 PRO가 된다 | 의도인지 확인 |

---

# Phase 8 — 프레임

> 참고: `docs/03-frame.md`

**목표:** 서비스 핵심 도메인. 다형성 JSON + 정책 적용 + S3 생명주기.

### 수행
- [ ] `FrameType` enum (캔버스 크기 포함), `FrameLayout`
- [ ] `BackgroundAttributes` sealed + `@JsonTypeInfo`/`@JsonSubTypes` (COLOR/IMAGE)
- [ ] `ComponentType` enum
- [ ] `Frame` / `FrameComponent` 엔티티 (cascade, 연관관계 편의 메서드)
- [ ] `BackgroundConverter` (JSON ↔ 컬럼)
- [ ] `FrameAssetManager` (key 정규화, S3 삭제)
- [ ] `FrameComponentAssembler` (컴포넌트 생성, 응답 변환)
- [ ] 사용자 CRUD 5개 + 관리자 시스템 프레임 CRUD 4개
- [ ] 정책 적용: 생성 시 한도, 목록 시 cutoff+cap, 단건 시 소유권+cutoff+cap

### 테스트
- [ ] **다형성 직렬화/역직렬화**
  - [ ] `{"type":"COLOR","value":"#FFF"}` → `ColorBackgroundAttributes`
  - [ ] `{"type":"IMAGE","key":"...","opacity":0.8}` → `ImageBackgroundAttributes`
  - [ ] `{"type":"GRADIENT"}` → 역직렬화 실패 → `GEN-006`
- [ ] `zIndex` 직렬화 이름이 `zIndex`다 (`zindex` 아님)
- [ ] 요청에 `zindex`(소문자)를 보내도 인식된다 (하위 호환)
- [ ] 컴포넌트가 **zIndex 오름차순**으로 정렬돼 나온다
- [ ] `scale`을 null로 보내면 응답은 1.0
- [ ] `canvasWidth/Height`는 요청값을 무시하고 frameType 값이 나온다
- [ ] **정책**
  - [ ] BASIC 사용자가 생성 → `SUBS-003` 403
  - [ ] PLUS 사용자가 4번째 생성 → `SUBS-003`
  - [ ] PRO 사용자는 계속 생성 가능
  - [ ] PLUS 사용자 목록 → 최신 3개만 (소프트 캡)
  - [ ] 보관 기간 지난 프레임이 목록에서 빠진다
  - [ ] 보관 기간 지난 프레임 단건 조회 → `SUBS-002`
- [ ] 남의 프레임 조회 → 403 `GEN-021`
- [ ] **시스템 프레임은 소유자 검사/기간/cap을 우회한다**
- [ ] 시스템 프레임이 모든 사용자 목록에 포함된다
- [ ] 관리자 API로 사용자 프레임 id 조작 시도 → `FRAME-001` 404
- [ ] 수정 시 교체된 배경/프리뷰/사진의 옛 S3 key가 삭제된다

### 완료 체크
- [ ] "소프트 캡"이 무엇이고 왜 데이터를 안 지우는지 설명할 수 있다
- [ ] 컴포넌트 조회에 N+1이 없다 (**SQL 로그로 확인**)
- [ ] `spring.jpa.show-sql` 켜고 목록 조회 시 쿼리 수를 세어봤다

### 판단할 것
| 질문 | 참고 |
|------|------|
| **S3 삭제를 커밋 후로 미룰까?** | 기존은 커밋 전 → 롤백 시 파일만 사라짐. `@TransactionalEventListener(AFTER_COMMIT)` |
| 수정을 전체 교체로 할까 부분 수정으로 할까? | 전체 교체는 컴포넌트 PK가 매번 바뀜 |
| 생성 응답에 frameId를 줄까? | 안 주면 프론트가 목록을 다시 받아야 함 |
| 수정 시에도 보관 한도를 검사할까? | 기존은 생성에만 |
| BASIC 한도 0(저장 불가) 정책을 유지할까? | 서비스 방향 결정 |

---

# Phase 9 — 미디어

> 참고: `docs/04-media.md`

**목표:** 프레임과 유사한 구조. 빠르게 통과. **파일명 처리가 핵심.**

### 수행
- [ ] `UserMedia` 엔티티 (`s3Key` unique)
- [ ] 표시 파일명 정제 로직 (`resolveDisplayName`) — 경로/따옴표/개행/제어문자 제거, 확장자 재부착, 255자 절단
- [ ] 등록 / 목록(페이징+cutoff) / 다운로드URL / 파일명 수정
- [ ] `UserMediaDeletionHandler` (Phase 12에서 사용)

### 테스트
- [ ] 등록 멱등: 같은 s3Key 두 번 → 행 1개, 같은 응답
- [ ] 남이 등록한 s3Key → 403 `GEN-021`
- [ ] `displayName` 없이 등록 → `harucut_{yyyyMMdd_HHmmss}.png`
- [ ] **파일명 정제**
  - [ ] `"../../etc/passwd"` → 경로 제거됨
  - [ ] `"a\"b.png"` → 따옴표 제거
  - [ ] `"a\nb.png"` → 개행 제거
  - [ ] 300자 이름 → 255자로 절단, **확장자 보존**
  - [ ] `"my_photo"` (확장자 없음) → `"my_photo.png"` (key 확장자 부착)
- [ ] 남의 미디어 다운로드URL → **404** (403 아님)
- [ ] 보관 기간 초과 → `SUBS-002`
- [ ] BASIC 사용자 목록 → 3일 이내 것만

### 완료 체크
- [ ] **파일명 정제가 왜 필요한지(헤더 인젝션) 설명할 수 있다**
- [ ] 다운로드 시 한글 파일명이 안 깨진다
- [ ] 미디어는 개수 cap 없이 기간만 적용됨을 확인했다

### 판단할 것
- **미디어 삭제 API를 만들까?** (기존에 없다. 사용자가 자기 사진을 못 지운다 → 개인정보 관점에서 필요)
- 남의 리소스에 403과 404 중 무엇을 줄까? (**프레임과 미디어가 지금 서로 다르다. 통일할 것**)
- 목록에 presigned URL을 매번 넣을까, 다운로드 API에서만 줄까?

---

# Phase 10 — 결제

> 참고: `docs/07-payment.md`

**가장 어렵고 가장 실무적인 구간.** 시간을 충분히 쓸 것.

### 수행
- [ ] `PaymentGateway` 인터페이스 + DTO 6종
- [ ] `MockPaymentGateway` (FAIL 문자열 규약)
- [ ] `BillingKey` / `PaymentOrder` / `Payment` 엔티티 + enum 6종
- [ ] `PaymentTransactionService` — **`REQUIRES_NEW` 별도 빈**
  - [ ] `createInitialOrderInNewTransaction`
  - [ ] `applyInitialChargeResultInNewTransaction`
- [ ] `PaymentServiceImpl` — PG 호출을 **트랜잭션 밖**에서
- [ ] `POST /api/auth/payments/subscribe`
- [ ] `PaymentWebhookVerifier` + `POST /api/payments/webhook` (raw String 수신)
- [ ] `PaymentErrorCode`

### 테스트
- [ ] BASIC 요청 → `PAY-007` 400
- [ ] 이미 유료 구독 중 → `PAY-003` 409
- [ ] `authKey`에 FAIL → `PAY-001` **502**
- [ ] `customerKey`에 FAIL → `PAY-002` **402**
- [ ] 정상 결제 → 구독 ACTIVE, 주기 `now ~ now+1개월`, `autoRenew=true`
- [ ] 정상 결제 → `Payment.status=APPROVED`, `Order.status=PAID`
- [ ] **청구 실패해도 `PaymentOrder`와 `Payment(FAILED)` 행이 남는다** ← 트랜잭션 분리 검증
- [ ] 웹훅 서명 실패 → `PAY-008`
- [ ] 웹훅 본문이 파싱 없이 raw로 전달된다

### 완료 체크
- [ ] **트랜잭션 경계를 그림으로 그려 설명할 수 있다**
- [ ] 왜 PG 호출을 트랜잭션 밖으로 빼는지 설명할 수 있다
- [ ] 왜 청구 **전에** 주문을 커밋하는지 설명할 수 있다
- [ ] `REQUIRES_NEW`가 **다른 빈**이어야 하는 이유(프록시)를 설명할 수 있다
- [ ] 결제 실패를 예외가 아닌 result 객체로 반환하는 이유를 설명할 수 있다

### 판단할 것
| 질문 | 참고 |
|------|------|
| **멱등성 키를 진짜 멱등하게 만들까?** | 기존은 랜덤이라 무의미. 클라이언트 생성 키 or 결정적 키 |
| 환불/취소 API를 만들까? | `cancel()`이 인터페이스에만 있고 호출부가 없다 |
| 결제 내역 조회 API를 만들까? | 사용자 입장에서 당연히 필요한데 없다 |
| 요금제 변경(업/다운그레이드)을 만들까? | 기존은 해지 후 재구독만 가능. 비례 정산은 난이도 높음 |
| 정기결제 실패 재시도를 넣을까? | 기존은 1회 실패 = 즉시 PAST_DUE |
| mock이 운영에서 절대 안 뜨게 어떻게 보장할까? | 프로파일 조건부 빈 + 기동 시 검증 |

---

# Phase 11 — 쿠폰

> 참고: `docs/08-coupon.md`

**목표:** 구독/결제 위에 얹는 도메인. **동시성 연습의 장.**

### 수행
- [ ] `Coupon` / `UserCoupon` 엔티티
- [ ] **`(user_id, coupon_id)` unique 제약**
- [ ] 사용 상한 동시성 대책 (조건부 UPDATE / 락 / 카운터 중 택1)
- [ ] `GrantActivationService`
- [ ] `POST /redeem` — 즉시 개시 vs 예약 분기
- [ ] `GET /api/auth/coupons`
- [ ] 관리자: 생성 / 목록(집계) / 비활성화
- [ ] `CouponUserDeletionHandler`

### 테스트
- [ ] BASIC 사용자 사용 → `applied=true`, 구독 `GRANTED`, 주기 `now ~ +1개월`
- [ ] PRO 구독 중 사용 → `applied=false`, `UserCoupon.RESERVED`, `reservedGrantCouponId` 세팅
- [ ] 예약 응답의 `startsAt`이 **현재 주기 종료 시각**이다
- [ ] 없는 코드 → `COUPON-001`
- [ ] 비활성 쿠폰 → `COUPON-004`
- [ ] `validUntil` 경과 → `COUPON-004`
- [ ] 상한 도달 → `COUPON-005`
- [ ] 같은 사용자 재사용 → `COUPON-006`
- [ ] 예약이 이미 있음 → `COUPON-007`
- [ ] 관리자가 BASIC tier로 생성 → `COUPON-003`
- [ ] 코드 중복 → `COUPON-002`
- [ ] **동시성: 상한 1인 쿠폰에 10개 스레드 동시 요청 → 성공은 정확히 1건**
- [ ] 관리자 목록의 `redeemedCount`가 맞다 (RESERVED 포함)

### 완료 체크
- [ ] **동시성 테스트가 통과한다** (기존 구현은 이걸 통과 못 한다)
- [ ] 예약 설계(돈 낸 구독을 무료 쿠폰이 덮지 않음)의 의도를 설명할 수 있다
- [ ] 목록 조회에 N+1이 없다 (SQL 로그 확인)

### 판단할 것
- 상한 동시성을 어떻게 풀까? (조건부 UPDATE가 가장 가볍다)
- 코드를 대문자 정규화할까? (기존은 대소문자 구분 → 사용자가 헷갈린다)
- 부여 기간 1개월 고정을 쿠폰별 설정으로 뺄까?
- 예약 슬롯을 여러 개 허용할까? (기존은 단일 컬럼이라 1개)

---

# Phase 12 — 배치 + 회원 탈퇴

> 참고: `docs/01-auth.md` 7절, `docs/12-domain-model.md` 6절

**목표:** 스케줄러 패턴 + 핸들러 체인. 모든 도메인이 준비된 뒤에 한다.

### 수행
- [ ] `@EnableScheduling`
- [ ] `UserDeletionHandler` 인터페이스 + 도메인별 구현 6개
- [ ] `UserExitService` — `requestExit` / `exit` / `reActivate`
- [ ] `DELETE /api/harucut/exit`, `POST /api/harucut/reactivate`
- [ ] `UserDeletionScheduler` (`0 0 0 * * *`)
- [ ] `SubscriptionRenewalScheduler` (`0 0 1 * * *`)
- [ ] `SubscriptionExpirationScheduler` (`0 30 1 * * *`)
- [ ] 배치 서비스는 전부 `REQUIRES_NEW` + try/catch

### 테스트
- [ ] 탈퇴 요청 → `DELETED_REQUESTED`, `deleteRequestedAt` 세팅, Redis 토큰 삭제
- [ ] `DELETED_REQUESTED` 사용자가 일반 API → 403
- [ ] `DELETED_REQUESTED` 사용자가 `/reactivate` → 200
- [ ] `ACTIVE` 사용자가 `/reactivate` → 403
- [ ] **하드 삭제 배치**
  - [ ] 6일 전 요청 → 대상 아님
  - [ ] 8일 전 요청 → 대상
  - [ ] 실행 후 프레임/미디어/구독/쿠폰/결제/동의 전부 삭제
  - [ ] `user` 행은 **남아 있고 익명화**됨 (`deleted_{id}@harucut.local`)
- [ ] **1건 실패해도 나머지가 처리된다** (핸들러 하나가 예외를 던지도록 목킹)
- [ ] 갱신 배치: 만료 도래 ACTIVE만 대상
- [ ] 만료 배치: CANCELED(즉시) / PAST_DUE(3일 유예) / GRANTED(즉시) 각각
- [ ] 예약 쿠폰이 있으면 갱신/만료 대신 grant 활성화

### 완료 체크
- [ ] **1건 실패가 전체를 막지 않음이 테스트로 증명됨**
- [ ] 스케줄러를 직접 호출하는 테스트가 있다 (cron을 기다리지 않는다)
- [ ] 왜 사용자 행을 지우지 않고 익명화하는지 설명할 수 있다
- [ ] 삭제 순서 의존성이 있는지 확인했다 (있으면 `@Order`)

### 판단할 것
| 질문 | 참고 |
|------|------|
| **다중 인스턴스 중복 실행을 어떻게 막을까?** | ShedLock / DB 락 / Spring Batch. **기존에 대책 없음** |
| 실패 건 재시도·알림을 넣을까? | 기존은 로그만 |
| 배치 실행 이력을 남길까? | "어제 배치가 돌았나?"를 확인할 수단이 없다 |
| 유예 기간(7일/3일)을 설정으로 뺄까? | |

---

# Phase 13 — 소셜 로그인

> 참고: `docs/01-auth.md` 6절

**목표:** OAuth2 클라이언트. **독립적이라 언제 해도 되지만 난이도가 있다.**

### 수행
- [ ] OAuth2 client 설정 (google=OIDC, kakao=OIDC, naver=OAuth2)
- [ ] `ProviderUser` 추상화 + `GoogleUser`/`KakaoUser`/`NaverUser` + 팩토리
- [ ] `CustomOAuth2UserService` / `CustomOidcUserService`
- [ ] `CustomOAuth2SuccessHandler` — 토큰 발급 + 쿠키 + 프론트 리다이렉트
- [ ] `CustomOAuth2FailureHandler` — `?error=oauth2` 리다이렉트
- [ ] 카카오/네이버 unlink 서비스
- [ ] `POST /api/oauth2/unlink/naver` 웹훅 (HMAC 검증)

### 테스트
- [ ] 제공자별 사용자 정보 매핑 (응답 JSON 목으로 `ProviderUser` 검증)
- [ ] 신규 사용자 → 가입 + BASIC 구독 생성
- [ ] 기존 사용자 → 재로그인 (중복 생성 안 함)
- [ ] 성공 핸들러 → 쿠키 2개 + 302 `{frontend}/oauth2/callback`
- [ ] 실패 핸들러 → 302 `?error=oauth2`
- [ ] 네이버 unlink 서명 불일치 → 500 `AUTH-091`

### 완료 체크
- [ ] 실제 구글 계정으로 로그인 → 쿠키 발급 → `GET /api/auth/status` 200
- [ ] 왜 JSON이 아니라 리다이렉트인지 설명할 수 있다
- [ ] OIDC와 OAuth2의 차이를 설명할 수 있다

### 판단할 것
- 소셜 계정과 로컬 계정을 연결(link)할 수 있게 할까? (기존: 별개 계정)
- 리다이렉트 대신 프론트에 code를 넘기는 방식으로 갈까?

---

# Phase 14 — 관리자 통계

> 참고: `docs/11-admin-stats.md`

**목표:** 집계. 결제 데이터가 쌓인 뒤에 의미가 있다.

### 수행
- [ ] `Granularity` enum, `AdminErrorCode`
- [ ] `GET /api/admin/stats/revenue`
- [ ] `GET /api/admin/stats/subscriptions`

### 테스트
- [ ] `from`/`to` 생략 → 이번 달 1일 ~ 오늘
- [ ] `from > to` → `ADMIN-001`
- [ ] 367일 범위 → `ADMIN-001`
- [ ] 366일 범위 → 통과 (경계)
- [ ] **`to` 당일 23:59:59 결제가 포함된다** (경계 — 이거 틀리기 쉽다)
- [ ] `to` 다음날 00:00:00 결제는 제외
- [ ] `DAY` 버킷 키가 `yyyy-MM-dd`, `MONTH`가 `yyyy-MM`
- [ ] `INITIAL`/`RENEWAL` 분리 합계가 맞다
- [ ] `APPROVED`가 아닌 결제는 제외
- [ ] `byTier`에 CANCELED 구독이 **포함**된다 (기간 남았으면)
- [ ] `byTier`에 BASIC이 없다

### 완료 체크
- [ ] 결제 데이터를 시드로 넣고 합계를 손으로 검산했다
- [ ] 버킷팅을 메모리에서 할지 DB에서 할지 **의식적으로 결정**했다
- [ ] `byTier`에 CANCELED를 포함하는 이유를 설명할 수 있다

### 판단할 것
- 메모리 집계 vs DB GROUP BY vs 사전 집계 테이블
- 빈 버킷을 0으로 채워줄까?
- COUNT 쿼리 9회를 GROUP BY 1회로 줄일까?

---

# Phase 15 — 마무리

### 수행
- [ ] springdoc-openapi 추가, Swagger 문서화
- [ ] `PUBLIC_PATHS`와 Swagger 자물쇠 아이콘 일치 확인
- [ ] 로깅 정리 (레벨, 민감정보 마스킹)
- [ ] 프로파일별 설정 정리 (`local`/`staging`/`prod`)
- [ ] 환경변수 목록 문서화 (`.env.example`)
- [ ] `docs/decisions.md` 최종 정리

### 완료 체크
- [ ] `/swagger-ui/index.html`에서 모든 API를 실행해볼 수 있다
- [ ] 로그에 비밀번호/토큰/카드정보가 **절대 안 나온다**
- [ ] 시크릿이 코드/깃에 하나도 없다
- [ ] `docs/decisions.md`에 기존 구현과 다르게 간 결정이 전부 기록돼 있다

---

# 테스트 전략

### 계층별 무엇을 검증하나

| 계층 | 도구 | 검증 대상 | 비중 |
|------|------|-----------|------|
| **단위** | JUnit 5 + Mockito + AssertJ | 비즈니스 규칙, 분기, 경계값 | 가장 많이 |
| **슬라이스** | `@WebMvcTest` + MockMvc | 요청 매핑, 검증, 응답 형태, 상태 코드 | 중간 |
| **JPA 슬라이스** | `@DataJpaTest` | 쿼리 메서드, 정렬, 제약 조건 | 중간 |
| **통합** | `@SpringBootTest` | 도메인 간 흐름 (결제→구독, 가입→구독) | 적게 |

> 현재 `build.gradle`은 Boot 4 방식으로 **모듈별 테스트 스타터**를 쓴다
> (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`,
> `spring-boot-starter-security-test`). 필요한 모듈이 늘면 대응 테스트 스타터도 추가한다.

### 반드시 테스트할 것

- **경계값** — 한도 3에서 3번째/4번째, 만료 시각 직전/정각/직후, 페이지 0/-1
- **시간 의존 로직** — `Clock.fixed()`로 고정. 절대 `Thread.sleep` 쓰지 말 것
- **에러 응답** — 성공 경로만 테스트하는 건 절반만 한 것
- **동시성** — 쿠폰 상한, 구독 낙관적 락 (`ExecutorService` + `CountDownLatch`)
- **N+1** — SQL 로그를 켜고 쿼리 수를 센다

### 테스트 작성 규칙

- `@DisplayName`은 **한글로, 무엇을 검증하는지** 쓴다
- `@Nested`로 메서드/시나리오별 그룹핑
- given-when-then 주석으로 구간 구분
- **테스트 하나에 검증 하나** (assert가 5개면 테스트를 나눌 신호)

---

# 진행 상황 추적

각 Phase를 끝낼 때마다 아래를 채운다.

| Phase | 시작 | 완료 | 주요 결정 | 배운 것 |
|-------|------|------|-----------|---------|
| 0 | | | | |
| 1 | | | | |
| 2 | | | | |
| 3-1 | | | | |
| 3-2 | | | | |
| 3-3 | | | | |
| 3-4 | | | | |
| 3-5 | | | | |
| 3-6 | | | | |
| 3-7 | | | | |
| 4 | | | | |
| 5 | | | | |
| 6 | | | | |
| 7 | | | | |
| 8 | | | | |
| 9 | | | | |
| 10 | | | | |
| 11 | | | | |
| 12 | | | | |
| 13 | | | | |
| 14 | | | | |
| 15 | | | | |

---

# 마지막 원칙

> **한 Phase를 대충 넘기면 그 위에 쌓는 모든 것이 흔들린다.**
>
> 특히 **Phase 1(공통 기반)** 과 **Phase 3(인증)** 은 이후 전부가 의존한다.
> 여기서 시간을 아끼면 나중에 몇 배로 돌려준다.
>
> 그리고 **"돌아가니까 됐다"에서 멈추지 말 것.**
> 이 프로젝트의 산출물은 서버가 아니라 **설명할 수 있는 코드**다.
> 각 Phase의 "완료 체크"에 "~를 설명할 수 있다"가 들어 있는 이유가 그것이다.
