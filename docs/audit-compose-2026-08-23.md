# Harucut 네컷 합성 파이프라인 — 최종 감사 보고서

**감사 전제:** 100만 사용자 규모. 지금 트래픽이 작아서 안 터진다는 건 변호가 아니다.
**대상:** `compose` 파이프라인 전체 (서버 + compose-core + compose-lambda + 인프라 문서)
**날짜:** 2026-08-23

---

## 1. 한 줄 결론

> **"요청이 오면 즉시 Lambda를 부른다"는 이 파이프라인의 핵심 경로가 지금 100% 죽어 있다.** 모든 합성이 30초 주기 재실행 배치로만 투입되고, 그 배치의 상한은 인스턴스당 분당 40건이다. 100만 사용자가 하루 5%만 합성하면 5만 건/일인데 단일 인스턴스 상한이 5.76만 건/일이라, 저녁 피크에서 큐가 회복 불가능하게 쌓인다.

이 버그 하나가 나머지 문제 6개를 동시에 가리고 있다. 고치는 순간 숨어 있던 위험(요청 스레드가 최악 128초 붙잡힘, 선점 후 10분 동결)이 살아나므로, **1번과 함께 3번·5번을 한 묶음으로 고쳐야 한다.**

---

## 2. 지금 당장 고쳐야 하는 것

### C-1. AFTER_COMMIT 리스너의 `claim()`이 매번 예외로 죽는다 — 즉시 접수 경로가 통째로 동작 안 함

**위치:** `src/main/java/com/harucut/media/compose/ComposeWorker.java:43` → `src/main/java/com/harucut/media/service/ComposeService.java:107`

**무엇이 / 언제 터지나**
`ComposeWorker.handle`은 `@TransactionalEventListener(AFTER_COMMIT)`다. 스프링은 이 단계를 `afterCommit()`이 아니라 `TransactionSynchronization.afterCompletion(STATUS_COMMITTED)`에서 실행한다(spring-tx 7.0.8 `TransactionalApplicationListenerSynchronization.PlatformSynchronization`, 소스 확인). 이 시점에 `EntityManagerHolder`가 아직 스레드에 묶여 있고 `transactionActive=true`라, 여기서 부른 `@Transactional claim()`은 **새 트랜잭션을 못 열고 "이미 커밋된" 트랜잭션에 참여한다.** Hibernate는 커밋된 세션의 벌크 UPDATE를 거부한다.

이 저장소에서 프로브 테스트를 만들어 직접 재현했다(확인 후 삭제):

```
### PROBE1 startedAt(after AFTER_COMMIT listener) = null
### PROBE1 executor.execute CALLED = NO (WantedButNotInvoked)
### PROBE1 claimError = InvalidDataAccessApiUsageException:
          TransactionRequiredException: No active transaction for update or delete query
### PROBE2 claim outside tx = true / startedAt=2026-08-23T21:20:19.841
### PROBE3 claim inside tx  = true
```

예외가 호출자에게 안 올라오는 이유도 확인했다 — `TransactionSynchronizationUtils.invokeAfterCompletion`이 `catch(Throwable){logger.error(...)}`로 삼킨다. **그래서 202는 그대로 나가고, 실패는 어떤 지표에도 안 잡힌다.** 게다가 `claim()` 호출은 `ComposeWorker`의 try 블록 **밖**(43행)이라 워커의 방어 주석(50~57행)이 이 예외를 전혀 다루지 못한다.

반증 시도 4가지가 전부 막혔다:
| 반증 가설 | 결과 |
|---|---|
| 리스너가 비동기로 돈다 | `@EnableAsync`·`applicationEventMulticaster` 커스터마이징 grep 0건 |
| `claim`에 REQUIRES_NEW | `ComposeService.java:35` 클래스 레벨 `@Transactional`만. REQUIRES_NEW는 payment 패키지 전용 |
| `hibernate.allow_update_outside_transaction=true` | `application*.yaml` 4개 어디에도 없음 |
| 스프링 소스가 다르다 | gradle 캐시 `spring-tx-7.0.8-sources.jar` 직접 확인 |

**수치 영향**
- 모든 합성이 **0~30초(평균 15초) 늦게 시작**된다. `ComposeJobRepository.java:43`의 `findStalled`가 `started_at IS NULL` 가지에 시간 조건이 없어 방금 만든 Job을 바로 주기 때문에, 30초 주기 배치가 유일한 투입구가 된다.
- 시스템 전체 투입 상한이 **재실행 배치 하나(30초당 20건 = 분당 40건 = 5.76만 건/일)** 로 고정된다.
- `docs/measurement-2026-08-23.md`의 "개선 후 30건 53초"도 이 설명과 일치한다 — 30건은 batch-size 20으로 두 주기가 필요하고 그게 정확히 +30초다(개선 전 스레드2는 30초, 스레드20은 10초였다).

**왜 테스트가 못 잡았나:** `ComposeWorkerTest.java:43-44`가 `@Mock private ComposeService composeService`라 `claim()`이 항상 true를 돌려준다. `ComposeJobRepositoryTest`에는 `claim`/`findStalled` 테스트가 아예 없다. 즉 **구조적으로 못 잡는다.**

**어떻게 고치나**

```java
// 최소 수정 — ComposeService.claim
@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean claim(Long jobId, Duration staleAfter) {
    LocalDateTime now = LocalDateTime.now(clock);
    return composeJobRepository.claim(jobId, now, now.minus(staleAfter)) == 1;
}
```

```java
// 더 나은 수정 — 리스너를 요청 스레드에서 떼어낸다 (C-5도 같이 해결된다)
@Bean("composeExecutor")
public TaskExecutor composeTaskExecutor() {
    ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
    e.setCorePoolSize(4); e.setMaxPoolSize(8); e.setQueueCapacity(200);
    e.setThreadNamePrefix("compose-submit-");
    return e;
}

@Async("composeExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handle(ComposeRequestedEvent event) { execute(event); }
```

**반드시 함께:** 실제 트랜잭션 경계를 타는 통합 테스트 1개를 추가한다(→ H-6).

---

### C-2. 소유권 검사가 3곳에 있는데 프레임 경로에서 빠졌고, 원본 검사는 폴더를 안 본다 — 남의 사진을 읽고 지울 수 있다

세 개의 별개 구멍이지만 원인이 같다: **소유권 판정 규칙이 코드 4곳에 복제돼 있고 통일된 함수가 없다.**

#### (a) 프레임 자산 key에 소유자 검사가 없다 — 남의 파일 읽기/반출/삭제

**위치:** `src/main/java/com/harucut/media/compose/ComposeSpecAssembler.java:49`, `:32`

합성의 자산 관문은 `if (!S3Keys.isManagedKey(source))` 하나뿐이고, `isManagedKey`는 `key.startsWith("uploads/")`만 본다(`S3Keys.java:19-21`). 즉 **"우리 버킷인가"만 보고 "이 사용자 것인가"는 안 본다.**

저장 경로에도 검사가 없다: `FrameComponentAssembler.java:33` → `FrameAssetManager.java:23-28 normalizeSource`는 `S3Keys.normalizeManagedKey(source)` 한 줄이 전부다. `previewKey`(`FrameComponentAssembler.java:91,115`)·background key(`:74`)도 같다. `FrameCreateRequest.java:85-91`의 `source`는 `@NotBlank`뿐이다.

**대조군이 명확하다.** 같은 프로젝트 세 곳은 전부 소유권을 강제한다 — `FileController.java:90`, `UserService.java:49`, `ComposeService.java:142`가 모두 `startsWith(S3Keys.userRoot(publicId))`다. `FileController`의 Swagger는 "남의 key는 403"이라고 못박고 있다. **프레임만 이 규칙에서 빠졌다.**

**공격 시나리오 (3갈래로 터진다)**
1. 결과 key는 `ComposeService.java:130`에서 `uploads/users/{publicId}/fourcuts/job-{jobId}.png`로 결정적이고 jobId는 순차 Long이다. publicId 하나만 알면 피해자의 완성 네컷 key를 1번부터 열거할 수 있다(공유된 presigned URL 경로에 publicId가 그대로 들어 있다).
2. `POST /api/auth/user/frames`로 `{"type":"PHOTO","source":"uploads/users/{피해자}/fourcuts/job-137.png"}` 프레임을 만든다. 검증 없이 저장된다.
3. 결과:
   - **읽기** — `GET /frames/{id}` 응답 조립 시 `FrameAssetManager.java:52-56 presignIfManaged`가 `uploads/`로 시작하기만 하면 무조건 presigned GET URL을 붙인다. **합성을 돌릴 필요조차 없다.**
   - **반출** — 그 프레임으로 합성하면 `ComposeHandler.java:55`가 Lambda 실행 역할(`uploads/*` 전체 권한)로 피해자 파일을 내려받아 공격자 결과물에 그린다.
   - **삭제** — 프레임을 DELETE 하면 `FrameService.java:96` → `collectAllKeys` → `S3DeleteListener.java:32-42`가 실제로 지운다. **S3 삭제는 롤백이 없다.**

#### (b) 원본 key 검사가 폴더를 안 본다 — 사용자가 자기 프로필/프레임 자산을 스스로 지운다

**위치:** `src/main/java/com/harucut/media/service/ComposeService.java:142`

```java
String root = S3Keys.userRoot(publicId);          // = "uploads/users/{publicId}/"
if (!sourceKey.startsWith(root)) { throw ... }    // 폴더를 안 본다
```

그 prefix 아래에 업로드 전략 4개의 폴더가 전부 산다 — `profile/`(ProfileUploadPathStrategy), `frames/`, `components/`(FrameComponentUploadPathStrategy), `fourcuts/sources/`(원본). 결과물(`fourcuts/job-N.png`)도 같은 root다. **"원본 사진인가"는 어디서도 검사되지 않는다.** 그리고 성공하면 `ComposeService.java:100`이 그 key들을 실제로 지운다.

실패 시나리오: 사용자가 `sourceKeys`에 `["uploads/users/Ab12/components/스티커.png", 정상 원본 3장]`을 넣으면 403이 아니라 202가 나가고, Lambda가 스티커를 사진처럼 그려 성공시킨 뒤 `completeJob`이 그 4개를 지운다. `frame_component.source` 행은 남아 있으므로 **그 프레임을 쓰는 이후 모든 합성이 S3 NoSuchKey → RetriesExhausted → FAILED**가 되고, 프레임 편집 화면의 presigned URL도 전부 404다. 복구 경로가 없다 — S3 버전 관리 설정이 코드·문서 어디에도 없다.

**수치:** 프론트가 key 배열을 잘못 조립하는 버그 하나면 배포 즉시 전 사용자에게 동시 발생한다. 하루 100만 건(11.6건/s) 기준 잘못된 요청이 1%만 섞여도 **하루 1만 명의 자산이 영구 삭제**되고, 에러율·지연 지표는 전부 정상이라 탐지되지 않는다.

#### (c) 정규화 부재 — `..`·중복 슬래시를 안 거른다 (LOW, 같이 고침)

`uploads/users/{내}/../{피해자}/fourcuts/job-1.png`가 `startsWith`를 통과한다. 오늘은 S3가 key를 불투명 문자열로 다루므로 NoSuchKey로 끝나지만, SDK나 프록시가 dot segment를 정규화하는 구성으로 바뀌면 GetObject뿐 아니라 **삭제**까지 간다.

> 참고 — 검증 중 배제한 오탐: publicId prefix 충돌은 성립하지 않는다(`S3Keys.java:14` userRoot가 끝에 `/`를 붙이고 `PublicIds.java:7-9`가 길이 12 고정 `[0-9A-Za-z]`). URL 감싸기도 안전하다(`normalizeToKey`가 path만 뽑고 버킷은 서버가 정한다).

**어떻게 고치나 — 규칙을 한 곳으로 모은다**

```java
// S3Keys — 단 하나의 소유권 판정 함수
public static void assertOwnedBy(String rawKey, String publicId) {
    String key = normalizeToKey(rawKey);
    if (key.contains("..") || key.contains("//") || key.contains("\\")) {
        throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }
    if (!key.startsWith(userRoot(publicId)) && !key.startsWith(SYSTEM_ROOT)) {
        throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }
}

// ComposeService.validateSourceOwnership — 원본 폴더까지 좁힌다 (한 줄)
String root = S3Keys.userRoot(publicId) + "fourcuts/sources/";
```

`FourcutSourceUploadPathStrategy.java:22`가 이미 정확히 그 경로만 발급하므로 정상 요청은 하나도 안 막힌다. 그리고 `FrameService.createFrame/updateFrame`이 `previewKey`·`background.key`·`components[].source`·`renderedKey` 전부에 `assertOwnedBy`를 걸고, `FileController`·`UserService`·`ComposeService`도 같은 함수를 부르게 한다. 시스템 프레임 공용 자산은 `uploads/system/`으로 분리해 허용 목록에 넣는다. 테스트로 "남의 key 프레임 생성 → 403"을 고정한다.

---

### C-3. 픽셀 수 상한이 없다 — 1.16MB짜리 PNG 한 장으로 Lambda를 OOM으로 죽인다

**위치:** `compose-core/.../FourcutRenderer.java:213` (근본), `compose-lambda/.../ComposeHandler.java:78` (호출부)

**무엇이**
방어선은 `PresignedUploadRequest.java:44` `@Max(MAX_FILE_SIZE=10MB)` 하나뿐인데, 이건 **바이트 수**만 막고 **픽셀 수**를 못 막는다. 서버는 presigned 직행 업로드라 원본 픽셀을 한 번도 안 본다(`ComposeService.requestCompose:58-63`은 key 소유권만 검사). `ComposeHandler.java:78`이 통째로 힙에 올리고 `FourcutRenderer.java:213`이 크기 확인 없이 `ImageIO.read`한다.

**실측 (Temurin 17로 직접 재현)**
| 항목 | 값 |
|---|---|
| 20000×20000 단색 PNG 파일 크기 | **1,213,109바이트 (10MB 한도의 11.6%)** |
| `-Xmx512m` 디코드 | `IIOException` → `OutOfMemoryError` (PNGImageReader.readImage) |
| `-Xmx2048m` 디코드 성공 시 래스터 | type=5(TYPE_3BYTE_BGR), 4억 px × 3B = **1,144MB** (RGBA면 1,526MB) |
| `MaxRAMPercentage` 기본값 | **25.0 {default}** → 2048MB 함수의 힙은 512MB |
| **헤더만 읽어 크기를 아는 비용** | **113마이크로초** |

**증폭 요인 2개**
1. `POST /api/auth/user/media/compose`에 **레이트리밋이 없다**(`grep -rn "RateLimit|bucket4j|Throttl" src/` → `EmailRateLimit`(회원가입 메일용)뿐). 같은 요청을 무한히 넣을 수 있다.
2. **악의 없이도 터진다.** 10MB 이하 JPEG으로도 1억 화소(12000×9000급 폰 사진)를 만들 수 있고, 3B/px면 324MB, 4장이면 1.3GB로 512MB 힙을 넘는다.

**실패 후 원본이 안 지워진다.** `ComposeService.failJob`(`:103-105`)은 `job.fail(reason)`만 하고 `s3Deleter`를 안 부른다 — 삭제는 `completeJob:100` 경로에만 있다. **즉 같은 폭탄 key를 무한 재사용할 수 있다.**

**수치 정정 (검증 결과)**
- ~~"0.371MB"~~ → 재현값 1.16MB. 인코더 차이. 어느 쪽이든 한도의 4~12%라 결론 동일.
- ~~"10MB 예산으로 10만×10만"~~ → 실제 상한은 약 4만×4만(1.6기가픽셀, 약 4.6MB). 이것만으로 4.8~6.4GB 힙이 필요해 어떤 Lambda 설정으로도 못 버틴다.
- ~~"OOM이 실행 환경을 폐기시켜 이후 전부 콜드 스타트"~~ → 근거 없음. 컨테이너 메모리 초과가 아니라 **JVM 힙 OOM**이라 런타임은 함수 에러로 보고하고 환경을 재사용하는 게 보통이다. 동시성 슬롯 점유와 3회 과금은 그대로 사실.
- 비용 상한: Lambda 슬롯 1개가 태울 수 있는 최대는 2GB × $0.0000166667 × 3600 = **시간당 $0.12**. 계정 한도 1000이면 시간당 $120, 이 계정 실제 한도 10(`docs/measurement-2026-08-23.md:61`)이면 시간당 $1.2. **돈보다 가용성이 문제다.**

**어떻게 고치나**

```java
// FourcutRenderer.decode — 서버·Lambda 양쪽에 자동 적용된다
private static final long MAX_PIXELS = 50_000_000L;   // 4장 전부 최대여도 힙 800MB 이내

private BufferedImage decode(byte[] bytes) {
    try (ImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        if (!readers.hasNext()) throw new IllegalArgumentException("이미지 형식을 인식할 수 없다");
        ImageReader reader = readers.next();
        reader.setInput(iis);
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);   // 113μs
        if (pixels > MAX_PIXELS) {
            throw new IllegalArgumentException("이미지가 너무 큽니다: " + pixels + "px");
        }
        return reader.read(0);
    }
}
```

더 앞단에서 막으려면 업로드 완료 시 S3 이벤트나 원본 등록 API에서 헤더만 읽어 검증한다 — 그래야 사용자가 합성 요청 **전에** 400을 받는다. 그리고 해상도 초과는 재시도해도 똑같이 실패하므로, Lambda가 별도 errorType으로 던져 소비자가 재시도 없이 즉시 `failJob` 하게 만든다(현재는 3회 태운다).

---

### C-4. `compose_job`에 정리 배치가 없다 — 1년에 3.65억 행 · 약 0.9TB, 그 99.99%는 아무도 안 읽는다

**위치:** `src/main/java/com/harucut/media/entity/ComposeJob.java:22` (주석이 스스로 인정)

**무엇이**
엔티티 주석이 이미 문제를 인지한다: *"이 테이블은 단조 증가한다 — 완료된 Job을 지우는 배치가 없다(deleteByUserId는 탈퇴용)."* 삭제 경로를 전수 확인했다:
- `ComposeJobRepository.java` 전체 47줄 중 삭제 쿼리는 `:26-27 deleteByUserId` 하나 (UserDeletionJob 전용)
- `src/main/java` 전체 `@Scheduled`는 정확히 4개 — 탈퇴 01:00(`UserDeletionScheduler.java:28`), 구독 갱신 02:00(`SubscriptionRenewalScheduler.java:25`), 구독 만료 02:30(`SubscriptionExpirationScheduler.java:26`), 합성 재실행 30초(`ComposeRerunScheduler.java:39`). **compose_job을 지우는 건 없다.**

**방어 판정: 불충분.** 주석의 결론이 "인덱스를 미리 걸어둔다"인데, 인덱스는 조회 비용을 낮출 뿐 행 수를 안 줄인다. 오히려 인덱스가 커져 buffer pool을 잠식한다.

**행 크기 재계산 (InnoDB · utf8mb4)**
| 항목 | 바이트 |
|---|---|
| BIGINT 4개 (job_id, user_id, frame_id, media_id) | 32 |
| status + idempotency_key(UUID 36자) | 44 |
| source_key ×4 — 실제 경로 `uploads/users/{12}/fourcuts/sources/{36}.png` = 84자, VARCHAR(512) utf8mb4는 길이 바이트 2 → 86B × 4 | **344** |
| result_key + failure_reason(대부분 NULL) | 46 |
| datetime(6) × 3 (Hibernate 6에서 8B) | 24 |
| InnoDB 행 헤더 + trx_id + roll_ptr | 34 |
| **spec_json 제외 고정분** | **524** |

`spec_json` 가정에 따라 총량이 3배 폭으로 갈린다:
| 프레임 유형 | 행 크기 | 3.65억 행 |
|---|---|---|
| 컴포넌트 없는 최소 프레임 (spec ~300B) | 824B | **301GB** |
| 스티커·텍스트 10개 실제 프레임 (spec ~1.8KB) | 2,324B | **848GB** |
| + 인덱스 2개(uk 22GB, idx 11GB, 채움률 ×1.4) | | +46GB |
| **합계 (실제 프레임 기준)** | | **약 0.9TB / 년** |

**계산 근거:** 100만 사용자 × 하루 1건 = 하루 100만 행 × 365일 = 3.65억 행.

**이 0.9TB의 용도:** 조회 경로는 셋뿐이다 — `findByIdAndUser`(폴링, 합성 직후 15초), `findByUserAndIdempotencyKey`(요청 직후), `findStalled`(PENDING만). **DONE 행은 그 뒤 영원히 아무도 안 읽는다.** 24시간 지난 DONE이 전체의 99.99%다.

**붕괴 지점**
- buffer pool 64GB(db.r6g.2xlarge) 기준 데이터 840GB는 13배 초과. `findStalled`의 인덱스 페이지조차 상주 못 해 30초마다 디스크 랜덤 I/O가 나고, 같은 DB의 폴링 조회(피크 347 QPS)의 p99가 함께 튄다.
- 스토리지: RDS gp3 $0.114/GB-월 × 900GB = **월 $103**, 백업(7일 보존)까지 치면 실질 2배.
- **운영 불가능성이 더 크다.** 900GB 테이블에 `ALTER TABLE`을 걸면 온라인 DDL이라도 수 시간이고 gh-ost 없이는 사실상 못 한다. **정리 배치가 없다는 결정이 스키마를 얼려버린다.**

**어떻게 고치나 (지금 테이블이 작을 때 결정해야 한다)**

```sql
-- 방법 1: 보존 배치 (한 번에 다 지우면 언두 로그 폭발 + 복제 지연)
DELETE FROM compose_job
WHERE status IN ('DONE','FAILED') AND created_at < NOW() - INTERVAL 30 DAY
LIMIT 1000;   -- 루프
-- 필요 인덱스: (status, created_at). 기존 (status, started_at)은 못 쓴다.
-- 30일 보존이면 3,000만 행 · 70GB에서 평형.

-- 방법 2 (권장): 파티셔닝 — 언두 로그·복제 지연·인덱스 단편화가 전부 없다
ALTER TABLE compose_job PARTITION BY RANGE (TO_DAYS(created_at)) (...);
ALTER TABLE compose_job DROP PARTITION p20260701;
-- ⚠️ 파티션 키가 PK에 포함돼야 하므로 PK를 (compose_job_id, created_at)으로 바꿔야 한다.
--    지금이 아니면 못 한다.
```

**추가 절감:** `spec_json`은 Job마다 프레임 스냅샷 전체를 복사한다. spec 해시를 키로 하는 `compose_spec` 테이블을 두고 compose_job은 id만 들면 행이 2.3KB → 0.5KB, **840GB → 180GB**가 된다. 스냅샷 불변성은 해시 기준이라 그대로 지켜진다.

**⚠️ 배치 추가 시 주의:** `spring.task.scheduling.pool.size=4`가 5개를 물게 된다. 풀을 6으로 올리고 추가할 것.

---

### C-5. LambdaClient가 SDK 기본 타임아웃을 그대로 쓴다 — 한 요청이 최악 128초를 붙잡는다

**위치:** `src/main/java/com/harucut/config/ComposeConfig.java:12-17`

`LambdaClient.builder().region(...).build()`가 전부다 — `overrideConfiguration`도 `apiCallTimeout`도 `apiCallAttemptTimeout`도 `httpClientBuilder`도 없다.

**gradle 캐시의 실제 jar를 바이트코드로 확인한 기본값:**
```
http-client-spi-2.25.67.jar / SdkHttpConfigurationOption 정적 초기화:
  DEFAULT_SOCKET_READ_TIMEOUT        = Duration.ofSeconds(30)
  DEFAULT_CONNECTION_TIMEOUT         = Duration.ofSeconds(2)
  DEFAULT_CONNECTION_ACQUIRE_TIMEOUT = Duration.ofSeconds(10)
  MAX_CONNECTIONS                    = bipush 50
sdk-core-2.25.67.jar / SdkDefaultRetrySetting$Legacy:
  MAX_ATTEMPTS  ConstantValue: int 4       (RetryMode 기본 fallback = LEGACY)
lambda-2.25.67.jar / DefaultLambdaBaseClientBuilder:
  apiCallTimeout 기본값 주입 코드 grep 0건 — 서비스 차원 보호막도 없다
```

→ **4회 시도 × (연결 2초 + 소켓 읽기 30초) ≈ 128초** (+ 재시도 백오프)

**수치 영향**
`ComposeWorker.java:13` 주석의 "비동기 invoke가 수십 ms에 끝나므로 요청 스레드에서 그대로 부른다"는 **정상 경로 값이지 상한이 아니다.** AFTER_COMMIT 리스너는 컨트롤러가 반환하기 전에 돌아 실제로 HTTP 요청 스레드를 붙잡는다. Tomcat 스레드 수 설정이 yaml에 없어 기본 200 — 피크 200 RPS에서 합성이 5%면 초당 10건, **128초 × 10건/s면 20초 만에 200 스레드가 소진**된다. 합성 기능 하나의 외부 장애가 로그인·결제까지 포함한 서비스 전면 장애로 번진다.

**지금은 C-1 버그에 가려져 있다** — 프로브에서 `executor.execute`가 아예 호출되지 않았다. **C-1을 고치는 순간 이 위험이 그대로 살아난다.**

```java
@Bean
public LambdaClient lambdaClient(AwsProperties props) {
    return LambdaClient.builder()
        .region(Region.of(props.region()))
        .overrideConfiguration(c -> c
            .apiCallTimeout(Duration.ofSeconds(5))
            .apiCallAttemptTimeout(Duration.ofSeconds(2))
            .retryPolicy(RetryPolicy.builder().numRetries(2).build()))
        .build();
}
```
`S3Client`·`SqsClient`도 같은 기본값을 쓰므로 함께 점검한다.

---

### H-1. 통지 소비자 = 스레드 1개 + 동기 S3 삭제 4회 — 인스턴스당 10~18건/s가 상한

> 원래 4개 지적(consumer-sync-s3-delete / consumer-single-thread-inline-s3 / single-thread-consumer-rerun-amplification / delete-message-not-batched)을 하나로 병합했다.

**위치:** `src/main/java/com/harucut/media/service/ComposeService.java:100` → `S3DeleteListener.java:21-31` → `S3FileStorageService.java:94-96`

**무엇이 — 이 결합은 소비자 코드만 읽으면 안 보인다**
```
ComposeResultConsumer (전용 단일 스레드, :84 newSingleThreadExecutor)
  └ pollOnce (:140) 10건 순차 루프
      └ completeJob  @Transactional
          └ s3Deleter.deleteAfterCommit(job.sourceKeys())   ComposeService.java:100
              └ S3DeleteListener  @TransactionalEventListener(AFTER_COMMIT), @Async 없음
                  └ for (key : keys) fileStorageService.delete(key)   ← DeleteObjects 배치가 아니라 단건 4회
```
저장소 전체 grep 결과 `@EnableAsync`·`@Async`·`TaskExecutor` 빈이 **하나도 없다**(유일한 히트는 `ComposeWorker.java:55`의 "@Async가 없어진 뒤로"라는 과거형 주석). 따라서 리스너는 커밋한 스레드 = `compose-result` 단일 스레드 위에서 돈다. `sourceKeys()`는 항상 4개(`ComposeJob.java:137-139`)이고 `isManagedKey`가 4개 전부 통과시킨다 — 걸러지는 게 없다.

**통지 1건의 비용 분해 (같은 리전 EC2, p50)**
| 단계 | 시간 |
|---|---|
| completeJob 트랜잭션 (커넥션 + SELECT + INSERT + UPDATE + COMMIT) | 5ms |
| **S3 DeleteObject × 4 (각 15ms)** | **60ms ← 전체의 79%** |
| SQS deleteMessage (단건, `:152`) | 10ms |
| receiveMessage 10건 배치 상환분 | 1ms |
| **합계** | **76ms → 인스턴스당 13.2건/s** |

민감도: S3 DELETE 10ms면 56ms/17.9건/s, 20ms면 96ms/10.4건/s. **13.2는 ±40% 폭을 가진 점추정이다.**

**ADR-0003 §8의 근거가 틀렸다.** "처리 동시성 순차 1건 / 통지 처리가 DB 트랜잭션 하나라 짧다"라고 적었지만 **AFTER_COMMIT의 S3 4회를 계산에 넣지 않았다.** 실제로는 DB가 아니라 S3 왕복이 지배한다. `ComposeResultConsumer.java:53`의 "처리에 외부 호출이 붙는 날 이 값을 다시 봐야 한다"가 이미 발동했는데 `VISIBILITY_TIMEOUT_SECONDS=30`은 그대로다.

**과장 정정 (검증 결과)**
- ~~"백로그가 SQS 4일 보존을 넘어 통지 소멸"~~ → 자체 계산이 54시간(<96시간)이라 결론이 전제에서 안 나온다.
- ~~"되먹임 루프"~~ → PENDING 재투입은 `ComposeRerunScheduler` batch-size 20 / 30초에 묶여 인스턴스당 0.67건/s다. 유입 11.6건/s의 5.7%라 증폭이 아니라 감쇠다.
- ~~"인스턴스를 늘려도 그대로"~~ → 소비자 스레드는 인스턴스마다 하나씩이라 **상한이 대수에 정비례**한다.
- ~~"초당 300건이면 39대"~~ → 프로젝트 자체 규모 모델(100만 건/월 = 평균 0.39건/s, ADR-0001:297)과 50배 어긋난다.

**진짜 결론:** 회복 불가능한 붕괴가 아니라 **대수로 사는 처리량 문제**다. 저녁 피크 34.7건/s(하루 물량의 50%가 4시간에 집중: 500,000/14,400s)를 받으려면 인스턴스 2~4대가 필요하고, **그 스레드는 CPU를 거의 안 쓴다 — 76ms 중 70ms가 네트워크 대기라 CPU의 2% 미만이다. 돈으로 산 인스턴스의 98%를 버리는 구조다.**

**어떻게 고치나 (효과 큰 순)**

```java
// 1. S3 삭제를 소비자 스레드에서 떼어낸다 → 76ms → 16ms (62건/s, 4.7배)
@Async("s3DeleteExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onDelete(S3DeleteEvent event) { ... }

// 2. DeleteObject 4회 → DeleteObjects 1회 (최대 1000개) → 60ms → 15ms
s3Client.deleteObjects(DeleteObjectsRequest.builder()
    .bucket(bucket)
    .delete(Delete.builder().objects(keys.stream()
        .map(k -> ObjectIdentifier.builder().key(k).build()).toList()).build())
    .build());
// 1번 없이도 76ms → 31ms (32건/s, 2.4배)

// 3. SQS DeleteMessageBatch — 배치당 90ms 절약, 그러나 크래시 시 재배달이 1건 → 10건이 된다.
//    completeJob이 멱등하므로(status != PENDING 조기 반환) 안전하지만 주석으로 남길 것.
//    ⚠️ 응답의 Failed 리스트를 반드시 로그로 남긴다 — 무시하면 재배달이 조용히 는다.

// 4. 소비자 병렬화는 1·2 뒤에 — 지금 하면 커넥션 풀 10과 S3 maxConnections 50으로 병목이 옮겨간다.
```
1+2+3을 다 하면 약 7ms/건 → **140건/s(10배)**, 인스턴스 1대로 피크를 여유 있게 넘긴다.

---

### H-2. 재시도 상한이 없다 — 영구 실패 Job이 10분마다 영원히 재투입되고, 그 안전장치는 README의 CLI 한 줄뿐

> `no-retry-cap-zombie-starvation` + `no-attempt-cap-and-event-age-not-in-iac` 병합.

**위치:** `ComposeResultConsumer.java:187-191`, `ComposeJob.java:38-89`, `ComposeRerunScheduler.java:41`

**(a) 포기 조건이 코드 어디에도 없다**
`ComposeJob` 엔티티 필드를 전수 확인했다 — `status, idempotencyKey, frameId, sourceKey1~4, spec, resultKey, mediaId, failureReason, startedAt`. `attempts`·`retryCount`·`lastAttemptedAt` 같은 컬럼이 **없다**(media 패키지 전체 grep 0건). status는 PENDING/DONE/FAILED 셋뿐이다.

`ComposeResultConsumer.java:187-191`의 마지막 분기는 Success·RetriesExhausted가 **아닌 모든 condition**을 PENDING으로 유지한다. `ComposeWorker.java:58`의 catch도 PENDING을 유지한다. **종료 조건이 없다.**

> 주석의 판단 자체는 옳다: *"여기서 failJob을 부르면 재시도 가능한 실패가 영구 손실이 된다(2026-08-21 측정에서 429가 정확히 그렇게 죽었다)."* `measurement-2026-08-23.md`가 30건 중 9건 영구 손실을 실측으로 보여준다. **문제는 반대쪽 끝에 벽이 없다는 것이다. "영구 손실을 막는다"와 "영원히 포기하지 않는다"는 다르다.**

**(b) 유일한 벽인 `maximumEventAge=300`이 코드에 없다**
- 있는 곳: `compose-lambda/README.md:89-99`의 **손으로 치는 `aws lambda put-function-event-invoke-config` CLI 한 줄**
- IaC 탐색 결과: `.tf` / `template.y*ml` / `serverless.yml` / `cdk.json` / CloudFormation **전부 0건**
- **실제로 틀어졌던 기록이 저장소에 남아 있다** — `docs/blog-post.md:407`: *"그런데 실제 설정은 6시간이었다 … MaximumEventAgeInSeconds : 21600 ← 기본값. 300이어야 했다."*
- `docs/adr-0001:314`, `docs/README.md:121`, `ComposeRerunScheduler.java:18`이 모두 "`stale-after: 10m`은 eventAge=300 전제 위에 있다"고 못박는다.

**판정: 인지는 문서 3곳에 충분한데 강제하는 코드가 0줄이다.** `create-function`으로 함수를 다시 만들면 event invoke config는 초기화되고, 아무도 에러를 못 본다.

**수치 (과장 정정 포함)**
- 좀비 재투입 속도는 `ComposeRerunScheduler.java:31`의 20건/30초에 묶여 **인스턴스당 0.67건/s**. 1대 하루 최대 56,360회 × (2GB × 8.3s × $0.0000166667 = $0.000277) = **하루 최대 $15.6**, 8대여도 $125.
- ~~"하루 350만 회 여분 invoke, $970~$9,960"~~ → 재실행 상한이 물리적으로 막는다. 8대로도 7.8일 걸린다. **같은 리뷰의 rerun-throughput-ceiling 항목과 서로 모순이었다.**
- ~~"인스턴스를 10대로 늘려도 400건 그대로"~~ → 틀렸다. `claim`이 `started_at`을 now로 올려 잡은 행을 결과집합에서 빼므로(`ComposeJobRepository.java:31-37` + `:43`) 다음 인스턴스는 그다음 20건을 본다. **재실행 처리량은 인스턴스 수에 비례한다.**
- ~~"진짜 복구가 필요한 신규 Job이 영영 재투입 안 된다"~~ → 정상 신규 Job은 스케줄러를 안 탄다(C-1이 고쳐진 뒤 기준). 굶주림은 **좀비가 인스턴스당 400건을 넘을 때만** 성립한다.

**무한 루프의 정확한 범위:** 소비자가 PENDING을 유지하는 건 EventAgeExceeded·ZeroReservedConcurrency 같은 용량·수명 계열뿐이다. 원본 S3 소실 같은 진짜 함수 오류는 RetriesExhausted로 와서 `:180-184`가 FAILED로 확정한다. 즉 **'Lambda 용량이 영원히 부족할 때만' 영원하다.** 사용자에게는 "영원한 PENDING"이 아니라 "무기한 지연"이다 — 그래도 종료 상태가 안 뜬다는 사실은 같다.

**어떻게 고치나**

```sql
ALTER TABLE compose_job ADD COLUMN attempts INT NOT NULL DEFAULT 0;
```
```java
// ComposeJobRepository.claim — 상한을 조건에 넣고 시도 횟수를 올린다
@Modifying(clearAutomatically = true)
@Query("""
        UPDATE ComposeJob j SET j.startedAt = :now, j.attempts = j.attempts + 1
        WHERE j.id = :jobId AND j.status = ComposeStatus.PENDING
            AND j.attempts < :maxAttempts
            AND (j.startedAt IS NULL OR j.startedAt < :staleBefore)
        """)
int claim(Long jobId, LocalDateTime now, LocalDateTime staleBefore, int maxAttempts);
```
```java
// 기동 시 전제를 코드가 검증한다 — LambdaComposeExecutor 생성자와 같은 철학, 10줄
@PostConstruct
void assertEventAgeContract() {
    var cfg = lambdaClient.getFunctionEventInvokeConfig(r -> r.functionName(functionName));
    long eventAge = cfg.maximumEventAgeInSeconds();
    if (eventAge + FUNCTION_TIMEOUT_SEC >= staleAfter.getSeconds()) {
        throw new IllegalStateException(
            "maximumEventAge(" + eventAge + "s) + 타임아웃이 stale-after(" + staleAfter + ")보다 크다");
    }
}
```
- `findStalled`의 `ORDER BY j.id`를 `ORDER BY j.startedAt`으로 바꿔 오래된 좀비가 신규 Job을 막지 않게 한다(의미상으로도 "가장 오래 방치된 것"이 맞다).
- 상한 초과 Job은 별도 배치가 `FAILED + failureReason='재시도 상한 초과'`로 확정하고 사용자에게 통보한다.

---

### H-3. 결과물을 PNG로 저장한다 — 파일 42MB, 인코딩이 렌더 시간의 74%, 1년 뒤 월 $132,500

> `png-result-6-to-14mb` + `no-result-storage-lifecycle-or-cost-model` 병합. **이 둘은 같은 뿌리다 — 렌더 시간 문제가 아니라 저장 비용 문제다.**

**위치:** `compose-core/.../FourcutRenderer.java:77, 231-241`, `ComposeHandler.java:62`, `ComposeService.java:127`

**(a) PNG로 저장하는 근거가 어디에도 없다**
- `FourcutRenderer.java:77` `new RenderResult(encodePng(canvas), encodeThumbnail(canvas))` — 분기 없이 무조건 PNG
- `:231-241` encodePng는 `ImageIO.write(canvas,"png",out)` 하나뿐. 포맷 선택지가 없다
- `ComposeHandler.java:62` `.contentType("image/png")`, `ComposeService.java:130` 키 확장자까지 `.png`로 박힘
- **알파가 필요 없다는 건 코드가 자백한다** — `FourcutRenderer.java:265-266` 주석: *"합성 결과는 배경이 항상 칠해져 있어 잃는 픽셀이 없다."* 그 논리로 썸네일은 이미 JPEG q0.8(`:58`)
- `grep -rn "PNG|JPEG" docs/adr-*.md` → **0건.** ADR 어디에도 근거가 없다. "의도된 트레이드오프"로 방어할 문서가 없다.

**실측 (4000×6000 GRID 캔버스, 1000×1500 노이즈 포함 사진 4장을 1.70배 확대해 cover)**
| 포맷 | 크기 | 인코딩 |
|---|---|---|
| **PNG** | **42.39MB** | **3,079ms** |
| JPEG q0.90 | 2.86MB | 558ms |
| JPEG q0.85 | 2.21MB | 501ms |

**크기 14.8배, 인코딩 5.5배.** (더 매끈한 이미지 기준으로는 14.4MB / 5.3배 / 1.9배 — 어느 쪽이든 결론 동일. PNG 인코딩이 전체 렌더 2,087ms 중 1,553ms = **74%**다.)

**(b) 그 파일을 지우는 코드가 없다**
`Retention.java:6` 주석: *"기간이 지난 데이터도 지우지 않는다(업그레이드 시 다시 보인다)."* `UserMediaService.getMyMedia`는 cutoff로 **목록에서 숨길 뿐**이다. BASIC(3일 보관) 사용자의 사진도 S3와 `user_media`에 영구히 남는다. 유일한 삭제 경로는 사용자가 직접 DELETE하거나 탈퇴하는 것인데, **안 보이는 사진을 지우러 오는 사용자는 없다.**

그리고 `docs/compose-channel-cost-model.md`는 Lambda GB-초·SQS 요청·NAT만 계산하고 **S3 저장 비용 항목이 아예 없다**(표의 S3 항목은 `GET/HEAD ~$0.0004/1,000`뿐).

**수치 — 이게 이 프로젝트에서 가장 큰 숫자다**

| | PNG(14.4MB 기준) | JPEG q0.90(2.72MB) |
|---|---|---|
| 월 100만 건 신규 저장 | 14,400GB | 2,720GB |
| S3 Standard $0.023/GB-월 | $331/월 (12개월 뒤 **$3,974/월**) | $63/월 |
| egress $0.09/GB (1회 다운로드) | $1,296/월 | $245/월 |
| **월 절감** | | **$1,319** |
| Lambda 인코딩 0.75초 단축 | | 150만 GB-s ≈ $25/월 |

**하루 100만 건 기준 장기 누적 (서울 리전 $0.025/GB-월):**
- 하루 14.5TB → 30일 435TB → **1년 5.3PB**
- 1년 차 말 **월 $132,500 (약 1억 8천만 원)**
- JPEG q0.85(약 1.5MB)면 이 숫자가 **1/10**, Glacier 전환·만료 규칙까지 걸면 다시 **1/10**

**사용자 체감:** GRID 프레임 결과 14.4MB를 LTE 실효 10Mbps로 받으면 **11.5초**. JPEG q0.9면 2.2초다.

**어떻게 고치나**

```java
// FourcutRenderer — encodeJpeg와 알파 제거 로직(drawScaled의 TYPE_INT_RGB)이 이미 있다
return new RenderResult(encodeJpeg(canvas, 0.90f), encodeThumbnail(canvas));
```
함께 바꿔야 하는 곳 (`user_media.s3_key`에 unique가 있어 키가 어긋나면 안 된다):
- `ComposeService.resultKeyFor` — `".png"` → `".jpg"`
- `ComposeHandler.java:62` — `contentType("image/jpeg")`
- 프론트 다운로드 경로

저장 포맷을 `ComposeSpec`/payload에 실어 프레임별로 고르게 하면 나중에 WebP 전환도 배포 순서 걱정 없이 된다.

**그리고 반드시 함께 — 보관 수명주기를 코드/IaC로 남긴다:**
1. `uploads/users/*/fourcuts/sources/` 만료 7일 Lifecycle. **`FourcutSourceUploadPathStrategy.java:10-11` 주석이 이미 약속한 것이다** — *"나중에 S3 Lifecycle로 sources/만 N일 후 자동 삭제를 걸어 고아를 치우기 위함."* 안 걸면 주석이 거짓이 된다. 이 규칙 하나가 성공·실패·좀비·탈퇴·미합성 고아를 **전부** 덮는다.
2. 결과물에 요금제별 보관 정책을 실제 삭제로 연결하는 배치, 또는 Standard-IA/Glacier IR 전환 Lifecycle.
3. `compose-channel-cost-model.md`에 저장 비용 항목 추가 — **"통지 채널 비용 $3 vs 저장 비용 $132,500"이라는 비율이 면접에서 가장 강한 그림이다.**

---

### H-4. ImageIO 기본 설정(useCache=true) 때문에 모든 디코드가 /tmp 디스크를 왕복한다 — 한 줄로 3.3배

**위치:** `compose-core/.../FourcutRenderer.java:213`(decode), `:234`(PNG write), `:291`(JPEG createImageOutputStream)

레포 전수 grep(build 디렉터리 제외) 결과 `ImageIO.setUseCache(false)`가 **0건**. 실행해 클래스를 직접 확인했다:
```
default useCache = true
createImageInputStream(ByteArrayInputStream)  -> javax.imageio.stream.FileCacheImageInputStream
createImageOutputStream(ByteArrayOutputStream) -> javax.imageio.stream.FileCacheImageOutputStream
```
**힙에 이미 있는 바이트가 디스크를 왕복한다.**

**실측 (각 3회 평균, 워밍 후)**
| 작업 | useCache=true | false | 배수 |
|---|---|---|---|
| 원본 4장 디코드 | 286ms / 273ms | 87ms / 81ms | **3.3배** |
| PNG 인코딩(4000×6000) | 3,079ms | 2,465ms | 1.25배 |
| 스티커 1장(600×600) | 5.4ms | 3.1ms | 1.7배 |

**단서:** 이 수치는 Windows에서 잰 것이다. Lambda의 /tmp는 리눅스 파일시스템이고 페이지 캐시가 물리 디스크를 대부분 흡수하므로 실제 격차는 더 작을 수 있다. 그래도 syscall(write/seek/read)이 버퍼마다 발생하는 건 리눅스에서도 같아 이득이 사라지지는 않는다. **고칠 비용이 한 줄이라 이득이 절반으로 깎여도 남는 장사다.**

**왜 HIGH인가:** `docs/measurement-2026-08-23.md:59` 기준 이 계정의 Lambda 동시 실행 한도가 **10**이다. 동시성이 병목인 구조에서는 **실행 시간 단축이 곧 처리량**이다. 건당 11~28% 단축.

```java
// FourcutRenderer static 초기화 — JVM 전역이라 서버·Lambda 양쪽에 한 번만 걸린다
static { ImageIO.setUseCache(false); }

// 전역 설정에 의존하지 않으려면 명시적으로
new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes));   // decode
new MemoryCacheImageOutputStream(out);                              // encodePng
```
입력이 이미 `byte[]`이고 출력이 `ByteArrayOutputStream`이라 메모리 캐시로 늘어나는 힙은 사실상 없다.

---

### H-5. SQS 소비자가 로컬 프로파일에서도 기본 ON이고 `.env`에 실제 큐 URL이 있다 — 개발자 PC가 운영 통지를 가로채고 운영 S3를 지운다

**위치:** `src/main/java/com/harucut/media/compose/ComposeResultConsumer.java:31`

```java
@ConditionalOnProperty(name = "compose.result-consumer.enabled",
                       havingValue = "true", matchIfMissing = true)   // ← 기본이 켜짐
```
- 끄는 곳은 `application-test.yaml:39-41` 하나뿐. `application-local.yaml`에는 그 설정이 **없다**
- `application.yaml:3` `spring.profiles.default: local`
- `application.yaml:108` `compose-result-queue-url: ${COMPOSE_RESULT_QUEUE_URL:}`
- **저장소 루트 `.env:70`에 실제 큐 URL이 들어 있고**, `docker-compose.yml:60`이 그 `.env`를 그대로 주입한다
- AWS 자격증명도 같은 `.env`에서 온다 → **로컬 앱이 운영 큐와 운영 버킷 양쪽에 실제 권한을 갖는다**

**실패 시나리오**
개발자가 로컬에서 앱을 띄우면 소비자 스레드가 운영 큐를 20초 롱폴링으로 긁는다. 운영 Job #4321 성공 통지를 로컬 앱이 먼저 집으면:
- (a) 로컬 DB에 id=4321 PENDING이 있으면(둘 다 IDENTITY라 1부터 시작 — 충돌이 흔하다) `completeJob`이 성공 처리하고 메시지를 삭제한다. **그 순간 로컬 앱이 운영 S3의 원본 4장을 지우고**, 운영 서버는 통지를 영영 못 받아 Job #4321이 PENDING으로 남아 10분마다 무한 재실행된다(H-2와 결합).
- (b) 로컬 DB에 없으면 `IllegalStateException("합성 Job이 사라졌다: 4321")` → 메시지를 안 지움 → maxReceiveCount 5회(`compose-lambda/README.md:32`) 소진 후 DLQ로 사라진다.

어느 쪽이든 **운영 사용자는 화면에서 영원히 '합성 중'을 본다.**

**규모:** 개발자 3명이 각자 띄우면 운영 통지의 3/4이 엉뚱한 곳으로 간다. 초당 수백 건 규모에서 DLQ가 하루 수만 건으로 차는데, **DLQ를 읽는 코드도 알림도 저장소에 없다.**

**어떻게 고치나 (셋 다)**
```yaml
# application-local.yaml
compose:
  result-consumer:
    enabled: false      # (1) 로컬 기본값을 끔으로 뒤집는다
```
```java
// (2) 계약을 뒤집는다 — "켜려면 명시적으로 켜라"
@ConditionalOnProperty(name = "compose.result-consumer.enabled",
                       havingValue = "true", matchIfMissing = false)
```
(3) 개발·운영 큐를 분리하고, 통지의 `resultKey`가 서버가 계산한 `resultKeyFor(publicId, jobId)`와 일치할 때만 처리한다 → L-4의 수정이 이 사고도 같이 막는다.

---

### H-6. PENDING → DONE을 쓰는 유일한 코드에 테스트가 0개다 — 그리고 이 프로젝트에 이미 있는 패턴을 여기만 안 썼다

> `result-consumer-untested` + `no-integration-test-in-compose-pipeline` 병합. **C-1이 살아남은 근본 원인이다.**

**위치:** `ComposeResultConsumer.java:163`, `ComposeRerunScheduler`, `ComposeWorkerTest.java:43-44`

**검증되지 않는 것 (전수 확인)**
| 대상 | 상태 |
|---|---|
| `ComposeResultConsumerTest` | **파일 자체가 없다** |
| `ComposeRerunSchedulerTest` | **파일 자체가 없다** |
| `ComposeJobRepository.claim`(`:37`)의 조건부 UPDATE | **어느 테스트에서도 실행되지 않는다.** `grep -rn 'claim\|findStalled' src/test/java/com/harucut/media` → ComposeWorkerTest 7건뿐이고 전부 `given(composeService.claim(...)).willReturn(true/false)` 목 |
| `ComposeJobRepositoryTest` 216줄 | @DisplayName 전수 확인 — 왕복/멱등 unique/소유권/깨진 스펙/탈퇴 삭제뿐. `claim`·`findStalled` 없음 |
| `handle()`의 3분기, 성공 시에만 deleteMessage(`:152-155`), 실패 시 일부러 안 지움(`:156-160`), SmartLifecycle start/stop, 5초 백오프 | 전부 미검증 |

**패턴이 이미 있는데 여기만 안 썼다.** `src/test` 전체에서 `@SpringBootTest`를 쓰는 테스트는 10개다(UserDeletionJobTest, CouponRedeemFlowTest, PaymentSubscribeFlowTest, SubscriptionProvisioningFlowTest, **S3DeleteFlowTest**, TermsIntegrationTest 등). **`media/` 패키지에는 0개다.** 특히 `S3DeleteFlowTest`는 `S3DeleteListener`의 AFTER_COMMIT 흐름을 실제 트랜잭션으로 검증한다 — **ComposeWorker도 똑같은 AFTER_COMMIT 리스너인데 같은 패턴이 적용되지 않았다.**

**결과:** `./gradlew test`에서 compose 테스트 12개가 전부 초록으로 통과하는데, 실제 서버에서는 즉시 접수 경로가 통째로 죽어 있다(C-1). **테스트가 이 상태를 구조적으로 잡을 수 없다.**

**투자 방향이 위험과 반대다.** 커버리지가 가장 두꺼운 곳은 실패해도 사용자가 즉시 아는 접수 API 쪽이고, 유실률이 0%→100%로 바뀔 수 있는 통지 소비 쪽이 0이다.

**어떻게 고치나**
```java
// ComposeFlowTest — S3DeleteFlowTest를 그대로 본뜬다. @Transactional 없이 실제 커밋.
// ComposeExecutor는 인터페이스라 테스트 빈으로 갈아끼우면 되므로 AWS가 필요 없다.
@Test void 접수하면_AFTER_COMMIT에서_startedAt이_찍히고_executor가_호출된다()
@Test void 같은_Job에_rerun을_두_번_부르면_두_번째는_선점에_실패한다()
@Test void 성공_통지를_넣으면_UserMedia가_생기고_Job이_DONE이_되고_원본_삭제_이벤트가_나간다()
```
```java
// ComposeResultConsumerTest — SqsClient 목, 최소 5케이스
Success → completeJob + deleteMessage
RetriesExhausted → failJob + deleteMessage
알 수 없는 condition → 두 서비스 모두 미호출 + deleteMessage
completeJob이 예외 → deleteMessage 미호출
jobId가 null → 스킵
```
```java
// ComposeJobRepositoryTest에 @DataJpaTest 4케이스 추가
PENDING·startedAt=null → 1     이미 선점·stale 전 → 0
stale 후 → 1                    DONE → 0
```

---

### H-7. 스프링 종료 타임아웃 20초 < 소비자 종료 대기 25초 — 클래스 주석이 막으려던 사고가 그대로 난다

**위치:** `src/main/resources/application.yaml:5` vs `ComposeResultConsumer.java:100`

```yaml
spring.lifecycle.timeout-per-shutdown-phase: 20s     # application.yaml:4-5
```
```java
worker.awaitTermination(WAIT_SECONDS + 5, SECONDS);  // = 25초, :100
```

**20 < 25라서 순서 보장이 깨진다.** 클래스 주석 `:25-27`이 위험을 정확히 지목했다 — *"소비자가 가장 먼저 멈추고 DB·커넥션 풀은 그 뒤에 내려간다. 순서가 반대면 처리 중이던 통지가 죽은 커넥션을 잡는다."*

**ADR-0003 §3의 근거 숫자가 틀렸다.** *"docker-compose.yml의 stop_grace_period: 60s 안이고, 배포는 하루 1회다. 하루 10초"*라고 정당화했는데, **실제로 소비자를 끊는 값은 도커의 60초가 아니라 스프링의 20초다.**

**실패 시나리오**
배포 중 SIGTERM이 롱폴링 시작 직후 도착 → `shutdownNow`의 인터럽트가 SDK 블로킹 소켓을 즉시 못 깨는 건 주석 `:96-97`이 스스로 인정한 사실이라 `receiveMessage` 반환까지 최대 20초 → 반환 직후 `completeJob`(UserMedia INSERT + Job UPDATE + S3 삭제 4회)이 시작되지만 스프링은 이미 20초를 다 써서 `Failed to shut down beans within timeout`만 남기고 다음 phase로 → 웹서버 정지 → HikariCP 종료 → **처리 중이던 스레드가 닫힌 커넥션을 잡고 죽는다.**

통지는 삭제되지 않아 30초 뒤 재배달되고, **UserMedia INSERT가 이미 커밋된 경우 재처리가 `user_media.s3_key` unique에 걸려 또 실패** → 5회 후 DLQ. Job은 PENDING으로 남는다.

**규모:** 롱폴링이 20초라 큐가 한산할수록 SIGTERM이 폴링 중에 떨어질 확률이 높다 — **사실상 대부분의 배포가 이 경로를 탄다.** 인스턴스 N대 롤링이면 배포마다 N번, 종료 순간 in-flight가 10건(MAX_MESSAGES)이면 한 번의 배포가 최대 10건을 위험에 노출시킨다.

```yaml
# 가장 간단한 수정 — WAIT_SECONDS(20) + 5 + 여유
spring.lifecycle.timeout-per-shutdown-phase: 30s
```
반대 방향(`awaitTermination(WAIT_SECONDS - 5)`)은 **안 된다** — 처리 중인 통지를 잘라 버린다. 이 제약을 `ComposeResultConsumer` 주석에 명시하고 ADR-0003 §3의 '60초' 근거를 정정한다.

---

### H-8. 업로드 API가 WEBP를 정식 허용하는데 렌더러에 WebP 디코더가 없다 — 202 받고 3번 재시도한 뒤 실패

**위치:** `src/main/java/com/harucut/storage/enums/ContentType.java:16`

- `ContentType`이 `WEBP("image/webp", Set.of("webp"))`를 허용하고, `PresignedUploadRequest`에 UploadType별 제한이 없어 `FOURCUT_SOURCE`에도 그대로 쓸 수 있다
- `FourcutRenderer.decode`는 `ImageIO.read`가 null이면 `IllegalArgumentException("이미지 형식을 인식할 수 없다")`를 던지는데, **JDK 21 표준 ImageIO 내장 리더는 JPEG·PNG·BMP·GIF·WBMP·TIFF뿐이고 WebP는 없다**
- `compose-core/build.gradle`·`compose-lambda/build.gradle` 어디에도 TwelveMonkeys 같은 WebP 플러그인이 없다(두 파일 전체 의존성이 jackson·AWS SDK·lambda-core뿐)

**흐름:** webp 4장 업로드 200 → 합성 요청 202 → Lambda가 첫 장 decode에서 예외 → 비동기라 AWS가 2회 더 재시도, 전부 같은 이유로 죽음 → RetriesExhausted → failJob. 사용자는 수십 초 기다린 끝에 `IllegalArgumentException: 이미지 형식을 인식할 수 없다`만 본다. **왜 안 되는지 알 방법이 없고, 다시 올려도 똑같다.**

**수치:** webp 비율 5%면 하루 100만 건 중 **5만 건이 확정 실패**하고, 각각 Lambda를 3번 돌려 15만 회(2048MB × 약 3초 = 92만 GB-초)를 태운다. 실패 1건마다 S3 GET도 (4+자산수)×3회.

**어떻게 고치나 (둘 중 하나, '허용 포맷'과 '디코드 가능 포맷'이 한 곳에서 나오게)**
```java
// (1) 지금 정직한 선택 — 업로드 단계에서 415로 끊는다
public enum UploadType {
    FOURCUT_SOURCE(Set.of(ContentType.JPEG, ContentType.PNG)),  // 화이트리스트
    ...
}
// (2) 제대로 지원 — compose-core에 의존성 추가 + webp 픽스처 테스트
implementation 'com.twelvemonkeys.imageio:imageio-webp:3.10.1'
```

---

### H-9. 포트폴리오 증거 전체(docs/, monitoring/)가 `.gitignore`로 제외돼 저장소에 한 파일도 없다

**위치:** `.gitignore:43-45`

```
43: deploy/
44: monitoring/
45: docs/
```
- `git check-ignore -v docs/adr-0004-cicd-pipeline.md` → `.gitignore:45:docs/`
- `git ls-files docs monitoring deploy` → **0행**
- `find . -name decisions.md` → **0건**. 그런데 그 파일을 가리키는 주석은 **7곳 전부 실재**한다: `ComposeService.java:99`, `ComposeWorker.java:16`, `LambdaComposeExecutor.java:14`, `ComposeResultConsumer.java:190`, `ComposeJob.java:17`, `ComposeStatus.java:4`, `UploadType.java:8`
- `docs/README.md:106-109`가 이 문제를 이미 인지하고 있지만, **그 README 자체가 제외 대상이라 방어가 되지 않는다**

**정정:** ADR 4편은 정확히 1,009줄(319+138+222+330)이 맞다. `docs/**/*.md` 전체는 3,251줄이라 "3,284줄"은 33줄(1%) 과다. `deploy/`는 '제외된' 게 아니라 **디스크에 아예 없다**(`ls deploy` 실패).

**영향:** 면접관이 clone → `docs/` 폴더가 없다 → `ComposeWorker.java:16`의 "decisions.md 2026-08-21 «합성 Lambda 호출을 비동기로»"를 찾는다 → 없다. **주석이 존재하지 않는 문서를 7곳 가리키는 상태로 보인다.** 이 프로젝트의 가장 강한 자산(측정 기반 ADR)이 심사자에게 0으로 보인다. 그리고 3,251줄의 설계 문서와 유일한 관측 대시보드가 **PC 한 대의 디스크에만** 존재한다.

**어떻게 고치나**
1. `.gitignore`에서 `docs/`, `monitoring/` 제거 (`deploy/`는 비밀값이 들어가면 유지하되 `.env` 예시만 남긴다)
2. 커밋 후 코드 주석 7곳의 `decisions.md`를 실제 ADR 번호(`docs/adr-0001` 등)로 치환
3. 문서 자신의 불일치도 정정 — `docs/README.md`의 '미결' 3건 중 2건(WAIT_SECONDS 10초, 가시성 타임아웃 미지정)은 이미 코드에서 20/30초로 고쳐졌고, `docs/adr-0003:3`은 '채택됨'인데 같은 파일 §0 표는 '코드=10초', `docs/README.md`의 ADR 표는 ADR-0003을 '제안됨(값 변경 필요)'으로 적어 **세 곳이 서로 다르다**

---

### H-10. `LambdaComposeExecutor` 클래스 주석 세 문장이 전부 현재 코드와 정반대다

**위치:** `src/main/java/com/harucut/media/compose/LambdaComposeExecutor.java:13-15`

| 주석 | 실제 코드 |
|---|---|
| `:13` "Lambda를 **동기 호출**한다" | `:47` `.invocationType(InvocationType.EVENT)` — **비동기** |
| `:14` "호출의 응답이 곧 완료 통지라서 **콜백 엔드포인트·큐가 필요 없다**" | `ComposeResultConsumer`가 SQS 큐를 20초 롱폴링한다(`:133-138`) |
| `:15` "실패는 예외로 바꿔 **워커가 Job을 FAILED로 기록**하게 한다" | `ComposeWorker.java:52-54` 주석이 명시적으로 **"FAILED로 적지 않는다"** |

같은 파일 `:51`에는 "비동기 접수는 202다"라는 **올바른** 주석이 따로 있어 **한 파일 안에서 주석끼리 모순된다.**

**존재하지 않는 설정 키:** `:34` 예외 메시지의 `compose.executor`는 `grep -rn 'compose.executor'` 결과가 **자기 자신 1건뿐**이다. 이 클래스엔 `@ConditionalOnProperty`가 없어(리포 전체 3곳: `ComposeResultConsumer:31`, `MockPaymentGateway:16`, `MockPaymentWebhookVerifier:7`) **켜고 끌 스위치 자체가 없다.**

**같은 종류의 죽은 주석 2곳 더:** `compose-lambda/.../ComposeHandler.java:52`가 PR #21에서 삭제된 `InProcessComposeExecutor`를 가리키고, `build.gradle:56`이 `awssdk:lambda`를 "**동기** invoke용"이라 설명한다.

**대칭 붕괴:** `application-test.yaml:48-49`가 *"생성자가 빈 값이면 죽어서 넣는 자리표시자다"*라며 가짜 함수 이름을 넣는 반면, 소비자는 같은 파일 `:38-41`에서 `enabled: false`로 그냥 끈다. (기동 실패 자체는 `:32` 주석이 "첫 합성 요청에서 죽는 것보다 낫다"고 근거를 남긴 **의도된 fail-fast**다 — 이 항목의 확정 근거는 주석 역전 쪽이다.)

**면접 시나리오:** *"동기 호출이면 Lambda 동시성 한도에서 429가 그대로 예외가 되는데 어떻게 처리하나요?"* → 실제 코드는 이미 그 문제를 푼 비동기인데, **주석이 자기 개선의 정반대를 설명하고 있다.** 프로젝트의 핵심 성과(유실 30%→0%)를 설명하는 파일이 그 성과를 부정하는 주석을 달고 있다.

```java
// 13~15행 다시 쓰기
// EVENT로 접수만 시키고, 202가 아니면 예외를 던져 워커가 PENDING을 유지하게 한다.
// DONE/FAILED는 Lambda Destination 통지를 받은 ComposeResultConsumer가 찍는다.

// 34행 메시지 — 실제 키로
"cloud.aws.lambda.compose-function이 비어 있다"

// ComposeHandler.java:52 → ComposeSpec.referencedAssetKeys()
// build.gradle:56 → "비동기 invoke용"
```

---

### H-11. 합성 파이프라인에 Micrometer 메트릭이 0개, 운영 프로파일엔 `/actuator/prometheus`가 안 열려 있다

**위치:** `ComposeResultConsumer.java:176`, `application.yaml`

- `grep -rn 'MeterRegistry|Counter|Timer|@Timed|Gauge' src` → **0건.** 의존성(`build.gradle:59-60` actuator + micrometer-registry-prometheus)은 있는데 **도메인 계측이 하나도 없다**
- 전부 로그뿐이라 집계가 불가능하다: `:176` `log.info("[합성 통지] 완료")`, `:183` `log.warn("영구 실패")`, `:191` `log.warn("일시적 실패로 본다")`, `ComposeRerunScheduler:50` `log.info("{}건 재투입")`
- `management:` 블록은 **`application-load.yaml:32`에만** 있다(include: health,prometheus,metrics). `application.yaml`·`application-local.yaml`에는 없고, 부트 기본 노출은 health뿐 → **부하 프로파일 밖에서 `/actuator/prometheus`가 404다.** 운영 전용 프로파일 파일 자체가 없어 `application.yaml`이 곧 운영 설정이다
- Grafana 대시보드 `harucut-compose.json`의 expr 13개가 전부 `http_server_requests_*`/`jvm_threads_*`/`hikaricp_*`이고, **대시보드 자신의 설명 패널(`:92`, `:392`)이 인정한다** — *"유실 건수는 이 대시보드에 없다… compose_job 테이블의 최종 상태로만 보인다."*

**실패 시나리오:** Lambda Destination이 SQS로 통지를 못 보내는 상황(큐 정책 오설정, DLQ 폭주) → 모든 Job이 PENDING → **접수 API는 계속 202를 주고 p95도 정상이라 대시보드가 전부 초록색** → 사용자가 "합성이 안 끝나요" 문의를 넣기 전까지 아무도 모른다. 재실행 스케줄러가 예외로 멈춰도 마찬가지다.

**수치:** 하루 10만 건(초당 1.16건 = 100,000/86,400) 규모에서 "지금 몇 건이 PENDING인가"를 알려면 수천만 행짜리 `compose_job`에 직접 `SELECT COUNT(*) WHERE status='PENDING'`을 쳐야 한다. 인덱스를 타도 수백 ms~수 초, 알람 주기로 반복하면 운영 DB에 부하를 더한다. **통지 지연을 재는 값이 없어 SLO를 정의할 수도 없다.**

> 완화 요인 하나는 인정할 만하다: 부하 프로파일에는 percentiles-histogram과 instance 태그까지 준비돼 있어 **'관측을 모르는' 게 아니라 '도메인 지표만 비어 있는' 상태**다.

```java
// ComposeResultConsumer
Counter.builder("compose.result.total").tag("condition", condition).register(registry).increment();
Timer.builder("compose.duration").register(registry).record(接수→완료);
// ComposeWorker: 접수 성공/실패 Counter
// ComposeRerunScheduler: 재투입 Counter (실제 claim 성공 건수로 — M-1 참고)
// PENDING 적체는 COUNT를 매번 치지 말고 Gauge로 캐시
```
```yaml
# application.yaml
management:
  server.port: 8081
  endpoints.web.exposure.include: health,prometheus
```

---

## 3. 100만 규모에서 재검토가 필요한 설계 결정 (MEDIUM)

### M-1. 재실행 스케줄러 — 상한 20건/30초의 근거가 없고, 로그가 실제 처리량을 부풀린다

> `rerun-scheduler-fleet-ceiling` + `rerun-throughput-ceiling` 병합. **둘 다 "인스턴스를 늘려도 안 는다"고 주장했으나 그건 반증됐다.**

**위치:** `ComposeRerunScheduler.java:31, 39, 50`

**반증된 주장 (기록):** ~~"인스턴스를 100대로 늘려도 분당 40건"~~ → `@Scheduled(fixedDelayString)`이라 인스턴스마다 타이머 위상이 독립적이고, `claim`이 각자 자기 트랜잭션에서 즉시 커밋된다(프로브 PROBE2로 확인). A가 t=0에 20행을 집어 `started_at`을 찍으면 t=3초에 도는 B의 `findStalled`는 그 20행을 이미 제외한다. **10대면 30초 창에서 약 200건. 대체로 선형이다.** 겹치는 구간은 20건 claim+invoke의 약 1초뿐이라 한 쌍이 충돌할 확률은 1/30 수준이다.

**실재하는 결함 2개**
1. **`:50`의 `log.info("{}건 재투입", stalled.size())`는 '찾은 후보 수'지 '실제로 선점한 수'가 아니다.** 진 쪽은 `ComposeWorker.java:44`의 `log.debug`라 운영에서 안 보인다. 다인스턴스에서 지표가 부풀려진다.
2. **`batch-size 20`의 근거가 코드·ADR·docs 어디에도 없다.** `WAIT_SECONDS(20)`(`ComposeResultConsumer.java:34-44`)·`VISIBILITY_TIMEOUT(30)`(`:47-54`)·`stale-after(10m)`(`README:99`, `ADR-0001:314`)에는 문단 단위 근거가 있는데 **이것만 없다.** `application-load.yaml:30`에 값만 있다. **면접에서 "왜 20인가요"에 답할 수 없는 유일한 숫자다.**

**장애 복구 SLA가 안 나온다.** Lambda 1시간 장애 → 3,600 × 11.6 = 41,700건 PENDING:
| 인스턴스 | 회수 시간 |
|---|---|
| 1대 (0.67건/s) | **17.3시간** |
| 4대 (2.67건/s) | **4.3시간** |
| 8대 | 2.2시간 |

"장애 1시간 = 복구 4시간"은 SLA를 세울 수 없는 비율이다. 다만 `batch-size`·`interval` 둘 다 `@Value` 외부 설정이라 **코드 수정 없이 환경변수로 올릴 수 있다** — 구조적 상한이 아니라 조율 가능한 기본값이다.

```java
// 1. 근거 있는 값으로 다시 정한다 (계산을 주석으로 남긴다)
//    접수 1건 = claim UPDATE(~5ms) + Lambda EVENT invoke(~30ms) ≈ 35ms
//    30초 주기의 안전 가동률 50%(15초) → 15,000 / 35 ≈ 400건
//    20은 그 5%다. 200~400으로 올리면 인스턴스당 6.7~13건/s.
@Value("${compose.rerun.batch-size:200}") int batchSize

// 2. 로그를 '실제 claim 성공 건수'로
public boolean rerun(ComposeRequestedEvent e) { ... }   // boolean 반환
long claimed = stalled.stream().filter(composeWorker::rerun).count();
log.info("[합성 재실행] 후보 {}건 중 {}건 재투입", stalled.size(), claimed);

// 3. 적응형 배치 — 코드 5줄이고 효과가 가장 크다
//    이번 주기에 batchSize만큼 꽉 채웠으면(=아직 더 있다) 다음 주기를 안 기다리고 즉시 한 번 더.
//    평시 20건 유지, 장애 복구 때만 자동 가속.

// 4. 다중 인스턴스 대비: SELECT ... FOR UPDATE SKIP LOCKED (MySQL 8)
// 5. stale PENDING 수를 Gauge로 — 지금은 stalled.isEmpty()면 로그조차 없다(:43-45)
```

---

### M-2. 선점 성공 후 invoke가 실패하면 `started_at`이 찍힌 채 남아 10분 동결

**위치:** `ComposeWorker.java:43` → `:58`

`claim`이 `started_at=now`를 찍은 뒤 invoke 예외를 `:58`에서 로그만 남기고 삼킨다. **선점을 되돌리는 코드가 없다.** `findStalled`(`:43`)는 `started_at IS NULL` 또는 `started_at < now-10m`이어야 다시 준다. `:50-57` 주석이 "PENDING으로 두면 stale-after 뒤 다시 던진다"고 인지했지만 **그 대가가 정확히 10분이라는 계산은 안 했다.**

**방아쇠 정정:** ~~"Lambda 429"~~ → `docs/measurement-2026-08-23.md`의 429는 **개선 전 동기 호출(e32247a)** 값이고, 같은 문서가 *"비동기 EVENT 호출은 초과분이 Lambda 내부 큐에서 대기해 같은 한도에서도 실패 0"*이라고 실측으로 적었다. 현재 코드는 EVENT(`LambdaComposeExecutor.java:47`)이므로 **실제 방아쇠는 SDK/네트워크 수준의 일시적 실패**다. "피크마다 수천 건이 10분씩 멈춘다"는 이 프로젝트 자신의 측정으로 반박된다.

**그래도 고칠 값어치:** C-5(SDK 타임아웃 미설정, 최악 128초)와 겹치면 Lambda 엔드포인트 장애 구간에서 "모든 Job이 claim만 찍히고 10분 동결"이 된다.

```java
catch (Exception e) {
    log.error("[합성] Lambda 접수 실패 — 선점 해제: jobId={}", event.jobId(), e);
    composeService.releaseClaim(event.jobId());   // 10분 → 30초, 20배 개선
}
// UPDATE compose_job SET started_at = NULL WHERE compose_job_id = ? AND status = 'PENDING'
// 되돌리기가 실패해도 최악은 지금과 같으므로 추가 위험이 없다.
```

---

### M-3. `spec_json` VARCHAR(8000) + 컴포넌트 개수 상한 없음 — 40개 넘으면 정체불명의 500

> `spec-json-8000-overflow-500` + `unbounded-asset-download` 병합. **하나의 원인, 두 개의 증상.**

**위치:** `ComposeJob.java:75`, `FrameCreateRequest.java:65-67`, `ComposeHandler.java:53-56`

- `@Column(name="spec_json", length=8000)`. `docker-compose.yml:14` `--character-set-server=utf8mb4`에서 Hibernate 6 MySQLDialect의 maxVarcharLength가 16383이므로 **VARCHAR(8000)으로 생성된다**(LONGTEXT 승격 없음)
- `FrameCreateRequest.java:65-67 components`에 **`@Size`가 없다.** 같은 파일 `:58`의 `cellCutouts`에는 `@Size(min=4,max=4)`가 있다 — **상한을 거는 습관은 있는데 여기만 빠졌다**
- Layer 1개 JSON = 고정 골격 84자 + 숫자 8개 약 30자 + 컴포넌트 key 76자 = **190자**. 헤더(canvas·background·slots 4개·cellCutouts·필드명) 약 384자 → `(8000-384)/190 = 40.1` → **레이어 40개가 한계**

**두 개의 증상**
| 컴포넌트 수 | 결과 |
|---|---|
| ≤40개 | Lambda가 `ComposeHandler.java:53-56`에서 자산 전부를 `Map<String,byte[]>`에 **동시에** 들고 있는다(스트리밍 아님). 40 × 10MB = **390MB** + 캔버스 96MB + 디코드 1장 → 2048MB 함수에서 GC 압박. C-3(디코드 폭탄)과 겹치면 OOM |
| ≥41개 | `save()`가 IDENTITY라 즉시 INSERT → MySQL STRICT `Data too long for column 'spec_json'` → `DataIntegrityViolationException` → **`GlobalExceptionHandler:93 @ExceptionHandler(Exception.class)` → HTTP 500** |

**둘 다 설계된 동작이 아니다.** 500은 재시도를 유발해 같은 요청이 반복 유입된다. 사용자는 '서버 오류'만 볼 뿐 **스티커를 줄이라는 안내를 못 받는다.**

> 정정: ~~"영원히 못 고친다"~~ → 프레임 저장 자체는 성공하므로 스티커를 줄여 다시 저장하면 합성된다. 못 고치는 건 **무엇을 줄여야 하는지 안내가 없다**는 것이다. ~~"스티커 40개는 흔한 패턴, 헤비 유저 1%=1만 명"~~ → 코드·측정 근거 없는 가정이다.

```java
// ① 즉시 — 입구에서 막고 400을 준다
@Size(max = 30, message = "프레임 요소는 30개까지입니다")
private List<ComponentRequest> components;

// ② 근본 — 인덱싱 대상이 아니라 잃을 게 없다
@Column(name = "spec_json", nullable = false, columnDefinition = "MEDIUMTEXT")

// ③ 방어 — 과거에 만들어진 큰 프레임이 나중에 합성될 수 있으므로 조립 단계에서도 재확인
//    ComposeSpecAssembler에서 직렬화 길이를 재고 초과 시 INVALID_INPUT_VALUE로 400
// ④ Lambda는 자산을 한 장씩 내려받아 그린 뒤 참조를 버려 동시 보유량을 상수로
```

---

### M-4. 원본 4장 + 자산 N개 다운로드와 결과 2회 업로드가 전부 순차 — (6+N)회 직렬

**위치:** `compose-lambda/.../ComposeHandler.java:48-50, 53-56, 60-63, 68-72`

- `:48-50` `payload.sourceKeys().stream().map(key -> download(...)).toList()` — `parallelStream()`이 아니라 **순차 스트림**
- `:53-56` 자산 루프도 일반 for문
- `:60-63`, `:68-72` `putObject` 2회도 순차. 동기 `S3Client`(`:31`)라 사이에 어떤 병렬 장치도 없다

→ **4 + N + 2회 직렬.** 배경 1 + 스티커·텍스트 8개면 N=9 → **15회.**

**`RequestBody.fromBytes`의 복사도 바이트코드로 확인:**
```
public static RequestBody fromBytes(byte[]);
   3: invokestatic  java/util/Arrays.copyOf:([BI)[B    ← 전체 복사
```
이 캔버스의 PNG가 42MB까지 나오므로(H-3) **업로드 순간 힙에 42MB가 두 벌** 생긴다.

**단서:** "같은 리전 S3 GET 첫 바이트 20~30ms", "13회 GET이 550~700ms"는 **이 환경에서 잰 값이 아니라 일반 인용치**다. 작은 객체는 10~20ms인 경우도 흔해 실제 대기는 300ms 근처일 수 있다. 또 `docs/measurement-2026-08-23.md:56`의 테스트 프레임은 "컴포넌트 없는 최소 프레임"이라 **N=0이었으므로 실서비스의 평균 N에 대한 근거가 이 레포엔 없다.**

**그래도 결론 유지:** 직렬 I/O는 코드로 확정된 사실이고, **Lambda는 대기 중에도 동시성 슬롯을 붙잡는다.** 건당 2.6초 → 2.1초면 동시성 한도 1,000에서 385건/s → 476건/s(**+23.8%**).

```java
// 핸들러 필드로 두어 워밍 컨테이너에서 재사용
private final ExecutorService io = Executors.newFixedThreadPool(8);

List<byte[]> sources = payload.sourceKeys().stream()
    .map(k -> CompletableFuture.supplyAsync(() -> download(bucket, k), io))
    .toList().stream().map(CompletableFuture::join).toList();

// 복사 제거
RequestBody.fromContentProvider(() -> new ByteArrayInputStream(bytes), bytes.length, "image/jpeg")
```

---

### M-5. 멱등키 처리 두 가지 — 동시 요청은 500, 다른 내용은 조용히 옛 결과

**위치:** `ComposeService.java:53-57`, `:68`

**(a) 동시 요청 한쪽이 500(GEN-091)** — `findByUserAndIdempotencyKey`(`:53`)와 `save`(`:68`) 사이에 락이 없고, `ComposeJob`의 id가 `GenerationType.IDENTITY`(`:38-40`)라 `save()`가 즉시 INSERT를 낸다. `uk_compose_job_user_idempotency`(`:31-33`) 위반 → `DataIntegrityViolationException` → `GlobalExceptionHandler`에 `DataAccessException` 계열 핸들러가 없어 `handleUnexpected`(`:93`) → `GlobalErrorCode.java:27 INTERNAL_SERVER_ERROR('GEN-091')`.

`:52` 주석이 *"정확히 동시에 온 중복은 (user, key) unique가 막는다(한쪽은 실패하지만 데이터는 안전)"*이라고 인지했다 — **데이터 판정은 맞지만 그 실패가 사용자에게 500으로 나간다는 사실은 다루지 않았다. 방어가 절반만 돼 있다.**

> 정정: ~~"더블클릭이 방아쇠, 하루 500건의 5xx"~~ → `ComposeRequest`의 idempotencyKey 스키마 설명이 *"합성 버튼을 누를 때마다 새로 만들고, 네트워크 재시도에는 같은 값을 다시 보낸다"*로 계약을 명시한다. 계약대로면 더블클릭은 서로 다른 키 두 개를 만들어 충돌 자체가 안 난다. 500이 나려면 **첫 요청이 아직 응답을 못 준 상태에서 클라이언트가 동일 바디로 재전송**해야 한다. "하루 500건"은 근거 없는 수치다.

**(b) 요청 내용을 대조하지 않는다** — `:53-57`은 찾은 Job이 있으면 `return ComposeJobResponse.from(existing.get())`으로 즉시 돌려준다. `frameId`도 `sourceKeys`도 비교하지 않고, **정규화·소유권 검사·프레임 관문이 아예 실행되지 않는다.**

프론트가 UUID를 '버튼 누를 때마다'가 아니라 '화면 진입 시 한 번' 만드는 흔한 버그를 내면: 프레임 A로 합성 → DONE → 프레임 B를 골라 다시 누름 → **서버가 202와 함께 프레임 A의 jobId를 돌려주고, 폴링이 즉시 DONE을 받아 사용자는 프레임 A 결과를 다시 본다.** 프레임 B는 시도조차 되지 않았다. **서버 로그에 에러가 한 줄도 없고, 원본 4장은 첫 합성 때 이미 지워져 재시도도 못 한다.**

클라이언트 배포 한 번으로 전 사용자에게 동시 발생하는데 서버 지표(에러율·지연·FAILED 수)는 **100% 정상**이라 관측으로 절대 못 잡는다. 표준 멱등키 구현(Stripe 등)은 요청 본문 지문을 함께 저장하고 불일치 시 409를 낸다.

```java
// (a) 경합
try {
    job = composeJobRepository.save(...);
} catch (DataIntegrityViolationException e) {
    return ComposeJobResponse.from(reloadInNewTx(userId, idempotencyKey));  // 202로 재생
}
// 최소한이라도: GlobalExceptionHandler에 DataIntegrityViolationException → 409 CONFLICT

// (b) 지문
ALTER TABLE compose_job ADD COLUMN request_fingerprint CHAR(64);
// SHA-256(frameId + 정규화된 sourceKeys)
// 같으면 기존 Job 반환, 다르면 409 (MEDIA-0xx "같은 멱등키로 다른 요청이 왔다")
// 요점: 클라이언트 버그가 조용히 잘못된 결과를 만드는 대신 소리를 내게 만드는 것
```

---

### M-6. 합성 요청에 rate limit도 요금제 쿼터도 없다

**위치:** `ComposeController.java:63-69`

- 앞단에 아무 제한이 없다. `SecurityConfig.java:68-70`의 필터는 `JwtAuthenticationFilter` 하나뿐
- `grep` 결과 media/frame 패키지에 rate limit 코드 **0건**. 그런데 프로젝트에는 `auth/email/EmailRateLimit.java`가 **존재한다** — **"몰라서"가 아니라 합성에만 안 붙였다**
- 멱등키는 방어가 아니다(같은 키만 막는다)
- 요금제는 프레임 **보관 개수**만 제한한다(`FrameSubscriptionPolicy`). 월 합성 횟수 제한은 인터페이스에 없다
- **지적보다 나쁜 점:** `ComposeService.java:139-146 validateSourceOwnership`은 prefix만 보므로 **실제 존재하지 않는 가짜 key 4개로도 요청이 통과한다 — 공격자는 업로드조차 할 필요가 없다**

**정정된 붕괴 메커니즘:** ~~"429로 전원 실패 + 30초마다 재투입되는 되먹임"~~ →
- `docs/measurement-2026-08-23.md:34-42`, `adr-0001:56-59`: 동기 호출이던 옛 코드가 429로 FAILED가 됐고, **지금의 EVENT는 초과분을 Lambda 내부 큐에 쌓는다.** 정상 사용자는 '튕기는' 게 아니라 '지연'된다
- 재투입 최소 간격은 30초가 아니라 **10분**(`claim`이 `started_at`을 찍고 `findStalled`가 `< staleBefore`를 요구, `application.yaml:133` stale-after=10m). 폭주 루프가 아니다
- 비용 천장: 한도당 시간당 $0.12. 한도 1000이면 $120, 이 계정(한도 10)이면 $1.2. ~~"시간당 $36 / $2,000대"~~는 성립 불가

**남는 진짜 문제:** 한 계정이 동시성 슬롯과 내부 큐를 독점해 **다른 사용자의 합성을 수 분~무기한 지연**시킨다. 이건 실재하고 방어가 없다. event age 300초를 넘기면 PENDING으로 되돌아가 최장 10분 뒤 재투입되는 사이클이 된다.

```java
// (1) Redis가 이미 있다 — EmailRateLimit과 같은 방식, publicId당 분당 N건 토큰 버킷 → 429
// (2) FrameSubscriptionPolicy에 assertComposeQuota(userId, 월 사용량) — 비즈니스 요구사항이기도 하다
// (3) 블라스트 반경 차단: Lambda에 reserved concurrency 200 — 콘솔 한 줄, 즉시 적용 가능
// (4) validateSourceOwnership에서 S3 headObject로 존재 확인 → 가짜 key는 400
```

---

### M-7. 탈퇴·실패 경로에서 원본 4장과 결과물이 S3에 남는다

> `orphaned-source-photos-unrecoverable` + `withdrawal-orphans-inflight-job-result` 병합.

**위치:** `UserMediaDeletionHandler.java:29-31`, `ComposeJobRepository.java:22`

- 원본 4장을 지우는 코드는 `ComposeService.java:100` 한 줄뿐 — **성공한 Job만** 지운다. `failJob`(`:103-105`)에는 삭제가 없다
- 탈퇴 삭제는 `findS3KeysByUserId` + `findThumbnailKeysByUserId` + `findResultKeysByUserId` 셋뿐이고, 그 JPQL은 `SELECT j.resultKey ... AND j.resultKey IS NOT NULL`(`:22`)이라 **`source_key_1~4`를 안 본다.** 그다음 `deleteByUserId`로 행을 지운다
- 다른 `UserDeletionHandler` 구현 6개(coupon/frame/payment/subscription/terms/media)를 전부 열었고 source key를 지우는 경로는 없다
- **진행 중 Job은 더 나쁘다:** `resultKey`는 `complete()`에서만 채워지므로 PENDING Job은 key를 하나도 못 모은 채 행만 사라진다. 그런데 결과 key는 `resultKeyFor(publicId, jobId)`로 **결정적으로 계산 가능하다 — 지울 수 있는데 안 지운다**

**실패 시나리오 (탈퇴 + 진행 중 Job):** Job #9001 PENDING → 새벽 01:00 탈퇴 배치가 행과 그때까지의 S3 파일을 지움 → 몇 초 뒤 Lambda가 `uploads/users/{publicId}/fourcuts/job-9001.png`를 **새로 쓴다**(이미 사라진 사용자의 얼굴 사진) → 성공 통지가 오면 `completeJob`의 `findById`가 비어 `IllegalStateException("합성 Job이 사라졌다: 9001")` → 메시지 안 지움 → 5회 재배달 후 DLQ. **결과 PNG는 어떤 DB 행도 가리키지 않아 앞으로 어떤 코드도 찾아 지울 수 없다.**

**정정:** ~~"영원히 회수할 수 없다"~~ → 원본 key는 `uploads/users/{publicId}/fourcuts/sources/` 라는 **결정적 prefix** 아래 있어 `ListObjectsV2` 한 번으로 전량 열거·삭제가 된다. `FourcutSourceUploadPathStrategy.java:10-12` 주석이 그 설계 의도를 명시했다. **회수 불가가 아니라 '아직 안 건 청소 규칙'이다.**

**지적이 놓친 것:** 진짜 큰 누수는 실패 Job(1%)이 아니라 **업로드만 하고 합성을 안 한 파일**이다. compose_job 행 자체가 없어 DB 기반 수집으로는 원천적으로 못 잡는다. **그래서 정답은 탈퇴 핸들러 보강이 아니라 Lifecycle 규칙이다.**

**수치:** 월 100만 합성 실패율 1% = 1만 건 × 4장 × 3MB = 월 120GB, 1년 1.4TB × $0.023 = 월 $33. **비용보다 개인정보다** — 탈퇴자 얼굴 사진이 남는 건 파기 의무 위반이고 감사에서 바로 "왜 남았는지 설명할 수 있는가"로 이어진다.

```
① S3 Lifecycle: uploads/users/*/fourcuts/sources/ 만료 7일
   → 성공·실패·좀비·탈퇴·미합성 전부를 한 번에 덮는다 (H-3의 수정과 같은 항목)
② UserMediaDeletionHandler: resultKey가 null인 Job도 resultKeyFor/thumbnailKeyFor로 계산해 삭제 목록에 넣는다
   (두 메서드 모두 이미 static이다). 더 확실하게는 탈퇴 시 그 사용자의 PENDING Job을 먼저 FAILED로 막는다
③ completeJob에서 Job이 없을 때 예외 대신 WARN + 정상 반환 — 절대 성공할 수 없는 상황을 5번 재시도하지 않게
```

---

### M-8. Lambda 배포·런타임 설정 3건 — 안 쓰는 Netty 4MB, 힙 미고정, 콜드 스타트 미측정

**위치:** `compose-lambda/build.gradle:43`, `compose-lambda/README.md:73-83`

**(a) zip에 안 쓰는 것이 들어 있다** — `compose-lambda/build/dist/compose-lambda.zip` 실측 **14,479,057바이트**, 항목 59개, 해제 15.44MB
| 항목 | 크기 | 실제 사용 |
|---|---|---|
| apache-client 계열(httpclient/httpcore/commons-codec) | 1.50MB | ✅ 동기 `S3Client`가 이걸 고른다 |
| **netty 계열 14개 + netty-nio-client** | **약 4.16MB** | ❌ `S3AsyncClient`가 코드에 없다 — **한 줄도 안 돌아간다** |
| `previous-compilation-data.bin` | 0.09MB | ❌ Gradle 증분 컴파일 내부 파일. 원인은 `build.gradle:43 from compileJava`(JavaCompile 태스크 출력 전체) |

**(b) JVM 힙 상한이 코드·설정 어디에도 없다** — 레포 전수 grep(`Xmx|MaxRAMPercentage|JAVA_TOOL_OPTIONS`, build 제외) 결과 나온 5줄이 전부 무관하다(산문 언급 2건, gradlew 래퍼 자기 옵션 2건). `README.md:83`의 "메모리 2048MB: 6000×4000 캔버스(~96MB) + 원본 디코드 + JVM 오버헤드 여유분"은 방향이 맞지만 **실제 힙이 얼마인지 확인한 기록이 없다.**

실측: 캔버스 4000×6000 TYPE_INT_ARGB = 24,000,000px × 4B = **91.55MiB**. `MaxRAMPercentage = 25.0 {default}` → 2048MB 함수의 힙은 **512MB**. 여유가 2048MB가 아니라 그 1/4이고, **C-3의 폭탄이 정확히 이 512MB에서 터졌다.**

> 단서: "Xmx 180MB 성공 / 160MB OOM"은 콘텐츠 의존적이라 일반화하면 안 된다. 노이즈가 많은 사진에서 결과 PNG가 42MB까지 나왔고 `encodePng`의 `ByteArrayOutputStream`(`:232`)이 2배씩 늘며 복사하므로, 인코딩 순간 캔버스 91.6MB + 버퍼 64MB + 결과 42MB ≈ **200MB**가 겹친다. 필요 힙은 200~250MB일 수 있고 **여유가 2.8배가 아니라 2배 근처다.**

**(c) 콜드 스타트가 한 번도 측정된 적이 없다** — `docs/measurement-2026-08-23.md` 146줄 전체를 읽었는데 콜드 스타트 수치가 **한 줄도 없다.** "측정 환경" 표(`:50-59`)에도, "이 측정으로는 확인되지 않는 것"(`:65-78`)에도 없다. `README.md:136`이 "콜드 스타트가 느리면 SnapStart를 검토한다"로 미뤄 뒀다. 실측 참고: `S3Client.builder().build()` 한 줄에 **576ms, 클래스 2,965개 로드**(빠른 개발 머신, C1 전용 기준). **자바 Lambda 포트폴리오에서 이건 면접에서 바로 찔린다.**

**(d) arm64가 아니다** — `README.md:73`의 `create-function`에 `--architectures arm64`가 없어 x86_64로 뜬다. Graviton2는 약 20% 싸다: 월 100만 건 × 2048MB × 2초 = 4,000,000 GB-s × $0.0000166667 = $66.7/월 → **월 $13.3 절감**.

> **인과 관계 주의:** 안 쓰는 netty jar를 빼도 **콜드 스타트는 거의 안 줄어든다.** JVM은 참조되지 않는 클래스를 로드하지 않으므로 netty 4MB는 클래스 로딩 시간에 0을 기여한다(줄어드는 건 패키지 다운로드·해제 구간이고 Lambda는 패키지를 캐시한다). **실제 효과 순서는 (1) SnapStart, (2) arm64, (3) zip 슬림화다.**

```gradle
// (1) Netty 14개 제거
implementation('software.amazon.awssdk:s3') {
    exclude group: 'software.amazon.awssdk', module: 'netty-nio-client'
}
// (2) Gradle 내부 파일 제외
from sourceSets.main.output.classesDirs   // was: from compileJava
```
```bash
# (3) 힙 고정 — 같은 2048MB 안에서 안전 마진이 커진다, 추가 비용 0
JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"   # 1 vCPU급 단일 요청에 SerialGC가 유리
# (4) arm64 + SnapStart(java21 지원, 추가 비용 없음, INIT 스냅샷 복원)
--architectures arm64
# (5) 핸들러 첫 호출에 Runtime.getRuntime().maxMemory()를 로그로 남겨 실제 힙을 문서에 못 박는다
#     C-3의 픽셀 상한까지 넣으면 "입력 상한 × 최악 힙"이 계산 가능해져
#     "2048MB는 어떻게 정했나요"에 실측으로 답할 수 있다
# (6) 콜드/웜 Init Duration·Duration을 CloudWatch에서 뽑아 measurement 문서에 표로
```

---

### M-9. CI 워크플로와 IaC가 리포에 없다 — ADR-0004는 '채택됨'인데 §9는 미완료 to-do

**위치:** `docs/adr-0004-cicd-pipeline.md:3`, `compose-lambda/README.md:21-97`

- `.github` 디렉터리 없음. `git ls-files | grep workflow` → **0건**
- `.tf` / `template.yaml` / `serverless.yml` / `cdk.json` → **0건**
- `settings.gradle:4-6`이 `compose-core`·`compose-lambda`를 포함하고 두 모듈 다 `useJUnitPlatform()`인데, **`./gradlew test`를 사람이 손으로 칠 때만 돈다**

**정정:** ~~"ADR-0004가 이미 돌아간다고 거짓 서술한다"~~ → 같은 문서 **§9 '실행 항목'이 8개 미완료 to-do를 열거**하고 그 2번이 `.github/workflows/deploy.yml` 추가다. '채택됨'은 '결정을 채택했다'는 표준 표기이고 **문서 스스로 아직 안 만들었다고 적어 놓았다.**

**IaC 밖에 있어서 다른 계정에서 재현 불가능한 것:** SQS 큐, DLQ(maxReceiveCount 5), Lambda Destination 매핑, 예약 동시성, IAM 역할 2개, `maximumEventAgeInSeconds=300`.

**특히 이벤트 수명은 코드가 그 값에 의존한다고 주석까지 달아 놓고 값 자체가 코드 밖에 있다** (`ComposeRerunScheduler.java:14-16, 18`: *"staleAfter가 Lambda 이벤트 수명(5분)보다 넉넉해야 한다"*). 기본값 21,600초(6시간)인 계정에 그대로 올리면 `stale-after: 10m`과 충돌해 **같은 Job이 최대 36번 재투입될 수 있다**(21,600÷600=36).

> 단서: 36배가 실제로 나려면 (a) 콘솔에 `maximumEventAgeInSeconds` 방치 **그리고** (b) 6시간 내내 동시성 포화가 동시에 성립해야 한다. `ComposeRerunScheduler.java:18` 주석이 300초를 전제한다는 건 현재 계정엔 설정돼 있다는 뜻이므로, **"지금 36배가 난다"가 아니라 "계정을 옮기면 재현할 근거가 코드에 없다"가 정확하다.**

**IAM도 같은 상태** (`compose-lambda/README.md:48-63). 문서화된 권한 자체는 좁다 — 실행 역할은 `s3:GetObject/PutObject` on `arn:aws:s3:::<버킷>/uploads/*` + 결과 큐 `sqs:SendMessage`, 서버 역할은 Receive/Delete/Invoke. **최소 권한 의식은 분명하다.** 한계는 둘:
1. `uploads/*`는 **전 사용자 공용 prefix**라 IAM으로는 사용자 간 격리를 만들 수 없다 → **C-2가 뚫렸을 때 2차 방어선이 실제로 없었다**
2. 문서와 실제 계정 상태가 일치하는지 **검증할 수단이 없다.** 누가 콘솔에서 `AmazonS3FullAccess`를 붙여도 PR에도 리뷰에도 기록이 안 남는다

`docs/adr-0003:207`도 *"README에 큐·DLQ·IAM·Destination 생성 절차를 추가"*까지가 의도된 범위였음을 확인시킨다. **ADR-0003 §5는 "콘솔 설정은 코드 리뷰에 안 잡히고, IaC도 없어서 누가 바꿔도 아무도 모른다"고 이미 인정했다 — 인정을 해결로 바꾸는 단계다.**

```yaml
# .github/workflows/ci.yml — 최소한 테스트 게이트는 실제로 존재해야 '채택됨'이 참이 된다
on: [push, pull_request]
jobs:
  test:
    steps:
      - run: ./gradlew test jacocoTestReport   # 3개 모듈 전부
```
```hcl
# infra/compose.tf — README의 CLI 값이 이미 정답이므로 옮겨 적는 수준
resource "aws_lambda_function_event_invoke_config" "compose" {
  maximum_event_age_in_seconds = 300
  maximum_retry_attempts       = 2
  destination_config { on_success { destination = aws_sqs_queue.result.arn } ... }
}
resource "aws_sqs_queue" "result" { redrive_policy = jsonencode({ maxReceiveCount = 5, ... }) }
# + aws:SourceArn 조건으로 SendMessage 주체를 해당 함수로 좁힌다
# + reserved_concurrent_executions
# + uploads/system/ prefix 분리 (나중에 사용자 격리를 IAM으로도 걸 수 있게)
```
```
# CloudWatch 알람: DestinationDeliveryFailures, ApproximateAgeOfOldestMessage
#   → 통지가 조용히 사라지는 경로(README:64-66)를 지금은 아무도 안 보고 있다
```

> **확인됨(문제 없음):** `.env`는 git 이력에 단 한 번도 커밋된 적이 없고(`.gitignore:46`, `.dockerignore` 모두 등록), `application.yaml`에 하드코딩된 비밀 기본값도 없다.

---

## 4. 포트폴리오 완성도 (LOW)

### L-1. 폴링이 요청마다 users를 한 번 더 읽는다 — 피크에서 쿼리가 2배

**위치:** `ComposeService.java:80-85`

`getJob`이 `getUser(publicId)` → `userRepository.findByPublicId`로 users를 먼저 읽고 그 엔티티를 `findByIdAndUser`에 넘긴다. **폴링 1회 = SELECT 2회.** 인덱스는 둘 다 정상이다(`uk_users_public_id`, PK+user_id). 저장소 전체에 `@Cacheable`·`@EnableCaching`이 0건이라 Redis도 이 경로엔 안 걸린다. `findByIdAndUser`는 user 엔티티가 아니라 **user_id만 필요**하므로 **회피 가능한 왕복**이다.

**QPS 계산 (가정 명시):** 합성 1건이 Lambda에서 8.3초(실측), 체감 12~15초. 컨트롤러 문서가 "1~2초 간격 폴링"을 지시하므로(`ComposeController.java:41, :74`) 1.5초 × 15초 = **합성 1건당 약 10회**.
- 하루 100만 × 10 = 1,000만 GET/일 → 1,000만/86,400 = **평균 115.7 QPS**
- 저녁 4시간 50% 집중 → 500만/14,400s = **피크 347 QPS** → 쿼리로 **694 q/s**, 그 절반(347 q/s)이 제거 가능한 users 조회

**폴링이 서비스 전체 DB 부하의 지배 항목이다** — 합성 요청(11.6/s)의 **30배**가 상태 조회다. 파이프라인을 아무리 최적화해도 DB에서 가장 큰 것은 폴링이다.

**검증 결과 안전으로 판정한 3건 (면접에서 방어 가능한 지점)**
| 항목 | 판정 |
|---|---|
| `findStalled`의 N+1 | **없다.** `ComposeJobRepository.java:41 JOIN FETCH j.user`가 있어 `ComposeService.java:119`의 `job.getUser().getPublicId()`는 초기화된 엔티티를 읽는다. 없었다면 30초마다 20회 추가 SELECT × 인스턴스 수. 엔티티 대신 `ComposeRequestedEvent`로 내보내는 것(`:112-113`)도 옳은 처리 |
| `completeJob`의 LAZY user 추가 SELECT | **없다.** `UserMedia.of`(`:47-52`)는 null 체크와 필드 대입뿐이고, INSERT는 프록시의 **식별자만** 필요하다. 통지 1건 = 정확히 쿼리 3개(findById + INSERT + UPDATE) |
| HikariCP 10 | **병목 아니다.** 4대 기준 1대 부하 = 폴링 87 QPS×2ms + POST 9/s×10ms + 소비자 13/s×5ms ≈ **동시 커넥션 0.31개**. 풀 10의 상한 3,333 tx/s는 필요량의 100배 이상. 15대까지 늘려도 150 < max_connections 151 |

**단, 마진의 크기를 결정하는 건 폴링 1회당 쿼리 수다.** DB p99가 3ms→50ms로 튀면 풀 10의 상한이 200 tx/s로 떨어져 4대 800 tx/s에 여유가 15%밖에 안 남는다. users 조회를 없애면 그 여유가 115%가 된다.

> **추가 위험:** `open-in-view: false`가 `application-local.yaml:9`에만 있고 **운영용 프로파일 yaml 자체가 저장소에 없다.** 나중에 prod 프로파일을 만들며 이걸 빠뜨리면 OSIV 기본값(true)이 요청 내내 커넥션을 붙잡아 위 마진 계산이 통째로 바뀐다.

```java
// findByIdAndUser_PublicId(jobId, publicId) — 왕복 2→1, 폴링 쿼리 694 → 347 q/s
// 이 패턴은 getJob뿐 아니라 requestCompose·미디어 API 전반에 반복된다
// + 응답에 retryAfterMillis를 실어 초반 3초, 이후 지수 증가 → 합성당 10회 → 5회 (347 → 174 QPS)
```

---

### L-2. `completeJob`/`fail` 상태 검사가 원자적이지 않다 — 실제 방어선은 unique 하나

> `complete-job-check-not-atomic` + `double-render-source-delete-order` + `fail-notification-overwrites-success` + `sibling-job-source-deletion` 병합.

**위치:** `ComposeService.java:89-101`, `ComposeJob.java:118-134`

`findById`(`:90`) → status 검사(`:92`) → UserMedia save(`:96`) → `job.complete`(`:98`)가 READ COMMITTED에서 **락 없이** 돈다. 같은 Job에 Success 통지가 2건 오면 두 인스턴스가 모두 검사를 통과할 수 있다. **실제로 막는 건 `UserMedia.java:25`의 `s3_key` unique 하나**이고, 그게 성립하는 유일한 이유는 `resultKeyFor`가 jobId만으로 만드는 결정적 key이기 때문이다.

**즉 안전성이 "결과 key는 결정적이다"라는 암묵 규칙에 매달려 있다** — 누가 key에 UUID나 타임스탬프를 넣는 순간 최후 방어선이 사라진다. `ComposeService.java:127-128` 주석이 이 트레이드오프를 **명시적으로 적어둔 의도된 설계**다.

**파생 증상 4가지 (전부 데이터는 안전, 관측/UX 오염)**
| 증상 | 위치 | 결과 |
|---|---|---|
| 유니크 충돌 | `:96` | ERROR 로그 1줄 + 약 30초 재배달 지연 |
| 이중 렌더 시 두 번째가 원본 소실로 실패 | `:100` | **이미 DONE인 Job에 "[합성 통지] 영구 실패" WARN**이 찍혀 알림을 오염시킨다. `ComposeJob.fail`이 `status != PENDING`이면 무시하므로 DONE은 안 뒤집힌다 |
| 성공 통지 DLQ 유실 + 재실행분 영구 실패 | `:103-105` | 결과 PNG가 S3에 있는데 Job이 FAILED. **다만 `completeJob`이 안 돌았으므로 원본 4장이 남아 있어 재요청 가능** |
| 형제 Job이 원본을 지움 | `:100` | 같은 사진으로 프레임 A·B를 둘 다 뽑으면 두 번째가 `NoSuchKeyException` 원문을 그대로 받는다 |

**과장 정정 (기록)**
- ~~"1번 항목 때문에 이 경합이 상시 경로"~~ → C-1의 결과는 'Job이 **한 번** 늦게 투입된다'이지 '두 번 렌더된다'가 아니다. 이중 렌더가 되려면 첫 invoke 후 **staleAfter 10분**이 지나도록 통지가 안 와야 하는데, `ComposeRerunScheduler.java:18-19` 주석이 밝히듯 staleAfter는 **일부러 Lambda 이벤트 수명(5분)보다 길게 잡아 그 창을 닫아둔 값**이다
- ~~"Hikari 풀(10)을 점유해 소비자가 멈춘다"~~ → 소비자는 스레드 1개(`:84`)라 어떤 경우에도 커넥션을 1개만 잡는다. **자기 자신이 풀을 소진시킬 수 없다**
- ~~"`fail()`이 result_key를 안 본다"~~ → 공허한 지적이다. `resultKey`를 쓰는 곳은 `complete()` 하나뿐이고 거기서 `status=DONE`을 같은 문장에서 찍으므로 **`status` 검사가 곧 `resultKey` 검사다**
- 형제 Job 삭제는 **문서화된 제품 결정의 부작용**이다 — `ComposeController.java:53-54` Swagger가 *"성공하면 원본 4장을 서버가 지운다. 같은 사진으로 다른 프레임에 합성하려면 원본을 다시 올려야 한다"*고 굵게 적었다. 다만 **이미 202를 받은 두 번째 작업이 나중에 죽는 경우는 그 안내로 예방할 수 없다**
- `failureReason`은 `ComposeJobResponse` Schema에 *"사용자에게 그대로 보여줄 문구는 아니다"*라고 API 계약으로 못박혀 있다 — 원문을 화면에 띄우는 건 클라이언트 선택이다

```java
// 상태 전이를 claim과 같은 조건부 UPDATE로
@Modifying @Query("""
    UPDATE ComposeJob j SET j.status = DONE, j.resultKey = :key, j.mediaId = :mediaId
    WHERE j.id = :jobId AND j.status = PENDING
    """)
int complete(...);   // 1을 돌려줄 때만 UserMedia를 만든다 → 진 쪽은 예외 없이 조용히 끝난다

// ComposeJob.fail이 실제로 바꿨는지 boolean 반환 → 안 바꿨으면 WARN 대신 debug
// Lambda가 시작 시 결과 key를 HeadObject로 확인해 있으면 렌더 스킵 → 이중 렌더 비용 소멸
// requestCompose에서 같은 sourceKey를 쓰는 PENDING Job이 있으면 409 (202 후 지연 실패 대신 즉시 400)
// failureReason을 사용자용 문구로 매핑 (NoSuchKey → "원본 사진이 이미 사용되어 사라졌습니다")
// 원본 삭제를 S3 Lifecycle에 맡기면(H-3/M-7) 이 경합이 통째로 사라진다
```

---

### L-3. PENDING에 만료가 없어 사용자가 언제 포기할지 알 수 없다

**위치:** `ComposeService.java:80-85`

`getJob`은 만료 판정 없이 현재 status를 그대로 돌려준다. `ComposeJob`에 `deadline`/`expiresAt` 컬럼이 없고, `ComposeJobResponse`는 `jobId`/`status`/`mediaId`/`failureReason` 4필드뿐이라 **"언제까지 기다리면 되는지" 정보를 클라이언트에 전혀 안 준다.** `ComposeController` Swagger(`:73-80`)는 "1~2초 간격으로 호출한다"까지만 적고 포기 시점을 정의하지 않는다.

H-2의 좀비 Job은 status가 영원히 PENDING이라 프론트가 무한히 폴링한다. 사용자는 로딩 스피너를 보다 앱을 끄고, 다시 들어와도 같은 화면이다.

> 정정: ~~"초당 667 QPS가 영구히 쌓인다"~~ → 폴링은 **클라이언트가 도는 것**이라 앱을 닫으면 즉시 0이 된다. 지적문 자신이 "사용자는 앱을 끄고"라고 적고 다음 문단에서 영구 부하로 계산했다 — 두 문장이 모순이다. 또 `ComposeRerunScheduler.java:50`의 "N건 재투입" 로그가 좀비 누적 신호를 남긴다 — **지표가 아니라 로그라는 게 약점이지 신호가 0인 건 아니다.**

```java
// expires_at(생성 + 15분) 컬럼 + getJob에서 status==PENDING && now > expiresAt이면 FAILED로 응답
// 응답에 retryAfterMs와 expiresAt을 실어 프론트가 폴링을 언제 멈출지 판단하게 한다
// H-2의 attempts 상한과 묶으면 "5회 초과 → FAILED 확정 → 종료 상태 전달"이 한 번에 해결된다
```

---

### L-4. SQS 통지의 `resultKey`를 그대로 믿는다 — 서버가 이미 갖고 있는 정답과 대조 안 함

**위치:** `ComposeResultConsumer.java:175` → `ComposeService.java:89-101`

`completeJob(jobId, payload.resultKey(), payload.thumbnailKey())` — jobId도 key도 전부 메시지 본문에서 온 값이다. **서버는 `resultKeyFor(publicId, jobId)`로 정답을 이미 계산할 수 있는데(`:129`) 대조하지 않는다. 대조 비용이 0인데 안 한다.**

**위협 전제는 IAM 침해 이후다.** 정상 경로에서 그 값은 서버가 `LambdaComposeExecutor.java:41-43`에서 만든 payload가 Destination을 통해 그대로 돌아오는 것이라 사용자 입력이 끼어들 자리가 없다. 큐에 쓸 수 있는 주체는 `README.md:57-63` 기준 Lambda 실행 역할뿐.

**반증된 시나리오 2개**
- ~~"resultKey가 null이면 조용히 쌓인다"~~ → `UserMedia.java:25 @Column(nullable=false)`라 INSERT가 터지고, `ComposeResultConsumer.java:156-160`이 예외 시 메시지를 안 지워 5회 후 DLQ로 간다. **조용히가 아니라 시끄럽게 쌓인다**
- ~~"피해자의 완성 네컷을 내 보관함에"~~ → 같은 25행 `unique=true`. 피해자 job-137이 이미 DONE이면 그 s3Key 행이 있으므로 공격자 INSERT가 유니크 위반으로 실패. **성립하는 건 '아직 완료 안 된 jobId 선점'뿐이고 그건 데이터 탈취보다 DoS에 가깝다**

```java
// 두 줄짜리 하드닝 — 여전히 넣을 값어치가 있다 (비용: 문자열 비교 1회)
String expected = ComposeService.resultKeyFor(job.getUser().getPublicId(), jobId);
if (!expected.equals(payload.resultKey())) {
    log.warn("결과 key 불일치"); metrics.increment("compose.notification.key_mismatch"); return;
}
// 더 깔끔하게: completeJob(Long jobId)로 시그니처를 바꿔 key를 인자로 받지 않는다
//   → 통지는 "끝났다"는 신호로만 쓰이고 key는 신뢰 경계 밖으로 나가지 않는다
// + 큐 정책에 aws:SourceArn 조건으로 SendMessage 주체를 실제로 좁힌다
```

---

### L-5. 결과 key가 완전히 예측 가능하고 presigned URL은 24시간

**위치:** `ComposeService.java:129`, `S3FileStorageService.java:28`

`uploads/users/{publicId}/fourcuts/job-{jobId}.png`이고 jobId는 IDENTITY 순차 Long이다. **업로드 4종 전략은 전부 UUID를 쓴다**(`FourcutSourceUploadPathStrategy:22`, `FrameComponentUploadPathStrategy:20`, `FrameUploadPathStrategy:20`, `ProfileUploadPathStrategy:20`) — **"key는 추측 불가해야 한다"는 원칙이 이 프로젝트에 이미 있고 결과물만 벗어나 있다.**

주석 `:127-128`이 결정성의 이유를 밝힌다 — *"재실행이 겹쳐도 같은 객체를 덮어쓰므로 고아 파일이 안 생긴다."* **의도된 설계이고 멱등성 측면에서 옳다.**

**단독으로는 악용 경로가 없다.** key를 알아도 presign을 받아낼 창구가 `FileController.java:89-93`(남의 key 403)과 소유자 조건 쿼리들로 막혀 있다. **이 항목이 실제 피해로 바뀌는 유일한 통로가 C-2(a)이고, 그게 고쳐지면 영향이 0에 수렴한다.**

> 정정: publicId 노출은 이 항목의 문제가 아니다 — `S3Keys.java:14-16 userRoot`가 모든 key에 publicId를 넣으므로 UUID key인 프로필 사진 URL을 공유해도 똑같이 노출된다. **결정적 key와 무관한 구조적 성질이다.**

```java
// 결정성은 유지하되 추측 불가능하게 — 고아 방지 이득을 그대로 지킨다
ALTER TABLE compose_job ADD COLUMN job_token CHAR(36);
// uploads/users/{publicId}/fourcuts/{jobToken}.png
// 조회용 presign 만료는 24시간 → 15분~1시간. 업로드 PUT과 조회 GET을 같은 상수로 묶어 쓰지 않는다.
```

---

### L-6. `ComposeHandler`의 thumbnailKey null 관용 — 전환이 끝난 호환 코드

**위치:** `compose-lambda/.../ComposeHandler.java:67`

`if (payload.thumbnailKey() != null)` — thumbnailKey를 만드는 곳은 `ComposeService.java:75`와 `:122` 둘뿐이고 둘 다 `thumbnailKeyFor`(`:134-136`)를 부르며 그건 문자열 연결이라 null을 못 낸다. `LambdaComposeExecutor.java:41-43`도 그대로 전달한다. **현재 서버 코드로는 false 경로가 도달 불가능하다.**

> 정정: ~~"죽은 코드/구멍"~~ → ADR-0004는 **SHA 태그 롤백을 파이프라인의 핵심 기능으로 둔다**(`:117-119, 183, 299`). 서버 이미지는 롤백 가능하고 Lambda는 별도 배포다 — '썸네일 이전 서버 이미지로 롤백'은 지금도 `workflow_dispatch` 한 번으로 가능한 **정상 운영 동작**이고, 이 분기는 그때 Lambda가 죽지 않게 하는 안전판이다. **'죽은 코드'가 아니라 '전환 종료 후 정리 후보'다.**

지적된 무증상 회귀(리팩터링 중 thumbnailKey 누락 → 예외 없이 원본만 올리고 `{"ok":true}` → 썸네일만 조용히 안 나옴, `UserMedia.java:28-31` 주석 탓에 조회 코드도 null을 정상 취급)는 **관용 분기의 부작용이지 지금 존재하는 결함이 아니다.**

```java
// 전환 종료를 선언할 때 계약으로 바꾼다
if (payload.thumbnailKey() == null) throw new IllegalArgumentException("thumbnailKey는 필수다");
// UserMedia.of(user, s3Key, displayName) 3인자 오버로드(:43-45)도 합성 경로에선 안 쓰이니 정리 대상 확인
```

---

### L-7. 렌더 최적화 2건 — 같은 스티커 반복 디코드 / BILINEAR 확대

**(a) 레이어마다 다시 디코드** — `FourcutRenderer.java:121`

`ComposeSpec.referencedAssetKeys()`(`:32-41`)가 LinkedHashSet으로 **다운로드**만 중복 제거하고, 렌더 루프는 `decode(requireAsset(assets, layer.source()))`를 레이어 수만큼 돈다. `assets` 맵은 `byte[]`를 담지 `BufferedImage`를 캐시하지 않는다.

**실측 정정 (PNG 스티커, 워밍 후 10회 평균) — 원래 주장이 3배 부풀려졌다:**
| 스티커 | 원래 주장 | 실측(useCache=true) | 실측(false) |
|---|---|---|---|
| 300×300 | 7.3ms | 3.0ms | 1.5ms |
| 600×600 | 18.8ms | **5.4ms** | 3.1ms |
| 1200×1200 | 70.3ms | **15.1ms** | 10.9ms |

같은 600×600 스티커 20개면 낭비가 ~~356ms~~ → **약 103ms**(렌더 2초 대비 5%). **영향 범위도 좁다:** 캐시는 같은 key가 반복될 때만 듣고(20개가 서로 다른 그림이면 이득 0), H-4를 먼저 고치면 같은 낭비가 자동으로 45% 줄어 **두 항목의 이득이 겹친다.**

**정작 진짜 위험은 M-3이다** — `FrameCreateRequest.java:65-67`에 `@Size`가 없어 레이어 500개짜리 프레임을 만들면 디코드가 500회 돈다. 중복 제거로는 못 막는(전부 다른 그림이면 무의미) 문제라, **고쳐야 할 건 캐시보다 개수 상한이다.**

```java
// drawLayers 진입 전 (배경도 같은 맵 사용)
Map<String, BufferedImage> decoded = new HashMap<>();
BufferedImage image = decoded.computeIfAbsent(layer.source(), k -> decode(assets.get(k)));
// ⚠️ zIndex 순서는 절대 건드리면 안 된다 — 그래서 그룹핑이 아니라 맵 캐시가 안전하다
```

**(b) BILINEAR는 실수가 아니라 계약일 가능성이 높다** — `FourcutRenderer.java:306-311`

슬롯 좌표 확인: CLASSIC 1700×1200, WIDE 2400×1700, GRID/POLAROID 1700×2400. 실제 렌더를 돌려 `drawCover`의 scale을 찍으니 GRID 슬롯에 1000×1500을 넣으면 정확히 **1.70배**로 나온다.

> **하지만 "원본 1000×1500"은 제품 규격이 아니라 부하테스트 이미지 크기다.** `docs/measurement-2026-08-23.md:57`이 유일한 출처이고, 같은 문서 `:78`이 *"측정용 프레임은 배경만 있고 컴포넌트가 없다… 절대 시간은 운영값과 다르다"*고 못박는다. **실제 폰 카메라 사진(3024×4032)이면 GRID 1700×2400 슬롯에 대해 scale = max(1700/3024, 2400/4032) = 0.595 — 확대가 아니라 축소다.**

> **그리고 BICUBIC이 오히려 계약을 깬다.** `FourcutRenderer.java:38-39` 주석: *"수식·순서·상수는 프론트 composeFrame.ts(drawFrameOnce)와 1:1이다 — 어긋나면 예외 없이 결과물만 편집 화면 미리보기와 틀어진다."* 브라우저 canvas `drawImage`의 기본 `imageSmoothingQuality`는 **'low'**(크롬 기준 대체로 bilinear급)다. ~~"프론트는 브라우저 기본 보간(대체로 더 고품질)을 쓴다"~~는 **방향이 거꾸로다.**

**남는 실질 문제는 보간 알고리즘이 아니라 "업로드 원본의 최소 해상도를 아무 데서도 검증하지 않는다"이다**(`ComposeService.java:58-63`은 key 소유권만). 작은 원본이 오면 슬롯 크기까지 확대돼 그려진다.

> 참고 — `scaleToLongEdge`(`:252-263`)는 **정확하다.** 2000×6000 → target 171×512, 반감 루프가 1000×3000 → 500×1500 → 250×750에서 멈추고(125×375는 171 미만) 마지막 250×750 → 171×512, 비율 1.46배. 주석(`:249-251`)이 말한 "보간이 잘 듣는 배율(≤2배)" 안에 정확히 들어온다.

---

## 5. 잘한 점 — 면접에서 강조할 지점

### S-1. 조건부 UPDATE 한 방으로 선점 — 잠금 없는 다중 인스턴스 안전

**위치:** `ComposeJobRepository.java:29-37`, `ComposeService.java:107-110`

```sql
UPDATE compose_job SET started_at = :now
WHERE id = :jobId AND status = 'PENDING'
  AND (started_at IS NULL OR started_at < :staleBefore)
```
영향 행 수가 1일 때만 true. **비관적 락도 Redis 분산 락도 없이 DB의 행 단위 원자성만으로 "한 번만 실행"을 만들었다.**

**깨뜨릴 지점을 찾아봤지만 실패했다:** 두 인스턴스가 동시에 쳐도 InnoDB가 같은 행을 직렬화하고, 뒤 트랜잭션은 갱신된 `started_at`을 현재값으로 다시 읽어 `started_at < staleBefore`가 거짓이 되므로 0행 → **정확히 한 쪽만 true를 받는다.**

**설계의 우아함:** `started_at < staleBefore` 조건 덕에 **선점한 인스턴스가 죽어도 유예 시간 뒤 자동 회수된다 — 선점과 만료 회수가 같은 한 문장 안에 있다.** 두 번째 방어선은 `ComposeJob.complete/fail`(`:118-134`)의 `status != PENDING` 조기 반환, 세 번째는 jobId 기반 결정적 결과 key(`ComposeService.java:129-136`)라 중복 실행이 겹쳐도 같은 S3 객체를 덮어쓴다.

**규모 영향:** 인스턴스를 2대에서 20대로 늘려도 코드를 고칠 필요가 없다. **락 서버가 없으니 락 서버 장애라는 실패 모드 자체가 없고**, 경합 비용은 UPDATE 한 번의 행 잠금(수 μs)뿐이다.

> 단서: `findStalled`(`:39-46`)에는 SKIP LOCKED나 분산이 없어 N대가 같은 상위 20행을 함께 읽는다. 진 쪽 비용은 0행 UPDATE 한 번이라 20대 기준 30초당 40회 인덱스 조회 수준이다.

**→ 면접 포인트:** 여기에 `@DataJpaTest` 4케이스만 추가하면 "**설계도 잘하고 검증도 한다**"가 된다(현재 이 로직이 어느 테스트에서도 실행되지 않는다 — H-6).

---

### S-2. 실패를 '영구 vs 일시적'으로 나누고, 모르는 실패는 아무것도 하지 않는 쪽으로 기본값을 잡았다

**위치:** `ComposeResultConsumer.java:180-191`

`RetriesExhausted`만 영구 실패로 확정하고, `EventAgeExceeded`·`ZeroReservedConcurrency`·**앞으로 AWS가 추가할 값까지** 전부 PENDING 유지로 떨어뜨린다. 주석이 근거를 숫자로 남겼다 — *"2026-08-21 측정에서 429가 정확히 그렇게 죽었다."*

같은 원칙이 `ComposeWorker.java:50-58`에도 반복되고(접수 실패는 FAILED가 아니라 PENDING), `ComposeResultNotification.java:13-15`가 condition을 enum이 아니라 String으로 받는 것도 같은 결정의 연장이다 — *"condition을 enum으로 받지 않는다… 모르는 값은 건드리지 않는다로 떨어져야 한다."*

**규모 영향:** AWS가 새 condition 값을 추가하는 날(과거에 실제로 있었다) **배포 없이 안전하게 넘어간다.** 재시도 가능한 실패를 영구 손실로 확정하는 사고 — 개선 전 30% 유실의 원인 그 자체 — 가 구조적으로 재발하지 않는다.

**→ 면접 포인트:** "모르는 것은 실패로 확정하지 않는다"는 원칙을 **측정 수치(30% 유실 → 0%)와 함께** 말하면 설득력이 크다.
**→ 단, H-2와 세트로 말할 것:** *"유실을 막았지만 반대쪽 끝에 벽이 없다는 걸 알고 있고, `attempts` 컬럼으로 상한을 거는 게 다음 단계다."* — 약점을 먼저 아는 것도 실력이다.

---

### S-3. 렌더 코드를 별도 모듈로 뽑아 '로컬 픽셀 = 운영 픽셀'을 빌드 구조로 강제

**위치:** `settings.gradle:4`, `compose-core/build.gradle:1-2`, `ComposeJob.java:74-76`

- `settings.gradle:4`가 `compose-core`를 서버와 Lambda가 함께 쓰는 모듈로 분리
- `compose-core/build.gradle:1-2` 주석: *"스프링을 모른다 — 여기 스프링이 들어오는 순간 Lambda 배포물에도 끌려간다."* **`grep -rn springframework compose-core/src/main/java` 결과가 실제로 0건이라 선언이 지켜지고 있다**
- 잭슨 버전을 양쪽 다 같은 Boot BOM(4.1.0)으로 고정(`compose-core/build.gradle:19-20`, `compose-lambda/build.gradle:21-22`)해 `@JsonTypeInfo` 다형성 배경(`BackgroundAttributes.java:8-11`)의 직렬화가 어긋나지 않게 했다. **애노테이션만 `com.fasterxml` 좌표(잭슨 3에서도 유지)를 쓰고 databind는 양쪽 다 `tools.jackson`이라 어긋날 수 없다**
- `ComposeJob.spec`(`:74-76`)이 요청 시점 프레임의 **스냅샷**이라 프레임이 수정·삭제돼도 재실행이 같은 결과를 낸다. `frame_id`에 FK를 안 건 것(`:56-58`)도 같은 결정이다
- `build.gradle:29-33`의 `allprojects { options.encoding = 'UTF-8' }`도 "빌드는 성공하는데 한글만 깨지는" 실제 사고(200건)를 겪고 남긴 방어다

**규모 영향:** Lambda 동시성이 수천으로 늘어도 서버와 Lambda가 다른 픽셀을 그릴 위험이 **컴파일 단계에서 차단**된다. 스냅샷 덕에 프레임 테이블이 수백만 행으로 커지고 사용자가 프레임을 자유롭게 수정해도, 재실행 배치가 원본 Frame을 조회하지 않으므로 **조인 부하와 '수정된 프레임으로 다시 그려지는' 버그가 동시에 사라진다.**

**→ 면접 포인트:** "왜 멀티모듈로 나눴나"에 '중복 제거'가 아니라 **'두 실행 환경이 같은 픽셀을 그린다는 보장을 빌드가 강제한다'**로 답할 수 있다.

---

## 6. 우선순위 실행 순서

**작업량:** S = 반나절 이하 / M = 1~3일 / L = 1주 이상

### 0단계 — 오늘 안에 (S만, 효과 대비 비용 최소)

| # | 항목 | 위치 | 작업량 | 효과 |
|---|---|---|---|---|
| 0-1 | `claim`에 `REQUIRES_NEW` | `ComposeService.java:107` | **S** | **즉시 접수 부활. 평균 15초 단축, 투입 상한 제거** |
| 0-2 | 원본 검사 prefix를 `fourcuts/sources/`까지 (한 줄) | `ComposeService.java:140` | **S** | 자기 자산 자폭 차단 |
| 0-3 | `ImageIO.setUseCache(false)` | `FourcutRenderer` static | **S** | 디코드 3.3배, 렌더 11~28% 단축 |
| 0-4 | `timeout-per-shutdown-phase: 30s` | `application.yaml:5` | **S** | 배포마다 나던 DLQ 유실 차단 |
| 0-5 | `application-local.yaml`에 소비자 `enabled: false` + `matchIfMissing=false` | `ComposeResultConsumer.java:31` | **S** | 로컬이 운영 통지 가로채기 차단 |
| 0-6 | `.gitignore`에서 `docs/`, `monitoring/` 제거 | `.gitignore:44-45` | **S** | 설계 증거 3,251줄 + 대시보드 복구 |
| 0-7 | LambdaClient 타임아웃 명시(5s/2s/retry 2) | `ComposeConfig.java:14` | **S** | **0-1과 반드시 세트** — 128초 스레드 점유 차단 |
| 0-8 | `FrameCreateRequest.components`에 `@Size(max=30)` | `:65-67` | **S** | 정체불명 500 + Lambda 390MB 차단 |
| 0-9 | `ContentType.WEBP` 제거 또는 FOURCUT_SOURCE 화이트리스트 | `ContentType.java:16` | **S** | 확정 실패 5%를 415로 앞당김 |
| 0-10 | Lambda reserved concurrency 200 (콘솔) | — | **S** | 블라스트 반경 차단 |
| 0-11 | `LambdaComposeExecutor` 주석 3줄 + 죽은 주석 2곳 | `:13-15`, `ComposeHandler.java:52`, `build.gradle:56` | **S** | 면접 방어 |

### 1단계 — 이번 주 (보안 + 데이터 파괴 차단)

| # | 항목 | 위치 | 작업량 | 효과 |
|---|---|---|---|---|
| 1-1 | `S3Keys.assertOwnedBy` 하나로 통합 + 프레임 4경로 적용 + 테스트 | `ComposeSpecAssembler:32,49`, `FrameService`, `FileController:90`, `UserService:49` | **M** | **남의 사진 읽기·반출·삭제 전면 차단. 정규화 누락(`..`)도 함께 해결** |
| 1-2 | 픽셀 상한(5천만) — `decode` 안에 헤더 검사 | `FourcutRenderer.java:213` | **S** | Lambda OOM 차단. 서버·Lambda 양쪽 자동 적용 (비용 113μs) |
| 1-3 | `ComposeFlowTest` + `ComposeResultConsumerTest` + `claim` @DataJpaTest | `src/test/media/**` | **M** | **0-1이 다시 죽는 것을 막는 유일한 장치.** S3DeleteFlowTest 패턴 재사용 |
| 1-4 | S3 Lifecycle: `sources/` 만료 7일 | IaC | **S** | 고아 원본 전 경우(성공·실패·좀비·탈퇴·미합성) 일괄 해결 |
| 1-5 | 결과물 PNG → JPEG q0.90 (+ 키 확장자·contentType·프론트) | `FourcutRenderer:77`, `ComposeService:130`, `ComposeHandler:62` | **M** | **저장 14.4TB→2.7TB, 월 $1,319 절감. 인코딩 74%→ 절반** |
| 1-6 | 합성 API rate limit (Redis, publicId당 분당 N건) | `ComposeService.requestCompose` | **S** | 동시성 독점 차단 |

### 2단계 — 2주 내 (처리량 + 관측성)

| # | 항목 | 위치 | 작업량 | 효과 |
|---|---|---|---|---|
| 2-1 | S3 삭제를 소비자 스레드에서 분리(@Async) + DeleteObjects 배치 | `S3DeleteListener`, `S3FileStorageService:94` | **M** | **76ms → 16ms. 소비 13→62건/s (4.7배)** |
| 2-2 | Micrometer Counter/Timer/Gauge + `application.yaml`에 prometheus 노출 | `ComposeResultConsumer`, `ComposeWorker`, `ComposeRerunScheduler` | **M** | 통지 유실·PENDING 적체가 처음으로 보인다 |
| 2-3 | `attempts` 컬럼 + `claim`에 상한 + 초과 시 FAILED 확정 | `ComposeJob`, `ComposeJobRepository:37` | **M** | 좀비 무한 재투입 종료 |
| 2-4 | 기동 시 `maximumEventAge` 계약 검증 (10줄) | `LambdaComposeExecutor` | **S** | **IaC 없이 할 수 있는 가장 강한 방어** |
| 2-5 | invoke 실패 시 `started_at` 되돌리기 | `ComposeWorker:58` | **S** | 10분 → 30초 (20배) |
| 2-6 | 멱등키 지문 + `DataIntegrityViolationException` → 409 | `ComposeService:53,68`, `GlobalExceptionHandler` | **M** | 조용한 오답 → 시끄러운 409 |
| 2-7 | 재실행 batch-size 근거 있는 값(200~400) + 적응형 + 로그를 실제 claim 수로 | `ComposeRerunScheduler:31,50` | **S** | 1시간 장애 회수 17.3h → 2h 이하 |
| 2-8 | `findByIdAndUser_PublicId` + `retryAfterMillis` | `ComposeService:80` | **S** | 폴링 쿼리 694 → 174 q/s |

### 3단계 — 한 달 내 (구조)

| # | 항목 | 위치 | 작업량 | 효과 |
|---|---|---|---|---|
| 3-1 | **`compose_job` 파티셔닝 (PK를 `(id, created_at)`으로)** | `ComposeJob:22` | **L** | **0.9TB/년 → 70GB 평형. 테이블이 작은 지금이 아니면 못 한다** |
| 3-2 | `.github/workflows/ci.yml` (3모듈 test + jacoco) | — | **S** | ADR-0004 §9 실행 항목 2번. '채택됨'이 참이 된다 |
| 3-3 | `infra/` Terraform — 큐·DLQ·Destination·eventAge 300·예약동시성·IAM | — | **M** | 계정 이전·재해 복구에서 전제가 조용히 깨지는 것을 막는다 |
| 3-4 | 결과물 보관 정책 배치(Glacier IR 전환 또는 삭제) + 비용 모델에 저장 항목 | `Retention.java:6`, `cost-model.md` | **M** | **1년 5.3PB / 월 $132,500 → 1/10~1/100.** 면접에서 가장 강한 그림 |
| 3-5 | Lambda 다운로드/업로드 병렬화 + `fromContentProvider` | `ComposeHandler:48,53,60,68` | **M** | 건당 0.5초, 동시성 처리량 +23.8% |
| 3-6 | SnapStart + arm64 + Netty 제외 + 콜드스타트 측정 문서화 | `compose-lambda/**` | **M** | 요금 20% + 콜드스타트 표가 measurement 문서에 생긴다 |
| 3-7 | 상태 전이를 조건부 UPDATE로 (`complete`) | `ComposeService:98` | **S** | unique 하나에 매달린 정합성 해소 |
| 3-8 | 소비자 병렬화 (2-1 이후에만) | `ComposeResultConsumer:84` | **M** | 62 → 140건/s |
| 3-9 | `spec_json` 정규화(`compose_spec` 테이블) | `ComposeJob:75` | **M** | 행 2.3KB → 0.5KB (840GB → 180GB) |

---

### 순서에 대한 한 가지 경고

**0-1(claim REQUIRES_NEW)을 0-7(LambdaClient 타임아웃) 없이 단독 배포하면 안 된다.**

지금은 `claim`이 먼저 죽어서 `LambdaComposeExecutor.invoke`가 요청 스레드에서 **실행되지 않는다**(프로브에서 `executor.execute` 호출 0회로 확인). 0-1을 고치는 순간 그 코드가 살아나고, SDK 기본값(4회 시도 × (연결 2초 + 소켓 30초) ≈ **128초**)이 그대로 HTTP 요청 스레드에 붙는다.

Tomcat 기본 200 스레드, 피크 200 RPS 중 합성 5%면 초당 10건 → **20초 만에 200 스레드 전부 소진**. 합성 장애 하나가 로그인·결제까지 멈춘다.

**→ 0-1, 0-7, 그리고 가능하면 전용 executor(@Async)를 한 커밋으로 묶어라.**