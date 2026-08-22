# compose-lambda — 네컷 합성 Lambda

서버(`LambdaComposeExecutor`)가 **비동기로 호출**하는 함수다 (`InvocationType.EVENT`).
호출은 202(접수됨)만 돌려주고, 결과는 **Lambda Destination이 SQS로 보낸 통지**를
`ComposeResultConsumer`가 받아 Job을 DONE/FAILED로 확정한다.
그리기 코드는 `compose-core`를 그대로 쓴다 — 서버와 픽셀이 같다.

## 배포 zip 만들기

```bash
./gradlew :compose-lambda:buildZip
# → compose-lambda/build/dist/compose-lambda.zip
```

## 최초 1회 세팅

**순서가 중요하다: SQS → Lambda → 서버.**
Destination은 비동기 호출에만 붙으므로, 그 사이 옛 서버(동기·자기가 결과를 기록)와
새 서버가 잠시 공존한다. `ComposeResultConsumer`의 `jobId == null` 분기가 그 구간을 위한 것이다.

### 1. SQS 큐와 DLQ

```bash
# DLQ 먼저 — 본 큐의 redrive 정책이 이 ARN을 참조한다
aws sqs create-queue --queue-name harucut-compose-result-dlq

aws sqs create-queue \
  --queue-name harucut-compose-result \
  --attributes '{
    "VisibilityTimeout": "30",
    "MessageRetentionPeriod": "1209600",
    "RedrivePolicy": "{\"deadLetterTargetArn\":\"<DLQ ARN>\",\"maxReceiveCount\":\"5\"}"
  }'
```

- **`VisibilityTimeout: 30`** — 큐 기본값과 같지만 명시한다.
  다만 **소비자가 `ReceiveMessage`마다 30을 직접 넘기므로 이 큐 속성은 덮인다.**
  값을 바꾸려면 `ComposeResultConsumer.VISIBILITY_TIMEOUT_SECONDS`를 고쳐야 한다.
- **`ReceiveMessageWaitTimeSeconds`를 설정하지 않는다.** 설정해도 소용없다 —
  소비자가 `waitTimeSeconds(20)`을 요청마다 넘기고, **요청 파라미터가 큐 속성을 이긴다.**
  롱폴링 값의 단일 출처는 `ComposeResultConsumer.WAIT_SECONDS`다.
- `MessageRetentionPeriod: 1209600`(14일, 최대값) — 서버가 오래 내려가 있어도 통지가 남는다.
- `maxReceiveCount: 5` — 소비자는 처리에 실패하면 메시지를 **지우지 않는다.**
  5회 재배달 후 DLQ로 간다.

### 2. IAM

**Lambda 실행 역할**에 아래 둘을 준다. 버킷은 `uploads/` 아래만 만진다.

```json
{
  "Effect": "Allow",
  "Action": ["s3:GetObject", "s3:PutObject"],
  "Resource": "arn:aws:s3:::<버킷이름>/uploads/*"
}
```
```json
{
  "Effect": "Allow",
  "Action": "sqs:SendMessage",
  "Resource": "<결과 큐 ARN>"
}
```
> Destination 통지를 보내는 주체는 함수가 아니라 Lambda 서비스지만,
> 권한은 **함수의 실행 역할**에서 가져간다. 이게 없으면 통지가 조용히 사라지고
> `DestinationDeliveryFailures` 지표만 오른다.

**서버 역할**에는 `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `lambda:InvokeFunction`을 준다.

### 3. 함수 생성

```bash
aws lambda create-function \
  --function-name harucut-compose \
  --runtime java21 \
  --handler com.harucut.compose.lambda.ComposeHandler::handleRequest \
  --memory-size 2048 \
  --timeout 60 \
  --zip-file fileb://compose-lambda/build/dist/compose-lambda.zip \
  --role arn:aws:iam::<계정ID>:role/<역할이름>
```

- 메모리 2048MB: 6000×4000 캔버스(~96MB) + 원본 디코드 + JVM 오버헤드 여유분
- 타임아웃 60초: 콜드 스타트(자바, 수 초) + 다운로드 + 렌더 + 업로드

### 4. 비동기 호출 설정 + Destination

```bash
aws lambda put-function-event-invoke-config \
  --function-name harucut-compose \
  --maximum-event-age-in-seconds 300 \
  --maximum-retry-attempts 2 \
  --destination-config '{
    "OnSuccess": {"Destination": "<결과 큐 ARN>"},
    "OnFailure": {"Destination": "<결과 큐 ARN>"}
  }'
```

- **`maximum-event-age-in-seconds 300`(5분)은 반드시 준다.**
  기본값은 21600(6시간)인데, 서버의 `compose.stale-after: 10m`이
  **"이벤트 수명 5분 + 함수 타임아웃 60초 + 여유"에서 유도된 값**이다.
  기본값 그대로 두면 재실행 스케줄러가 10분 뒤, 아직 Lambda 큐에 살아 있는 이벤트를
  다시 던진다 — 선점이 데이터는 지켜주지만 같은 합성이 두 번 돌고 호출비가 두 번 나간다.
- **성공과 실패를 같은 큐로 받는다.** 소비자가 `requestContext.condition`으로 가른다:
  `Success` → 완료, `RetriesExhausted` → 영구 실패,
  그 밖(`EventAgeExceeded` 등) → **아무것도 안 한다**(PENDING으로 둬야 재실행이 줍는다).

## 코드 갱신

```bash
./gradlew :compose-lambda:buildZip
aws lambda update-function-code \
  --function-name harucut-compose \
  --zip-file fileb://compose-lambda/build/dist/compose-lambda.zip
```

## 서버 설정

```bash
COMPOSE_LAMBDA_FUNCTION=harucut-compose            # cloud.aws.lambda.compose-function
COMPOSE_RESULT_QUEUE_URL=https://sqs.<리전>...     # cloud.aws.sqs.compose-result-queue-url
```

둘 다 비어 있으면 서버가 **기동에서 죽는다** (의도된 동작 — 첫 합성 요청에서 죽거나
통지를 조용히 흘리는 것보다 낫다).

소비자만 따로 끌 수 있다: `compose.result-consumer.enabled=false`.
끄면 통지를 아무도 안 받으므로 모든 Job이 재실행 스케줄러에 의존하게 된다 —
로컬에서 큐 없이 띄울 때만 쓴다.

## 참고

- 버킷 이름은 함수 설정이 아니라 **호출 payload에 실려 온다** — 설정의 원천은 서버 한 곳이다.
  `jobId`도 같이 실린다. Destination 통지에 원본 payload가 그대로 돌아오므로
  서버가 그걸로 Job을 찾는다 (함수는 이 값을 읽지 않는다).
- 콜드 스타트가 느리면 SnapStart(자바 전용 스냅샷 부팅)를 검토한다.
- 왜 SQS인지, 왜 롱폴링 20초인지는 설계 문서(ADR-0001 / ADR-0003)에 있다.
