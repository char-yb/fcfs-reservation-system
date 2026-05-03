# Long-Term Improvement Roadmap

이 문서는 현재 구현이 요구사항을 만족한 뒤, 실제 운영 수준으로 올리기 위해 남아 있는 부족한 점과 장기 개선 방향을 정리한다.

현재 시스템은 다음 목표에 집중되어 있다.

- 한정 재고에서 over-selling을 막는다.
- Redis 장애 시에도 DB 정합성을 최종 방어선으로 유지한다.
- 복합 결제 실패 시 성공 결제를 역순 보상한다.
- 500/1000 TPS booking spike 후 DB/Redis 정합성을 검증한다.

다만 운영 시스템으로 확장하려면 "요청 중 장애가 난 뒤에도 스스로 복구되는가", "실패를 관측하고 재처리할 수 있는가", "외부 PG와 사용자 포인트 변경을 감사 가능하게 남기는가"가 더 중요해진다.

---

## 1. 개선 원칙

| 원칙 | 의미 |
|---|---|
| DB를 최종 기준으로 둔다 | Redis counter, lock, cache는 빠른 방어선이고 최종 재고/주문 상태는 DB로 판단한다. |
| 복구 가능성을 처리량보다 먼저 본다 | 장애 시 처리량이 낮아져도 주문/결제/재고 정합성이 깨지지 않아야 한다. |
| 외부 side effect는 추적 가능해야 한다 | PG 승인, PG 취소, 포인트 차감/환불은 장애 후 대조할 수 있는 기록이 필요하다. |
| 자동화 전 수동 runbook을 먼저 만든다 | 복구 로직이 잘못 동작하면 피해가 커진다. 먼저 조회/대조/수동 복구 기준을 명확히 한다. |
| Kafka/MQ는 병목이나 유실 문제가 증명된 뒤 도입한다 | 현재 동기 흐름에서는 MQ가 정합성을 자동으로 보장하지 않는다. 도입 시 idempotent consumer, DLQ, 재처리 정책이 함께 필요하다. |

---

## 2. 우선순위 로드맵

### Phase 1. 장애 복구 루프 닫기

가장 먼저 보강해야 할 영역은 결제 도중 서버가 종료되거나 보상 취소가 실패했을 때의 복구 루프다.

| 항목 | 부족한 점 | 첫 산출물 | 검증 기준 |
|---|---|---|---|
| 오래된 `PENDING` 주문 복구 | 결제 중 서버 종료 시 주문과 재고가 중간 상태로 남을 수 있다. PG 승인 여부와 포인트 차감 여부를 대조하기 전에는 안전하게 실패 처리할 수 없다. | 수동 복구 runbook, PG 결과 대조 정책, 관리자 API 또는 Spring Batch Job | 강제 중단 시나리오 후 주문/결제/재고를 일관 상태로 복구 |
| PG 결과 조회 기반 확정/실패 처리 | 현재 mock PG는 즉시 성공/실패만 반환한다. 실제 PG는 timeout과 결과 불명이 발생한다. | PG transaction id 저장, 결과 조회 gateway, timeout 후 상태 전이 정책 | PG timeout 후 재조회로 `CONFIRMED` 또는 `FAILED`가 결정됨 |
| outbox 재처리 worker | 보상 실패 기록은 남지만 자동 재시도는 없다. | `COMPENSATION_FAILURE` worker, 재시도 간격, 재시도 제한, 계속 실패하는 이벤트 상태 | cancel 실패 이벤트가 재시도되고 최종 성공/수동대상으로 분류됨 |
| Y 포인트 ledger | 현재 잔액 조건부 차감은 동시성은 보강됐지만 감사 이력이 없다. | `user_point_transactions` 또는 equivalent ledger | 차감/환불 이력이 잔액과 대조 가능 |

### Phase 2. 멱등성과 API 안정성

현재 같은 `Idempotency-Key`는 중복 요청으로 거부한다. 운영에서는 네트워크 재시도 시 같은 결과를 replay하는 편이 클라이언트 경험과 장애 복구에 유리하다.

| 항목 | 부족한 점 | 첫 산출물 | 검증 기준 |
|---|---|---|---|
| Idempotency-Key 결과 재응답 | 성공한 요청을 재시도하면 기존 결과를 돌려주지 않는다. | order key별 응답 재구성 정책 또는 idempotency table | 같은 key 재시도 시 중복 결제 없이 동일 결과 반환 |
| 요청 검증 세분화 | 주문 금액과 결제 합계 검증은 있으나 API 오류 응답 문서화가 더 필요하다. | error code 목록과 API examples 보강 | 잘못된 결제 조합/금액/필수 token 누락 응답이 문서와 일치 |
| 인증/인가 | 현재 userId path variable을 신뢰한다. | 인증된 user context와 path userId 일치 검증 | 타 사용자 포인트 사용이 차단됨 |
| rate limit | 매진 이후 대량 요청을 모두 받는다. | 사용자/IP/API 단위 제한 정책 | 비정상 반복 요청이 예약 처리 경로를 잠식하지 않음 |

### Phase 3. 관측성과 운영 runbook

부하테스트는 통과했지만 운영에서는 "문제가 발생했는지"를 자동으로 알아야 한다.

| 항목 | 부족한 점 | 첫 산출물 | 검증 기준 |
|---|---|---|---|
| 핵심 metric | 현재는 k6와 정합성 검증 스크립트 중심이다. | Prometheus 지표 또는 Actuator metric: pending count, outbox failure count, Redis circuit state, lock failure count | 대시보드에서 재고/주문/결제 이상 징후 확인 |
| alert 기준 | 어떤 수치가 장애인지 정의가 부족하다. | alert rule: 오래된 PENDING, outbox retry 초과, Redis fallback 지속, payment compensation failure | 장애 주입 시 alert 발생 |
| 구조화 로그 | 장애 분석에 필요한 correlation key가 부족할 수 있다. | orderKey, orderId, productOptionId, payment method, transactionId 포함 로그 | 단일 주문의 예약/결제/보상 흐름 추적 |
| 운영 runbook | 오래된 `PENDING`, 보상 실패, Redis 재고 불일치의 판단 기준이 아직 부족하다. | 장애 유형별 query, 판단 기준, 관리자 API | 운영자가 동일 절차로 복구 가능 |

### Phase 4. 실제 외부 연동과 보안

mock PG에서 실제 PG로 넘어가면 timeout, webhook, 중복 승인, 취소 실패가 주요 위험이 된다.

| 항목 | 부족한 점 | 첫 산출물 | 검증 기준 |
|---|---|---|---|
| 실제 PG sandbox 연동 | 현재는 mock PG gateway다. | PG idempotency key, 승인/취소/조회 gateway | 같은 orderKey 재시도 시 PG 중복 승인 없음 |
| webhook/callback 처리 | 외부 결제 결과가 비동기로 올 수 있다. | webhook signature 검증, 중복 이벤트 처리 | 같은 webhook이 여러 번 와도 상태가 한 번만 전이 |
| secret 관리 | 로컬 env 중심이다. | 환경별 secret 주입과 rotation 기준 | secret이 코드/이미지에 남지 않음 |
| 개인정보/결제정보 보호 | 결제 token logging 방지와 마스킹 기준이 필요하다. | 민감정보 로그 필터, request/response 마스킹 | 로그에서 token/개인정보 노출 없음 |

### Phase 5. 확장성과 비용 최적화

현재 구조는 재고 10개와 1000 TPS spike를 기준으로 검증했다. 더 큰 상품 수, 더 긴 피크, 실제 PG latency가 들어오면 병목 위치가 달라진다.

| 항목 | 부족한 점 | 첫 산출물 | 검증 기준 |
|---|---|---|---|
| 혼합 트래픽 검증 | booking 단일 spike 외 실제 checkout+booking 혼합 부하가 필요하다. | `midnight-mixed.js` 기준 결과표와 정합성 검증 결과 | checkout 조회가 booking spike 중에도 SLO 안에 유지 |
| DB connection/pool 튜닝 | 실제 PG latency와 DB latency에서 pool 고갈 가능성이 있다. | Hikari, MySQL slow query, connection wait metric | peak 중 connection timeout 없음 |
| Redis HA | 단일 Redis 장애 fallback은 있으나 failover 운영 검증은 없다. | Sentinel/Cluster 또는 managed Redis 검토 | failover 중 over-selling 없음, fallback 지속 시간 관측 |
| 상품 옵션 수 확대 | hot key 한 개 중심으로 검증했다. | 다중 productOptionId spike 시나리오 | option별 재고 정합성 검증 통과 |
| MQ 도입 검토 | 동기 흐름 한계가 관측되기 전에는 비용 대비 효과가 불명확하다. | MQ 도입 판단 기준: 외부 연동 지연, outbox backlog, 재처리량 | MQ 도입 전후 복잡도와 장애 복구 개선이 수치로 확인 |

---

## 3. 장기 TODO 우선순위

| 우선순위 | 작업 | 이유 |
|---|---|---|
| P0 | 오래된 `PENDING` 주문 수동 복구 기준 | 서버 중단 시 재고 누수와 결제 불명 상태를 안전하게 분류하기 위한 첫 단계 |
| P0 | 오래된 `PENDING` 주문 자동 복구 | 수동 복구 기준을 관리자 API 또는 Spring Batch Job으로 확장 |
| P0 | PG 승인/취소/조회 결과 영속화 | 외부 side effect를 장애 후 대조하기 위한 최소 기록 |
| P1 | outbox compensation retry worker | 현재 기록만 남는 보상 실패를 자동 복구 대상으로 전환 |
| P1 | Y 포인트 ledger | 잔액 정합성뿐 아니라 감사와 고객 문의 대응에 필요 |
| P1 | Idempotency-Key 결과 재응답 | 클라이언트 재시도 안정성과 중복 결제 방지 강화 |
| P2 | 운영 metric과 alert | 장애를 수동 확인이 아니라 자동 감지로 전환 |
| P2 | 실제 PG sandbox + webhook 중복 처리 | mock에서 실제 결제 환경으로 가기 위한 필수 단계 |
| P3 | 인증/인가, rate limit, 민감정보 마스킹 | 운영 보안 기본선 |
| P3 | 혼합 트래픽과 다중 상품 부하테스트 | 단일 hot option 검증을 실제 사용 패턴으로 확장 |
| P4 | Redis HA/Cluster, MQ 도입 검토 | 현재 병목과 운영 비용을 확인한 뒤 결정할 인프라 확장 |

---

## 4. 당장 하지 않는 것이 나은 것

| 항목 | 이유 |
|---|---|
| Kafka/MQ 우선 도입 | 현재 위험은 비동기 처리 부재 자체보다 복구 기준과 중복 처리 방지 부족이다. MQ는 consumer 중복 처리 방지와 DLQ 없이는 오히려 복잡도를 높인다. |
| 결제 전체를 DB 트랜잭션 안에 넣기 | 외부 PG latency를 DB transaction과 lock에 포함하면 contention이 커진다. 현재처럼 예약 DB 변경, 외부 결제, 확정을 분리하는 편이 낫다. |
| Redis만으로 정합성 보장 | Redis는 빠른 방어선이다. 최종 판매 수량은 DB 조건부 update와 제약으로 제한해야 한다. |
| 모든 repository 단위 테스트 확대 | 핵심 위험은 orchestration과 장애 복구다. repository 테스트는 조건부 update, lock/counter, 복구 query처럼 위험도가 높은 부분부터 추가한다. |

---

## 5. 다음 마일스톤 제안

가장 현실적인 다음 마일스톤은 "서버가 결제 도중 죽어도 운영자가 복구할 수 있다"를 증명하는 것이다.

1. 결제 실행 전/후 payment attempt record를 남긴다.
2. 오래된 `PENDING` 주문 조회 기준과 수동 복구 runbook을 먼저 만든다.
3. PG 결과 조회 mock을 추가한다.
4. 미완료 주문 복구 관리자 API 또는 Spring Batch Job을 만든다.
5. 강제 종료 시나리오를 테스트로 재현한다.
6. 복구 후 `confirmed_orders + db_remaining == total_quantity`, `pending_orders == 0`, payment status가 주문 상태와 일치하는지 검증한다.

이 마일스톤이 닫히면 현재 문서의 가장 큰 남은 위험인 `PENDING` 주문/재고 누수 문제가 운영 가능한 수준으로 낮아진다.

### 오래된 PENDING 주문 복구 구현 방식

2대 이상의 애플리케이션 서버를 전제로 하면 `@Scheduled`는 기본 선택지에서 제외한다. 각 서버에서 같은 스케줄이 동시에 실행될 수 있고, 별도 분산 스케줄 락을 추가하면 복구 로직보다 실행 제어가 더 복잡해진다.

대신 복구는 다음 두 방식 중 하나로 실행한다.

| 실행 방식 | 장점 | 주의점 | 판단 |
|---|---|---|---|
| 관리자 API 또는 CLI | 추가 라이브러리 없이 시작 가능. 운영자가 대상 범위와 dry-run 여부를 제어하기 쉽다. | 대량 처리, 재시작, 처리 이력 관리가 약하다. | 1차 구현에 적합하다. |
| Spring Batch Job | Job/Step 실행 이력, 재시작, chunk 처리, 실패 항목 추적이 좋다. | Spring Batch JobRepository 메타 테이블과 실행 파라미터 설계가 필요하다. | 주문 수가 많아지거나 반복 복구가 필요하면 적합하다. |

두 방식 모두 같은 `PendingOrderRecoveryService`를 호출해야 한다. 실행기가 API인지 Batch인지와 무관하게 정합성은 DB claim과 상태 전이가 지켜야 한다.

#### 필요한 저장 구조

자동 판단을 하려면 현재 테이블만으로는 부족하다. 최소한 다음 기록이 필요하다.

| 추가/보강 항목 | 이유 |
|---|---|
| `payments` 선저장 | 결제 실행 전에 `PENDING` payment attempt를 만들어야 서버 종료 후 어떤 결제를 시도했는지 알 수 있다. |
| `payments.external_request_id` | PG에 보낸 멱등키이자 결과 조회 키다. 예: `{orderKey}:{method}` |
| PG 결과 조회 gateway | `external_request_id`로 `APPROVED`, `DECLINED`, `NOT_FOUND`, `UNKNOWN`을 조회한다. |
| `user_point_transactions` | Y 포인트 차감/환불 이력을 남겨 서버 종료 후 포인트 차감 여부를 대조한다. |
| 주문 복구 claim 상태 | 여러 서버나 여러 배치 실행이 같은 주문을 동시에 복구하지 못하게 한다. |

주문 복구 claim은 `orders.status`에 `RECOVERING`을 추가하는 방식이 가장 단순하다.

```text
PENDING -> RECOVERING -> CONFIRMED
PENDING -> RECOVERING -> FAILED
RECOVERING -> MANUAL_REVIEW
```

`MANUAL_REVIEW`는 PG 결과가 `UNKNOWN`이거나 포인트 이력과 payment 상태가 맞지 않아 자동 판단하면 위험한 주문을 분리하기 위한 상태다. 상태를 늘리고 싶지 않다면 `recovery_status`, `recovery_reason`, `recovery_claimed_at` 컬럼을 별도로 둬도 된다.

#### 복구 처리 순서

복구 서비스는 다음 순서로 동작한다.

1. 오래된 `PENDING` 주문 후보를 찾는다.
   - 예: `orders.status = 'PENDING'`
   - 예: `orders.created_at < NOW() - INTERVAL 5 MINUTE`
2. 후보 주문을 DB 조건부 update로 claim한다.
   - `UPDATE orders SET status = 'RECOVERING' WHERE id = ? AND status = 'PENDING'`
   - update count가 1인 실행기만 해당 주문을 처리한다.
3. 결제 수단별 상태를 대조한다.
   - `CREDIT_CARD`, `Y_PAY`: `external_request_id`로 PG 결과 조회
   - `Y_POINT`: `user_point_transactions`에서 차감 이력 조회
   - `payments`의 attempt/approved/cancelled 상태와 외부 결과 비교
4. 주문을 분류한다.

| 분류 | 조건 | 처리 |
|---|---|---|
| 결제 전 중단 | PG 승인 없음, 포인트 차감 없음 | 주문 `FAILED`, `order_products.canceled_at`, DB 재고 복구 |
| 전체 결제 승인 | 모든 결제 수단 승인 또는 차감 확인 | payment `APPROVED`, 주문 `CONFIRMED`, `order_products.confirmed_at` |
| 일부 결제 승인 | 일부 PG 승인 또는 포인트 차감만 확인 | 승인된 결제 역순 보상 후 주문 `FAILED`, DB 재고 복구 |
| 결과 불명 | PG `UNKNOWN`, 포인트 이력 불일치 | `MANUAL_REVIEW`, 재고 복구 금지 |

5. DB 상태 변경은 주문 단위 짧은 트랜잭션으로 처리한다.
6. 주문을 `FAILED`로 닫아 재고를 복구했다면 Redis `stock:{productOptionId}`는 DB `product_stock.remaining_quantity` 기준으로 다시 맞춘다.
7. 처리 결과와 판단 근거를 audit log 또는 복구 이력 테이블에 남긴다.

#### Spring Batch로 구현할 때

Spring Batch를 선택하면 다음 구조가 적합하다.

```text
PendingOrderRecoveryJob
  Step 1. 오래된 PENDING 주문 ID 조회
  Step 2. 주문별 DB claim
  Step 3. PG/Y_POINT 대조 후 CONFIRMED, FAILED, MANUAL_REVIEW 중 하나로 상태 전이
  Step 4. 처리 결과 집계와 실패 항목 기록
```

구현 단위는 다음처럼 나눈다.

| 컴포넌트 | 역할 |
|---|---|
| `PendingOrderRecoveryReader` | 오래된 `PENDING` 주문 ID를 page/chunk 단위로 읽는다. |
| `PendingOrderRecoveryProcessor` | 주문 ID를 claim하고 PG/포인트 상태를 조회해 복구 결정을 만든다. claim 실패 시 이미 다른 실행기가 가져간 주문이므로 skip한다. |
| `PendingOrderRecoveryWriter` | 복구 결정을 DB에 반영하고 Redis 재고를 DB 기준으로 맞춘다. |
| `PendingOrderRecoveryService` | API와 Batch가 함께 사용하는 실제 복구 유스케이스다. |

Spring Batch의 JobRepository는 같은 job execution 이력과 재시작을 관리하는 데 유용하지만, 2대 이상 서버에서 주문 중복 복구를 막는 최종 장치로 보면 안 된다. 같은 Job이 서로 다른 파라미터로 동시에 실행될 수 있고, 운영자가 API와 Batch를 동시에 실행할 수도 있다. 따라서 최종 중복 방어는 반드시 주문 단위 DB claim으로 해야 한다.

Batch 실행은 `@Scheduled`가 아니라 운영자가 명시적으로 시작하는 방식으로 둔다.

```text
POST /internal/recovery/pending-orders/jobs
```

또는 운영 runbook에서 CLI로 실행한다.

```text
java -jar app.jar --spring.batch.job.name=pendingOrderRecoveryJob cutoffMinutes=5 dryRun=false
```

이렇게 하면 2대 이상 서버 구조에서도 스케줄 중복 실행 문제를 피하고, 복구 판단과 처리 이력을 Spring Batch로 남길 수 있다.

### 수동 복구 runbook 초안

오래된 `PENDING` 주문을 바로 `FAILED`로 바꾸고 재고를 복구하는 방식은 위험하다. 서버가 포인트 차감이나 PG 승인 이후 payment 저장 전에 종료됐을 수 있기 때문이다. 따라서 수동 복구는 다음 순서를 따라야 한다.

1. 오래된 `PENDING` 주문을 조회한다.
   - 기준 예: `orders.status = 'PENDING'`
   - 기준 예: `orders.created_at < NOW() - N minutes`
   - `order_products`, `payments`, 사용자 포인트 이력, PG 거래 결과를 함께 확인한다.
2. 주문을 상태별로 분류한다.
   - PG 승인 또는 포인트 차감이 확인된 주문: 실패 처리하지 않고 PG 결과 조회 후 `CONFIRMED` 또는 보상 대상으로 분류한다.
   - PG 요청 전이고 포인트 차감도 없는 주문: 실패 처리와 재고 복구 후보로 본다.
   - PG 결과나 포인트 차감 여부가 불명확한 주문: 수동 확인 대상으로 남긴다.
3. 실패 처리 후보만 하나의 DB 트랜잭션에서 처리한다.
   - `orders.status`를 `FAILED`로 변경한다.
   - `order_products.canceled_at`을 기록한다.
   - `product_stock.remaining_quantity`를 복구한다.
4. DB 반영 후 Redis `stock:{productOptionId}`는 DB `product_stock.remaining_quantity` 기준으로 다시 맞춘다.
5. 처리 결과를 감사 가능하게 남긴다.
   - 어떤 주문을 어떤 근거로 실패 처리했는지 기록한다.
   - 수동 처리자, 처리 시각, PG/포인트 확인 결과를 남긴다.
6. 복구 후 다음 값을 확인한다.
   - `pending_orders == 0` 또는 남은 `PENDING`이 모두 수동 확인 대상으로 분류됨
   - `confirmed_orders + db_remaining == total_quantity`
   - Redis 재고와 DB 재고가 일치
   - payment 상태와 order 상태가 충돌하지 않음

이 runbook을 실제 코드로 옮길 때는 먼저 PG 결과 조회와 포인트 ledger가 필요하다. 이 두 기록 없이 오래된 `PENDING` 주문을 자동 실패 처리하면 포인트 차감 누락 복구나 결제 성공 주문 취소 같은 문제가 생길 수 있다.
