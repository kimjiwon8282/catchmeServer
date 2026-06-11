# CatchMe Authentication Cache Load Test

이 k6 테스트는 동일 회원의 JWT 인증 요청에서 Redis 인증 캐시 적용 여부에 따른 지연 시간과 안정 처리 요청률을 비교한다.

측정 범위는 `/api/perf/ping` 요청이 Spring Security 인증 필터를 통과하는 구간이다. 운영 환경 최대 처리량이나 전체 서비스 최대 성능을 의미하지 않는다.

## 사전 조건

- MySQL 실행
- Redis 실행
- 성능 테스트 회원 존재
- Spring Boot 서버 실행
- k6 설치 또는 Docker k6 사용
- k6 환경변수 설정

비밀번호, access token, 실제 계정 정보는 파일에 저장하지 않고 환경변수로 전달한다.

필수 환경변수:

```powershell
$env:BASE_URL="http://localhost:8080"
$env:EMAIL="load-test@example.com"
$env:PASSWORD="테스트 계정 비밀번호"
```

이미 발급한 토큰을 쓰려면 `EMAIL`, `PASSWORD` 대신 다음 값을 설정한다.

```powershell
$env:ACCESS_TOKEN="..."
```

## 서버 실행

캐시 미적용 서버:

```powershell
.\gradlew.bat bootRun `
  --args="--spring.profiles.active=local,perf-nocache"
```

캐시 적용 서버:

```powershell
.\gradlew.bat bootRun `
  --args="--spring.profiles.active=local,perf-cache"
```

`perf-nocache`는 `NoOpCacheManager`를 사용해 JWT 인증 요청마다 `MemberRepository.findById()`를 실행한다. `perf-cache`는 `RedisCacheManager`를 사용하며 워밍업 이후 `memberAuthCache` hit로 DB 조회를 생략한다.

## 기존 Load Test

기존 Load Test는 동일 요청률에서 평균, p95, p99 응답 지연을 비교한다. 기본값은 100 RPS, 2분, 3회 측정이다.

PowerShell 환경변수:

```powershell
$env:BASE_URL="http://localhost:8080"
$env:EMAIL="load-test@example.com"
$env:PASSWORD="테스트 계정 비밀번호"
$env:RATE="100"
$env:DURATION="2m"
$env:PRE_ALLOCATED_VUS="50"
$env:MAX_VUS="300"
$env:WARMUP_REQUESTS="20"
$env:PROFILE="perf-nocache"
$env:RESULT_LABEL="nocache-1"
```

실행:

```powershell
k6 run .\performance\k6\auth-cache-load.js
```

캐시 적용 서버에서는 다음처럼 profile과 label만 바꿔 실행한다.

```powershell
$env:PROFILE="perf-cache"
$env:RESULT_LABEL="cache-1"
k6 run .\performance\k6\auth-cache-load.js
```

기존 Load Test 결과 비교:

```powershell
.\performance\k6\compare-results.ps1 `
  -NoCache .\performance\k6\results\nocache-1.json `
  -Cache .\performance\k6\results\cache-1.json
```

## Stress Test

Stress Test는 요청률을 단계적으로 올리며 캐시 적용, 미적용 구조의 최대 안정 요청률을 비교한다. 각 요청률은 독립된 k6 실행으로 측정하고, 결과를 Load Test와 혼합하지 않는다.

기본 요청률 단계:

```text
100 RPS
200 RPS
400 RPS
600 RPS
800 RPS
```

기본 실행 시간은 단계별 1분이며, 단계 사이에는 10초 대기한다.

캐시 미적용 서버를 실행한 뒤 별도 PowerShell에서:

```powershell
$env:BASE_URL="http://localhost:8080"
$env:EMAIL="load-test@example.com"
$env:PASSWORD="테스트 계정 비밀번호"

.\performance\k6\run-auth-cache-stress.ps1 `
  -Profile perf-nocache `
  -Rates 100,200,400,600,800 `
  -Duration "1m" `
  -Runs 1
```

캐시 적용 서버를 실행한 뒤 별도 PowerShell에서:

```powershell
.\performance\k6\run-auth-cache-stress.ps1 `
  -Profile perf-cache `
  -Rates 100,200,400,600,800 `
  -Duration "1m" `
  -Runs 1
```

실패한 단계 이후에도 기본적으로 다음 요청률을 계속 실행한다. 실패 즉시 중단하려면 `-StopOnFailure`를 추가한다.

대기 시간을 바꾸려면 `-CooldownSeconds`를 사용한다.

```powershell
.\performance\k6\run-auth-cache-stress.ps1 `
  -Profile perf-cache `
  -Rates 400,600 `
  -Duration "1m" `
  -Runs 3 `
  -CooldownSeconds 15
```

## Stress Test 결과

결과 파일:

```text
performance/k6/results/{RESULT_LABEL}.json
```

Stress Test 파일명 규칙:

```text
perf-nocache-stress-100-run1.json
perf-nocache-stress-200-run1.json
perf-nocache-stress-400-run1.json
perf-cache-stress-100-run1.json
perf-cache-stress-200-run1.json
perf-cache-stress-400-run1.json
```

저장 지표:

- 평균 응답 시간
- 중앙값
- p95
- p99
- 최대 응답 시간
- 총 요청 수
- 실제 처리 RPS
- 오류율
- check 성공률
- dropped iterations
- threshold 통과 여부
- 실행 profile
- RATE, DURATION, VU, warmup 설정

결과 JSON에는 `label`, `profile`, `settings`, `metrics`만 저장한다. Access Token, EMAIL, PASSWORD, 전체 `setup_data`, 원본 k6 summary, Authorization header는 저장하지 않는다.

`performance/k6/results/*.json`은 Git에 포함하지 않는다.

## Stress Test 결과 비교

```powershell
.\performance\k6\compare-stress-results.ps1 `
  -ResultsDirectory .\performance\k6\results
```

출력 항목:

```text
Profile
설정 요청률
실제 RPS
평균
p95
p99
최대
오류율
Dropped Iterations
Threshold 결과
안정 처리 여부
```

안정 처리 기준은 다음 조건을 모두 만족해야 `PASS`다.

```text
errorRate < 0.01
checkRate > 0.99
droppedIterations == 0
p95Ms < 200
p99Ms < 500
thresholdsPassed == true
```

`dropped_iterations`가 발생하면 해당 요청률은 안정 처리 실패로 판단한다.

여러 번 실행한 결과가 있으면 profile과 요청률별로 다음 값의 중앙값을 계산한다.

```text
avgMs
p95Ms
p99Ms
maxMs
rps
errorRate
droppedIterations
```

특정 회차의 가장 좋은 결과만 선택하지 않는다. Threshold 결과는 같은 profile과 요청률의 모든 회차가 통과해야 통과로 본다.

Profile별 최대 안정 요청률은 `PASS`인 요청률 중 가장 높은 값이다. 모든 요청률이 `PASS`면 현재 테스트 범위에서는 상한 미확인으로 보고 다음 요청률 단계 추가가 필요하다고 출력한다. 모든 요청률이 실패하면 현재 최소 요청률에서도 안정 기준 미충족으로 보고 더 낮은 요청률부터 재측정이 필요하다고 출력한다.

## 권장 측정 절차

탐색 측정:

```text
100
200
400
600
800 RPS
```

목적은 대략적인 안정 한계와 실패 구간 확인이다.

확인 측정:

탐색 측정에서 확인한 다음 두 요청률을 각 3회 실행한다.

```text
마지막 안정 요청률
최초 실패 요청률
```

예:

```text
400 RPS PASS
600 RPS FAIL

-> 400 RPS 3회 재측정
-> 600 RPS 3회 재측정
```

각 지표의 중앙값으로 최종 판단한다.

## Threshold

기본 threshold:

```javascript
auth_ping_failures: ['rate<0.01']
auth_ping_duration: ['p(95)<200', 'p(99)<500']
auth_ping_requests: ['count>0']
checks: ['rate>0.99']
dropped_iterations: ['count==0']
```

안정 처리 기준:

```text
오류율 1% 미만
Check 성공률 99% 초과
Dropped Iterations 0건
p95 200ms 미만
p99 500ms 미만
```

## Smoke Test

서버가 실행 중일 때 낮은 부하로 스크립트를 확인한다.

```powershell
.\performance\k6\run-auth-cache-stress.ps1 `
  -Profile perf-nocache `
  -Rates 1,2 `
  -Duration "10s" `
  -Runs 1
```

## Docker k6 실행

Windows Docker에서 Spring Boot 서버가 호스트의 8080 포트에 있으면 `host.docker.internal`을 사용한다.

```powershell
docker run --rm `
  -i `
  -e BASE_URL="http://host.docker.internal:8080" `
  -e EMAIL="$env:EMAIL" `
  -e PASSWORD="$env:PASSWORD" `
  -e RATE="100" `
  -e DURATION="2m" `
  -e PRE_ALLOCATED_VUS="50" `
  -e MAX_VUS="300" `
  -e WARMUP_REQUESTS="20" `
  -e PROFILE="perf-cache" `
  -e RESULT_LABEL="cache-1" `
  -v "${PWD}:/work" `
  -w /work `
  grafana/k6 run .\performance\k6\auth-cache-load.js
```

## 로컬 환경 해석 주의사항

다음 구성 요소가 모두 동일한 로컬 PC에서 실행되는 환경이다.

```text
Spring Boot
MySQL
Redis
k6
Docker Desktop
```

따라서 결과는 다음 범위로만 표현한다.

```text
로컬 단일 인스턴스 환경 기준
동일 회원 Hot Cache 인증 요청 기준
동일한 로컬 단일 인스턴스 환경에서 캐시 적용·미적용 구조의 안정 처리 요청률 비교
```

다음처럼 표현하지 않는다.

```text
운영 환경 최대 처리량
실제 서비스에서 보장되는 최대 RPS
전체 서비스 최대 성능
```
