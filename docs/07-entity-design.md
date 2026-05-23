# 엔티티 설계 근거

이 문서는 선착순 예약 결제 시스템에서 각 엔티티를 어떻게 나누었는지, 왜 그렇게 나누었는지, 어떤 확장성을 고려했는지를 기록한다.

---

## 1. 전체 엔티티 목록

| 엔티티 | 테이블 | 위치 |
|---|---|---|
| `UserEntity` | `users` | `storage/rdb/user` |
| `UserPointEntity` | `user_points` | `storage/rdb/user/point` |
| `ProductEntity` | `products` | `storage/rdb/product` |
| `ProductOptionEntity` | `product_options` | `storage/rdb/product/option` |
| `BookingScheduleEntity` | `booking_schedules` | `storage/rdb/product/booking` |
| `ProductStockEntity` | `product_stock` | `storage/rdb/product/stock` |
| `OrderEntity` | `orders` | `storage/rdb/order` |
| `OrderProductEntity` | `order_products` | `storage/rdb/order/product` |
| `PaymentEntity` | `payments` | `storage/rdb/payment` |
| `OutboxEventEntity` | `outbox_events` | `storage/rdb/common/outbox` |

---

## 2. Entity와 Domain 분리 원칙

모든 엔티티는 `storage:rdb` 모듈에만 존재하고, `apps:domain`에는 순수 도메인 data class가 따로 있다.

```
storage:rdb        → JPA @Entity (저장소 표현)
apps:domain        → data class (비즈니스 표현)
```

각 엔티티는 `toDomain()` 메서드로 도메인 객체를 반환하고, 도메인 계층은 JPA 어노테이션과 영속성 세부를 모른다. `apps:application`이 `storage:rdb`에 의존하되, `apps:domain`은 `storage:rdb`에 의존하지 않는다.

이 분리의 핵심 이유는 두 가지다:
- 비즈니스 규칙(포인트 차감, 주문 상태 전이, 판매 오픈 검증)을 JPA proxy나 LazyInitializationException 걱정 없이 단위 테스트할 수 있다.
- JPA 매핑 변경이 도메인 로직으로 번지지 않는다.

---

## 3. BaseEntity 설계

```kotlin
@MappedSuperclass
abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @CreationTimestamp val createdAt: LocalDateTime
    @UpdateTimestamp  var updatedAt: LocalDateTime?
    var deletedAt: LocalDateTime?

    fun softDelete() { this.deletedAt = LocalDateTime.now() }
}
```

**`BaseEntity`를 상속하지 않는 엔티티가 있다**: `ProductStockEntity`, `UserPointEntity`, `OutboxEventEntity`, `OrderProductEntity`.

| 엔티티 | `BaseEntity` 상속 안 하는 이유 |
|---|---|
| `ProductStockEntity` | PK가 `product_option_id` (FK). 재고는 옵션 생성 시 함께 만들어지고 삭제되지 않는다. `createdAt`이 필요 없고, soft delete 개념도 없다. |
| `UserPointEntity` | PK가 `user_id` (FK). 포인트 잔액은 사용자 1:1 레코드다. 생성 시각보다 잔액과 최종 변경 시각(`updatedAt`)이 중요하다. |
| `OutboxEventEntity` | PK는 auto-increment지만 이벤트 특성상 `createdAt`을 직접 제어해야 하고, soft delete 대신 `status`와 `processed_at`으로 이벤트 생명주기를 관리한다. |
| `OrderProductEntity` | 연결 엔티티로, 주문 타임라인(`orderedAt`, `confirmedAt`, `canceledAt`)을 직접 관리한다. soft delete 없이 상태 변경 시각만 기록한다. |

---

## 4. 엔티티별 분리 근거와 확장성

### 4.1 `UserEntity` / `UserPointEntity`

**분리 이유**

포인트는 충전과 차감이 빈번하다. 포인트 잔액을 `users` 테이블에 두면 예약 요청마다 사용자 행 전체에 write가 발생하고, 사용자 프로필 변경과 포인트 변경이 같은 row lock을 공유한다.

`user_points`를 별도 테이블로 두면 포인트 관련 update lock이 사용자 프로필 조회/수정과 분리된다.

**`UserPointEntity`의 PK 선택**

```kotlin
@Id @Column(name = "user_id")
val userId: Long
```

포인트 레코드는 사용자당 반드시 하나다. `user_id`를 PK로 두면 별도 unique 제약 없이 1:1 관계를 DB 레벨에서 강제하고, 포인트 조회 시 PK로 직접 접근한다.

**확장성**

포인트 이력이 필요한 경우 `user_point_histories` 테이블을 추가하면 된다. 현재 `user_points`는 잔액만 유지하므로 이력 테이블 추가가 기존 테이블 변경 없이 가능하다.

---

### 4.2 `ProductEntity` / `ProductOptionEntity` / `BookingScheduleEntity` / `ProductStockEntity`

**Product와 ProductOption 분리**

하나의 상품(`products`)이 여러 옵션(`product_options`)을 가진다. 예를 들어 "제주 오션뷰 숙박권"이라는 상품 아래 "오션뷰 더블룸", "마운틴뷰 트윈룸" 같은 옵션이 올 수 있다.

옵션 단위로 가격과 판매 오픈 시각(`sale_open_at`)이 다르다. 재고도 옵션 단위로 관리한다. 이 구조는 상품 정보 변경(이름, 타입)과 옵션 정보 변경(가격, 오픈 시각)을 서로 다른 row로 분리한다.

**ProductType enum**

```kotlin
enum class ProductType { BOOKING, ... }
```

`products.type`은 상품 종류를 구분한다. 현재는 `BOOKING`만 있지만, 새로운 상품 유형(예: `TICKET`, `PACKAGE`)을 추가할 때 `products` 테이블 구조를 바꾸지 않고 enum 값만 추가할 수 있다.

**BookingScheduleEntity 분리 이유**

체크인/체크아웃 일정은 숙박 예약 상품(`BOOKING` 타입)에만 있다. 이 데이터를 `product_options`에 두면 다른 타입 상품의 행에 NULL 컬럼이 생긴다.

`booking_schedules`를 별도 테이블로 분리하면:
- `BOOKING` 타입에만 관련 데이터가 존재한다.
- 다른 상품 유형(항공권의 출발/도착 시각 등)은 새 테이블을 추가하는 방식으로 확장한다.
- `product_options` 테이블에 타입별 NULL 컬럼이 누적되지 않는다.

`product_option_id`에 unique 제약을 두어 옵션 하나에 일정 하나임을 DB 레벨에서 강제한다.

**ProductStockEntity 분리 이유**

재고는 예약 흐름에서 가장 빈번하게 update되는 데이터다. `product_options` 행에 `remaining_quantity`를 두면 재고 차감 시 옵션 정보 행 전체에 write lock이 걸린다.

`product_stock`을 별도 테이블로 두면:
- 재고 조건부 update(`WHERE remaining_quantity > 0`)가 상품 옵션 정보 행과 lock을 공유하지 않는다.
- `CHECK (remaining_quantity >= 0)` 제약을 단독으로 관리한다.
- Redis counter와의 sync 대상이 명확하다.

`product_option_id`를 PK로 두어 조회 시 PK 직접 접근이 가능하고, 옵션당 재고 레코드가 하나임을 보장한다.

**도메인의 `BookingProductOption`**

`BookingProductOption`은 DB 엔티티가 아니라 도메인 조합 객체다. `Product`, `ProductOption`, `BookingSchedule`을 묶어 체크아웃 응답과 예약 검증에서 사용한다. 쿼리 결과를 조합하는 책임은 `CoreRepository`가 담당하고, 도메인 계층은 이 조합 객체만 사용한다.

---

### 4.3 `OrderEntity` / `OrderProductEntity`

**분리 이유**

`orders`는 주문 단위 집계 정보(총 금액, 상태, 멱등성 키)를 담는다. `order_products`는 주문 안의 품목 단위 이력을 담는다.

현재 한 주문에 하나의 상품 옵션만 들어가지만, 테이블 구조는 한 주문에 여러 품목이 들어오는 확장을 허용한다. `order_products`를 별도 테이블로 두면:
- 품목 추가가 `orders` 행 변경 없이 가능하다.
- 품목별 타임스탬프(`ordered_at`, `confirmed_at`, `canceled_at`)를 독립적으로 기록한다.
- 특정 상품 옵션이 포함된 주문을 `idx_order_products_product_option_id`로 빠르게 조회한다.

**`OrderProductEntity`가 `BaseEntity`를 상속하지 않는 이유**

주문 품목의 생명주기는 `orderedAt`/`confirmedAt`/`canceledAt` 타임스탬프 3개로 관리한다. soft delete가 아니라 명시적인 상태 시각 기록이 필요하기 때문에 `BaseEntity`의 `deletedAt`/`softDelete()`가 맞지 않는다.

**`orders.order_key` 멱등성 설계**

```sql
order_key VARCHAR(64) NOT NULL,
CONSTRAINT uk_order_key UNIQUE (order_key)
```

`Idempotency-Key` 헤더 값을 `order_key`에 저장하고 unique 제약으로 중복 주문을 DB 레벨에서 막는다. Redis `SETNX`가 아닌 DB unique key를 멱등성 최종 방어선으로 선택한 이유는 Redis 장애 폴백 경로에서도 중복 방지가 동작해야 하기 때문이다.

---

### 4.4 `PaymentEntity`

**결제와 주문을 분리한 이유**

`orders`와 `payments`를 분리하면 복합 결제(예: `Y_POINT + CREDIT_CARD`)를 하나의 주문에 여러 payment 행으로 자연스럽게 표현할 수 있다. 결제 수단이 추가되어도 `payments` 테이블 구조는 바뀌지 않는다.

**`method` 컬럼 enum 설계**

```kotlin
enum class PaymentMethod { CREDIT_CARD, Y_PAY, Y_POINT }
```

결제 수단을 enum string으로 저장한다. 새 결제 수단 추가 시:
1. `PaymentMethod` enum 값 추가
2. `PaymentStrategy` 구현체 추가
3. 조합 규칙이 바뀌면 `PaymentValidator` 수정

`PaymentStrategyRegistry`가 기동 시 모든 enum 값에 strategy가 등록되었는지 검증하므로, enum만 추가하고 구현체를 빠뜨리는 실수를 기동 전에 잡는다.

**`pg_transaction_id` / `external_request_id` nullable 설계**

`Y_POINT`는 외부 PG를 거치지 않으므로 `pg_transaction_id`가 없다. nullable로 두어 내부 결제 수단과 외부 PG 결제를 같은 테이블에서 통일된 구조로 관리한다.

---

### 4.5 `OutboxEventEntity`

**위치: `storage/rdb/common/outbox`**

outbox는 특정 도메인(주문, 결제)이 아니라 보상 실패 기록을 위한 공통 인프라다. `common` 패키지에 두어 어떤 흐름에서도 보상 실패를 기록할 수 있게 했다.

**현재 사용 범위**

현재는 `COMPENSATION_FAILURE` 이벤트 타입만 사용한다. 결제 보상(cancel) 자체가 실패했을 때 그 사실을 잃지 않기 위한 기록 장치다. outbox worker와 자동 재처리는 구현하지 않았다.

**확장성**

`event_type` enum을 추가하면 다른 종류의 비동기 이벤트(예: `ORDER_CONFIRMED_NOTIFY`, `STOCK_REPLENISHED`)도 같은 테이블에 저장하고 worker로 처리할 수 있다. `status` 컬럼과 `idx_outbox_status_type` 인덱스는 이 확장을 미리 고려해 설계되어 있다.

---

## 5. scalar FK 선택

모든 엔티티는 연관 엔티티를 JPA `@ManyToOne`/`@OneToMany` 관계 어노테이션 없이 scalar FK(`Long`)로만 참조한다.

```kotlin
// 관계 어노테이션 없이 scalar FK
@Column(name = "order_id", nullable = false)
val orderId: Long
```

이 선택의 이유:
- 락 안에서의 DB 예약과 결제는 독립 `REQUIRES_NEW` 트랜잭션으로 나뉜다. JPA 관계 어노테이션이 있으면 EntityManager가 연관 엔티티를 영속성 컨텍스트에 로드하거나 cascade 동작을 예상치 못하게 수행할 수 있다.
- N+1 쿼리 발생 경로를 차단한다. 연관 조회가 필요한 경우 명시적 쿼리로 처리하고 도메인 조합 객체(`BookingProductOption`)로 조립한다.
- `REQUIRES_NEW` 트랜잭션 경계에서 부모 영속성 컨텍스트와 자식 컨텍스트 간 상태 불일치 위험이 없다.

---

## 6. 인덱스 설계 요약

| 테이블 | 인덱스 | 목적 |
|---|---|---|
| `orders` | `idx_user_created (user_id, created_at)` | 사용자별 주문 내역 조회 |
| `orders` | `uk_order_key` | 멱등성 중복 방지 |
| `order_products` | `idx_order_products_order_id` | 주문 → 품목 조회 |
| `order_products` | `idx_order_products_product_option_id` | 옵션 단위 주문 내역 조회 |
| `payments` | `idx_order_id` | 주문 → 결제 내역 조회 |
| `product_options` | `idx_product_options_product_id` | 상품 → 옵션 목록 조회 |
| `outbox_events` | `idx_outbox_status_type (status, event_type)` | worker가 처리할 이벤트 필터링 |
| `outbox_events` | `idx_outbox_order_id` | 주문별 outbox 조회 |

---

## 7. 테이블별 컬럼 상세

<details>
<summary><strong>7.1 users</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | `BIGINT PK AUTO_INCREMENT` | 사용자 식별자. `orders.user_id`, `user_points.user_id`의 참조 대상. |
| `name` | `VARCHAR(100) NOT NULL` | 사용자 이름. 현재 비즈니스 로직에서 직접 사용하지 않고 조회 응답에만 포함된다. |
| `created_at` | `DATETIME(3) NOT NULL` | 레코드 생성 시각. `BaseEntity`의 `@CreationTimestamp`가 자동으로 채운다. |
| `updated_at` | `DATETIME(3)` | 마지막 변경 시각. `@UpdateTimestamp`가 자동으로 채운다. 최초 생성 시에는 NULL이다. |
| `deleted_at` | `DATETIME(3)` | soft delete 시각. `BaseEntity.softDelete()`가 채운다. NULL이면 활성 사용자다. |

</details>

<details>
<summary><strong>7.2 user_points</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `user_id` | `BIGINT PK` | 사용자 FK이자 PK. `users.id` 참조. 사용자당 포인트 레코드가 반드시 하나임을 DB 레벨에서 강제한다. |
| `point_balance` | `BIGINT NOT NULL DEFAULT 0` | 현재 포인트 잔액. 도메인의 `UserPoint.deduct()`가 잔액 부족 시 `INSUFFICIENT_POINT` 예외를 던지고, 잔액이 충분할 때만 차감한다. 음수가 될 수 없도록 애플리케이션 레벨에서 검증한다. |
| `updated_at` | `DATETIME(3) NOT NULL` | 포인트 잔액이 마지막으로 변경된 시각. 충전 또는 차감 시 갱신된다. `BaseEntity`를 상속하지 않으므로 직접 관리한다. |

</details>

<details>
<summary><strong>7.3 products</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | `BIGINT PK AUTO_INCREMENT` | 상품 식별자. `product_options.product_id`, `order_products.product_id`의 참조 대상. |
| `name` | `VARCHAR(200) NOT NULL` | 상품명. Checkout 응답의 `productName` 필드에 노출된다. |
| `type` | `VARCHAR(30) NOT NULL` | 상품 종류. 현재 값은 `BOOKING`만 존재한다. 상품 유형에 따라 연관 테이블(`booking_schedules` 등)이 달라지는 판단 기준이 된다. V2 마이그레이션에서 추가되었다. |
| `created_at` | `DATETIME(3) NOT NULL` | 레코드 생성 시각. |
| `updated_at` | `DATETIME(3)` | 마지막 변경 시각. |
| `deleted_at` | `DATETIME(3)` | soft delete 시각. |

> V2 마이그레이션에서 V1의 `price`, `check_in_at`, `check_out_at`, `sale_open_at` 컬럼이 `product_options`와 `booking_schedules`로 이동했다.

</details>

<details>
<summary><strong>7.4 product_options</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | `BIGINT PK AUTO_INCREMENT` | 옵션 식별자. `product_stock.product_option_id`, `booking_schedules.product_option_id`, `order_products.product_option_id`의 참조 대상. Redis 재고 카운터 키(`stock:{id}`)와 분산락 키(`lock:booking:{id}`)의 기준이다. |
| `product_id` | `BIGINT NOT NULL` | 소속 상품 FK. scalar FK로만 참조하고 JPA 관계 어노테이션은 사용하지 않는다. `idx_product_options_product_id` 인덱스로 상품별 옵션 목록을 조회한다. |
| `name` | `VARCHAR(200) NOT NULL` | 옵션명. Checkout 응답의 `optionName` 필드에 노출된다. |
| `price` | `DECIMAL(19, 2) NOT NULL` | 옵션 가격. V3 마이그레이션에서 `BIGINT`에서 `DECIMAL(19,2)`로 변경되었다. 도메인에서는 `Money` value class로 감싸서 사용한다. |
| `sale_open_at` | `DATETIME(3) NOT NULL` | 판매 오픈 시각. 도메인의 `ProductOption.validateSaleOpen()`이 현재 시각과 비교해 오픈 전이면 `SALE_NOT_OPEN` 예외를 던진다. |
| `created_at` | `DATETIME(3) NOT NULL` | 레코드 생성 시각. |
| `updated_at` | `DATETIME(3)` | 마지막 변경 시각. |
| `deleted_at` | `DATETIME(3)` | soft delete 시각. |

</details>

<details>
<summary><strong>7.5 booking_schedules</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | `BIGINT PK AUTO_INCREMENT` | 일정 식별자. |
| `product_option_id` | `BIGINT NOT NULL UNIQUE` | 소속 옵션 FK. unique 제약(`uk_booking_schedules_product_option_id`)으로 옵션 하나에 일정 하나임을 보장한다. |
| `check_in_at` | `DATETIME(3) NOT NULL` | 체크인 시각. Checkout 응답과 예약 확정 시 사용자에게 노출된다. |
| `check_out_at` | `DATETIME(3) NOT NULL` | 체크아웃 시각. 체크인 시각과 함께 숙박 기간을 정의한다. |
| `created_at` | `DATETIME(3) NOT NULL` | 레코드 생성 시각. |
| `updated_at` | `DATETIME(3)` | 마지막 변경 시각. |
| `deleted_at` | `DATETIME(3)` | soft delete 시각. |

> `BOOKING` 타입 상품 옵션에만 이 테이블 레코드가 존재한다. 다른 상품 유형(예: 항공권)은 별도 스케줄 테이블을 추가하는 방식으로 확장한다.

</details>

<details>
<summary><strong>7.6 product_stock</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `product_option_id` | `BIGINT PK` | 옵션 FK이자 PK. 재고 조회 시 PK 직접 접근이 가능하고, 옵션당 재고 레코드가 하나임을 보장한다. |
| `total_quantity` | `INT NOT NULL` | 총 재고 수량. 상품 등록 시 설정하고 이후 변경하지 않는다. Redis 재고 카운터 초기화(`StockCounterInitializer`)는 `remaining_quantity`를 기준으로 한다. 실제 판매 가능 수량 확인이나 재고 복구 검증 시 참조한다. |
| `remaining_quantity` | `INT NOT NULL` | 현재 잔여 재고. 예약 시 조건부 `UPDATE remaining_quantity = remaining_quantity - 1 WHERE remaining_quantity > 0`으로 차감한다. `CHECK (remaining_quantity >= 0)` 제약이 음수를 DB 레벨에서 방지한다. Redis counter는 이 값을 기준으로 초기화된다. |
| `updated_at` | `DATETIME(3) NOT NULL` | 재고 마지막 변경 시각. 재고 차감 또는 복구 시 갱신된다. `BaseEntity`를 상속하지 않으므로 직접 관리한다. |

> V4 마이그레이션에서 낙관적 락용 `version` 컬럼이 제거되었다. Redisson 공정락 + 조건부 update 조합으로 대체했기 때문이다.

</details>

<details>
<summary><strong>7.7 orders</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | `BIGINT PK AUTO_INCREMENT` | 주문 식별자. `order_products.order_id`, `payments.order_id`, `outbox_events.order_id`의 참조 대상. Booking 응답의 `orderId` 필드에 노출된다. |
| `user_id` | `BIGINT NOT NULL` | 주문한 사용자 FK. `idx_user_created(user_id, created_at)` 복합 인덱스로 사용자별 주문 내역을 시간 순으로 빠르게 조회한다. |
| `total_amount` | `DECIMAL(19, 2) NOT NULL` | 주문 총 금액. 결제 요청의 합산 금액과 이 값이 일치해야 `PaymentValidator`를 통과한다. V3 마이그레이션에서 `BIGINT`에서 `DECIMAL(19,2)`로 변경되었다. |
| `status` | `VARCHAR(20) NOT NULL` | 주문 상태. 값: `PENDING`, `PAID`, `CONFIRMED`, `FAILED`, `CANCELLED`. 도메인의 `Order.transitionTo()`가 허용된 상태 전이만 통과시키고, `OrderRepository.updateStatusIfCurrent()`가 DB 레벨에서 현재 상태 조건으로 update해 동시 상태 변경을 방지한다. |
| `order_key` | `VARCHAR(64) NOT NULL UNIQUE` | 멱등성 키. `Idempotency-Key` 요청 헤더 값(UUID)을 그대로 저장한다. `uk_order_key` unique 제약으로 같은 키의 중복 주문을 DB 레벨에서 방지한다. Redis 장애 상황에서도 중복 방지가 동작하도록 DB를 최종 방어선으로 선택했다. |
| `created_at` | `DATETIME(3) NOT NULL` | 주문 생성 시각. `idx_user_created` 인덱스에 포함된다. |
| `updated_at` | `DATETIME(3)` | 마지막 상태 변경 시각. |
| `deleted_at` | `DATETIME(3)` | soft delete 시각. |

</details>

<details>
<summary><strong>7.8 order_products</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | `BIGINT PK AUTO_INCREMENT` | 주문 품목 식별자. |
| `order_id` | `BIGINT NOT NULL` | 소속 주문 FK. `idx_order_products_order_id`로 주문별 품목 목록을 조회한다. |
| `product_id` | `BIGINT NOT NULL` | 상품 FK. `idx_order_products_product_id`로 상품별 주문 내역을 조회한다. |
| `product_option_id` | `BIGINT NOT NULL` | 옵션 FK. `idx_order_products_product_option_id`로 특정 옵션이 포함된 주문 조회 또는 재고 복구 시 사용한다. |
| `ordered_at` | `DATETIME(3) NOT NULL` | 주문 접수 시각. `BookingReservationProcessor`가 PENDING 주문을 생성할 때 채운다. |
| `confirmed_at` | `DATETIME(3)` | 예약 확정 시각. 결제 완료 후 `BookingReservationProcessor.confirm()`이 채운다. NULL이면 미확정이다. |
| `canceled_at` | `DATETIME(3)` | 취소 시각. 결제 실패 보상 시 `BookingReservationProcessor.failAndRelease()`가 채운다. NULL이면 취소되지 않은 품목이다. |

> `BaseEntity`를 상속하지 않는다. 이 엔티티의 생명주기는 `orderedAt`/`confirmedAt`/`canceledAt` 세 타임스탬프로 표현하며, soft delete 대신 취소 시각을 직접 기록한다.

</details>

<details>
<summary><strong>7.9 payments</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | `BIGINT PK AUTO_INCREMENT` | 결제 식별자. 복합 결제 시 한 주문에 여러 레코드가 생긴다. |
| `order_id` | `BIGINT NOT NULL` | 소속 주문 FK. `idx_order_id`로 주문별 결제 내역을 조회한다. |
| `method` | `VARCHAR(20) NOT NULL` | 결제 수단. 값: `CREDIT_CARD`, `Y_PAY`, `Y_POINT`. `PaymentStrategyRegistry`가 기동 시 모든 enum 값에 strategy가 등록됐는지 검증한다. 새 결제 수단 추가 시 enum 값과 strategy 구현체만 추가하면 이 컬럼 정의는 바뀌지 않는다. |
| `amount` | `DECIMAL(19, 2) NOT NULL` | 이 결제 수단으로 결제한 금액. 복합 결제에서 각 수단별 부담 금액이다. V3 마이그레이션에서 `BIGINT`에서 `DECIMAL(19,2)`로 변경되었다. |
| `status` | `VARCHAR(20) NOT NULL` | 결제 상태. 값: `PENDING`, `APPROVED`, `FAILED`, `CANCELLED`. `APPROVED`는 PG 또는 포인트 차감 성공, `CANCELLED`는 보상 취소 완료를 나타낸다. |
| `pg_transaction_id` | `VARCHAR(100)` | PG가 반환한 트랜잭션 식별자. `Y_POINT` 내부 결제는 PG를 거치지 않으므로 NULL이다. 결제 취소나 이중 청구 확인 시 PG 측 조회 키로 사용한다. |
| `external_request_id` | `VARCHAR(100)` | PG 요청 시 전달한 외부 요청 식별자. PG 멱등성 검증과 중복 승인 방지를 위한 키다. 내부 결제 수단(`Y_POINT`)은 NULL이다. |
| `created_at` | `DATETIME(3) NOT NULL` | 결제 레코드 생성 시각. |
| `updated_at` | `DATETIME(3)` | 마지막 상태 변경 시각. |
| `deleted_at` | `DATETIME(3)` | soft delete 시각. |

</details>

<details>
<summary><strong>7.10 outbox_events</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | `BIGINT PK AUTO_INCREMENT` | 이벤트 식별자. |
| `order_id` | `BIGINT NOT NULL` | 이벤트가 발생한 주문 FK. `idx_outbox_order_id`로 주문별 이벤트를 조회한다. |
| `event_type` | `VARCHAR(30) NOT NULL` | 이벤트 종류. 현재 값: `COMPENSATION_FAILURE` (결제 보상 취소 실패). 추후 `ORDER_CONFIRMED_NOTIFY` 등을 추가해 비동기 후처리로 확장할 수 있다. `idx_outbox_status_type(status, event_type)`으로 worker가 처리할 이벤트를 효율적으로 필터링한다. |
| `payload` | `TEXT NOT NULL` | 이벤트 본문. JSON 직렬화된 `OutboxEventPayload` 구현체다. 현재는 `CompensationFailurePayload`(실패한 결제 수단 목록, 오류 메시지)가 저장된다. |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'PENDING'` | 이벤트 처리 상태. 값: `PENDING`, `PROCESSED`, `FAILED`. 현재는 worker가 없으므로 모든 레코드가 `PENDING`으로 남는다. 저장 자체를 `REQUIRES_NEW` 트랜잭션으로 수행해 주문/재고 복구 실패 여부와 무관하게 이벤트 기록이 유지된다. |
| `created_at` | `DATETIME(3) NOT NULL` | 이벤트 생성 시각. |
| `processed_at` | `DATETIME(3)` | 이벤트 처리 완료 시각. worker 미구현 상태에서는 NULL이다. |
| `retry_count` | `INT NOT NULL DEFAULT 0` | 재처리 시도 횟수. worker가 도입되면 재시도 간격과 최대 횟수 정책에 사용할 컬럼이다. |

</details>

---

## 8. 설계 결정 요약

| 결정 | 이유 |
|---|---|
| `UserPoint`를 별도 테이블로 분리 | 포인트 write와 사용자 프로필 write의 row lock 분리 |
| `BookingSchedule`을 별도 테이블로 분리 | 상품 유형별 추가 속성을 NULL 컬럼 없이 확장 |
| `ProductStock`을 별도 테이블로 분리 | 재고 조건부 update lock을 상품 옵션 정보와 분리 |
| `OrderProduct`를 별도 테이블로 분리 | 품목별 타임스탬프 기록, 다품목 주문 확장성 |
| scalar FK만 사용 | 영속성 컨텍스트 부작용 없이 `REQUIRES_NEW` 트랜잭션 경계 관리 |
| `product_stock.product_option_id`를 PK로 | 재고 조회 PK 직접 접근, 1:1 보장 |
| `user_points.user_id`를 PK로 | 포인트 조회 PK 직접 접근, 사용자당 1레코드 보장 |
| `orders.order_key` unique 제약 | Redis 없이도 중복 주문을 DB 레벨에서 방지 |
| `BaseEntity` 상속 선택적 적용 | 생명주기가 다른 엔티티에 불필요한 컬럼을 강제하지 않음 |
