# Agent Routing Rules

Codex does not auto-run repo agents from this file. The parent Codex agent uses these rules when the user explicitly asks for subagents, parallel agents, independent review, or delegated workflow.

## Default Path

- Use a single parent Codex thread for small edits, documentation, harness updates, and direct bug fixes.
- Use explorer-first when the target path or data flow is unclear.
- Use a reviewer pass for stock consistency, payment compensation, transaction boundaries, public API contracts, or cross-module changes.

## Built-In Agents

- `explorer`: read-only mapping of files, symbols, module dependencies, transaction boundaries, and tests. No edits.
- `worker`: implementation in an explicitly owned module or path. Use only when write scope is disjoint.
- `default`: broad reasoning or synthesis when no specialized role is needed.

## Custom Agent

- `fcfs_reservation_reviewer`: read-only reviewer for correctness, consistency, missing tests, and reliability risks in this repository.

## Worker Ownership Examples

Use these scopes when parallel work is explicitly requested:

| Worker | Owns | Must not edit |
|--------|------|---------------|
| Domain worker | `apps/domain/**` | `apps/api/**`, `storage/**`, build files unless assigned |
| Application worker | `apps/application/**` | API DTOs/controllers, storage adapter implementations |
| API worker | `apps/api/**` | application services, storage adapters |
| RDB worker | `storage/rdb/**` | domain model changes unless assigned |
| Redis worker | `storage/redis/**` | booking orchestration unless assigned |
| Test worker | selected `src/test/**` files | production files unless assigned |

Every worker prompt must include:

> You are not alone in the codebase. Do not revert edits made by others. Keep your writes within the assigned files/modules and adapt to compatible changes made by other agents.

## Review Output

Reviewers should return only actionable findings:
- severity
- file and line when possible
- concrete reasoning or reproduction path
- suggested fix or missing test

Drop style-only comments unless they hide a correctness or maintainability risk.
