# Testing Rules

The current test surface is small: `apps:api` has a Spring Boot context smoke test and `apps:application` has test doubles under `application/support`. Add focused tests when changing behavior.

## Test Selection

- Policy or pure application behavior: prefer fast unit tests in `apps/application/src/test`.
- Domain invariants: add tests in `apps/domain/src/test` when domain logic becomes non-trivial.
- Controller request/response or exception mapping: use API-layer tests in `apps/api/src/test`.
- JPA query, entity mapping, DB constraints, or conditional stock update: use `storage:rdb` tests.
- Redis counter or distributed lock behavior: use `storage:redis` tests. Do not assume Testcontainers are available unless the build file includes them.

## High-Risk Scenarios

Cover these when touched:
- Over-selling must remain impossible under concurrent booking attempts.
- Idempotency keys must not allow duplicate orders or payments.
- Payment combination policy must reject duplicate methods and card plus Pay mixing.
- Partial payment success must trigger compensation in reverse order.
- Redis outage fallback must preserve correctness even if fairness or throughput degrade.
- Lock acquisition timeout and lease expiration must produce controlled failures.

## Test Quality

- Prefer deterministic tests over sleeps. If concurrency tests need coordination, use latches or barriers.
- Keep fake adapters in test packages unless they are reusable production abstractions.
- Tests should assert domain/application outcomes, not implementation detail names, unless the behavior is the implementation contract.
- When adding a new external dependency for tests, update Gradle files and validation notes together.
- If adding executable tests to `apps:application`, add the module's JUnit/Kotlin test runtime dependencies in the same change; current application test sources compile but the module does not declare a JUnit runtime.
