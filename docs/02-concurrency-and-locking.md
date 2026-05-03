# Concurrency Control & Locking Strategy

> 본 문서는 현재 구현 기준의 재고 정합성, 공정성, Redis 장애 폴백, 분산락 경계를 설명한다.
> 핵심 질문은 10개 재고에 500~1000 TPS가 들어왔을 때 어떻게 오버셀링 없이 빠르게 실패시키는가이다.

---

## 1. 현재 채택 전략

```text
[Layer 1] Redis Lua conditional decrement
  - stock:{productOptionId}가 1 이상일 때만 DECR
  - 0 이하 또는 키 없음이면 값을 바꾸지 않고 -1 반환

[Layer 2] Redisson 공정락(fair lock)
  - lock:booking:{productOptionId}
  - DB 재고 차감 + PENDING 주문 생성 구간만 보호
  - waitTime 1s, leaseTime 5s

[Layer 3] DB conditional update + unique/check constraints
  - product_stock.remaining_quantity > 0 조건부 update
  - orders.order_key unique
  - product_stock.remaining_quantity >= 0 check
```

Layer 1은 매진 요청을 Redis 명령 1회로 빠르게 거절한다. Layer 2는 Redis counter를 통과한 요청의 DB 예약 구간을 상품 옵션 단위로 요청 순서에 가깝게 직렬화한다. Layer 3은 Redis가 없거나 락이 깨져도 최종 정합성을 보장한다.

---

## 2. Redis Counter

### 2.1 키

| 키 | 타입 | 용도 |
|---|---|---|
| `stock:{productOptionId}` | String number | 상품 옵션의 Redis 잔여 재고 |

Redis counter는 API 기동 시 `StockCounterInitializer`가 DB `product_stock.remaining_quantity` 값을 기준으로 초기화한다.

### 2.2 Lua 조건부 차감

현재 `StockRedisCounterRepository.decrement`는 Redisson `RScript`로 다음 Lua script를 실행한다.

```lua
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
if current <= 0 then
    return -1
end
return redis.call('DECR', KEYS[1])
```

이전 방식인 `DECR 후 음수면 INCR 보정`은 고부하에서 Redis counter drift를 만들 수 있다. 현재 방식은 재고가 없을 때 값을 바꾸지 않는다.

| 상황 | 반환 | Redis 값 |
|---|---|---|
| 현재 값 1 | 0 | 0 |
| 현재 값 0 | -1 | 0 |
| 키 없음 | -1 | 키 생성 없음 |

`StockService`는 `decrement` 결과가 음수이면 `STOCK_SOLD_OUT`을 반환하고 Redis counter를 복구하지 않는다. 실패가 발생해 실제로 counter를 차감한 경우에만 `increment`로 복구한다.

### 2.3 공정성 기준

Redis는 단일 스레드로 명령을 처리한다. 따라서 정상 Redis 경로에서는 `stock:{productOptionId}` Lua script가 Redis에 도착한 순서가 처리 순서가 된다.

Redis counter를 통과한 요청은 Redisson 공정락을 사용한다. 공정락은 같은 상품 옵션의 lock 대기자 사이에서 요청 순서를 보존하는 쪽으로 동작하므로, 일반 락보다 특정 애플리케이션 서버나 스레드가 반복적으로 앞서는 위험을 줄인다.

단, 이 공정성은 "Redis와 lock에 도착한 순서" 기준이다. 클라이언트 네트워크, 로드밸런서, 애플리케이션 서버까지의 지연 차이는 시스템 외부 변동으로 본다.

---

## 3. Distributed Lock

### 3.1 락 범위

| 항목 | 값 |
|---|---|
| 락 키 | `lock:booking:{productOptionId}` |
| waitTime | 1초 |
| leaseTime | 5초 |
| 보호 구간 | DB 재고 조건부 차감 + PENDING 주문 생성 |
| 락 밖 구간 | 결제 실행, 결제 저장, 주문 확정/실패 처리 |

현재 락은 결제 전체를 감싸지 않는다. 외부 PG 호출이 락을 오래 점유하면 상품 옵션 단위 직렬화 시간이 길어지고, 매진 직전 사용자의 대기 시간이 늘어난다. 그래서 락 안에서는 DB 예약만 빠르게 끝내고, 결제는 락 밖에서 보상 가능한 흐름으로 처리한다.

### 3.2 leaseTime 5초

락 안에서 수행하는 작업은 다음 정도로 제한된다.

| 작업 | 설명 |
|---|---|
| 중복 `order_key` 조회 | 같은 Idempotency-Key 재사용 방지 |
| DB 재고 차감 | `remaining_quantity > 0` 조건부 update |
| 주문 생성 | `orders`에 `PENDING` 저장 |
| 주문 상품 생성 | `order_products`에 상품/옵션 연결 |

정상적으로는 수십~수백 ms 안에 끝나는 구간이다. `leaseTime=5s`는 DB 지연이나 GC pause에 대한 버퍼로 둔다. Redisson watchdog은 사용하지 않는다. 유한한 임계 구간이 명확하므로 자동 무한 연장보다 명시적 leaseTime이 운영상 안전하다.

### 3.3 waitTime 1초

현재 `StockService`의 `LOCK_WAIT_TIME`은 1초다.

이 값은 1000 TPS 부하테스트에서 lock 획득 실패가 소수 발생했던 상황을 줄이기 위한 조정이다. Redis counter가 실제 재고 수만큼만 통과시키므로 lock 경합 대상은 제한적이며, 1초 대기는 긴 직렬화가 아니라 DB 예약 구간의 순간 지연을 흡수하기 위한 값이다.

### 3.4 일반 락과 공정락 비교

Redisson의 일반 락은 `redissonClient.getLock(key)`로 만들고, 공정락은 `redissonClient.getFairLock(key)`로 만든다. 둘 다 Redis 기반 분산락이지만, lock을 기다리는 요청의 순서를 다루는 방식이 다르다.

Redisson 공식 문서는 fair lock이 lock을 요청한 순서대로 획득되도록 대기 요청을 큐에 둔다고 설명한다. 대신 대기 중이던 클라이언트나 스레드가 죽은 경우 Redisson이 일정 시간 기다릴 수 있어, 일반 락보다 지연이 생길 수 있다. 참고: [Redisson Locks and synchronizers](https://redisson.pro/docs/data-and-services/locks-and-synchronizers/index.html)

| 구분 | 일반 락 `getLock` | 공정락 `getFairLock` |
|---|---|---|
| 획득 순서 | lock이 비는 순간 경쟁한 요청 중 하나가 획득한다. 요청 순서를 강하게 보장하지 않는다. | lock 요청 순서에 가깝게 대기열을 만들고 순서대로 획득하게 한다. |
| 장점 | 구조가 단순하고 대기열 관리 비용이 적다. 처리량이 중요한 일반 임계 구간에 적합하다. | 같은 상품 옵션에 몰린 요청에서 특정 서버나 스레드가 반복적으로 앞서는 위험을 줄인다. |
| 단점 | 선착순 요구가 있는 흐름에서는 lock 통과 순서가 불명확해질 수 있다. | 대기열 관리 비용이 있고, 죽은 대기자가 있으면 추가 대기가 생길 수 있다. |
| 어울리는 상황 | 순서보다 처리량이 중요한 짧은 작업 | 선착순, 쿠폰, 재고 예약처럼 요청 순서가 사용자의 체감 공정성과 연결되는 작업 |

이 프로젝트는 공정락을 선택했다. 이유는 다음과 같다.

- 요구사항의 핵심이 "한정 재고에 대한 선착순 예약"이므로, 단순히 오버셀링을 막는 것뿐 아니라 같은 상품 옵션에 몰린 요청이 가능한 한 도착 순서대로 DB 예약 구간에 들어가는 것이 중요하다.
- Redis Lua counter는 Redis에 도착한 순서대로 재고 수만큼만 통과시킨다. 그 다음 단계인 분산락에서 일반 락을 쓰면, Redis를 통과한 요청 사이의 순서가 다시 흐려질 수 있다.
- 현재 lock 안에서는 DB 재고 조건부 차감과 PENDING 주문 생성만 수행한다. 결제는 lock 밖에서 처리하므로 공정락의 대기열 비용을 감수해도 lock 점유 시간이 길어지지 않는다.
- Redis counter가 실제 재고 수량만큼만 통과시키므로 공정락 대기열에 쌓이는 요청 수도 제한된다. 이 구조에서는 공정성 이득이 비용보다 크다고 판단했다.

트레이드오프는 있다. 공정락은 일반 락보다 Redis 자료구조와 대기열 관리가 더 필요하고, Redis 장애 폴백 경로에서는 공정락을 사용할 수 없다. 그래서 정상 Redis 경로에서는 공정락으로 순서를 보강하고, Redis 장애 시에는 처리량과 공정성보다 DB 조건부 차감으로 오버셀링을 막는 것을 우선한다.

---

## 4. DB 최종 방어선

### 4.1 조건부 재고 차감

`ProductStockJpaRepository.decrementStock`는 단일 UPDATE로 검증과 차감을 함께 수행한다.

```sql
UPDATE product_stock
   SET remaining_quantity = remaining_quantity - 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE product_option_id = ?
   AND remaining_quantity > 0;
```

affected rows가 0이면 매진이다. InnoDB는 UPDATE 시 row-level X-lock을 잡으므로 별도의 `SELECT ... FOR UPDATE`가 필요하지 않다.

### 4.2 DB 제약

| 제약 | 역할 |
|---|---|
| `uk_order_key` | 같은 `Idempotency-Key` 기반 중복 주문 방지 |
| `chk_remaining_non_negative` | 코드/Redis 가정이 깨져도 음수 재고 방지 |

현재 멱등성은 Redis SETNX가 아니라 DB `orders.order_key` unique 제약에 의존한다. API는 헤더 존재 여부와 UUID 형식을 검증하고, application은 기존 `orderKey`가 있으면 `DUPLICATE_REQUEST`를 반환한다.

---

## 5. 트랜잭션 경계

현재 예약 흐름은 다음 순서다.

```text
1. BookingFacade: 판매 오픈 검증
2. StockService: Redis Lua counter 차감
3. StockService: Redisson 공정락 획득
4. BookingReservationProcessor.reserve(REQUIRES_NEW)
   - order_key 중복 확인
   - DB 재고 조건부 차감
   - PENDING 주문 생성
   - order_products 생성
5. lock 해제
6. PaymentService.execute(NOT_SUPPORTED)
   - Y_POINT 우선 실행
   - CREDIT_CARD/Y_PAY 외부 PG 실행
   - 실패 시 성공 결제 역순 cancel
7. PaymentService.saveApproved(REQUIRES_NEW)
8. BookingReservationProcessor.confirm(REQUIRES_NEW)
```

`BookingFacade`와 `StockService`는 트랜잭션을 시작하지 않는다. 락 안의 DB 변경은 `REQUIRES_NEW`로 커밋까지 끝낸 뒤 락을 해제한다. MySQL 기본 격리 수준인 REPEATABLE READ에서 상위 트랜잭션에 합류하면 락 해제 시점과 커밋 시점이 어긋나 오래된 snapshot 문제가 생길 수 있기 때문이다.

결제는 `NOT_SUPPORTED`로 기존 트랜잭션에 묶지 않는다. 외부 PG 호출 시간만큼 DB 커넥션을 점유하지 않기 위해서다.

---

## 6. Redis 장애 폴백

Redis counter나 lock 호출에서 Redisson 예외 또는 circuit open이 발생하면 `storage:redis`가 `RedisUnavailableException`으로 변환한다.

`StockService`는 action이 시작되기 전 Redis 장애를 감지하면 Redis counter/lock을 건너뛰고 `fallbackAction`을 실행한다. 이때 DB 조건부 update가 최종 정합성을 보장한다.

| 상태 | 처리 |
|---|---|
| counter 차감 전 Redis 장애 | DB-only 폴백 실행 |
| counter 차감 후 lock 장애 | Redis counter 복구 후 DB-only 폴백 실행 |
| action 시작 후 Redis 장애 | 이미 DB 예약 구간에 들어갔으므로 원래 예외를 유지 |
| `STOCK_SOLD_OUT` | Redis 장애 폴백 대상 아님 |
| `LOCK_ACQUISITION_FAILED` | Redis 장애 폴백 대상 아님 |

Redis 장애 폴백은 처리량과 공정성을 낮추지만 오버셀링은 막는다.

---

## 7. 1000 TPS 관점

재고가 10개라면 정상 Redis 경로에서 무거운 DB/결제 작업으로 들어갈 수 있는 요청은 최대 10개다.

| 단계 | 기대 동작 |
|---|---|
| Redis Lua counter | 재고 10개만 통과, 이후 요청은 값 변경 없이 `STOCK_SOLD_OUT` |
| Redisson 공정락 | 통과한 요청의 DB 예약 구간만 짧게 요청 순서에 가깝게 직렬화 |
| DB conditional update | Redis 장애나 race 상황에서도 최종 차감 수량 제한 |
| PaymentService | 복합 결제 실패 시 성공 결제 보상 |

k6 검증의 핵심 지표는 확정 주문 수, DB 잔여 재고, Redis counter, PENDING 주문 수다. 실행 절차는 [`05-runbook-and-validation.md`](./05-runbook-and-validation.md)를 따른다.

최근 로컬 실행에서는 다음 결과를 확인했다.

| 실행 시각 | peak TPS | iterations | 성공 예약 | 정상 매진 | 기타 expected fail | unexpected | dropped | p95 | p99 | 정합성 검증 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 2026-05-03 00:06 KST | 500 | 160,774 | 10 | 160,764 | 0 | 0.00% | 0 | 4.285ms | 14.451ms | 통과 |
| 2026-05-03 00:12 KST | 1000 | 318,524 | 10 | 318,514 | 0 | 0.00% | 0 | 3.275ms | 21.827ms | 통과 |

두 실행의 사후 정합성 검증 결과는 `confirmed_orders=10`, `pending_orders=0`, `db_remaining=0`, `expected_remaining=0`, `redis_stock=0`이었다. 즉 500/1000 TPS 피크 모두에서 확정 주문은 재고 10개로 제한되고, 매진 이후 요청은 Redis Lua counter 단계에서 정상 매진으로 분류되었다.

`booking-spike.js`는 50 TPS warm-up, 1초 ramp-up, 5분 peak 유지, 30초 cooldown을 포함한다. 따라서 k6 summary의 전체 `http_reqs rate`는 peak TPS보다 낮게 보이며, 용량 판단은 peak stage threshold, `dropped_iterations`, unexpected response, 사후 정합성 검증을 함께 본다.
