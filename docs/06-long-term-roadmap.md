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
| 자동화 전 수동 runbook을 먼저 만든다 | 자동 worker가 잘못 동작하면 피해가 커진다. 먼저 조회/대조/수동 복구 기준을 명확히 한다. |
| Kafka/MQ는 병목이나 유실 문제가 증명된 뒤 도입한다 | 현재 동기 흐름에서는 MQ가 정합성을 자동으로 보장하지 않는다. 도입 시 idempotent consumer, DLQ, 재처리 정책이 함께 필요하다. |

---

## 2. 우선순위 로드맵

### Phase 1. 장애 복구 루프 닫기

가장 먼저 보강해야 할 영역은 결제 도중 서버가 종료되거나 보상 취소가 실패했을 때의 복구 루프다.

| 항목 | 부족한 점 | 첫 산출물 | 검증 기준 |
|---|---|---|---|
| 오래된 `PENDING` 주문 복구 | 결제 중 서버 종료 시 주문과 재고가 중간 상태로 남을 수 있다. PG 승인 여부와 포인트 차감 여부를 대조하기 전에는 안전하게 실패 처리할 수 없다. | 수동 복구 runbook, PG 결과 대조 정책, 관리자 API 또는 자동 worker | 강제 중단 시나리오 후 주문/결제/재고를 일관 상태로 복구 |
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
| P0 | 오래된 `PENDING` 주문 자동 복구 | 수동 복구 기준을 worker/API로 확장 |
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
4. 미완료 주문 복구 관리자 API 또는 worker를 만든다.
5. 강제 종료 시나리오를 테스트로 재현한다.
6. 복구 후 `confirmed_orders + db_remaining == total_quantity`, `pending_orders == 0`, payment status가 주문 상태와 일치하는지 검증한다.

이 마일스톤이 닫히면 현재 문서의 가장 큰 남은 위험인 `PENDING` 주문/재고 누수 문제가 운영 가능한 수준으로 낮아진다.

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
