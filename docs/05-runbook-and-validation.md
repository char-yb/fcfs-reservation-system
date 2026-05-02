# Runbook & Validation

> 본 문서는 로컬 실행, 초기 데이터, smoke test, k6 부하테스트, 정합성 검증 절차를 현재 프로젝트 기준으로 정리한다.

---

## 1. 로컬 실행

### 1.1 사전 준비

| 도구 | 용도 |
|---|---|
| Java 25 | Gradle build/test |
| Docker / Docker Compose | MySQL, Redis, API 실행 |
| k6 | 부하테스트 실행 |

### 1.2 환경 변수

처음 실행할 때 저장소 루트에서 `.env`를 만든다.

```bash
cp .env.sample .env
```

`.env.sample`은 Docker Compose 내부 네트워크 이름을 기준으로 한다.

```text
DATASOURCE_RESERVATION_JDBC_URL=jdbc:mysql://mysql:3306/fcfs_reservation?...
DATASOURCE_RESERVATION_USERNAME=reservation
DATASOURCE_RESERVATION_PASSWORD=reservation
REDIS_RESERVATION_HOST=redis
REDIS_RESERVATION_PORT=6379
```

### 1.3 Docker Compose 실행

```bash
docker compose -f docker/docker-compose.yml up -d --build
```

상태 확인:

```bash
docker ps --format '{{.Names}} {{.Status}}'
curl -s http://127.0.0.1:8080/actuator/health
```

종료:

```bash
docker compose -f docker/docker-compose.yml down
```

데이터까지 초기화:

```bash
docker compose -f docker/docker-compose.yml down -v
```

---

## 2. 초기 데이터

Docker Compose의 API 컨테이너는 다음 profile로 실행된다.

```text
SPRING_PROFILES_ACTIVE=prod,local-seed
```

`local` profile도 `local-seed`를 group으로 포함한다.

`local-seed` profile에서는 Flyway가 다음 두 위치를 읽는다.

```text
classpath:db/migration
classpath:db/seed/local
```

로컬 시드 파일:

```text
storage/rdb/src/main/resources/db/seed/local/R__local_seed_data.sql
```

| 데이터 | 값 |
|---|---|
| 상품 | 1~10 |
| 상품 옵션 | 1~10 |
| 옵션별 재고 | 10 |
| 판매 오픈 시각 | `2000-01-01 00:00:00.000` |
| 예약 일정 | 2030년 1월 1~11일 |
| 사용자 | 1~1000 |
| 사용자별 포인트 | 1,000,000 |

API 기동 후 `StockCounterInitializer`가 DB 재고를 Redis counter로 복사한다.

```text
product_stock.remaining_quantity -> stock:{productOptionId}
```

Redis 확인:

```bash
docker exec fcfs-reservation-redis redis-cli GET stock:1
```

기대값:

```text
10
```

---

## 3. Smoke Test

### 3.1 Checkout

```bash
curl -s "http://127.0.0.1:8080/api/v1/checkout/1?productOptionId=1"
```

기대 응답:

```json
{
  "success": true,
  "status": 200,
  "data": {
    "product": {
      "productId": 1,
      "productOptionId": 1,
      "productType": "BOOKING",
      "price": 100000.00,
      "remainingQuantity": 10
    },
    "user": {
      "id": 1,
      "availablePoint": 1000000
    }
  }
}
```

### 3.2 Booking

신용카드 결제 예시:

```bash
curl -s -X POST "http://127.0.0.1:8080/api/v1/booking/1" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "productOptionId": 1,
    "totalAmount": 100000.00,
    "payments": [
      {
        "method": "CREDIT_CARD",
        "amount": 100000.00,
        "attributes": {
          "cardToken": "card-token-1"
        }
      }
    ]
  }'
```

Y 포인트와 신용카드 복합 결제 예시:

```bash
curl -s -X POST "http://127.0.0.1:8080/api/v1/booking/2" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "productOptionId": 1,
    "totalAmount": 100000.00,
    "payments": [
      {
        "method": "Y_POINT",
        "amount": 30000.00
      },
      {
        "method": "CREDIT_CARD",
        "amount": 70000.00,
        "attributes": {
          "cardToken": "card-token-2"
        }
      }
    ]
  }'
```

### 3.3 Point Charge

```bash
curl -s -X POST "http://127.0.0.1:8080/api/v1/users/1/points/charge" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 50000
  }'
```

---

## 4. 부하테스트 데이터 초기화

`local-seed`는 Docker 실행 직후 리뷰용 초기 데이터를 제공한다. 부하테스트를 반복할 때는 주문/결제/재고/Redis counter를 매번 같은 상태로 되돌려야 하므로 별도 reset script를 사용한다.

```bash
./k6/scripts/reset-local-data.sh
```

기본값:

| 항목 | 값 |
|---|---|
| product_id | 1 |
| product_option_id | 1 |
| product_price | 100000 |
| stock | 10 |
| users | 1..1000 |
| user_point_balance | 1000000 |

이 스크립트는 다음을 수행한다.

1. 상품 옵션 1번과 연결된 주문/결제/outbox 삭제
2. 상품, 옵션, 일정, 재고 재생성
3. 사용자 1~1000과 포인트 재생성
4. Redis `stock:1`을 `10`으로 세팅

---

## 5. 정합성 검증

부하테스트 후 다음 스크립트를 실행한다.

```bash
./k6/scripts/verify-local-invariants.sh
```

검증 내용:

| 검증 | 의미 |
|---|---|
| `confirmed_orders <= total_quantity` | 오버셀링 방지 |
| `db_remaining = total_quantity - confirmed_orders` | DB 재고 정합성 |
| `redis_stock = db_remaining` | Redis counter drift 없음 |
| `pending_orders = 0` | 완료 후 미정리 주문 없음 |

정상 출력 예시:

```text
product_option_id=1
total_quantity=10
confirmed_orders=10
pending_orders=0
db_remaining=0
expected_remaining=0
redis_stock=0
정합성 검증 통과
```

---

## 6. k6 실행

상세 변수 설명은 `k6/README.md`에 있다.

### 6.1 Checkout 평시 50 TPS

```bash
./k6/scripts/reset-local-data.sh
k6 run --summary-export k6/results/checkout-baseline.json k6/scenarios/checkout-baseline.js
```

### 6.2 Booking 500 TPS

`k6/scenarios/booking-spike.js`의 `peakTps`를 `500`으로 맞춘 뒤 실행한다.

```bash
./k6/scripts/reset-local-data.sh
k6 run --summary-export k6/results/booking-500.json k6/scenarios/booking-spike.js
./k6/scripts/verify-local-invariants.sh
```

### 6.3 Booking 1000 TPS

`k6/scenarios/booking-spike.js`의 `peakTps`를 `1000`으로 맞춘 뒤 실행한다.

```bash
./k6/scripts/reset-local-data.sh
k6 run --summary-export k6/results/booking-1000.json k6/scenarios/booking-spike.js
./k6/scripts/verify-local-invariants.sh
```

### 6.4 최근 로컬 측정 결과

다음 값은 로컬 Docker Compose 환경에서 API 컨테이너를 재빌드한 뒤 측정한 결과다. `booking-spike.js`는 50 TPS warm-up, 1초 ramp-up, 5분 peak 유지, 30초 cooldown을 포함하므로 `http_reqs rate`는 peak TPS가 아니라 전체 실행 평균이다.

| 실행 시각 | peak TPS | iterations | 전체 평균 http_reqs/s | 성공 예약 | 정상 매진 | 기타 expected fail | unexpected | dropped | http failed | p95 | p99 | max |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 2026-05-03 00:06 KST | 500 | 160,774 | 411.17/s | 10 | 160,764 | 0 | 0.00% | 0 | 0.00% | 4.285ms | 14.451ms | 497.176ms |
| 2026-05-03 00:12 KST | 1000 | 318,524 | 814.61/s | 10 | 318,514 | 0 | 0.00% | 0 | 0.00% | 3.275ms | 21.827ms | 1009.912ms |

사후 정합성 검증은 두 실행 모두 동일하게 통과했다.

```text
confirmed_orders=10
failed_orders=0
pending_orders=0
db_remaining=0
expected_remaining=0
redis_stock=0
정합성 검증 통과
```

해석 기준은 다음과 같다.

| 지표 | 판단 |
|---|---|
| 성공 예약 10건 | 시드 재고 10개만 확정되어 over-selling 없음 |
| 정상 매진 `iterations - 10` | 재고 소진 이후 요청은 제어된 `STOCK_SOLD_OUT`으로 분류 |
| 기타 expected fail 0건 | duplicate, payment decline, lock acquisition failure 없이 처리 |
| unexpected 0.00% | 시나리오가 정의하지 않은 응답 없음 |
| dropped 0건 | k6가 목표 arrival-rate를 놓치지 않음 |
| `redis_stock=0` | Redis counter drift 없음 |
| `pending_orders=0` | 예약 중간 상태 잔류 없음 |

### 6.5 기대 응답 분류

k6 Booking 시나리오는 다음 응답을 expected response로 본다.

| 결과 | 의미 |
|---|---|
| `200 CONFIRMED` | 예약 성공 |
| `409 STOCK_SOLD_OUT` | 정상 매진 |
| `409 DUPLICATE_REQUEST` | 동일 주문 키 중복 |
| `402 PAYMENT_DECLINED` | 결제 거절 |
| `503 LOCK_ACQUISITION_FAILED` | lock 획득 실패 |

`booking_unexpected_response_rate`가 0에 가까워야 한다. 정합성 판단은 k6 성공률보다 DB/Redis invariant를 우선한다.

---

## 7. Gradle 검증

문서만 변경했더라도 설정과 코드 drift를 잡기 위해 기본 검증을 실행한다.

```bash
./gradlew ktlintCheck compileKotlin compileTestKotlin
```

저장소 기본 검증:

```bash
./.codex/hooks/verify.sh
```

전체 커버리지 검증이 필요하면 다음을 사용한다.

```bash
CODEX_VERIFY_MODE=full ./.codex/hooks/verify.sh
```

---

## 8. 문제 발생 시 확인 순서

| 증상 | 확인 |
|---|---|
| API health down | `docker logs fcfs-reservation-api` |
| Flyway 실패 | `storage/rdb/src/main/resources/db/migration`, `db/seed/local` |
| Checkout 404 | `productOptionId`가 seed 범위인지 확인 |
| Booking이 모두 매진 | Redis `stock:{productOptionId}`와 DB `product_stock` 확인 |
| Redis drift | `reset-local-data.sh` 후 재현, Lua decrement 테스트 확인 |
| PENDING 주문 남음 | 결제 중단 또는 보상 실패 여부 확인 |
| 보상 실패 | `outbox_events`의 `COMPENSATION_FAILURE` 확인 |
