# compose-lambda — 네컷 합성 Lambda

서버(`LambdaComposeExecutor`)가 동기 호출하는 함수다.
그리기 코드는 `compose-core`를 그대로 쓴다 — 로컬(인프로세스)과 픽셀이 같다.

## 배포 zip 만들기

```bash
./gradlew :compose-lambda:buildZip
# → compose-lambda/build/dist/compose-lambda.zip
```

## 함수 만들기 (최초 1회)

1. **IAM 역할**: Lambda 기본 실행 역할(로그 쓰기) + 아래 정책을 추가한다.
   버킷의 `uploads/` 아래만 읽고 쓴다.
   ```json
   {
     "Effect": "Allow",
     "Action": ["s3:GetObject", "s3:PutObject"],
     "Resource": "arn:aws:s3:::<버킷이름>/uploads/*"
   }
   ```
2. **함수 생성**:
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

## 코드 갱신

```bash
./gradlew :compose-lambda:buildZip
aws lambda update-function-code \
  --function-name harucut-compose \
  --zip-file fileb://compose-lambda/build/dist/compose-lambda.zip
```

## 서버 스위치

환경변수 두 개로 실행기를 갈아끼운다 (스프링 relaxed binding):

```bash
COMPOSE_EXECUTOR=lambda            # compose.executor — 기본은 in-process
COMPOSE_LAMBDA_FUNCTION=harucut-compose   # cloud.aws.lambda.compose-function
```

함수 이름 없이 스위치만 켜면 서버가 기동에서 죽는다 (의도된 동작 — 첫 합성 요청에서
죽는 것보다 낫다).

## 참고

- 버킷 이름은 함수 설정이 아니라 **호출 payload에 실려 온다** — 설정의 원천은 서버 한 곳이다.
- 콜드 스타트가 느리면 SnapStart(자바 전용 스냅샷 부팅)를 검토한다.
