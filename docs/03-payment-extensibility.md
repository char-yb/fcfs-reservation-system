# Payment Extensibility Design

> 본 문서는 결제 수단 확장 구조를 다룬다.
> 핵심 질문: **새로운 결제 수단이 추가될 때 Booking API 비즈니스 로직 수정을 얼마나 줄일 수 있는가.**

---

## 1. 요구사항 분석

### 1.1 현재 지원 결제 수단

| 코드 | 명칭 | 처리 방식 |
|---|---|---|
| `CREDIT_CARD` | 신용카드 | 외부 PG 동기 승인 |
| `Y_PAY` | Y 페이 | 외부 PG 동기 승인 |
| `Y_POINT` | Y 포인트 | 내부 포인트 차감 |

위 목록에 없는 결제 수단 값은 public API enum에서 지원하지 않는다.

### 1.2 결제 조합 정책

| 조합 | 가능 여부 |
|---|---|
| 신용카드 단독 | 가능 |
| Y 페이 단독 | 가능 |
| Y 포인트 단독 | 가능 |
| 신용카드 + Y 포인트 | 가능 |
| Y 페이 + Y 포인트 | 가능 |
| 신용카드 + Y 페이 | 불가 |
| 동일 수단 중복 | 불가 |

정책은 `apps:application`의 `PaymentValidator`에 모아 둔다. Booking 흐름이나 개별 결제 전략에 조합 규칙을 흩뜨리지 않는다.

---

## 2. 현재 코드 구조

| 역할 | 현재 타입 | 위치 |
|---|---|---|
| 결제 수단 enum | `PaymentMethod` | `apps/domain` |
| 결제 명령 | `PaymentCommand` | `apps/application` |
| 결제 전략 인터페이스 | `PaymentStrategy` | `apps/application` |
| 외부 PG 인터페이스 | `PaymentGateway` | `apps/domain` |
| 전략 레지스트리 | `PaymentStrategyRegistry` | `apps/application` |
| PG 레지스트리 | `PgGatewayRegistry` | `apps/application` |
| 조합 정책 | `PaymentValidator` | `apps/application` |
| 실행/보상 조율 | `PaymentService` | `apps/application` |
| PG mock 구현 | `MockCreditCardGateway`, `MockYPayGateway` | `external:pg` |
| 보상 실패 기록 | `OutboxEventRepository`, `CompensationFailurePayload` | `apps/domain`, `storage:rdb` |

`apps:domain`은 Spring, JPA, Redis, PG 구현체를 알지 않는다. 결제 실행 command와 strategy contract, 실제 결제 알고리즘은 유스케이스 orchestration 성격이므로 `apps:application`에 두고, 외부 PG mock은 `external:pg`에 둔다.

---

## 3. 핵심 타입

```kotlin
enum class PaymentMethod {
    CREDIT_CARD,
    Y_PAY,
    Y_POINT,
}

data class PaymentCommand(
    val method: PaymentMethod,
    val amount: Money,
    val userId: Long,
    val attributes: Map<String, String> = emptyMap(),
)

interface PaymentStrategy {
    val method: PaymentMethod

    fun pay(command: PaymentCommand): PaymentExecutionResult

    fun cancel(transactionId: String): CancelResult
}
```

`PaymentCommand.userId`는 클라이언트가 결제 항목마다 보내지 않는다. `POST /api/v1/booking/{userId}`의 path variable을 `BookingRequest.toCommand`가 서버 측에서 주입한다. 따라서 Y 포인트 차감은 요청 body의 `attributes.userId`를 신뢰하지 않는다.

`attributes`는 결제 수단별 부가 정보만 담는다.

| 결제 수단 | 필요한 attributes |
|---|---|
| `CREDIT_CARD` | `cardToken` |
| `Y_PAY` | `payToken` |
| `Y_POINT` | 없음 |

---

## 4. 결제 전략

### 4.1 신용카드

`CreditCardPaymentStrategy`는 `cardToken`을 검증한 뒤 `PgGatewayRegistry.get(CREDIT_CARD)`로 PG gateway를 찾아 승인과 취소를 위임한다.

```kotlin
val response =
    pgGatewayRegistry.get(method).charge(
        PgChargeRequest(
            method = "CARD",
            amount = command.amount,
            token = cardToken,
        ),
    )
```

### 4.2 Y 페이

`YPayPaymentStrategy`는 `payToken`을 검증한 뒤 `PgGatewayRegistry.get(Y_PAY)`를 사용한다. PG 요청의 method 값은 `"Y_PAY"`로 통일한다.

### 4.3 Y 포인트

`YPointPaymentStrategy`는 외부 PG를 호출하지 않고 `UserPointService`로 포인트를 차감/환불한다.

```kotlin
val txId = userPointService.deduct(command.userId, command.amount)
```

포인트는 내부 DB 변경이고, 카드/Y 페이는 외부 PG 호출이다. 이 차이는 전략 내부에 캡슐화되어 `PaymentService`는 결제 수단별 세부 흐름을 알 필요가 없다.

---

## 5. Registry

`PaymentStrategyRegistry`는 Spring이 주입한 `List<PaymentStrategy>`를 `PaymentMethod` 키의 map으로 변환한다.

```kotlin
class PaymentStrategyRegistry(
    strategies: List<PaymentStrategy>,
) {
    private val byMethod = strategies.associateBy { it.method }

    init {
        PaymentMethod.entries.forEach { method ->
            requireNotNull(byMethod[method]) {
                "PaymentStrategy implementation missing for $method"
            }
        }
    }

    fun get(method: PaymentMethod): PaymentStrategy =
        checkNotNull(byMethod[method]) { "지원하지 않는 결제 수단: $method" }
}
```

새 enum이 추가됐는데 전략 구현체가 없으면 애플리케이션 기동 시점에 실패한다. 운영 중 특정 결제 수단만 누락되는 상태를 방지하기 위한 방어다.

`PgGatewayRegistry`는 외부 PG 호출이 필요한 결제 수단만 관리한다. 현재는 `CREDIT_CARD`, `Y_PAY`만 등록되고 `Y_POINT`는 등록하지 않는다.

---

## 6. PaymentValidator

`PaymentValidator`는 결제 조합과 금액 규칙을 검증한다.

```kotlin
fun validate(commands: List<PaymentCommand>) {
    require(commands.isNotEmpty()) { "결제 수단이 비어있습니다" }

    val types = commands.map { it.method }
    if (types.size != types.toSet().size) {
        throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)
    }

    val hasCard = PaymentMethod.CREDIT_CARD in types
    val hasYPay = PaymentMethod.Y_PAY in types
    if (hasCard && hasYPay) throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)

    commands.forEach {
        if (it.amount <= 0) throw ErrorException(ErrorType.PAYMENT_METHOD_INVALID)
    }
}
```

결제 합계는 `validateTotal(commands, expectedTotal)`에서 별도로 검증한다. 조합 정책이 바뀌면 이 클래스만 수정한다.

---

## 7. PaymentService

`PaymentService`는 복합 결제를 순차 실행하고, 일부 실패 시 이미 성공한 결제를 역순으로 취소한다.

실행 순서는 `Y_POINT`를 먼저 둔다.

1. Y 포인트는 내부 DB 변경이라 실패/복구 비용이 상대적으로 작다.
2. 외부 PG 승인 후 포인트 차감이 실패하면 PG 취소 네트워크 호출이 필요하다.
3. 따라서 포인트를 먼저 차감해 외부 보상 비용을 줄인다.

```kotlin
val ordered = commands.sortedBy { command ->
    when (command.method) {
        PaymentMethod.Y_POINT -> 0
        else -> 1
    }
}
```

보상은 best-effort다. 취소 자체가 실패하면 로그를 남기고, `outbox_events`에 `COMPENSATION_FAILURE` 이벤트를 저장한다. 현재 구현은 worker 재시도까지 자동 수행하지 않고, 사후 확인 가능한 근거를 남기는 단계다.

---

## 8. Booking 흐름과의 통합

Booking API는 결제 수단별 구현체를 직접 분기하지 않는다.

1. `BookingController`가 `Idempotency-Key` 형식을 검증한다.
2. `BookingRequest.toCommand(userId, orderKey)`가 path `userId`를 `PaymentCommand.userId`에 주입한다.
3. `BookingFacade`가 판매 오픈 검증과 `StockService.executeWithStockReservation` 호출을 조율한다.
4. `StockService`는 Redis Lua counter와 Redisson lock을 사용하고, lock 내부에서 `BookingReservationProcessor.reserve`가 DB 재고 차감과 PENDING 주문 생성을 `REQUIRES_NEW`로 수행한다.
5. lock 해제 후 `PaymentService.execute(command.payments, command.totalAmount, orderId)`가 결제 실행과 보상을 담당한다.
6. 결제 성공 시 결제 내역을 저장하고 주문을 확정한다. 실패 시 주문 실패 처리, DB 재고 복구, 필요 시 Redis counter 복구를 수행한다.

새 결제 수단 추가 시 Booking API와 BookingFacade는 수정하지 않는 것을 목표로 한다.

---

## 9. 새 결제 수단 추가 절차

동기 결제 수단을 추가하는 기본 절차는 다음과 같다.

1. `PaymentMethod` enum에 값 추가
2. `PaymentStrategy` 구현체 추가
3. 외부 PG가 필요하면 `PaymentGateway` 구현체 추가
4. 조합 규칙이 필요하면 `PaymentValidator` 수정
5. 단위 테스트에 허용/거부 조합과 보상 시나리오 추가

`PaymentStrategyRegistry`와 `PaymentService`는 일반적으로 수정하지 않는다.

비동기 결제 수단이 추가되면 `OrderStatus`에 결제 대기 상태와 webhook 처리 흐름이 필요하다. 현재 범위는 동기 승인 결제만 지원하므로 인터페이스에 비동기 필드를 미리 두지 않는다.

---

## 10. 패키지 구조

```text
apps/domain/src/main/kotlin/com/reservation/domain/payment/
├── PaymentMethod.kt
├── PaymentGateway.kt
├── PaymentExecutionResult.kt
└── CancelResult.kt

apps/application/src/main/kotlin/com/reservation/application/payment/
├── command/PaymentCommand.kt
├── PaymentStrategy.kt
├── PaymentService.kt
├── PaymentValidator.kt
├── PaymentStrategyRegistry.kt
├── PgGatewayRegistry.kt
├── PgPaymentStrategy.kt
├── credit/CreditCardPaymentStrategy.kt
├── ypoint/YPointPaymentStrategy.kt
└── ypay/YPayPaymentStrategy.kt

external/pg/src/main/kotlin/com/reservation/pg/
├── MockCreditCardGateway.kt
└── MockYPayGateway.kt

apps/domain/src/main/kotlin/com/reservation/domain/outbox/
├── CompensationFailurePayload.kt
├── OutboxEvent.kt
├── OutboxEventRepository.kt
└── OutboxEventType.kt
```

---

## 11. 테스트 전략

| 레이어 | 테스트 대상 | 목적 |
|---|---|---|
| Unit | `PaymentValidator` | 허용/거부 조합, 금액 검증 |
| Unit | `PaymentService` | Y 포인트 우선 실행, 실패 시 보상, 역순 취소 |
| Unit | `PaymentService` outbox | 보상 실패 시 `COMPENSATION_FAILURE` 기록 |
| API/Integration | Booking flow | enum 직렬화, 멱등성, 주문 확정/실패 흐름 |

핵심 테스트 케이스:

- `CREDIT_CARD + Y_POINT` 허용
- `Y_PAY + Y_POINT` 허용
- `CREDIT_CARD + Y_PAY` 거부
- 동일 결제 수단 중복 거부
- 합계 금액 불일치 거부
- Y 포인트 성공 후 Y 페이 실패 시 Y 포인트 환불
- 복수 성공 결제 보상은 역순 실행

---

## 12. 요약

| 컴포넌트 | 결제 수단 추가 시 변경 |
|---|---|
| `BookingController` / `BookingFacade` | 없음 |
| `PaymentService` | 보통 없음 |
| `PaymentStrategyRegistry` | 없음 |
| `PaymentValidator` | 조합 규칙 변경 시 수정 |
| `PaymentMethod` | enum 추가 |
| 새 `PaymentStrategy` 구현체 | 추가 |
| 새 `PaymentGateway` 구현체 | 외부 PG 필요 시 추가 |

결제 수단별 실행 알고리즘은 전략으로 분리하고, 조합 정책은 `PaymentValidator` 한 곳에 둔다. 이 구조가 Booking API 변경 없이 결제 수단을 확장하기 위한 핵심이다.
