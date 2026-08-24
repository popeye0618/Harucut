# 부하 테스트 대상

각 항목은 **코드에서 확인한 실제 상태**를 근거로 한다. 추측이 아니다.

---

## 분류 — 절반은 부하가 필요 없다

목록을 정할 때 가장 먼저 정리한 것. **동시 사용자 수와 무관한 항목이 절반이다.**

| 항목 | 필요한 것 | 동시 사용자 |
|---|---|---|
| #2 세션 | 서버 2대 띄우기 | **2** (로그인 한 번) |
| #4 프레임 목록 | 프레임 500개 심기 | **1** |
| #5 UserMedia | 미디어 대량 심기 | **1** |
| #6 배치 | 갱신 대상 대량 심기 | **0** |
| #1 합성 | 큐 상한 낮추기 | **5** (아래 참조) |
| #3 커넥션 | — | 30~50 |
| #8 메일 | 느린 SMTP | 50~200 |
| #7 쿠폰 | 쿠폰 1개 | 500~1000 |

**#4·#5·#6은 "데이터 양" 문제**고, **#2는 "환경" 문제**다. 부하 도구가 필요 없다.
진짜 동시성이 필요한 건 #3·#7·#8뿐이고, 그것도 동시 1000 안쪽이다.

---

## #1 네컷 합성 — 큐 오버플로우로 Job이 영구 PENDING

### 코드 상태

[`AsyncConfig.java:19`](../src/main/java/com/harucut/config/AsyncConfig.java) — `core=max=2, queue=100`.

동시 103건이 들어오면 103번째는 `RejectedExecutionException`으로 거부된다.
문제는 **터지는 위치**다. `AFTER_COMMIT` 리스너 안에서 터지므로
[`ComposeWorker.java:24`](../src/main/java/com/harucut/media/compose/ComposeWorker.java)
**요청은 이미 200 응답을 받고 나갔다.**

사용자는 jobId를 받아 폴링을 시작하는데, 그 Job은 **영원히 PENDING으로 남는다.**

> `AsyncConfig.java:13` 주석에 "기다림이 유실이 되지는 않는다"고 적혀 있다.
> **큐 100을 넘으면 유실된다. 주석이 틀렸다.**

### 재현 방법 — 상한을 낮춰서 싸게

**결정: 100건을 실제로 요청하지 않는다.** 합성 1건마다 S3 I/O가 실제로 발생하고
(in-process 실행이라도) 1건당 힙 ~100MB를 문다. 100건은 요금도 들고 로컬도 죽는다.

대신 **테스트 프로파일에서 `queueCapacity`를 2로 낮춘다.**

```
core=2, max=2, queue=2  →  2개 실행 + 2개 대기 = 4건 수용, 5번째 거부
```

**동시 5건**이면 재현된다. 합성이 몇 초 걸리므로 5건을 빠르게 던지면 워커가
아직 안 끝난 상태가 만들어진다.

### 확인할 것

1. 5건 모두 **200 응답**을 받는가 (받아야 한다 — 그게 버그다)
2. 폴링했을 때 **PENDING으로 남는 Job이 있는가**
3. 그 시점에 서버 로그에 `RejectedExecutionException`이 찍히는가

### 볼 지표

- `executor.queued` / `executor.active` (Actuator 기본 제공)
- 힙 사용량 — 1건 ~100MB × 동시 2 = 200MB

### 재현 결과 (2026-08-19) — ✅ 재현됨

셋업은 이미지 없이 끝난다: 회원가입→로그인→**시스템 프레임 1개**(`background: COLOR`,
컴포넌트 0개 → 참조 자산 없음). 원본은 **없는 key** 를 보낸다 — 워커가 S3 404 로 실패하지만,
그 전에 이미 풀에 들어갔으므로 거부 여부와는 무관하다.

| 요청 | 결과 |
|---|---|
| 동시 30건 | **전부 202** — 실패로 보이는 응답이 하나도 없다 |
| 실행된 Job | 6건 → `FAILED` (S3 404 = 정상 경로) |
| **버려진 Job** | **24건 → `PENDING` 영구** |
| 서버 로그 | `RejectedExecutionException` **24건** |

폴링하면 `200 {"jobId":38,"status":"PENDING"}` 이 **영원히** 돌아온다.

#### 배운 것 1 — `curl ... &` 는 동시 요청이 아니다

같은 30건을 백그라운드 프로세스(`curl ... &`)로 던졌을 때는 **거부가 0건**이었다.
Windows 에서 프로세스를 30개 fork 하는 시간 때문에 요청이 1초 넘게 흩어진다.
작업 하나가 ~30ms 라 그 사이 큐가 계속 비워졌다.

**한 프로세스 안에서 쏴야 한다.**

```
curl -s --parallel --parallel-immediate --parallel-max 30 --config burst.conf
```

> conf 파일에서 요청마다 `next` 로 끊지 않으면 `header`·`data` 가 **누적되어**
> 한 덩어리로 합쳐진다 — Tomcat 이 400 을 뱉는다.

#3·#7·#8 도 같은 함정에 빠질 수 있다. **"동시"라고 생각한 게 동시인지 먼저 확인할 것.**

#### 배운 것 2 — 이 순간은 Grafana 에 안 잡힌다

```
min_over_time(executor_queue_remaining_tasks{name="composeTaskExecutor"}[5m]) = 2
```

큐가 실제로 가득 찼는데 **2(=여유 만석)로 남아 있다.** 버스트가 5초 스크레이프 간격
안에서 끝나 Prometheus 가 그 순간을 보지 못했다.

**증거는 그래프가 아니라 DB 상태와 로그였다.** 순간 이벤트는 메트릭으로 못 잡는다 —
메트릭이 유효하려면 부하가 스크레이프 간격보다 오래 지속돼야 한다.

#### 배운 것 3 — 이건 부하 문제가 아니다

큐에 쌓인 작업은 **메모리에만 있다.** 서버를 재시작하면 큐에 있던 Job 도 전부 똑같이
영구 PENDING 이 된다. → **배포할 때마다 재현된다.**
큐 오버플로우는 이 문제의 한 가지 발현일 뿐이고, 진짜 원인은
**"인메모리 큐에 넣고 잊었다"** 다.

### 수정 (2026-08-21) — ✅ 닫힘

**원인 진단**: 큐 상한이 작은 게 아니라 **할 일 목록이 두 군데(DB·인메모리 큐)에 있고
둘이 안 맞는 것**이 원인이다. 거부는 그 불일치를 만드는 한 가지 방법일 뿐이고,
**서버 재시작도 똑같은 불일치를 만든다** — 배포할 때마다.

`docs/decisions.md`(2026-08-17 네컷 합성)가 이미 "Job 테이블이 내구성 있는 큐가 된다"를
전제하면서 **"재실행 장치가 아직 구현되지 않았다"** 고 적어둔 그 구멍이다.

#### 넣은 것 셋

| | 무엇 | 덮는 것 |
|---|---|---|
| **graceful shutdown** | `server.shutdown: graceful` + executor 의 `waitForTasksToCompleteOnShutdown`·`awaitTerminationSeconds` | 정상 배포 — **예방** |
| **`started_at` 컬럼** | 조건부 UPDATE 로 선점 (`ComposeJobRepository.claim`) | 중복 실행 방지 |
| **재실행 스케줄러** | `ComposeRerunScheduler` — 버려진 PENDING 을 다시 큐에 넣는다 | 거부·비정상 종료 — **복구** |

기본값 `waitForTasksToCompleteOnShutdown = false` 일 때 `ExecutorConfigurationSupport`가
`shutdownNow()` 후 남은 작업을 전부 `cancel` 한다 — 배포 유실의 정확한 코드 위치가 거기다.

#### `started_at` 이 임의의 시간 기준을 없앤다

처음 안은 `created_at < 지금 - N` 이었는데, N 이 "합성이 보통 이만큼 걸린다"는 **추측**이었다.
`started_at` 을 기록하면 두 경우가 갈린다.

| 대상 | 조건 | 임의의 N 이 필요한가 |
|---|---|---|
| 아예 시작 못 함 (거부·재시작) | `PENDING AND started_at IS NULL` | **불필요 — 즉시 재시도** |
| 시작했는데 안 끝남 (실행자 사망) | `PENDING AND started_at < 지금 - N` | 필요하지만 **Lambda 타임아웃에서 유도**된다 |

우리가 재현한 26건은 전부 첫 번째였다. 그리고 `started_at` 은 **컬럼이지 상태가 아니라서**
`ComposeStatus`(PENDING/DONE/FAILED)와 API 응답 계약이 그대로다.

#### 실측 결과

| | 수정 전 | **수정 후** |
|---|---|---|
| 동시 요청 | 30 | 30 |
| HTTP 202 | 30 | 30 |
| **실제로 실행된 Job** | **4** | **30** (`started_at` 전부 채워짐) |
| **영구 PENDING** | **26** | **0** |

재실행 로그 14회(`[합성 재실행] 대상 2건 중 2건 재투입`). 큐 용량이 4(실행 2 + 대기 2)이므로
**최초 이벤트로 처리 가능한 건 최대 4건** — 나머지는 전부 스케줄러가 주웠다는 뜻이다.

> 최종 상태가 전부 `FAILED` 인 것은 원본 key 가 S3 에 없기 때문이고 의도된 것이다.
> **이 항목이 보는 것은 성공 여부가 아니라 "시도되었는가" 다.**

#### 남은 숙제

- **graceful shutdown 을 실측하지 못했다.** 없는 key 로는 작업이 30ms 만에 끝나 `docker stop`
  시점에 큐가 이미 비어 있다. 검증하려면 진짜 이미지로 수 초짜리 합성이 필요하다.
  다만 재실행 스케줄러가 생긴 뒤로는 **못 비운 것도 결국 주워지므로** 중요도가 내려갔다.
- `ComposeRerunScheduler` 테스트 없음. 검증 가치가 있는 건 **큐가 차면 `break` 로 멈추는 부분** 하나다.
- 재시도 횟수 상한 없음 — 의도적. 실패는 `FAILED` 가 되어 조회 대상에서 빠지므로 대부분의
  무한 루프가 저절로 끊긴다. 실제로 반복되는 게 보이면 그때 `retry_count` 를 넣는다.
- **거부 횟수 메트릭 없음.** 지금은 거부가 나도 조용히 복구될 뿐이라 운영자가 "풀을 키워야
  하나"를 판단할 근거가 없다. Micrometer 카운터 하나면 된다.

---

## #2 세션 — 서버 2대에서 소셜 로그인 실패

### 코드 상태

OAuth2 인가 요청 상태가 `HttpSession`에 저장된다(Spring Security 기본
`HttpSessionOAuth2AuthorizationRequestRepository`). 콜백이 다른 서버로 가면
그 서버 세션에는 그 상태가 없어 실패한다.

### 재현 방법

앱 컨테이너 2개 + 앞에 nginx 라운드로빈. **로그인 시도 한 번이면 실패한다.**

**이건 측정이 아니라 증명이다.** 부하 항목과 섞지 않는다.

### 같이 확인할 것 — 배치 중복 실행

서버를 2대로 늘리면 **`@Scheduled` 배치가 두 서버에서 동시에 돈다.**
결제가 두 번 나갈 수 있다. Spring Batch의 JobRepository가 같은 JobParameters면
중복을 막아주지만(`JobInstanceAlreadyCompleteException`), 두 인스턴스가 정확히
같은 순간에 시작하면 경합이 생긴다. **#2를 테스트할 때 반드시 같이 본다.**

### 수정 방향 (참고)

Redis가 이미 떠 있다. `spring-session-data-redis` 또는 쿠키 기반 repository.

---

## #3 DB 커넥션 10개

### 코드 상태

[`application-local.yaml`](../src/main/resources/application-local.yaml)에 datasource 튜닝이 없다
→ HikariCP 기본값 **10개**. Tomcat 스레드는 기본 **200개**.

스레드 200개가 커넥션 10개를 놓고 줄을 선다.

### 재현 방법

동시 30 → 50 → 100으로 올린다.

### 볼 지표

- `hikaricp.connections.pending` ← **0보다 크면 그게 커넥션 대기다**
- `hikaricp.connections.acquire` (획득 소요시간)
- 엔드포인트 응답시간, 에러율

### 찾을 것

**응답시간이 늘기만 하다가 어느 순간 에러로 바뀌는 지점.**
커넥션 대기 타임아웃(기본 30초)을 넘으면 500이 난다. **그 전환점이 기록할 값이다.**

### 참고 — 잘 되어 있는 부분

`open-in-view: false`가 이미 꺼져 있다. 켜져 있었으면 커넥션을 뷰 렌더링까지
물고 있어서 이 실험의 결과가 훨씬 나빴을 것이다.

---

## #4 프레임 목록 — 페이징이 "없는" 게 아니라 "못 넣는" 구조

### 코드 상태 — 진단이 처음 생각과 다르다

[`FrameRepository.java:17`](../src/main/java/com/harucut/frame/repository/FrameRepository.java)이
`left join fetch f.components`다.

**컬렉션 fetch join에는 페이징을 붙일 수 없다.** 붙이면 Hibernate가 전부 메모리로
읽어서 자른다 (`HHH000104` 경고). 즉 **"페이징을 안 넣은" 게 아니라 "이 구조로는 못 넣는다."**

페이징을 넣으려면 fetch join을 포기하고 다른 방식(`@BatchSize`, 2단계 조회)으로
가야 한다. **그게 이 항목의 진짜 트레이드오프다.** 부하로 확인할 게 아니라 구조 문제.

### 부하로 볼 것은 따로 있다

[`FrameService.java:47`](../src/main/java/com/harucut/frame/service/FrameService.java) —
**내 프레임 전부 + 시스템 프레임 전부**를 concat한다.
시스템 프레임이 500개면 **모든 사용자가 매 호출마다 500개를 받는다.**

### 재현 방법

시스템 프레임 500개(각 컴포넌트 10개) 심고 단건 호출 → 응답 크기·시간 측정
→ 그다음 동시 20.

---

## #5 UserMedia — "인덱스가 없다"는 정확히는 틀렸다

### 코드 상태

`user_id`는 [`UserMedia.java:20`](../src/main/java/com/harucut/media/entity/UserMedia.java)의
`@JoinColumn` FK다. **MySQL InnoDB는 FK를 만들면 인덱스를 자동으로 만든다.**
그러니 인덱스는 있다.

**진짜 문제는 복합 인덱스가 없다는 것.**

쿼리가 `WHERE user_id=? ORDER BY created_at DESC LIMIT ?`인데,
`user_id` 단독 인덱스면 **그 유저의 행을 전부 읽어서 정렬한다**(filesort).
`(user_id, created_at)` 복합이면 정렬 없이 앞에서 N개만 읽고 멈춘다.

### 추가로 볼 것

1. **count 쿼리** — `Page`를 반환하므로 매 요청마다 count가 같이 나간다
   ([`UserMediaRepository.java`](../src/main/java/com/harucut/media/repository/UserMediaRepository.java))
2. **presigned URL 3개/건** —
   [`UserMediaService.java:90`](../src/main/java/com/harucut/media/service/UserMediaService.java)
   `toResponse`가 썸네일·조회·다운로드 URL을 각각 만든다.
   페이지 20개면 **요청당 서명 60번**. 네트워크는 안 타지만(HMAC 계산) CPU를 쓴다

### 재현 방법

한 유저에게 미디어 대량 심기 → `EXPLAIN`으로 filesort 확인 → 복합 인덱스 추가 후 재측정.

**인덱스를 배우기엔 이 케이스가 제일 좋다.** 전후 차이가 극적으로 나온다.

---

## #6 정기결제 배치 — 두 겹이다

### 첫 겹: chunk(1) + 건별 동기 PG 호출

[`SubscriptionRenewalJobConfig.java:53`](../src/main/java/com/harucut/payment/batch/SubscriptionRenewalJobConfig.java)
이 `chunk(1)`이다. **건당 트랜잭션.**
그리고 청구 스텝은 건별로 **PG를 동기 호출**한다
([`RenewalChargeService.java:24`](../src/main/java/com/harucut/payment/batch/RenewalChargeService.java)).

PG 응답이 200ms면 10만 건은 **5시간 반**이다. 새벽 2시 시작 → 아침 7시 반 종료.

### 둘째 겹: 스케줄러 스레드가 1개 ← 이게 더 크다

[`SchedulingConfig.java`](../src/main/java/com/harucut/config/SchedulingConfig.java)에
`@EnableScheduling`만 있고 `TaskScheduler` 빈이 없다.
**Spring 기본 스케줄러는 스레드가 1개다.**

배치가 셋인데 한 스레드에서 순서대로 돈다.

| 배치 | cron |
|---|---|
| 회원 탈퇴 | `0 0 1 * * *` |
| 구독 갱신 | `0 0 2 * * *` |
| 구독 만료 | `0 30 2 * * *` |

**2:00 갱신이 5시간 걸리면 2:30 만료 배치는 7시에 시작한다.**
조금 밀리는 게 아니라 그날 일정이 통째로 어긋난다.

### 재현 방법

갱신 대상 대량 심고 배치 1회 실행. **동시 사용자 0명.**

### 볼 지표

- 스텝별 소요시간
- **그 시간대의 DB 커넥션** ← 배치가 커넥션을 먹으면 같은 시각 일반 요청이 굶는다.
  5시간이면 아침 출근 시간과 겹친다

---

## #7 쿠폰 선착순 — 락의 정체를 정확히 알고 갈 것

### 코드 상태

**명시적 락이 없다.** `@Lock`도 비관적 락도 안 쓴다.

[`CouponRepository.java:22`](../src/main/java/com/harucut/coupon/repository/CouponRepository.java)의
`tryIncrementRedeemedCount`가 **조건부 UPDATE**다. 원자적이라 **상한은 안 깨진다.**
`UserCoupon`에 UNIQUE 제약도 걸려 있어 중복 발급도 DB가 막는다. **잘 짠 부분이다.**

### 그럼 무엇이 문제인가

**그 UPDATE가 InnoDB 행 락을 잡고, 락은 커밋까지 유지된다.**

[`CouponService.java:63`](../src/main/java/com/harucut/coupon/service/CouponService.java)에서
UPDATE한 뒤에도 subscription 조회 → 없으면 저장 → 예약/즉시적용 분기가 이어진다.
**그 시간 내내 락을 쥐고 있다.**

선착순이면 전부 **같은 쿠폰 행**을 노리므로 완전히 직렬화된다.
**락 보유 시간 = UPDATE 이후 커밋까지의 모든 작업 시간.**

### 재현 방법

쿠폰 1개(상한 100), 동시 1000명 클레임.

### 확인할 것

1. **발급 수가 정확히 100인가** — 정합성
2. **p99 응답시간** — 뒤에 선 사람이 얼마나 기다리나
3. **락 대기 타임아웃**(InnoDB 기본 50초) 초과 에러가 있나

**정합성과 성능을 한 번에 볼 수 있는 유일한 항목이다.**

---

## #8 이메일 발송이 요청 스레드를 붙잡는다

### 코드 상태

[`MailService.java:37`](../src/main/java/com/harucut/common/mail/MailService.java) —
`@Async`가 없다. **동기 호출이다.**

[`application.yaml`](../src/main/resources/application.yaml)의 타임아웃이
연결·읽기·쓰기 각 5초 → **최악 15초 동안 요청 스레드를 붙잡는다.**

### 재현 방법 — 로컬 Mailpit로는 재현이 안 된다

Mailpit이 너무 빠르다. **의도적으로 지연을 만들어야 한다.**
Mailpit을 내려서 연결 타임아웃을 유도하거나, 지연 주입 프록시를 물린다.

### 볼 것 — 여기가 핵심

**메일과 무관한 엔드포인트의 응답시간.**

그게 같이 느려지면 스레드 풀 고갈이 증명된 것이다.
메일 API만 느린 게 아니라 **서버 전체가 죽는다**는 게 이 항목의 요지다.

---

# 목록 밖에서 발견한 것

## (a) 합성 큐 오버플로우 → #1로 승격

가장 심각하다. 사용자는 성공했다고 믿는데 결과가 안 나온다.

## (b) 배치 vs API의 커넥션 경쟁

커넥션 10개를 배치와 일반 요청이 나눠 쓴다. #3·#6을 같이 볼 때 확인.

## (c) 다중 인스턴스 배치 중복 실행

#2에서 같이 확인. 위에 적었다.

## (d) S3 삭제와 DB 삭제의 원자성

회원 탈퇴 시 S3 키를 모아 지우는데, S3 삭제가 실패하면 DB는 지워지고 S3엔 파일이 남는다.
트래픽 문제는 아니지만 대량 탈퇴에서 드러난다.

## (e) `ddl-auto: update` — 부하 테스트 환경에서 위험

[`application-local.yaml:9`](../src/main/resources/application-local.yaml).
대량 데이터가 들어간 상태에서 스키마가 바뀌면 **기동 시 ALTER TABLE이 몇 분간 걸린다.**
부하 테스트 프로파일에서는 `validate`로 둔다. → [02-environment.md](02-environment.md)

## (f) 잘 되어 있는 부분

- `open-in-view: false` — 커넥션 수명이 짧다
- 쿠폰의 조건부 UPDATE + UNIQUE 제약 — 정합성이 DB에서 보장된다
- 합성이 `@Async` + `AFTER_COMMIT` — 요청 스레드를 즉시 놓아준다 (큐 상한만 문제)
