# Architecture Rules

These rules are repository facts or strong conventions inferred from the Gradle modules, source layout, and architecture docs. If implementation and docs disagree, inspect both before changing code and update the drift explicitly.

## Module Boundaries

- `apps:domain` owns domain models, repository/gateway abstractions, domain enums, and shared domain support abstractions such as `DistributedLock`.
- `apps:domain` must not depend on Spring infrastructure, JPA, Redis, Redisson, web DTOs, or storage entities.
- `apps:application` owns use cases and orchestration: checkout, booking, order, payment, product, user point services, policies, and processor registries.
- `apps:application` may use Spring components and transactions, but should depend on domain interfaces rather than concrete storage adapters.
- `apps:api` owns HTTP controllers, request/response DTOs, validation, exception handling, application bootstrap, and runtime wiring through module dependencies.
- `storage:rdb` owns JPA entities, Spring Data repositories, RDB adapters, Flyway migrations when present, and DB-specific transaction/query behavior.
- `storage:redis` owns Redisson/Redis configuration and adapters for distributed lock and stock counter behavior.
- Cross-module dependency direction should stay: API -> application/domain/storage, application -> domain, storage -> domain.

## Package and Type Placement

- Keep executable Kotlin references imported at the top of the file. Inline fully qualified names are reserved for places that require strings or framework syntax.
- Keep web request/response models out of application/domain layers.
- Keep JPA entities out of domain/application signatures. Translate at the storage adapter boundary.
- New repository or gateway behavior starts as an interface in `apps:domain` when application logic needs it, with adapters in `storage:rdb` or `storage:redis`.
- Avoid adding storage dependencies to `apps:application`; wire adapters through Spring in `apps:api` or adapter modules.

## FCFS Stock and Locking

- Preserve the layered defense model from `docs/02-concurrency-and-locking.md`:
  - Redis atomic counter is the fast-fail stock gate.
  - Redisson distributed lock protects the payment and order creation critical section after stock is tentatively reserved.
  - DB conditional update plus constraints remain the final source-of-truth defense.
- Do not replace the conditional DB update with `SELECT ... FOR UPDATE` unless the business logic truly needs read-then-branch semantics under a row lock.
- Redisson unlock code should check current-thread ownership before unlocking.
- Keep lock scope product-based unless a new consistency problem proves another key is needed.
- Keep timeout and lease decisions explicit. The documented baseline is short wait time and finite lease time, not indefinite watchdog extension.

## Payment and Compensation

- Payment methods should stay behind the domain/application strategy and registry shape.
- New payment methods should add a processor and update combination policy without spreading method-specific branches into booking controllers.
- Do not hold DB transactions open across external PG calls.
- Compensation is best-effort and must be safe to retry. If durable retry is added, use the outbox concepts already present in `storage:rdb`.

## Documentation Drift

- If a change alters stock semantics, lock timing, payment compensation, module ownership, or Redis fallback behavior, update the matching file under `docs/` and this rule set when needed.
- If docs state a dependency or testing tool that is not present in Gradle, treat the docs as design intent and the build file as implementation truth.
