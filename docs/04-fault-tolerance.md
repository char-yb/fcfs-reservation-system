# Fault Tolerance & Compensation

> 본 문서는 현재 구현 기준의 Redis 장애 fallback, 결제 실패 보상, 보상 실패 기록, 부분 실패 한계를 정리한다.
> 기본 우선순위는 정합성, 공정성, 가용성 순이다.

---

## 1. 장애 시나리오와 대응 범위

| 장애 | 현재 대응 |
|---|---|
| Redis counter 장애 | `RedisUnavailableException`으로 변환 후 DB-only fallback |
| Redis lock 장애 | counter 복구 후 DB-only fallback. 이미 action이 시작된 경우 원래 예외 유지 |
| Redis circuit open | Redis 호출을 application에 `RedisUnavailableException`으로 전달 |
| 재고 소진 | `STOCK_SOLD_OUT` 반환. Redis 장애 fallback 대상 아님 |
| lock 획득 실패 | `LOCK_ACQUISITION_FAILED` 반환. Redis 장애 fallback 대상 아님 |
| 외부 PG 명시적 실패 | 성공 결제 역순 cancel, 주문 FAILED, DB/Redis 재고 복구 |
| 보상 실패 | `outbox_events`에 `COMPENSATION_FAILURE` 기록 |
| API 서버가 결제 중 죽음 | PENDING 주문과 재고 차감이 남을 수 있음. 운영용 미완료 주문 복구 필요 |

현재 구현은 모든 장애를 자동 복구하지 않는다. 핵심 흐름에서는 오버셀링 방지와 보상 가능한 상태 기록을 우선한다.

---

## 2. Redis 장애 감지

Redis 장애 판별은 `storage:redis` 모듈 책임이다. `apps:application`은 Redisson이나 Resilience4j 타입을 직접 알지 않고 `RedisUnavailableException`만 처리한다.

### 2.1 Circuit Breaker 위치

`@CircuitBreaker(name = "redis")`는 순수 Redis 호출에만 붙는다.

| 타입 | circuit 대상 |
|---|---|
| `StockRedisCounterRepository.decrement` | Redis Lua script 실행 |
| `StockRedisCounterRepository.initialize` | counter 초기화 |
| `RedisLockClient.getLock` | Redisson lock 조회 |
| `RedisLockClient.tryLock` | Redisson tryLock |

`StockRedisCounterRepository.increment`에는 circuit breaker를 두지 않는다. 보상/복구성 `INCR`은 circuit open 때문에 호출 자체가 차단되면 counter drift를 만들 수 있으므로 실제 Redis 호출을 시도하고 실패는 로그로 남긴다.

### 2.2 예외 변환

`RedisCircuitBreakerExceptionAspect`는 `storage:redis` 안의 circuit 대상 메서드에서 발생한 다음 예외를 `RedisUnavailableException`으로 변환한다.

| 원본 | 변환 이유 |
|---|---|
| `CallNotPermittedException` | circuit open |
| Redisson/Redis 연결·timeout 계열 예외 | application이 Redis 구현 타입을 알지 않게 하기 위함 |

### 2.3 설정

설정은 `storage/redis/src/main/resources/redis.yml`에 둔다.

```yaml
redis:
  reservation:
    command-timeout-ms: 200
    connect-timeout-ms: 1000

resilience4j:
  circuitbreaker:
    instances:
      redis:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 100
        minimum-number-of-calls: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 5s
        permitted-number-of-calls-in-half-open-state: 10
        automatic-transition-from-open-to-half-open-enabled: true
        record-exceptions:
          - org.redisson.client.RedisException
```

slow-call 기반 open 조건은 제거했다. 고부하에서 정상 Redis 응답이 느린 호출로 집계되어 DB-only fallback을 과도하게 여는 것을 막기 위해서다. 현재는 Redis command timeout/예외를 장애 판단 기준으로 둔다.

---

## 3. Redis 장애 fallback 흐름

`StockService.executeWithStockReservation`은 Redis counter와 lock을 통과하면 `true`, DB-only fallback으로 예약하면 `false`를 반환한다. 이 값은 결제 실패 후 Redis counter 복구 여부를 판단하는 데 사용한다.

```text
Redis 정상:
  Lua decrement 성공
  → lock 획득
  → BookingReservationProcessor.reserve(REQUIRES_NEW)
  → true 반환

Redis 장애:
  counter 또는 lock 호출에서 RedisUnavailableException
  → counter가 이미 차감됐다면 increment 복구
  → action이 아직 시작 전이면 fallbackAction 실행
  → false 반환
```

### 예외별 처리

| 예외 | fallback 여부 | 이유 |
|---|---|---|
| `RedisUnavailableException` action 시작 전 | 실행 | Redis 없이 DB 조건부 update로 정합성 유지 가능 |
| `RedisUnavailableException` action 시작 후 | 실행 안 함 | DB 예약이 이미 시작됐으므로 원래 흐름 보존 |
| `STOCK_SOLD_OUT` | 실행 안 함 | 비즈니스 결과이며 Redis 장애가 아님 |
| `LOCK_ACQUISITION_FAILED` | 실행 안 함 | Redis 장애가 아니라 대기 시간 초과 |
| 결제 실패 | 실행 안 함 | 예약은 이미 생성됐으므로 보상 흐름으로 처리 |

Redis fallback에서는 공정성과 처리량이 약해진다. 대신 DB `UPDATE ... WHERE remaining_quantity > 0`와 CHECK 제약으로 오버셀링은 막는다.

---

## 4. 결제 실패 보상

### 4.1 정상 보상 흐름

```text
1. BookingReservationProcessor.reserve
   - DB 재고 차감
   - 주문 PENDING 생성
2. PaymentService.execute
   - Y_POINT 우선 실행
   - CREDIT_CARD/Y_PAY 실행
3. 후속 결제 실패
   - 성공 결제들을 역순 cancel
   - BookingFacade가 주문 FAILED 처리
   - DB 재고 복구
   - Redis counter를 실제 차감한 경우에만 복구
4. 원래 결제 예외 반환
```

`PaymentService.execute`는 `Propagation.NOT_SUPPORTED`로 실행된다. 외부 PG 호출이나 포인트 차감이 상위 DB 트랜잭션과 묶이지 않게 하기 위함이다.

### 4.2 보상 실패

보상 실패는 추가 보상을 시도하지 않는다. 실패한 보상 대상은 `outbox_events`에 `COMPENSATION_FAILURE`로 저장한다.

저장 payload는 `CompensationFailurePayload`다.

| 필드 | 의미 |
|---|---|
| `orderId` | 주문 ID |
| `method` | 보상 실패 결제 수단 |
| `amount` | 결제 금액 |
| `pgTransactionId` | 취소 실패 transaction id |
| `reason` | 실패 사유 |

현재 구현은 outbox worker를 자동 실행하지 않는다. outbox는 사후 확인과 재처리 근거를 남기는 저장소 역할이다.

---

## 5. 주문 상태와 재고 복구

주문 상태 변경은 `OrderRepository.updateStatusIfCurrent`를 사용한다.

| 메서드 | 조건 | 결과 |
|---|---|---|
| `confirm(orderId)` | 현재 `PENDING` | `CONFIRMED` |
| `failAndRelease(orderId, productOptionId)` | 현재 `PENDING` | `FAILED`, `order_products.canceled_at`, DB 재고 +1 |

이미 `CONFIRMED` 또는 `FAILED`인 주문을 다시 바꾸려 하면 `INVALID_ORDER_STATUS_TRANSITION`을 반환한다. 이 조건부 상태 전이는 보상 흐름이 중복 호출되더라도 주문 상태를 덮어쓰지 않기 위한 방어다.

---

## 6. 부분 실패와 한계

### 6.1 결제 중 서버 종료

예약 DB 트랜잭션은 이미 커밋됐고 결제 실행 중 서버가 종료되면 다음 상태가 남을 수 있다.

| DB 상태 | PG 상태 | 위험 |
|---|---|---|
| 주문 `PENDING`, DB 재고 차감 | 결제 요청 전 | 재고 누수 |
| 주문 `PENDING`, DB 재고 차감 | 결제 결과 불명 | 재고 누수 + 결제 불명 |
| 주문 `PENDING`, DB 재고 차감 | 결제 성공 | 결제 성공 + 주문 미확정 |

현재 구현은 이 케이스를 자동 복구하지 않는다. 운영 도입 시에는 오래된 `PENDING` 주문과 PG 결제 결과를 대조하는 worker 또는 운영 스크립트가 필요하다.
장기 개선 방향은 [`06-long-term-roadmap.md`](./06-long-term-roadmap.md)의 Phase 1에서 추적한다.

### 6.2 lock 보유 중 서버 종료

Redisson lock은 `leaseTime=5s` 후 만료된다. lock 범위가 DB 예약 구간으로 짧기 때문에 결제 전체를 lock으로 붙잡는 구조보다 영향이 작다.

서버가 lock을 보유한 채 죽더라도 최종 정합성은 DB 조건부 update와 CHECK 제약이 지킨다.

---

## 7. 관측과 운영 체크

| 지표 | 의미 |
|---|---|
| Redis circuit state | Redis 장애 또는 circuit open 여부 |
| Redis `stock:{productOptionId}` | 빠른 매진 판단에 쓰는 L1 counter |
| DB `product_stock.remaining_quantity` | 최종 재고 기준 |
| DB confirmed orders | 실제 판매 완료 수량 |
| PENDING 주문 수 | 부분 실패 또는 결제 중단 가능성 |
| `outbox_events`의 `COMPENSATION_FAILURE` | 수동/자동 재처리 대상 |

k6 정합성 검증은 다음 관계를 확인한다.

```text
expected_remaining = total_quantity - confirmed_orders
db_remaining == expected_remaining
redis_stock == db_remaining
pending_orders == 0
```

Redis fallback 경로에서 확정 주문이 발생하면 일시적으로 Redis counter와 DB confirmed count의 의미가 달라질 수 있다. 그래서 운영에서는 Redis counter 자체보다 DB confirmed count와 DB remaining을 최종 기준으로 본다.

---

## 8. 테스트 전략

| 테스트 | 검증 |
|---|---|
| `StockServiceTest` | sold-out 시 counter 미복구, lock/action 실패 시 복구, Redis 장애 fallback |
| `StockRedisCounterRepositoryTest` | Lua counter가 0 아래로 내려가지 않음, 키 없음이면 음수 키 미생성 |
| `BookingReservationProcessorTest` | DB 재고 차감 + PENDING 주문 생성, 실패/확정 조건부 상태 전이 |
| `PaymentServiceTest` | Y_POINT 우선 실행, 역순 보상, 보상 실패 outbox 기록 |
| `BookingFacadeTest` | 예약 성공, 결제 실패 보상, Redis fallback 후 결제 실패 시 counter 미복구 |
| k6 + 정합성 검증 스크립트 | 500/1000 TPS 후 DB/Redis 정합성 |

최근 로컬 booking spike 결과는 다음과 같다.

| peak TPS | 성공 예약 | 정상 매진 | 기타 expected fail | unexpected | dropped | p95 | p99 | 사후 정합성 검증 |
|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 500 | 10 | 160,764 | 0 | 0.00% | 0 | 4.285ms | 14.451ms | 통과 |
| 1000 | 10 | 318,514 | 0 | 0.00% | 0 | 3.275ms | 21.827ms | 통과 |

사후 정합성 검증은 두 실행 모두 `confirmed_orders=10`, `pending_orders=0`, `db_remaining=0`, `redis_stock=0`이었다. 장애 대응 관점에서 중요한 점은 실패 응답이 애플리케이션 예외나 lock timeout으로 새지 않고, 매진이라는 제어된 결과로 수렴했다는 것이다.

실행 절차는 [`05-runbook-and-validation.md`](./05-runbook-and-validation.md)를 따른다.
