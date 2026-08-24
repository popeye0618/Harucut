# 테스트 환경

---

## 결정: 앱을 컨테이너로 띄운다

호스트에서 `bootRun` 하지 않는다. 이 하나가 세 가지를 동시에 결정한다.

| | 컨테이너 (선택) | 호스트 `bootRun` |
|---|---|---|
| Prometheus가 찾는 법 | 서비스 이름 (`app:8080`) | `host.docker.internal:8080` |
| actuator 막기 | **호스트 포트를 안 열면 끝** | 포트 분리 + 방화벽 필요 |
| 스펙 고정 | **CPU·메모리 제한 가능 → 재현 가능** | 내 노트북 전체. 다른 게 돌면 오염 |
| 고치고 다시 띄우기 | `bootJar` → `up --build` | 바로 뜬다 |

### 이유

1. **"EC2 대신 로컬로 한다"의 근거가 자원 제한을 걸 수 있다는 것이었다.**
   호스트에서 띄우면 그 근거가 사라진다 — 측정할 때마다 스펙이 달라진다.
2. [`Dockerfile.local`](../Dockerfile.local)이 미리 빌드된 jar를 복사만 한다.
   gradle을 컨테이너 안에서 안 돌리니 재빌드 루프가 짧다.

### 포기한 것

**코드를 고칠 때마다 `bootJar` 한 단계가 더 붙는다.** #1 수정처럼 반복이 잦은 작업에서 거슬린다.
개발은 `bootRun`, 측정만 컨테이너로 나눠도 되지만 — **두 환경의 숫자를 섞어 비교하면 안 된다.**

---

## 결정: 로컬에서 전부. EC2는 마지막에, 필요하면

### 왜 EC2를 먼저 파지 않나

격리성 판단 자체는 맞다. 그런데 **Docker를 쓰는 순간 격리는 이미 얻었다.**
compose에 CPU·메모리 제한을 걸면 "고정된 스펙"까지 재현된다 —
EC2를 파는 이유의 절반이 이걸로 해결된다.

그리고 [01-test-targets.md](01-test-targets.md)의 항목 중 **EC2가 필요한 것이 없다.**
절반은 부하 자체가 필요 없고, 나머지도 동시 1000 안쪽이다.

### EC2가 진짜 필요해지는 경우

| 경우 | 대안 |
|---|---|
| 부하 도구와 서버를 물리적으로 분리하고 싶을 때 | 로컬에서 부하 도구 CPU를 같이 관찰하면 오염 여부를 판단할 수 있다 |
| 다중 서버(#2) | 로컬에서 앱 컨테이너 2개 + nginx로 재현된다 |

### EC2를 쓸 때의 함정

1. **t 계열(t3/t4g)은 CPU 크레딧이 있다.** 몇 분 부하를 주면 크레딧이 떨어져
   스로틀링된다. 그래프가 뚝 떨어지는데 그걸 앱 문제로 착각하기 쉽다.
   부하 테스트를 EC2에서 하려면 크레딧 없는 계열(m/c)을 써야 하고, 그건 싸지 않다.
2. **인스턴스를 꺼도 EBS 볼륨은 계속 과금된다.** 끝나면 인스턴스가 아니라 **볼륨까지** 지운다.

---

## 로컬 부하 테스트의 한계 — 알고 시작할 것

한 머신에서 앱·DB·Redis·모니터링·부하 도구를 다 돌린다는 게 근본 한계다.

- **부하 도구가 서버의 CPU를 뺏는다.** 느려진 게 서버 때문인지 부하 도구 때문인지
  구분이 안 된다. → **부하 도구의 CPU도 같이 봐야** 판단이 된다.
  부하 도구가 CPU를 100% 먹고 있으면 그 측정치는 버린다.
- **네트워크 지연이 0이다.** 실제 환경보다 훨씬 좋은 숫자가 나온다.
  → **절대값을 믿지 말고 "개선 전 vs 후" 상대 비교로만 쓴다.**
- **Windows + Docker Desktop**이면 컨테이너가 WSL2 VM 안에서 돈다.
  파일 I/O가 느려서 MySQL이 실제보다 나쁘게 나올 수 있다.
- **JVM 워밍업.** 처음 수천 요청은 JIT 컴파일 전이라 느리다. 이 구간은 빼고 본다.
  Grafana로 보면 그래프가 처음에 높다가 뚝 떨어지는 게 눈에 보인다.

---

## 부하 테스트 프로파일 (`application-load.yaml`)

**local을 매번 손으로 고치지 않는다.** 그러면 언젠가 `show-sql: true`인 채로
측정하고 그 숫자를 믿게 된다.

| 항목 | 값 | 이유 |
|---|---|---|
| `spring.jpa.show-sql` | **`false`** | **가장 중요.** local은 `true`다. 켜두면 콘솔 I/O가 병목이 되어 측정하는 게 앱 성능이 아니라 터미널 속도가 된다 |
| `spring.jpa.hibernate.ddl-auto` | `validate` | 대량 데이터에서 기동 시 ALTER가 몇 분 걸린다 |
| 커넥션 풀 크기 | **명시** | 기본 10을 그대로 쓰더라도 "의도한 10"이어야 비교가 된다 |
| actuator 노출 | 필요한 것만 | 아래 참조 |
| 합성 풀 `queueCapacity` | **2** | #1 재현용. 100건을 실제로 요청하지 않기 위해 |

---

## 시딩 전략

**결정: 큰 숫자가 필요한 항목에만 붙인다. 그 전까지는 효율적으로.**

### 왜

- 대량 시딩은 **디스크와 시간을 실제로 많이 먹는다.** 수십만 행이면 MySQL 데이터가 수 GB다.
- 그런데 **대부분의 항목은 큰 숫자가 필요 없다.** #5의 filesort는 10만이든 50만이든 똑같이 보인다.
- 큰 숫자가 실제로 필요한 건 **#6(배치 소요시간)** 정도다. 여긴 건수가 곧 시간이라
  숫자를 줄이면 문제가 안 보인다.

### 항목별 필요량 (착수 시 확정)

| 항목 | 필요량 | 비고 |
|---|---|---|
| #1 합성 | 없음 | 큐 상한을 낮춰서 재현 |
| #2 세션 | 없음 | 사용자 1명 |
| #3 커넥션 | 적음 | 조회 대상만 있으면 됨 |
| #4 프레임 | 시스템 프레임 500개 | 컴포넌트 10개씩 |
| #5 UserMedia | 중간 | filesort는 적은 수에서도 보인다. 인덱스 전후 비교가 목적 |
| #6 배치 | **큼** | 건수가 곧 소요시간. 여기만 대량 필요 |
| #7 쿠폰 | 없음 | 쿠폰 1개 |
| #8 메일 | 없음 | 지연 주입이 핵심 |

### 넣는 방법 — JPA로 넣지 말 것

대량 행을 JPA로 저장하면 몇 시간 걸린다.
`LOAD DATA INFILE` 또는 JDBC 배치 INSERT를 쓴다. **필요해지는 시점에 정한다.**

---

## Prometheus + Grafana 구성 시 결정할 것

### (1) 긁는 주기(scrape interval)

Prometheus 기본은 **1분**이다. 5분짜리 테스트면 데이터 점이 5개다. 아무것도 안 보인다.

**5초로 내린다.** 운영에서는 낭비지만 부하 테스트에서는 이게 맞다.
Grafana 새로고침 주기도 같이 내린다.

### (2) `/actuator/prometheus`를 어떻게 막나 — **관리 포트를 분리한다 (8081)**

이 엔드포인트는 힙 상태·커넥션 풀·모든 엔드포인트 호출 통계를 그대로 뱉는다.
[`SecurityPaths.java`](../src/main/java/com/harucut/config/SecurityPaths.java)의
`PUBLIC` 배열에 넣으면 **안 된다.**

| 방법 | 트레이드오프 |
|---|---|
| **관리 포트 분리**(`management.server.port: 8081`) ← 선택 | 8081을 publish 안 하면 compose 네트워크 안에서만 보인다. 포트가 하나 늘고 Prometheus도 그 포트를 봐야 한다 |
| 같은 포트(8080) + 호스트 포트 미개방 | **성립하지 않는다** ↓ |

> **한 번 틀렸던 판단이라 남긴다.**
> 처음엔 "앱이 컨테이너니까 호스트 포트를 안 열면 끝"이라고 봤다. 틀렸다.
> **앱의 8080은 반드시 호스트로 열어야 한다** — 부하 도구가 API를 때려야 하기 때문이다.
> 같은 포트에 actuator가 있으면 `/actuator/prometheus`도 같이 열린다.
> 그래서 포트를 나누고, **API 포트만 publish** 한다.

단, 막는 것과 **필터 체인을 통과시키는 것은 다른 문제다.**
[`SecurityConfig`](../src/main/java/com/harucut/config/SecurityConfig.java)의
`anyRequest().hasAnyRole("USER","ADMIN")`에 걸리면 Prometheus가 401을 받는다.
actuator 경로를 permitAll 해야 한다 — **네트워크로 막고, 인증은 통과시킨다.**

### (3) Prometheus가 앱을 어떻게 찾나 — **서비스 이름**

현재 루트 [`docker-compose.yml`](../docker-compose.yml)에는 **앱이 없다** (mysql·redis·mailpit뿐).
앱이 있는 건 [`deploy/frontend/docker-compose.yml`](../deploy/frontend/docker-compose.yml)이다.

→ 루트 compose에 **app 서비스를 추가해야 한다.** 그러면 Prometheus는 `app:8080`으로 찾는다.
`host.docker.internal`은 쓰지 않는다.

---

## 의존성 — Boot 4에서 구조가 바뀌었다 (프로퍼티는 안 바뀌었다)

**Boot 3 감각으로 의존성을 쓰면 틀린다.** Boot 4에서 metrics가 actuator에서 분리되어
`spring-boot-starter-micrometer-metrics`라는 별도 스타터가 생겼다.

다만 `spring-boot-starter-actuator` 4.1.0의 POM이 그것을 **이미 끌어온다**
(Maven Central POM에서 확인). 그래서 결과적으로 필요한 건 두 개다.

| 아티팩트 | 역할 | 버전 |
|---|---|---|
| `org.springframework.boot:spring-boot-starter-actuator` | 엔드포인트 + 계측 기반 | Boot 플러그인이 관리 |
| `io.micrometer:micrometer-registry-prometheus` | Prometheus 포맷 출력 | micrometer-bom(1.17.0)이 관리 |

**둘 다 버전을 명시하지 않는다.** springdoc처럼 버전을 직접 박아야 하는 경우가 아니다.

### 프로퍼티 이름은 Boot 3과 같다 (확인함)

아티팩트만 옮겨졌고 설정 키는 그대로다. `spring-boot-micrometer-metrics-4.1.0.jar`와
`spring-boot-actuator-autoconfigure-4.1.0.jar`의 `spring-configuration-metadata.json`에서 확인.

| 프로퍼티 | 용도 |
|---|---|
| `management.endpoints.web.exposure.include` | 무엇을 노출할지 |
| `management.server.port` | 관리 포트 분리 (이번엔 안 쓴다) |
| `management.metrics.distribution.percentiles-histogram.<meter>` | **p95/p99용 히스토그램** |
| `management.metrics.tags.<key>` | 공통 태그 (인스턴스 구분) |

`PrometheusScrapeEndpoint` 클래스도 그대로 있다 → `/actuator/prometheus` 경로 동일.

---

## 볼 화면 넷

1. **엔드포인트별 p95/p99 응답시간, 처리량, 에러율** — 증상
2. **HikariCP active / idle / pending** — `pending > 0`이면 커넥션 대기
3. **JVM 힙 + GC 시간** — 응답시간이 주기적으로 튀면 대개 GC
4. **CPU** — 앱과 부하 도구 각각

1~3은 Actuator가 **기본으로** 뱉는다. 추가 코드가 없다.
4의 컨테이너 리소스는 cAdvisor가 필요한데, 처음엔 과하니 나중에.
