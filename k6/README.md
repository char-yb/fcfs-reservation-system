# k6 부하테스트

이 디렉터리는 과제 조건의 트래픽 프로파일을 검증하기 위한 k6 스크립트를 담고 있다.
대상은 Checkout API와 Booking API이며, 평시 50 TPS 상태에서 00:00 정각에 500~1000 TPS로 급등하는 피크를 재현한다.

## 사전 준비

- `docker/docker-compose.yml`의 MySQL, Redis, API 컨테이너가 실행 중이어야 한다.
- 기본 API 주소는 `http://localhost:8080`이다.

## k6 설치

k6는 Node.js 패키지가 아니라 별도 CLI 바이너리다.
따라서 `npm install`로 설치하지 않고, 운영체제 패키지 매니저나 Docker 이미지를 사용한다.
자세한 설치 옵션은 [Grafana k6 공식 설치 문서](https://grafana.com/docs/k6/latest/set-up/install-k6/)를 기준으로 한다.

### macOS

```bash
brew install k6
k6 version
```

### Ubuntu/Debian

```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
k6 version
```

### Windows

```powershell
winget install k6 --source winget
k6 version
```

Chocolatey를 사용한다면 다음 명령도 가능하다.

```powershell
choco install k6
k6 version
```

### Docker로 실행

로컬에 k6를 설치하지 않고 실행하려면 Docker 이미지를 사용할 수 있다.
이 경우 컨테이너에서 호스트의 Spring Boot API를 바라봐야 하므로 `k6/lib/config.js`의 `baseUrl`을 `http://host.docker.internal:8080`으로 바꾼다.

```bash
docker pull grafana/k6
docker run --rm -i -v "$PWD/k6:/scripts" grafana/k6 run /scripts/scenarios/booking-spike.js
```

Linux에서 `host.docker.internal`이 동작하지 않으면 `--network host`를 붙이고 `baseUrl`은 `http://localhost:8080`으로 유지한다.

```bash
docker run --rm -i --network host -v "$PWD/k6:/scripts" grafana/k6 run /scripts/scenarios/booking-spike.js
```

## 값 변경 위치

환경변수로 값을 주입하지 않는다. 테스트 값을 바꾸려면 파일 상단의 변수 값을 직접 수정한다.

| 파일 | 주요 변수 |
|---|---|
| `k6/lib/config.js` | `baseUrl`, `productId`, `productPrice`, `stock`, `userStartId`, `userCount` |
| `k6/scenarios/checkout-baseline.js` | `baseTps`, `duration`, `preAllocatedVUs`, `maxVUs` |
| `k6/scenarios/booking-spike.js` | `baseTps`, `peakTps`, `warmupDuration`, `rampDuration`, `spikeDuration` |
| `k6/scenarios/midnight-mixed.js` | `checkoutTps`, `peakTps`, `mixedDuration`, `spikeDuration` |
| `k6/scenarios/idempotency-race.js` | `raceTps`, `duration` |
| `k6/scenarios/payment-failure.js` | `failureTps`, `duration` |
| `k6/scripts/reset-local-data.sh` | `product_id`, `stock`, `user_count`, MySQL/Redis 컨테이너 값 |
| `k6/scripts/verify-local-invariants.sh` | `product_id`, MySQL/Redis 컨테이너 값 |

예를 들어 500 TPS를 검증하려면 `k6/scenarios/booking-spike.js`의 `peakTps`를 `500`으로 바꾼 뒤 실행한다.
1000 TPS를 검증하려면 같은 값을 `1000`으로 바꾼다.
`warmupDuration`은 00:00 직전 평시 구간, `rampDuration = '1s'`는 00:00 정각의 급등 구간, `spikeDuration`은 00:00 이후 피크 유지 구간으로 본다.

## 주요 변수 설명

### 공통 데이터 변수

| 변수 | 위치 | 의미 |
|---|---|---|
| `baseUrl` | `k6/lib/config.js` | 부하테스트 대상 API 서버 주소 |
| `productId` | `k6/lib/config.js`, `k6/scripts/*.sh` | 테스트 대상 상품 ID. DB 시드, Redis `stock:{productId}`, API 요청이 모두 같은 값을 써야 한다. |
| `productPrice` | `k6/lib/config.js`, `reset-local-data.sh` | 상품 가격이자 기본 Booking 결제 금액 |
| `stock` | `k6/lib/config.js`, `reset-local-data.sh` | 과제 기준 한정 수량. 기본값은 10개다. |
| `userStartId` | `k6/lib/config.js`, `reset-local-data.sh` | 테스트 사용자 ID 시작값 |
| `userCount` | `k6/lib/config.js`, `reset-local-data.sh` | 시드하고 순환 사용할 사용자 수 |
| `timeout` | `k6/lib/config.js` | k6 HTTP 요청 timeout |

### 트래픽 변수

| 변수 | 의미 |
|---|---|
| `baseTps` | 00:00 직전 평시 요청률. 과제 기준은 50 TPS다. |
| `peakTps` | 00:00 정각 이후 목표 요청률. 500 또는 1000으로 바꿔 비교한다. |
| `checkoutTps` | 혼합 시나리오에서 Checkout API에 지속적으로 주는 요청률 |
| `warmupDuration` | 00:00 직전 평시 요청률을 유지하는 시간 |
| `rampDuration` | 00:00 정각 급등 시간. 순간 스파이크를 가정해 기본값은 1초다. |
| `spikeDuration` | 00:00 이후 피크 요청률을 유지하는 시간 |
| `cooldownDuration` | 피크 종료 후 요청률을 0으로 낮추는 시간 |
| `duration` | 단일 constant-arrival-rate 시나리오의 총 실행 시간 |
| `mixedDuration` | Checkout + Booking 혼합 시나리오의 Checkout 실행 시간. Booking 총 시간과 맞춰 둔다. |
| `raceTps` | 멱등성 race 시나리오의 요청률 |
| `failureTps` | 결제 실패 시나리오의 요청률 |

### k6 실행 용량 변수

| 변수 | 의미 |
|---|---|
| `preAllocatedVUs` | k6가 실행 전에 미리 확보할 VU 수. 목표 TPS 대비 부족하면 `dropped_iterations`가 생긴다. |
| `maxVUs` | k6가 실행 중 동적으로 늘릴 수 있는 최대 VU 수 |
| `maxDroppedIterations` | 목표 TPS를 놓친 반복 허용 개수. 용량 검증에서는 0을 기준으로 본다. |

## 데이터 초기화와 정합성 검증

각 부하테스트 전에는 로컬 데이터를 초기화한다. 이 스크립트는 상품 1개, 재고 10개, 테스트 사용자 포인트를 만들고 Redis `stock:{productId}` 값을 DB 재고와 맞춘다.

```bash
./k6/scripts/reset-local-data.sh
```

테스트 실행 후에는 DB와 Redis 정합성을 검증한다.

```bash
./k6/scripts/verify-local-invariants.sh
```

## 시나리오 실행

```bash
# Checkout 평시 부하
k6 run k6/scenarios/checkout-baseline.js

# Booking 00시 피크
k6 run k6/scenarios/booking-spike.js

# Checkout + Booking 혼합 00시 흐름
k6 run k6/scenarios/midnight-mixed.js

# 동일 Idempotency-Key 동시 요청
k6 run k6/scenarios/idempotency-race.js

# 결제 실패와 재고 복구
k6 run k6/scenarios/payment-failure.js
```

## Booking 기대 응답

다음 응답은 제어된 결과로 분류한다.

- `200 CONFIRMED`
- `409 STOCK_SOLD_OUT`
- `409 DUPLICATE_REQUEST`
- `402 PAYMENT_DECLINED`
- `503 LOCK_ACQUISITION_FAILED`

그 외 응답은 `booking_unexpected_response_rate`에 집계되며 조사 대상이다.

자세한 실행 절차와 장애 드릴은 `k6/RUNBOOK.md`를 참고한다.
