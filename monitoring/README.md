# 관측 스택 사용법

부하 테스트 결과를 Grafana 로 보기 위한 설정이다.
루트의 `docker-compose.yml` 에도 같은 이름의 서비스가 있지만 그쪽은 부하 테스트용이고,
이 폴더의 `docker-compose.view.yml` 은 **결과를 조회하기 위한 것**이다. 포트만 다르다.

## 함께 보기

| 문서 | 목적 |
|---|---|
| [01-test-targets.md](01-test-targets.md) | 부하 테스트 대상 8개 — 코드 상태, 재현 방법, 실측 결과 |
| [02-environment.md](02-environment.md) | 부하 테스트 환경 설계 근거 (컨테이너 vs 로컬/EC2, 부하 프로파일) |
| [03-grafana.md](03-grafana.md) | Grafana 대시보드·PromQL 가이드 |

## 실행

```bash
docker compose -f monitoring/docker-compose.view.yml up -d
```

| 주소 | 용도 |
|---|---|
| http://localhost:3002 | Grafana. 열면 바로 대시보드가 뜬다. 로그인 없음 |
| http://localhost:9091 | Prometheus. 수집 상태 확인용 |
| http://localhost:9091/targets | 앱을 제대로 수집하고 있는지 확인 |

종료는 `docker compose -f monitoring/docker-compose.view.yml down` 이다.
`down -v` 를 붙이면 **측정 데이터까지 지워진다.** 붙이지 않는다.

## 포트를 3002/9091 로 쓰는 이유

루트 compose 는 3001/9090 을 쓴다. 이 PC 에서는 다른 프로젝트 컨테이너가
그 두 포트를 이미 점유하고 있어 충돌한다. 포트만 옮겼고 나머지는 같다.

## 파일 구성

| 파일 | 역할 |
|---|---|
| `prometheus.yml` | 수집 대상과 주기. 앱의 관리 포트 8081 을 5초마다 긁는다 |
| `grafana/provisioning/datasources/datasource.yml` | Prometheus 데이터소스 자동 등록 |
| `grafana/provisioning/dashboards/provider.yml` | 대시보드 파일 자동 로드 설정 |
| `grafana/provisioning/dashboards/harucut-compose.json` | 대시보드 본체 |
| `docker-compose.view.yml` | 조회 전용 스택 |

대시보드는 파일로 관리한다. Grafana UI 에서 고쳐도 저장되지 않으니
바꿀 일이 있으면 `harucut-compose.json` 을 수정한다. 10초 안에 반영된다.

## 데이터가 안 보일 때

**1. 시간 범위부터 확인한다.**
오른쪽 위 시간 범위가 측정한 시각을 포함해야 한다. 기본값은 최근 24시간이다.

**2. 수집 대상이 살아 있는지 본다.**
http://localhost:9091/targets 에서 `harucut-app` 이 UP 인지 확인한다.
DOWN 이면 앱이 안 떠 있거나 관리 포트(8081)에 닿지 못하는 것이다.

- 앱을 compose 로 띄웠다면 `app:8081` 이 UP 이어야 한다
- 앱을 IDE 에서 직접 실행했다면 `host.docker.internal:8081` 이 UP 이어야 한다

**3. 볼륨이 맞는지 본다.**
예전 측정 데이터는 `harucut_harucut-prometheus-data` 볼륨에 들어 있다.

```bash
docker volume ls --filter name=harucut
```

이 볼륨이 없으면 그 PC 에서는 측정한 적이 없는 것이다.
측정한 PC 에서 이 폴더를 그대로 복사해 실행하면 같은 볼륨에 붙는다.

## 보존 기간

루트 compose 는 `retention.time=7d` 다. 측정하고 일주일이 지나면 데이터가 사라진다.
이 파일은 **90d** 로 잡아 두었다. 포트폴리오에 쓸 자료라면 지워지면 안 되기 때문이다.

보존 기간과 무관하게 남기고 싶으면 스냅샷을 뜬다.

```bash
curl -XPOST http://localhost:9091/api/v1/admin/tsdb/snapshot
```

응답에 나온 이름의 폴더가 볼륨 안 `/prometheus/snapshots/` 에 생긴다.
이걸 복사해 두면 원본이 만료돼도 복원할 수 있다.

## 새로 측정할 때

부하 테스트는 루트 compose 의 `load` 프로파일로 돌린다.

```bash
./gradlew bootJar
docker compose --profile load up -d --build
```

이때 루트 스택도 `harucut-prometheus` 라는 이름의 컨테이너를 만들기 때문에
**이 조회 스택과 동시에 띄울 수 없다.** 한쪽을 내리고 쓴다.
