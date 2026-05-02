---
name: fcfs-backend-workflow
description: "Work on the fcfs-reservation Kotlin/Spring backend: implement, review, refactor, test, validate, or update harness rules for first-come-first-served booking, stock, payment, Redis, RDB, and API changes."
---

# FCFS Backend Workflow

Use this skill for repository-local backend work in `fcfs-reservation`.

## Inputs

- User request and any selected files.
- Repository guidance: `AGENTS.md`.
- Rules: `.codex/rules/architecture.md`, `validation.md`, `testing.md`, `agent-routing.md`, `governance.md`.
- Design docs under `docs/` when the change touches architecture, concurrency, payments, or fault tolerance.
- Optional prior artifacts in `_workspace/`.

## Repository Map

- `apps/domain`: domain models, enums, repository/gateway abstractions, shared domain support interfaces.
- `apps/application`: use cases, services, facades, payment processors, policies, transaction orchestration.
- `apps/api`: Spring Boot app, controllers, DTOs, validation, exception handling.
- `storage/rdb`: JPA entities, repositories, RDB adapters, Flyway and DB behavior.
- `storage/redis`: Redisson/Redis config, stock counter, distributed lock adapters.

## Default Workflow

1. Read `AGENTS.md`, the relevant `.codex/rules/*` files, and the target source/doc files.
2. Check `git status --short` before editing. Preserve unrelated user changes.
3. Classify the work:
   - API contract or controller behavior.
   - Application orchestration, transactions, payment policy, or compensation.
   - Domain model/interface change.
   - RDB adapter, entity, migration, or query change.
   - Redis counter, lock, or fallback change.
   - Harness/docs/test-only change.
4. Inspect both sides of any boundary being changed.
5. Make the smallest coherent change in the owning module.
6. Add or update tests according to `.codex/rules/testing.md` when behavior changes.
7. Run `.codex/hooks/verify.sh` or a narrower command from `.codex/rules/validation.md`.
8. Report changed files, validation result, and residual risk.

## Architecture Guardrails

- Keep dependency direction aligned with `settings.gradle.kts` and `.codex/rules/architecture.md`.
- Do not let web DTOs, JPA entities, Redis types, or Redisson types leak into domain/application contracts.
- Keep domain abstractions in `apps/domain` and concrete adapters in `storage:rdb` or `storage:redis`.
- Keep executable Kotlin references imported at the top of files.
- Update design docs when changing stock defense, lock semantics, payment compensation, transaction split, or fallback behavior.

## High-Risk Flow Checks

For booking, stock, lock, payment, or idempotency changes, explicitly check:

- Redis atomic counter still fast-fails sold-out requests before expensive work.
- Distributed lock scope and lease/wait behavior still protect the critical section.
- DB conditional update and constraints remain the final consistency defense.
- Idempotency remains backed by a durable DB uniqueness guarantee when Redis is unavailable.
- Payment methods remain behind processors/registry/policy.
- Partial payment success triggers retry-safe compensation.
- External PG calls are not held inside long DB transactions.

## Subagent Runbook

Only use subagents when the user explicitly asks for subagents, parallel agents, delegated work, or independent review.

### Explorer-first

Spawn one read-only `explorer` when the code path is unclear.

Prompt contract:
- Goal: map files, symbols, data flow, module boundaries, tests, and risks.
- Constraint: read-only, no fixes.
- Output: relevant file paths, evidence, proposed write boundaries, and validation command.

### Disjoint Worker Implementation

Use workers only when write scopes are clearly separated by module or path. The parent owns shared contracts and final integration.

Every worker prompt must include:

> You are not alone in the codebase. Do not revert edits made by others. Keep your writes within the assigned files/modules and adapt to compatible changes made by other agents.

Suggested ownership:
- Domain worker: `apps/domain/**`.
- Application worker: `apps/application/**`.
- API worker: `apps/api/**`.
- RDB worker: `storage/rdb/**`.
- Redis worker: `storage/redis/**`.
- Test worker: assigned `src/test/**` files only.

### Reviewer Validation

For high-risk changes, spawn `fcfs_reservation_reviewer` or a read-only reviewer only when explicitly requested. Ask it to check correctness, regressions, missing tests, API contracts, consistency, and reliability risks.

## Failure Handling

- If Gradle cannot resolve dependencies because network is restricted, report the blocked command and continue with static checks.
- If tests fail because of the current change, fix them before handoff.
- If tests fail due to unrelated dirty worktree changes, report the evidence and avoid reverting user changes.
- If docs and code disagree, call out the drift and update both only when the requested change requires it.

## Output

Final responses should include:
- What changed.
- Files created or edited.
- Validation commands and results.
- Assumptions or weak-signal conventions.
