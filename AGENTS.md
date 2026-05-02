# Repository Guidance

## Global Preferences

- For Kotlin code, prefer top-of-file imports over inline fully qualified class, enum, or object references in executable code. Keep fully qualified names only when the language or framework requires them, such as JPQL constructor expressions or string-based class names.

## Harness: FCFS Reservation Backend

**Goal:** Keep Codex work aligned with this Kotlin/Spring first-come-first-served reservation system, especially stock consistency, payment compensation, Redis/DB fallback, and module boundaries.

**Trigger:** For backend implementation, review, architecture, testing, validation, or harness updates in this repository, use `$fcfs-backend-workflow` or follow `.agents/skills/fcfs-backend-workflow/SKILL.md`.

**Project facts:**
- Gradle Kotlin DSL multi-module project: `apps:domain`, `apps:application`, `apps:api`, `storage:rdb`, `storage:redis`.
- Kotlin `2.3.20`, Spring Boot `4.0.6`, Java toolchain `25`, ktlint plugin `12.1.2`.
- Architecture docs live under `docs/` and currently describe layered stock defense, payment extensibility, and fault tolerance.

**Generated artifacts:**
- Workflow skill: `.agents/skills/fcfs-backend-workflow/`
- Codex rules: `.codex/rules/`
- Verification hook: `.codex/hooks/verify.sh`
- Optional reviewer agent: `.codex/agents/fcfs_reservation_reviewer.toml`
- Intermediate artifacts: `_workspace/`

**Validation:**
- Default wrapper: `./.codex/hooks/verify.sh`
- Full check: `CODEX_VERIFY_MODE=full ./.codex/hooks/verify.sh`
- Direct Gradle fallback: `./gradlew ktlintCheck compileKotlin compileTestKotlin :apps:api:test :storage:rdb:test`

**Change log:**
| Date | Change | Target | Reason |
|------|--------|--------|--------|
| 2026-04-30 | Initial Codex harness | all | Add repo-local workflow, rules, reviewer, and validation hook. |
