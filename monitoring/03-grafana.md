# Grafana 사용법

---

## 띄우기

```
./gradlew bootJar
docker compose --profile load up -d --build
```

`--profile load` 가 없으면 **평소처럼 인프라(mysql·redis·mailpit)만** 뜬다.
기존 작업 흐름을 안 건드리려고 그렇게 잡았다.

| 주소 | 무엇 |
|---|---|
| http://localhost:8080 | 앱 API — 부하 도구가 때리는 곳 |
| http://localhost:9090 | Prometheus |
| http://localhost:3001 | **Grafana** |
| http://localhost:8025 | Mailpit |

> Grafana 기본 포트는 3000 인데 프론트가 그 포트를 쓸 수 있어 3001 로 열었다.

### 제일 먼저 확인할 것

Grafana를 열기 전에 **Prometheus가 앱을 실제로 긁고 있는지** 본다.
http://localhost:9090 → 상단 **Status → Targets**.

`harucut` 잡이 **UP** 이어야 한다. DOWN 이면 Grafana를 아무리 만져도 빈 그래프만 나온다.

DOWN 일 때 볼 순서:
1. 앱이 떴나 — `docker compose logs app`
2. Prometheus 가 뭐라고 하나 — `(Invoke-RestMethod "http://localhost:9090/api/v1/targets").data.activeTargets | Format-Table scrapePool, health, lastError`
   → `lastError` 에 이유가 그대로 찍힌다 (연결 거부인지 401인지)
3. 401/403 이 나오면 → SecurityConfig 에서 actuator 경로를 permitAll 하지 않은 것

---

## 데이터소스는 이미 붙어 있다

[`grafana/provisioning/datasources/datasource.yml`](grafana/provisioning/datasources/datasource.yml)이
컨테이너 기동 시 자동 등록한다. UI 에서 손으로 붙일 필요 없다.

컨테이너를 지웠다 다시 만들어도 다시 붙는다.

---

## 대시보드 — 두 가지 길

### (A) 기존 대시보드 가져오기 (빠름)

Grafana → **Dashboards → New → Import** → ID 입력 → Load.

| ID | 내용 |
|---|---|
| **4701** | JVM (Micrometer). 힙·GC·스레드·CPU. HikariCP 패널도 들어있다 |

**얻는 것**: 5분 만에 그럴듯한 화면이 나온다. JVM·커넥션은 이걸로 충분하다.

**잃는 것**: **우리가 진짜 보려는 게 안 나온다.** 합성 접수 지연(#1), 배치 스텝 시간(#6),
엔드포인트별 p99 — 남이 만든 대시보드가 우리 문제를 알 리 없다.
그리고 패널이 수십 개라 정작 볼 것을 찾기 어렵다.

### (B) 직접 만들기 (권장)

**항목마다 패널 3~5개짜리 대시보드를 하나씩** 만든다.
#3 볼 때 #6 패널이 화면에 있으면 방해만 된다.

New Dashboard → Add visualization → Prometheus 선택 → 아래 쿼리를 넣는다.

**(A)로 JVM 기본기를 깔고, 항목별 대시보드는 (B)로 만드는 조합이 제일 낫다.**

---

## PromQL — 이 네 개만 알면 된다

### 1. `rate()` — 카운터는 그냥 보면 안 된다

요청 수 같은 카운터는 **계속 커지기만 한다.** 그래프가 우상향 직선이라 아무 정보가 없다.
`rate()` 로 감싸면 **초당 증가량**이 된다.

```
rate(http_server_requests_seconds_count[1m])
```

`[1m]` 은 "최근 1분을 보고 계산" 이라는 뜻이다.
scrape 가 5초니까 1분이면 12개 점으로 계산한다 — 너무 짧으면 튀고, 너무 길면 스파이크가 뭉개진다.

### 2. `sum()` / `sum by ()` — 쪼개거나 합치거나

엔드포인트마다 따로 나오는 걸 하나로 합칠 때:

```
sum(rate(http_server_requests_seconds_count[1m]))
```

엔드포인트별로 보고 싶을 때:

```
sum by (uri) (rate(http_server_requests_seconds_count[1m]))
```

### 3. `histogram_quantile()` — p95/p99

```
histogram_quantile(0.99, sum by (le, uri) (rate(http_server_requests_seconds_bucket[1m])))
```

`le` 를 `sum by` 에 반드시 넣어야 한다. 버킷 경계를 나타내는 라벨이라 이게 빠지면 계산이 안 된다.

> **빈 그래프가 나오면** `application-load.yaml` 의 `percentiles-histogram` 이 안 켜진 것이다.
> Micrometer 는 기본으로 평균과 최대만 준다.

### 4. 게이지는 그냥 쓴다

현재 값을 재는 것(커넥션 수, 큐 깊이, 힙)은 `rate()` 를 씌우면 안 된다.

```
hikaricp_connections_pending
```

---

## 항목별 쿼리

### 공통 — 어느 테스트에서든 띄워두는 4개

| 패널 | 쿼리 |
|---|---|
| 처리량 (RPS) | `sum(rate(http_server_requests_seconds_count[1m]))` |
| p99 응답시간 | `histogram_quantile(0.99, sum by (le, uri) (rate(http_server_requests_seconds_bucket[1m])))` |
| 에러율 | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m])) / sum(rate(http_server_requests_seconds_count[1m]))` |
| 힙 사용량 | `jvm_memory_used_bytes{area="heap"}` |

### #3 커넥션 풀

| 패널 | 쿼리 | 보는 법 |
|---|---|---|
| 대기 | `hikaricp_connections_pending` | **0보다 크면 그게 커넥션 대기다** |
| 사용 중 | `hikaricp_connections_active` | 10 에 붙어 있으면 포화 |
| 획득 시간 | `rate(hikaricp_connections_acquire_seconds_sum[1m]) / rate(hikaricp_connections_acquire_seconds_count[1m])` | 평균 대기 시간 |

### #1 합성 — **관측 지점이 앱 밖으로 나갔다** (2026-08-21)

비동기 전환으로 `composeTaskExecutor` 가 없어졌다. 아래 쿼리들은 **이제 아무것도 반환하지 않는다.**
고장이 아니라 그 풀이 사라진 것이다.

```
executor_queued_tasks{name="composeTaskExecutor"}          ✗ 없다
executor_queue_remaining_tasks{name="composeTaskExecutor"} ✗ 없다
executor_active_threads{name="composeTaskExecutor"}        ✗ 없다
```

#### 그래서 무엇이 어디로 갔나

| 보고 싶은 것 | 전 (Prometheus) | 후 |
|---|---|---|
| 밀린 작업 | `executor_queued_tasks` | **CloudWatch** SQS `ApproximateNumberOfMessages` |
| 얼마나 오래 밀렸나 | — | **CloudWatch** SQS `ApproximateAgeOfOldestMessage` |
| 동시 실행 수 | `executor_active_threads` | **CloudWatch** Lambda `ConcurrentExecutions` |
| 한도에 부딪혔나 | `RejectedExecutionException` 로그 | **CloudWatch** Lambda `Throttles` |
| 죽은 통지 | — | **CloudWatch** DLQ `ApproximateNumberOfMessages` |

**DLQ 깊이가 0이 아니면 그게 제일 급한 신호다.** 통지를 못 읽었다는 뜻이고,
그 Job 들은 `ComposeRerunScheduler` 가 10분 뒤 다시 던질 때까지 `PENDING` 으로 남는다.

#### Grafana 에서 아직 볼 수 있는 것

| 패널 | 쿼리 | 보는 법 |
|---|---|---|
| 접수 지연 | `histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/api/auth/user/media/compose"}[1m])))` | **50ms 언저리여야 한다.** 예전엔 8초였다 — 이 숫자가 비동기 전환의 성과다 |
| 소비자 스레드 | `jvm_threads_live_threads` | `compose-result` 스레드 하나가 상시 떠 있다. 전체 수가 계단식으로 늘면 소비자가 재시작을 반복하는 것 |
| 커넥션 대기 | `hikaricp_connections_pending` | 통지 처리도 DB 를 쓴다. 배치와 겹치면 여기가 먼저 아프다 |

#### CloudWatch 를 Grafana 로 끌어올 수는 있다

Grafana 에 **CloudWatch 데이터소스**가 기본 내장이라 SQS·Lambda 지표를 같은 화면에 놓을 수 있다.

- **얻는 것**: 앱 지표와 AWS 지표를 한 대시보드에서 겹쳐 본다. "접수는 빠른데 큐가 밀린다" 같은 게 한눈에 보인다.
- **잃는 것**: IAM 에 `cloudwatch:GetMetricData`·`cloudwatch:ListMetrics` 를 더 줘야 하고,
  CloudWatch API 는 **조회에 과금된다**. 그리고 지표 해상도가 1분이라
  Prometheus 의 5초와 축이 안 맞는다 — 5초짜리 스파이크는 여전히 안 보인다.

지금은 **급하지 않다.** 필요해지는 시점은 "큐가 밀리는지 앱에서 알 수 없어 답답할 때"다.

#### 없는 것 — 소비자 지표

`ComposeResultConsumer` 는 **로그만 남긴다.** Micrometer 카운터가 하나도 없어서
`condition` 별 통지 수(Success / RetriesExhausted / 그 밖)를 그래프로 못 본다.

`Counter` 셋만 붙이면 **"일시적 실패로 판정해 PENDING 으로 둔 건수"** 가 보이는데,
그게 지금은 로그를 grep 해야만 알 수 있다. 다음에 이 항목을 다시 볼 때 첫 후보다.

### #6 배치

| 패널 | 쿼리 |
|---|---|
| 스텝 소요시간 | `spring_batch_step_seconds_max` |
| 잡 소요시간 | `spring_batch_job_seconds_max` |

**같은 화면에 `hikaricp_connections_active` 를 같이 띄운다.**
배치가 커넥션을 먹는 동안 일반 요청이 굶는 걸 봐야 이 항목의 요지가 드러난다.

### #8 메일

패널은 특별할 게 없다. **볼 것이 다르다.**

```
sum by (uri) (rate(http_server_requests_seconds_count[1m]))
```

메일과 **무관한** uri 의 응답시간이 같이 느려지는지 본다.
그게 이 항목의 증명이다 — 메일 API 만 느린 게 아니라 서버 전체가 죽는다.

---

## 화면 설정 — 안 하면 헛본다

| 설정 | 값 | 왜 |
|---|---|---|
| 시간 범위 (우상단) | **Last 15 minutes** | "Last 6 hours" 로 보면 5초짜리 스파이크가 픽셀 하나로 뭉개진다 |
| 새로고침 | **5s** | scrape 주기와 맞춘다. 더 짧게 해도 새 데이터가 없다 |

부하 테스트를 돌리는 동안은 **시간 범위를 좁게 두고 실시간으로 본다.**
끝난 뒤 분석할 때만 넓힌다.

---

## 대시보드는 저장된다

`harucut-grafana-data` 볼륨이 잡혀 있어서 컨테이너를 내려도 남는다.
`docker compose down -v` 로 볼륨까지 지우면 날아간다 — **`-v` 를 조심한다.**

만든 대시보드를 git 에 남기고 싶으면
Dashboard → Share → Export → **Save to file** 로 JSON 을 받아
`monitoring/grafana/dashboards/` 에 둔다.
