# 시나리오 하나를 처음부터 끝까지 돌린다.
#
#   .\run-scenario.ps1 -Label before-pool2  -Image harucut:before -Pool 2  -N 30
#   .\run-scenario.ps1 -Label before-pool20 -Image harucut:before -Pool 20 -N 30
#   .\run-scenario.ps1 -Label after         -Image harucut:after  -Pool 2  -N 30
#
# 매 실행마다 compose_job 을 비운다. 안 그러면 이전 실행에서 남은 PENDING 을
# 재실행 스케줄러가 계속 집어가서 다음 측정에 부하가 섞인다.

param(
  [Parameter(Mandatory = $true)][string]$Label,
  [Parameter(Mandatory = $true)][string]$Image,
  [int]$Pool = 2,
  [int]$N = 30,
  [int]$Timeout = 360
)

# 네이티브 명령(docker, node)은 진행 상황을 stderr 로 낸다.
# Stop 으로 두면 그 출력이 오류로 잡혀 스크립트가 죽는다. 종료 코드로 직접 판단한다.
$ErrorActionPreference = 'Continue'
# Windows PowerShell 5.1 의 Join-Path 는 인자를 두 개만 받는다.
$repo = Resolve-Path (Join-Path (Join-Path $PSScriptRoot '..') '..')
Push-Location $repo
try {
  Get-Content .env | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') { Set-Item -Path "env:$($Matches[1])" -Value $Matches[2].Trim() }
  }

  Write-Host "`n=========================================="
  Write-Host " $Label   이미지=$Image  풀=$Pool  동시요청=$N"
  Write-Host "=========================================="

  Write-Host "`n[1/4] 이전 작업 기록 비우기"
  $pw = $env:MYSQL_ROOT_PASSWORD; $db = $env:MYSQL_DATABASE
  # 비밀번호를 -p 로 주면 mysql 이 경고를 stderr 로 내보내고, PowerShell 은 그걸 오류로 취급한다.
  # MYSQL_PWD 로 넘기면 경고가 없다.
  docker exec -e MYSQL_PWD=$pw harucut-measure-mysql mysql -uroot $db -e "DELETE FROM compose_job; DELETE FROM user_media;"

  Write-Host "[2/4] 앱 교체 후 기동"
  $env:APP_IMAGE = $Image
  $env:POOL_SIZE = "$Pool"
  $env:RUN_LABEL = $Label
  $env:SPRING_PROFILES = 'local,load'
  docker compose -f monitoring/docker-compose.measure.yml up -d --force-recreate app | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "컨테이너 기동 실패" }

  $started = $false
  for ($i = 0; $i -lt 100; $i++) {
    $log = docker logs harucut-measure-app 2>&1 | Out-String
    if ($log -match 'Started HarucutApplication') { $started = $true; break }
    if ($log -match 'APPLICATION FAILED|Application run failed') { throw "앱 기동 실패. docker logs harucut-measure-app 확인" }
    Start-Sleep -Seconds 3
  }
  if (-not $started) { throw "앱이 300초 안에 뜨지 않았다" }

  # 기동 직후 지표가 튀는 구간을 그래프에서 분리하기 위해 잠깐 쉰다.
  Start-Sleep -Seconds 10

  $begin = Get-Date
  Write-Host "[3/4] 부하 발생  시작 $($begin.ToString('HH:mm:ss'))"
  $env:BASE_URL = 'http://localhost:18080'
  if (-not $env:SEED_DIR) { $env:SEED_DIR = Join-Path $env:USERPROFILE '.claude\jobs\0eb3fea8\tmp\seed' }
  node monitoring/loadtest/run.mjs load --n $N --label $Label --timeout $Timeout
  $end = Get-Date

  Write-Host "[4/4] Grafana 시간 범위"
  Write-Host ("  from  {0}" -f $begin.AddSeconds(-20).ToString('yyyy-MM-dd HH:mm:ss'))
  Write-Host ("  to    {0}" -f $end.AddSeconds(20).ToString('yyyy-MM-dd HH:mm:ss'))

  # 최종 상태를 DB 에서 한 번 더 센다. API 폴링이 놓친 것이 없는지 대조하기 위해서다.
  Write-Host "`nDB 최종 상태:"
  docker exec -e MYSQL_PWD=$pw harucut-measure-mysql mysql -uroot $db -e "SELECT status, COUNT(*) AS cnt FROM compose_job GROUP BY status;"
}
finally {
  Pop-Location
}
