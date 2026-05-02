# k6 부하테스트 RUNBOOK

## 목적

Checkout API와 Booking API가 과제 조건의 부하를 어떻게 처리하는지 검증한다.
기준 트래픽은 평시 50 TPS, 00:00 정각의 500~1000 TPS 피크다.
핵심 판정 기준은 오버셀링 방지, DB/Redis 재고 정합성, 예상 밖 오류 비율이다.

500~1000 TPS에서 실패가 발생해도 현재 단계에서는 자연스러운 결과로 본다.
이 RUNBOOK은 실패를 숨기기 위한 문서가 아니라, 리팩토링 전 기준선을 수집하고 병목을 찾기 위한 절차다.

## 1. 사전 준비

1. 저장소 루트에서 로컬 스택을 실행한다.

```bash
docker compose -f docker/docker-compose.yml up -d --build
```

2. API, MySQL, Redis 상태를 확인한다.

```bash
docker ps --format '{{.Names}} {{.Status}}'
curl -s http://localhost:8080/actuator/health
```

3. k6 설치 여부를 확인한다.

```bash
k6 version
```

로컬에 k6가 없다면 아래 중 하나를 선택한다.
k6는 Node.js 패키지가 아니라 별도 CLI 바이너리이므로 `npm install` 대상이 아니다.
상세 설치 옵션은 [Grafana k6 공식 설치 문서](https://grafana.com/docs/k6/latest/set-up/install-k6/)를 기준으로 한다.

```bash
# macOS
brew install k6

# Ubuntu/Debian
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6

# Windows
winget install k6 --source winget
```

설치 없이 Docker로 실행하려면 `k6/lib/config.js`의 `baseUrl`을 `http://host.docker.internal:8080`으로 바꾸고 다음처럼 실행한다.

```bash
docker pull grafana/k6
docker run --rm -i -v "$PWD/k6:/scripts" grafana/k6 run /scripts/scenarios/booking-spike.js
```

Linux에서 `host.docker.internal`이 동작하지 않으면 `--network host`를 붙이고 `baseUrl`은 `http://localhost:8080`으로 유지한다.

```bash
docker run --rm -i --network host -v "$PWD/k6:/scripts" grafana/k6 run /scripts/scenarios/booking-spike.js
```

4. 테스트 값을 확인한다.

- 공통 값은 `k6/lib/config.js` 상단에서 수정한다.
- Booking 피크 TPS는 `k6/scenarios/booking-spike.js`의 `peakTps`를 `500` 또는 `1000`으로 수정한다.
- `warmupDuration`은 00:00 직전 평시 구간이다.
- `rampDuration = '1s'`는 00:00 정각에 트래픽이 급등한다는 가정이다.
- `spikeDuration`은 00:00 이후 피크 유지 구간이다.
- 로컬 시드 값은 `k6/scripts/reset-local-data.sh` 상단에서 수정한다.
- 정합성 검증 대상 상품 옵션은 `k6/scripts/verify-local-invariants.sh` 상단의 `product_option_id`로 맞춘다.

### 주요 변수 설명

| 변수 | 의미 | 조정 기준 |
|---|---|---|
| `product_option_id` / `productOptionId` | 부하테스트 대상 상품 옵션 ID | 시드 스크립트, k6 공통 설정, 정합성 검증 스크립트가 모두 같은 값을 써야 한다. |
| `stock` | 한정 판매 수량 | 과제 기준은 10개다. 오버셀링 검증의 기준값이다. |
| `user_count` / `userCount` | 테스트 사용자 수 | 500~1000 TPS에서 사용자 ID가 부족하지 않도록 충분히 크게 둔다. |
| `baseTps` | 00:00 전 평시 요청률 | 과제 기준은 50 TPS다. |
| `peakTps` | 00:00 후 피크 요청률 | 500 TPS와 1000 TPS를 각각 실행해 비교한다. |
| `rampDuration` | 평시에서 피크로 전환되는 시간 | 00:00 정각 급등 가정이므로 1초로 둔다. |
| `spikeDuration` | 피크 요청률 유지 시간 | 과제 기준은 1~5분이다. 정식 검증에서는 5분을 권장한다. |
| `preAllocatedVUs` | k6가 미리 확보하는 VU 수 | `dropped_iterations`가 생기면 먼저 늘린다. |
| `maxVUs` | k6가 사용할 수 있는 최대 VU 수 | 피크 TPS에서 요청 생성기가 부족하면 늘린다. |
| `maxDroppedIterations` | 목표 요청률 미달 허용치 | 용량 검증은 0을 기준으로 보고, 리팩토링 전 기준선 수집에서는 실패 원인으로 기록한다. |

5. 각 시나리오 실행 전 데이터를 초기화한다.

```bash
./k6/scripts/reset-local-data.sh
```

## 2. Smoke 검증

고 TPS 실행 전에 짧은 저부하 실행으로 API 계약, 응답 분류, 정합성 검증 스크립트를 확인한다.
짧은 실행을 하려면 `k6/scenarios/booking-spike.js` 상단 값을 다음처럼 임시로 바꾼다.

```javascript
const baseTps = 5;
const peakTps = 20;
const warmupDuration = '5s';
const rampDuration = '1s';
const spikeDuration = '10s';
const cooldownDuration = '5s';
const preAllocatedVUs = 20;
const maxVUs = 100;
```

실행:

```bash
k6 run k6/scenarios/booking-spike.js
./k6/scripts/verify-local-invariants.sh
```

통과 기준:

- k6가 0으로 종료한다.
- `booking_unexpected_response_rate`가 1% 미만이다.
- `dropped_iterations`가 0이다.
- 정합성 검증 스크립트가 `정합성 검증 통과`를 출력한다.

## 3. 과제 부하 실행

각 실행은 반드시 `reset-local-data.sh` 이후 진행한다.

### Checkout 평시 50 TPS

`k6/scenarios/checkout-baseline.js` 상단 값:

```javascript
const baseTps = 50;
const duration = '2m';
const preAllocatedVUs = 100;
const maxVUs = 500;
```

실행:

```bash
./k6/scripts/reset-local-data.sh
k6 run --summary-export k6/results/checkout-baseline.json k6/scenarios/checkout-baseline.js
```

### Booking 피크 500 TPS

`k6/scenarios/booking-spike.js` 상단 값:

```javascript
const baseTps = 50;
const peakTps = 500;
const warmupDuration = '1m';
const rampDuration = '1s';
const spikeDuration = '5m';
const cooldownDuration = '30s';
const preAllocatedVUs = 500;
const maxVUs = 1500;
```

실행:

```bash
./k6/scripts/reset-local-data.sh
k6 run --summary-export k6/results/booking-500.json k6/scenarios/booking-spike.js
./k6/scripts/verify-local-invariants.sh
```

### Booking 피크 1000 TPS

`k6/scenarios/booking-spike.js` 상단 값:

```javascript
const baseTps = 50;
const peakTps = 1000;
const warmupDuration = '1m';
const rampDuration = '1s';
const spikeDuration = '5m';
const cooldownDuration = '30s';
const preAllocatedVUs = 800;
const maxVUs = 2500;
```

실행:

```bash
./k6/scripts/reset-local-data.sh
k6 run --summary-export k6/results/booking-1000.json k6/scenarios/booking-spike.js
./k6/scripts/verify-local-invariants.sh
```

### Checkout + Booking 혼합 00시 흐름

`k6/scenarios/midnight-mixed.js` 상단 값:

```javascript
const baseTps = 50;
const checkoutTps = 50;
const peakTps = 1000;
const mixedDuration = '6m31s';
const spikeDuration = '5m';
```

실행:

```bash
./k6/scripts/reset-local-data.sh
k6 run --summary-export k6/results/midnight-mixed.json k6/scenarios/midnight-mixed.js
./k6/scripts/verify-local-invariants.sh
```

## 4. 개별 장애/방어 시나리오

### 멱등성 동시 요청

```bash
./k6/scripts/reset-local-data.sh
k6 run --summary-export k6/results/idempotency-race.json k6/scenarios/idempotency-race.js
./k6/scripts/verify-local-invariants.sh
```

기대 결과:

- 동일 `Idempotency-Key`로 여러 요청이 들어가도 확정 주문은 최대 1건이다.
- 나머지는 `DUPLICATE_REQUEST`, `STOCK_SOLD_OUT`, `LOCK_ACQUISITION_FAILED` 같은 제어된 응답이어야 한다.

### 결제 실패 보상

```bash
./k6/scripts/reset-local-data.sh
k6 run --summary-export k6/results/payment-failure.json k6/scenarios/payment-failure.js
./k6/scripts/verify-local-invariants.sh
```

기대 결과:

- 결제 요청은 `PAYMENT_DECLINED`로 실패한다.
- 실패 주문이 생기더라도 DB 재고와 Redis 재고는 초기 수량으로 복구되어야 한다.

### Redis 중단 드릴

```bash
./k6/scripts/reset-local-data.sh
k6 run --summary-export k6/results/redis-stop.json k6/scenarios/booking-spike.js
```

실행 중 다른 터미널에서 Redis를 중단한다.

```bash
docker stop fcfs-reservation-redis
```

현재 구현 참고:
Redis fallback은 설계 목표로 문서화되어 있지만, 이 변경에서 백엔드 fallback을 구현하지는 않았다.
따라서 Redis 중단 드릴은 실패를 관찰하고 후속 리팩토링 포인트를 찾기 위한 테스트다.

## 5. 판정 기준

- `CONFIRMED` 주문 수가 `product_stock.total_quantity`를 넘지 않는다.
- `product_stock.remaining_quantity = total_quantity - confirmed_orders`가 유지된다.
- 정상 완료된 테스트 뒤 Redis `stock:{productOptionId}`가 DB remaining과 같다.
- 예상 밖 5xx 또는 알 수 없는 응답 코드가 `booking_unexpected_response_rate`에 쌓이지 않는다.
- `dropped_iterations`가 0이면 k6 부하 발생기가 목표 요청 수를 놓치지 않은 것이다.

500~1000 TPS 테스트가 실패하면 다음 정보를 남긴다.

- 실패한 TPS와 실행 시간
- `dropped_iterations`
- `http_req_duration` p95/p99
- `booking_unexpected_response_rate`
- `lock_timeout_total`
- DB/Redis 정합성 검증 결과
- API 로그의 대표 예외

## 6. 기록할 지표

- `http_reqs`와 실제 요청률
- `dropped_iterations`
- `http_req_duration` p95/p99
- `booking_success_total`
- `stock_sold_out_total`
- `duplicate_request_total`
- `payment_declined_total`
- `lock_timeout_total`
- `booking_unexpected_response_rate`
- 정합성 검증 결과: confirmed 주문 수, DB remaining, Redis stock

## 7. 조사 가이드

| 증상 | 우선 확인 |
|---|---|
| `dropped_iterations > 0` | k6 실행 머신 CPU, `preAllocatedVUs`, `maxVUs` 부족 여부 |
| `LOCK_ACQUISITION_FAILED` 급증 | Redisson lock wait time, DB update latency, API 로그 |
| 예상 밖 5xx | `docker logs fcfs-reservation-api --tail=300` |
| DB remaining 불일치 | `orders`, `payments`, `product_stock` 조회와 보상 처리 로그 |
| Redis stock drift | Redis `stock:{productOptionId}`와 DB remaining 비교, L1 카운터 복구 로그 |
| Checkout 실패 | `user_points` 시드 범위와 `userStartId`, `userCount` 값 |

## 8. 로컬 검증 기록

2026-04-30 로컬 Docker Compose 스택 기준으로 기록했다.
아래 결과는 스크립트 동작, 응답 분류, 정합성 검증을 확인하기 위한 단축 실행 결과다.
정식 용량 판단은 1~5분 피크 유지 테스트를 별도로 수행해야 한다.

| 검증 | 실행 프로파일 | 결과 |
|---|---|---|
| 정적 k6 검사 | 모든 scenario `k6 inspect` | 통과 |
| 저장소 검증 | `./.codex/hooks/verify.sh` | 통과 |
| 데이터 초기화와 정합성 baseline | 재고 10, 사용자 1000명 | 통과, DB remaining 10, Redis 10 |
| Checkout baseline | 5 TPS, 5초 | 통과, p95 13.67ms, dropped 0 |
| Booking smoke | 5 TPS에서 20 TPS까지, 총 25초 | 통과, confirmed 10, sold out 327, p95 19.21ms |
| 결제 실패 | 3 TPS, 5초 | 통과, declined 16, failed order 16, DB/Redis 재고 10으로 복구 |
| 멱등성 race | 동일 key로 20 TPS, 5초 | 통과, confirmed 1, duplicate 99 |
| Booking 500 TPS | 50 TPS에서 500 TPS까지, 총 25초 | 통과, confirmed 10, sold out 7863, lock timeout 1, p95 5.41ms |
| Booking 1000 TPS | 50 TPS에서 1000 TPS까지, 총 25초 | 통과, confirmed 10, sold out 15358, lock timeout 6, p95 3.01ms |
| Midnight mixed | checkout 5 TPS + booking 5~20 TPS, 총 25초 | 통과, confirmed 10, sold out 327, checkout unexpected 0% |

정식 결과를 남길 때는 3장의 500 TPS, 1000 TPS 명령을 `spikeDuration = '5m'` 상태로 실행하고 `k6/results/`의 summary export를 함께 보관한다.
