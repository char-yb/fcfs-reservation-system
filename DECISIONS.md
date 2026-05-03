# DECISIONS

> 본 문서는 예약·결제 플랫폼 구현 과정에서 결정한 주요 기술적 쟁점과 선택 근거를 기록한다.
> 현재 코드 기준의 사실과, 아직 구현하지 않은 운영 항목을 분리해서 적는다.

설계의 기본 우선순위는 다음과 같다.

1. 재고 정합성: 오버셀링을 막는 것을 최우선으로 둔다.
2. 장애 복구 가능성: 실패를 자동으로 모두 해결하지 못하더라도, 복구 가능한 근거를 남긴다.
3. 피크 대응: 00시 전후 500~1000 TPS에서 매진 요청을 빠르게 실패시킨다.
4. 결제 확장성: 결제 수단 추가가 Booking 흐름 전체 수정으로 번지지 않게 한다.
5. 단순성: 요구 범위를 넘는 인프라와 추상화는 도입하지 않는다.

---

## 쟁점 1. 재고 정합성을 어디에서 최종 보장할 것인가

### 상황

한정 수량 10개인 상품 옵션에 평시 50 TPS, 피크 500~1000 TPS 요청이 몰린다. Redis와 분산락은 빠른 제어에는 유리하지만 장애가 날 수 있고, 외부 결제까지 하나의 원자적 트랜잭션으로 묶을 수도 없다.

따라서 "빠른 실패"와 "최종 정합성"을 같은 도구에 맡기지 않는 구조가 필요했다.

### 선택지

| 선택지 | 장점 | 한계 |
|---|---|---|
| DB 조건부 update만 사용 | 단순하고 정합성이 강함 | 매진 이후 요청까지 DB에 몰림 |
| Redis counter만 사용 | 빠른 매진 처리 | 주문 생성과 결제까지 원자적으로 보장하지 못함 |
| Redisson 일반 락만 사용 | 상품 옵션 단위 직렬화 가능 | 매진 이후에도 모든 요청이 lock 경합, 선착순 흐름에서 lock 획득 순서가 불명확 |
| Redisson 공정락만 사용 | 상품 옵션 단위 직렬화와 요청 순서 보강 가능 | 매진 이후에도 모든 요청이 lock 경합, 일반 락보다 대기열 관리 비용 존재 |
| Kafka/MQ 직렬화 | 처리 순서와 backpressure를 명확히 관리 가능 | 인프라와 운영 복잡도가 요구 범위를 초과 |
| 다층 방어 | 빠른 실패와 최종 정합성을 분리 | Redis/DB 양쪽 상태를 관측해야 함 |

### 선택

`Redis Lua conditional decrement + Redisson 공정락(fair lock) + DB conditional update/CHECK` 다층 방어를 선택했다.

```text
L1 Redis Lua counter
  - stock:{productOptionId}가 1 이상일 때만 차감
  - 매진이면 값을 바꾸지 않고 -1 반환

L2 Redisson 공정락
  - lock:booking:{productOptionId}
  - DB 재고 차감과 PENDING 주문 생성 구간만 보호

L3 DB conditional update
  - product_stock.remaining_quantity > 0 조건으로 update
  - CHECK(remaining_quantity >= 0)로 마지막 방어
```

### 왜 그렇게 판단했는지

Redis는 매진 이후의 대부분 요청을 DB에 보내지 않기 위한 앞단 방어선이다. 하지만 Redis는 최종 데이터 원장이 아니므로 Redis가 정상이어도 DB 조건부 update가 마지막 정합성 기준이어야 한다.

DB 조건부 update는 다음 이유로 `SELECT ... FOR UPDATE`보다 적합했다.

- 재고 차감은 "읽고 복잡한 분기"가 아니라 "남아 있으면 1 감소"라는 단일 조건이다.
- InnoDB는 update 대상 row에 X-lock을 잡으므로 조건 평가와 차감이 한 SQL 안에서 처리된다.
- 별도의 select round trip을 줄여 lock 점유 시간을 짧게 만든다.

트레이드오프는 Redis와 DB의 일시적 재고 불일치 가능성이다. 이를 줄이기 위해 Redis counter는 Lua로 0 아래로 내려가지 않게 만들었고, 부하테스트 후 정합성 검증 스크립트인 `verify-local-invariants.sh`로 DB/Redis 관계를 확인하도록 했다.

Redis counter를 통과한 소수 요청은 공정락으로 DB 예약 구간에 진입한다. 일반 락보다 lock 대기 순서가 명확하므로, 여러 애플리케이션 서버가 동시에 요청을 처리해도 특정 서버나 스레드가 반복적으로 앞서는 위험을 줄일 수 있다.

이 선택의 트레이드오프는 일반 락보다 Redis 대기열 관리 비용이 생긴다는 점이다. 하지만 현재 구조에서는 Redis counter가 실제 재고 수량만큼만 통과시키고, lock 안의 작업도 DB 재고 차감과 PENDING 주문 생성으로 짧다. 선착순 요구사항에서는 처리량을 조금 더 아끼는 것보다, Redis를 통과한 요청의 DB 예약 순서를 더 분명히 하는 편이 맞다고 판단했다. 일반 락과 공정락 비교는 [`02-concurrency-and-locking.md`](docs/02-concurrency-and-locking.md#34-일반-락과-공정락-비교)에 정리했다.

---

## 쟁점 2. Redis counter를 `DECR 후 보정`이 아니라 Lua 조건부 차감으로 바꾼 이유

### 상황

초기에는 Redis `DECR` 후 결과가 음수이면 `INCR`로 되돌리는 방식도 고려할 수 있었다. 하지만 1000 TPS 부하에서 동시 실패와 보정이 겹치면 Redis counter가 음수로 밀리는 drift가 발생할 수 있다.

재고가 0인 상황은 오류가 아니라 정상적인 매진 결과다. 이때 counter 값을 변경한 뒤 보정하는 방식은 피크 구간에서 불필요한 write를 늘린다.

### 선택지

| 선택지 | 판단 |
|---|---|
| `DECR` 후 음수면 `INCR` | 구현은 쉽지만 고부하에서 보정 실패나 순서 꼬임으로 drift 위험 |
| DB만 사용 | 정합성은 강하지만 매진 요청이 모두 DB까지 감 |
| 이중 counter | 비동기 queue 구조에는 유용할 수 있으나 현재 동기 흐름에는 과함 |
| Lua 조건부 차감 | Redis 서버 안에서 조건 확인과 차감을 원자적으로 처리 |

### 선택

`StockRedisCounterRepository.decrement`를 Redisson `RScript` 기반 Lua 조건부 차감으로 구현했다.

```lua
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
if current <= 0 then
    return -1
end
return redis.call('DECR', KEYS[1])
```

`current <= 0` 또는 키가 없으면 값을 바꾸지 않고 `-1`을 반환한다. 따라서 sold-out 응답에서는 Redis counter 복구 `INCR`을 호출하지 않는다.

### 왜 그렇게 판단했는지

이 변경은 코드 수정 범위가 작으면서 drift의 원인을 직접 제거한다. 매진 요청은 Redis write 없이 `-1`로 끝나고, 실제로 차감에 성공한 요청만 lock과 DB 예약 단계로 들어간다.

`increment`에는 circuit breaker를 붙이지 않았다. 보상성 `INCR`이 circuit open 때문에 호출 자체가 차단되면 drift가 더 커질 수 있기 때문이다. 복구성 increment는 실제 호출을 시도하고, 실패하면 로그로 drift 가능성을 남기는 쪽을 선택했다.

로컬 k6 재검증에서는 500 TPS peak에서 `iterations=160,774`, `booking_success_total=10`, `stock_sold_out_total=160,764`, `dropped_iterations=0`, `booking_unexpected_response_rate=0.00%`를 확인했다. 1000 TPS peak에서도 `iterations=318,524`, `booking_success_total=10`, `stock_sold_out_total=318,514`, `dropped_iterations=0`, `booking_unexpected_response_rate=0.00%`였다. 두 실행 모두 사후 정합성 검증은 `confirmed_orders=10`, `pending_orders=0`, `db_remaining=0`, `redis_stock=0`으로 통과했다.

---

## 쟁점 3. 분산락 범위와 트랜잭션 전파를 어떻게 나눌 것인가

### 상황

분산락은 여러 서버에서 같은 상품 옵션의 재고 예약 구간을 직렬화하기 위해 사용한다. 문제는 lock과 DB 트랜잭션의 수명이다.

MySQL 기본 격리 수준인 REPEATABLE READ에서 상위 트랜잭션이 lock 밖까지 길게 살아 있으면, lock 해제 시점과 commit 시점이 어긋날 수 있다. 또한 외부 PG 호출을 lock 안에 두면 상품 옵션 단위 직렬화 시간이 길어진다.

### 선택지

| 선택지 | 장점 | 한계 |
|---|---|---|
| `BookingFacade` 전체를 `@Transactional` | 코드 흐름은 단순 | lock 해제와 commit 시점이 어긋날 수 있고 PG 호출 동안 커넥션 점유 |
| lock 안에서 결제까지 처리 | 한 요청의 전체 흐름을 직렬화 | PG 지연이 곧 lock 점유 시간으로 번짐 |
| lock 안에서는 DB 예약만 처리 | lock 점유 시간이 짧음 | 결제 실패 시 보상 흐름이 필요 |
| 분산락 없이 DB update만 사용 | 단순 | Redis 정상 상황의 공정성과 피크 완화가 약함 |

### 선택

lock 안에서는 `BookingReservationProcessor.reserve`만 실행한다. 이 메서드는 `REQUIRES_NEW` 트랜잭션으로 DB 재고 차감과 PENDING 주문 생성을 끝내고, commit 후 lock을 해제한다.

현재 흐름은 다음과 같다.

```text
BookingFacade
  → StockService: Redis counter 차감
  → Redisson 공정락 획득
  → BookingReservationProcessor.reserve(REQUIRES_NEW)
  → lock 해제
  → PaymentService.execute(NOT_SUPPORTED)
  → PaymentService.saveApproved(REQUIRES_NEW)
  → BookingReservationProcessor.confirm(REQUIRES_NEW)
```

### 왜 그렇게 판단했는지

분산락이 보호해야 하는 핵심은 "같은 상품 옵션 재고를 예약 가능한 주문으로 바꾸는 구간"이다. 결제는 그 이후의 외부 I/O이므로 lock 밖으로 빼야 한다.

`REQUIRES_NEW`를 사용한 이유는 lock 내부 DB 변경이 상위 트랜잭션에 합류하지 않게 하기 위해서다. lock이 끝나기 전에 DB 변경이 commit되어야 다음 요청이 오래된 snapshot으로 같은 재고를 다시 보는 위험을 줄일 수 있다.

`PaymentService.execute`는 `NOT_SUPPORTED`로 둔다. 외부 PG 호출과 포인트 차감이 상위 DB 트랜잭션에 묶이면 원격 I/O 시간만큼 DB 커넥션과 lock을 점유한다.

트레이드오프는 결제 중 서버가 종료되면 PENDING 주문과 차감된 DB 재고가 남을 수 있다는 점이다. 현재는 이 상태를 자동 복구하지 않는다. PG 승인 여부와 포인트 차감 여부를 대조하기 전에는 주문 실패 처리와 재고 복구를 안전하게 수행할 수 없기 때문이다.

---

## 쟁점 4. Redis 장애 폴백과 Circuit Breaker는 어느 모듈 책임인가

### 상황

Redis는 빠른 counter와 공정락을 제공하지만, Redis 장애가 예약 정합성 실패로 이어지면 안 된다. 동시에 application 계층이 Redisson이나 Resilience4j 세부 타입을 알아서는 안 된다.

추후 Kafka 같은 MQ가 도입되어도 같은 문제가 반복된다. 외부 인프라 장애 판단과 회로 차단은 해당 adapter 계층의 책임으로 두는 편이 확장에 유리하다.

### 선택지

| 선택지 | 장점 | 한계 |
|---|---|---|
| application에서 Redisson 예외 처리 | 구현 위치가 booking 흐름과 가까움 | application이 Redis 구현체와 Resilience4j를 알게 됨 |
| storage:redis에서 예외 변환 | 모듈 경계가 명확함 | AOP/설정 구조를 이해해야 함 |
| circuit breaker를 쓰지 않음 | 단순 | Redis 장애 시 요청마다 timeout을 반복 |
| Redis HA/Sentinel/Cluster 도입 | 장애 자체를 줄임 | 추가 인프라 비용이 요구 범위를 초과 |

### 선택

`resilience4j-spring-boot4`는 `storage:redis` 모듈에 둔다. `@CircuitBreaker(name = "redis")`는 Redis 호출 메서드에만 붙이고, `RedisCircuitBreakerExceptionAspect`가 `CallNotPermittedException`과 Redisson 계열 예외를 `RedisUnavailableException`으로 변환한다.

`apps:application`의 `StockService`는 `RedisUnavailableException`만 보고 DB-only 폴백을 수행한다.

### 왜 그렇게 판단했는지

장애의 원인을 식별하는 책임은 adapter가 가져야 한다. application은 "Redis를 사용할 수 없다"는 의미만 알면 충분하다.

설정은 `storage/redis/src/main/resources/redis.yml`에 둔다.

| 설정 | 값 | 근거 |
|---|---:|---|
| `command-timeout-ms` | 200ms | Redis 명령은 짧아야 하며, 지연이 길면 DB-only 폴백으로 빠르게 전환 |
| `sliding-window-size` | 100 | 100 booking TPS 기준 약 0.5초 분량의 Redis ops를 표본으로 봄 |
| `minimum-number-of-calls` | 20 | 1~2회 timeout으로 circuit이 열리지 않게 함 |
| `failure-rate-threshold` | 50% | 표본 절반 이상 실패 시 Redis 장애로 판단 |
| `wait-duration-in-open-state` | 5s | Redis 재시도를 요청마다 반복하지 않되 회복 지연을 길게 만들지 않음 |
| `permitted-number-of-calls-in-half-open-state` | 10 | 회복 여부를 제한된 호출로 확인 |

slow-call 기반 open 조건은 제거했다. 1000 TPS 상황에서는 Redis 자체 장애가 아니어도 정상 응답이 느린 호출로 집계될 수 있고, 이 경우 DB-only 폴백을 과도하게 열어 DB 부하를 키울 수 있기 때문이다. 현재는 Redis command timeout/예외를 장애 판단 기준으로 둔다.

---

## 쟁점 5. Redis 장애 시에도 예약을 계속 받을 것인가

### 상황

Redis가 내려가면 L1 counter와 L2 lock을 사용할 수 없다. 이때 API를 닫을지, DB-only path로 제한적으로 계속 받을지 결정해야 했다.

### 선택지

| 선택지 | 장점 | 한계 |
|---|---|---|
| Redis 장애 시 예약 API 전체 실패 | 공정성 정책이 명확함 | Redis 단일 장애가 전체 판매 중단으로 이어짐 |
| DB-only 폴백 | 정합성을 유지하며 일부 예약 가능 | 처리량과 공정성은 낮아짐 |
| MQ로 대기열 전환 | 복구와 순서 제어가 좋음 | Kafka/RabbitMQ 등 추가 인프라 필요 |

### 선택

Redis 장애가 action 시작 전 감지되면 DB-only 폴백으로 `BookingReservationProcessor.reserve`를 실행한다. 이때 최종 정합성은 DB 조건부 update와 CHECK 제약이 보장한다.

반대로 `STOCK_SOLD_OUT`, `LOCK_ACQUISITION_FAILED`, 결제 실패는 Redis 장애가 아니므로 폴백하지 않는다.

### 왜 그렇게 판단했는지

정상 상태에서는 Redis가 매진 요청을 빠르게 잘라 내고, 장애 상태에서는 DB가 느리지만 정확한 기준이 된다. 이는 "처리량보다 정합성"을 우선하는 결정이다.

비용은 명확하다.

- Redis 장애 중에는 DB row lock 경합이 늘어난다.
- Redis 도착 순서 기반의 공정성은 약해진다.
- 피크 1000 TPS를 같은 성능으로 처리한다는 보장은 하지 않는다.

하지만 이 비용은 오버셀링보다 낮은 위험으로 판단했다.

---

## 쟁점 6. 결제 수단과 복합 결제를 어떻게 확장 가능하게 둘 것인가

### 상황

현재 public API가 지원해야 하는 결제 수단은 `CREDIT_CARD`, `Y_PAY`, `Y_POINT` 세 가지다.

허용 조합은 다음으로 제한한다.

- 단독 결제: `CREDIT_CARD`, `Y_PAY`, `Y_POINT`
- 복합 결제: `CREDIT_CARD + Y_POINT`, `Y_PAY + Y_POINT`
- 금지: `CREDIT_CARD + Y_PAY`, 동일 수단 중복, 0 이하 금액, 총합 불일치

### 선택지

| 선택지 | 장점 | 한계 |
|---|---|---|
| `BookingFacade`에서 결제 수단별 분기 | 흐름이 한눈에 보임 | 새 결제 수단마다 booking 코드가 커짐 |
| Composite 패턴 | 결제 묶음을 하나의 객체로 다룰 수 있음 | 현재 결제는 tree가 아니라 flat list라 과함 |
| Strategy + Registry + Validator | 수단별 실행과 조합 정책을 분리 | 파일 수는 늘어남 |

### 선택

`PaymentStrategy`, `PaymentStrategyRegistry`, `PgGatewayRegistry`, `PaymentValidator`, `PaymentService` 구조를 선택했다.

결제 수단별 실행은 strategy가 담당한다.

- `CreditCardPaymentStrategy`: `cardToken` 검증 후 PG mock 호출
- `YPayPaymentStrategy`: `payToken` 검증 후 PG mock 호출
- `YPointPaymentStrategy`: `PaymentCommand.userId` 기준으로 내부 포인트 차감

조합 정책은 `PaymentValidator`에 모았다. `BookingFacade`는 결제 수단별 세부 구현을 모른다.

### 왜 그렇게 판단했는지

결제 수단 추가 시 수정 범위를 다음 정도로 제한할 수 있다.

1. `PaymentMethod` enum 추가
2. `PaymentStrategy` 구현체 추가
3. 외부 PG가 필요하면 `PaymentGateway` 구현체 추가
4. 조합 규칙이 바뀌면 `PaymentValidator` 수정

`PaymentStrategyRegistry`는 `PaymentMethod.entries` 전체에 strategy가 있는지 기동 시 검증한다. enum만 추가하고 구현체를 빠뜨리는 실수를 운영 전에 발견하기 위한 장치다.

---

## 쟁점 7. 복합 결제의 실행 순서와 보상 전략

### 상황

복합 결제에서는 일부 결제가 성공한 뒤 다음 결제가 실패할 수 있다. 예를 들어 `Y_POINT` 차감은 성공했지만 `Y_PAY` 승인이 실패할 수 있다.

분산 트랜잭션으로 내부 포인트 DB와 외부 PG를 하나로 묶을 수 없으므로, 실패 시 보상 방식을 정해야 했다.

### 선택지

| 선택지 | 장점 | 한계 |
|---|---|---|
| XA/분산 트랜잭션 | 이론적으로 원자성 제공 | 일반 PG와 맞지 않고 복잡도 큼 |
| Saga 프레임워크 | 장기 트랜잭션 모델 명확 | 현재 동기 결제 3종에는 과한 인프라 |
| best-effort 보상 | 구현이 단순하고 현재 범위에 적합 | 보상 실패는 별도 추적 필요 |

### 선택

`PaymentService`가 결제를 순차 실행하고, 실패하면 이미 성공한 결제를 역순으로 cancel한다. 실행 순서는 `Y_POINT`를 먼저 둔다.

```text
Y_POINT 먼저 실행
  → 내부 DB 차감 실패를 먼저 확인
  → 이후 CREDIT_CARD/Y_PAY 외부 PG 승인
  → 후속 실패 시 성공한 결제를 역순 cancel
```

### 왜 그렇게 판단했는지

외부 PG 승인 후 내부 포인트 차감이 실패하면 PG 취소라는 네트워크 보상이 필요하다. 반대로 포인트를 먼저 차감하면 내부 실패를 빠르게 알 수 있고, 외부 보상 비용을 줄일 수 있다.

보상은 best-effort다. cancel 실패까지 다시 보상하려 하면 흐름이 끝나지 않는다. 따라서 cancel 실패는 `outbox_events`에 `COMPENSATION_FAILURE`로 저장하고, 사용자 요청 흐름은 실패 응답으로 종료한다.

현재 구현은 outbox worker를 자동 실행하지 않는다. outbox는 보상 실패를 사후 확인하고 재처리할 근거를 남기는 저장소 역할이다.

---

## 쟁점 8. Outbox와 비동기 이벤트를 어디까지 사용할 것인가

### 상황

Outbox entity는 추가되어 있다. 하지만 정합성과 장애 복구가 핵심인 예약/결제 흐름에서 `@Async`나 `@EventListener` 기반 후처리를 넣을지 판단해야 했다.

### 선택지

| 선택지 | 장점 | 한계 |
|---|---|---|
| `@Async` / `@EventListener`로 후처리 | 요청 응답을 빠르게 줄 수 있음 | 핵심 상태 변경 실패가 비동기 영역으로 밀림 |
| outbox worker까지 구현 | 재처리 자동화 가능 | worker, locking, retry, 계속 실패하는 이벤트 처리 정책 필요 |
| outbox를 실패 근거 기록으로 제한 | 현재 범위에서 단순 | 자동 복구는 운영 TODO로 남음 |

### 선택

P0/P1 핵심 흐름에서는 `@Async`, `@EventListener`를 사용하지 않는다. 예약 생성, 주문 실패 처리, DB 재고 복구는 동기 트랜잭션으로 처리한다.

Outbox는 현재 `COMPENSATION_FAILURE` 기록에만 사용한다. 저장은 `REQUIRES_NEW`로 수행해 이후 주문/재고 복구가 실패하더라도 보상 실패 근거가 남게 했다.

### 왜 그렇게 판단했는지

정합성 복구는 "언젠가 이벤트가 처리될 것"이라는 가정에 기대면 안 된다. 특히 결제 실패 후 재고 복구와 주문 상태 변경은 사용자 요청의 실패 처리 안에서 가능한 한 끝내야 한다.

Outbox worker는 운영 도입 시 필요할 수 있다. 다만 현재 범위에서는 다음 정책까지 함께 설계해야 하므로 제외했다.

- 재시도 횟수와 재시도 간격
- 중복 처리 idempotency
- 계속 실패하는 이벤트 격리
- 관리자 알림과 수동 처리 화면

따라서 지금의 outbox는 자동 복구 시스템이 아니라, 보상 실패를 잃지 않기 위한 기록 장치다.

---

## 쟁점 9. 멱등성을 Redis가 아니라 DB unique key에 둔 이유

### 상황

사용자 더블 클릭이나 네트워크 재시도로 같은 예약 요청이 두 번 들어올 수 있다. 중복 주문과 중복 결제는 막아야 한다.

### 선택지

| 선택지 | 장점 | 한계 |
|---|---|---|
| Redis `SETNX` + TTL | 정상 Redis 상태에서 빠름 | Redis 장애/TTL 만료 후 최종 보장이 약함 |
| DB unique key | durable하고 Redis 장애와 무관 | 같은 결과 replay까지는 제공하지 않음 |
| 별도 idempotency table | replay와 상태 추적에 유리 | 현재 주문 모델보다 구현량 증가 |

### 선택

현재는 `Idempotency-Key`를 `orders.order_key`에 저장하고 unique 제약으로 중복을 막는다. API는 헤더 존재 여부와 UUID 형식을 검증하고, application은 같은 `orderKey`가 있으면 `DUPLICATE_REQUEST`를 반환한다.

### 왜 그렇게 판단했는지

멱등성의 최종 방어선은 Redis보다 DB가 적합하다. Redis는 장애 폴백 대상이므로, Redis에 멱등성 최종 기준을 두면 Redis 장애 시 가장 막아야 하는 중복 결제 위험이 커진다.

현재 구현은 "같은 요청의 결과 replay"까지는 제공하지 않는다. 같은 `Idempotency-Key` 재요청은 기존 결과를 재생하지 않고 `DUPLICATE_REQUEST`로 거절한다. 결과 replay는 별도 idempotency record나 주문 상태별 응답 재구성이 필요하므로 후순위 개선으로 둔다.

---

## 쟁점 10. 주문 상태 전이를 조건부 update로 제한한 이유

### 상황

예약 흐름은 `PENDING → CONFIRMED` 또는 `PENDING → FAILED`로 끝난다. 결제 실패, 보상 실패, 네트워크 재시도, 운영 재처리 과정에서 같은 주문 상태 변경이 중복 호출될 수 있다.

### 선택지

| 선택지 | 장점 | 한계 |
|---|---|---|
| 단순 `updateStatus(orderId, next)` | 구현이 간단 | 이미 확정된 주문도 실패로 덮을 수 있음 |
| 상태 객체에 전이 규칙 위임 | 도메인 표현력이 좋음 | 저장소 update와 race를 별도로 막아야 함 |
| `updateStatusIfCurrent` | DB update 조건으로 race 방어 | 허용 전이 추가 시 repository 메서드 사용 규칙 필요 |

### 선택

`OrderRepository.updateStatusIfCurrent`를 사용해 현재 상태가 `PENDING`일 때만 `CONFIRMED` 또는 `FAILED`로 바꾼다. 실패하면 `INVALID_ORDER_STATUS_TRANSITION`을 반환한다.

### 왜 그렇게 판단했는지

상태 전이는 데이터 정합성 문제다. 애플리케이션에서 읽은 뒤 분기하는 것만으로는 동시 호출을 막기 어렵다. DB update 조건에 현재 상태를 포함하면 "이미 처리된 주문을 뒤늦게 덮어쓰기"를 막을 수 있다.

이 결정은 `confirm`과 `failAndRelease`를 `REQUIRES_NEW`로 둔 선택과도 맞물린다. 각 상태 전이는 독립된 짧은 트랜잭션으로 끝나고, 실패하면 호출자가 보상 실패 로그를 남긴다.

---

## 쟁점 11. 기술 스택과 라이브러리 도입 사유

### 상황

핵심 요구사항은 Kotlin/Spring 기반 API, 주문/결제 도메인, Redis 기반 동시성 제어, DB 정합성, 테스트와 부하 검증이다. 라이브러리는 문제 해결에 직접 연결되는 것만 남기는 방향으로 정리했다.

### 선택

| 도구 | 현재 사용 | 도입 사유 | 비용/주의점 |
|---|---|---|---|
| Kotlin 2.3.20 | 전체 모듈 | null-safety, data class, DSL 기반 테스트와 궁합 | Spring/JPA proxy 설정 주의 |
| Spring Boot 4.0.6 | API/runtime | WebMVC, JPA, validation, actuator 통합 | 최신 major라 의존성 변화 확인 필요 |
| Java toolchain 25 | Gradle toolchain | 현재 로컬/빌드 기준 통일 | 실행 환경도 Java 25 준비 필요 |
| Spring WebMVC | API | 동기 결제/예약 흐름에 충분 | 대량 long polling에는 부적합 |
| Virtual threads | `application.yml` | 동기 MVC에서 I/O 대기 비용 완화 | DB pool, 외부 timeout 설정은 별도 관리 필요 |
| Spring Data JPA | RDB adapter | repository 구현 비용 절감, 트랜잭션 연동 | bulk update와 영속성 컨텍스트 차이 주의 |
| Flyway | schema/seed | DDL 이력과 local seed를 재현 가능하게 관리 | repeatable seed는 profile 분리 필요 |
| Redisson | Redis counter/lock | `RScript`, `RLock` 제공 | Redis client 예외를 adapter에서 변환해야 함 |
| Resilience4j | Redis circuit | Redis timeout 반복을 줄이고 DB-only 폴백 전환 | application 계층으로 새지 않게 storage:redis에 한정 |
| Kotest | test DSL/assertion | 한글 테스트명과 Kotlin 친화적 assertion | JUnit Platform 설정 필요 |
| Testcontainers | Redis/MySQL 통합 테스트 | 실제 Redis Lua/Redisson 동작 검증 | Docker 실행 환경 필요 |
| JaCoCo | coverage gate | domain/application 핵심 로직 80% line coverage gate | 설정이 너무 넓으면 DTO/인터페이스가 노이즈가 됨 |
| k6 | load test | 50 TPS 평시와 500/1000 TPS 피크 재현 | 로컬 장비와 VU 설정에 따라 결과 편차 |
| Docker Compose | local runbook | MySQL, Redis, API를 같은 절차로 실행 | 운영 HA를 의미하지 않음 |

### 왜 그렇게 판단했는지

추가 인프라는 Redis와 MySQL 정도로 제한했다. Redis는 피크 매진 요청을 DB 앞에서 빠르게 잘라내는 효과가 커서 비용 대비 이득이 있다. 반면 Kafka/MQ는 현재 동기 예약/결제 흐름에서 반드시 필요하지 않고, 도입하면 consumer 중복 처리 방지, 재처리, 계속 실패하는 메시지 처리, 모니터링까지 같이 풀어야 한다.

Spring WebFlux도 채택하지 않았다. 현재 병목은 reactive programming 모델이 아니라 재고 정합성과 외부 결제 보상이다. 동기 MVC와 virtual threads로 충분하다고 판단했다.

---

## 쟁점 12. 테스트와 검증 기준을 어디까지 둘 것인가

### 상황

정합성과 장애 복구가 핵심인 프로젝트라 단순 happy path 테스트만으로는 부족하다. 동시에 모든 repository를 세밀하게 테스트하면 검증 효율이 낮다.

### 선택지

| 선택지 | 장점 | 한계 |
|---|---|---|
| Controller 중심 테스트 | API 계약 확인에 좋음 | 재고/결제 보상 로직의 edge case가 빠짐 |
| Repository 테스트까지 전부 작성 | DB 동작을 넓게 확인 | 구현량 대비 중복 검증이 많음 |
| 핵심 application/domain 중심 + Redis Testcontainers | 비즈니스 위험에 집중 | 일부 JPA repository 세부 동작은 간접 검증 |

### 선택

핵심 비즈니스 로직 중심으로 테스트를 둔다.

- `PaymentValidatorTest`: 결제 조합, 중복 수단, 금액 합계 검증
- `PaymentServiceTest`: `Y_POINT` 우선 실행, 역순 보상, outbox 기록
- `StockServiceTest`: Redis sold-out, lock/action 실패 복구, Redis 장애 폴백
- `StockRedisCounterRepositoryTest`: Lua no-underflow를 Redis Testcontainers로 검증
- `BookingReservationProcessorTest`: DB 재고 차감, 주문 생성, 상태 전이
- `BookingFacadeTest`: 예약 성공, 결제 실패 복구, 폴백 흐름
- k6: 50 TPS 평시와 500/1000 TPS 피크, 정합성 검증 스크립트로 DB/Redis 정합성 확인

최근 로컬 측정에서는 500 TPS와 1000 TPS booking spike 모두 `dropped_iterations=0`, `unexpected=0.00%`, `http_req_failed=0.00%`로 끝났고, 사후 DB/Redis 정합성 검증도 통과했다. 특히 1000 TPS 실행의 `p95=3.275ms`, `p99=21.827ms`는 매진 이후 요청이 Redis Lua counter에서 빠르게 종료되고 있음을 보여준다. 단, 이 수치는 로컬 Docker Compose 환경의 현재 시드 데이터와 mock PG 기준이므로 운영 성능 보증값이 아니라 설계 정합성 검증 근거로 해석한다.

### 왜 그렇게 판단했는지

테스트 비용은 위험도에 맞춰야 한다. 이 프로젝트에서 가장 위험한 지점은 다음이다.

- 같은 재고가 두 번 팔리는가
- 결제 일부 성공 후 실패했을 때 복구되는가
- Redis 장애가 application 전체 실패로 번지는가
- 매진 요청이 Redis counter를 음수로 만드는가
- 중복 요청이 중복 주문/결제로 이어지는가

따라서 repository 전체 테스트보다 application orchestration, Redis adapter의 실제 동작, k6 정합성 검증에 더 많은 비중을 둔다.

---

## 명시적으로 제외한 것

아래 항목은 현재 구현 범위에서 제외했다. 단, 운영 도입 시에는 우선순위가 높아질 수 있다.

| 제외 항목 | 제외 이유 | 운영 도입 시 필요 조건 |
|---|---|---|
| 실제 PG 연동 | 현재는 mock PG로 결제 전략과 보상 구조 검증 | PG idempotency key, timeout, 결과 조회 API |
| 오래된 PENDING 주문 복구 API/Batch | PG 승인 여부와 포인트 차감 여부를 대조하는 기준이 아직 없음 | 관리자 API 또는 Spring Batch Job, PG 결과 조회, 포인트 ledger, 관리자 알림 |
| outbox 자동 worker | 보상 실패 기록까지만 구현 | 재시도 간격, 중복 처리, 계속 실패하는 이벤트 처리 정책 |
| Kafka/MQ | 현재 동기 구조에서는 비용 대비 과함 | consumer idempotency, DLQ, 운영 모니터링 |
| 결과 replay idempotency | 현재는 duplicate reject로 충분하다고 판단 | idempotency table 또는 주문 상태별 응답 재구성 |
| 인증/인가 | 현재 요구 범위 밖 | 사용자 인증, 권한, rate limit |
| Redis HA/Cluster | 로컬/검증 환경 비용 제한 | Sentinel/Cluster, failover drill |
| Prometheus/Grafana | 핵심 지표와 검증 스크립트까지만 제공 | circuit state, pending orders, outbox failure alert |

---

## 현재 설계의 남은 위험

현재 구현은 오버셀링 방지와 실패 보상 흐름을 우선한다. 다만 다음 위험은 남아 있다.

1. 결제 실행 중 API 서버가 종료되면 `PENDING` 주문과 차감된 DB 재고가 남을 수 있다. PG 승인 여부와 포인트 차감 여부를 대조하는 복구 루프가 필요하다.
2. PG 승인 성공 후 주문 확정 전에 서버가 종료되면 결제 성공과 주문 미확정 상태가 벌어질 수 있다.
3. 보상 cancel 자체가 실패하면 `outbox_events` 기록은 남지만 자동 재처리는 없다.
4. Redis 장애 폴백 중에는 정상 Redis 경로의 공정성과 처리량을 보장하지 않는다.
5. 같은 `Idempotency-Key` 재시도에 대해 기존 성공 결과 replay는 제공하지 않는다.

이 위험들은 숨기지 않고 `docs/04-fault-tolerance.md`와 [`06-long-term-roadmap.md`](docs/06-long-term-roadmap.md)에서 운영 항목으로 추적한다.
