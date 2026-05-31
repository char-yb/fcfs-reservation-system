# Harness Governance

The harness should stay small, factual, and repo-specific.

## Update Rules

- Keep `AGENTS.md` short. Put detailed procedures in `.agents/skills/fcfs-backend-workflow/` or `.codex/rules/`.
- Keep active Markdown guidance around 100 lines; split detail into focused rules, skills, or eval notes.
- Add a rule only when it is backed by source, docs, build configuration, repeated user preference, or a real failure.
- Do not copy conventions from another repository unless this repository already shows the same pattern.
- Update the AGENTS change log when harness behavior changes.
- Keep `.codex/hooks/verify.sh` aligned with real Gradle tasks.
- Record weak or not-yet-repeated rule candidates in `.codex/governance/rule-candidates.md`.
- Record dedicated harness trigger tests and dry-runs in `_workspace/evals/`.

## Drift Checks

When updating harness files:
- Compare module facts with `settings.gradle.kts` and build files.
- Compare architecture rules with `docs/01-architecture.md`, `docs/02-concurrency-and-locking.md`, `docs/03-payment-extensibility.md`, and `docs/04-fault-tolerance.md`.
- Check that skill descriptions still trigger only for this repository's backend workflow.
- Check that custom agents are still justified. Remove stale agents instead of expanding them.

## Candidate Rules

If a possible rule is weakly supported, record it in `.codex/governance/rule-candidates.md` instead of promoting it directly. Promote it into the harness only after the codebase, repeated work, or one high-risk miss confirms it.

## Evaluation Records

Use `_workspace/evals/` for short, dated markdown files that capture:

- trigger and non-trigger examples
- which `AGENTS.md`, skill, or agent should be selected
- the smallest useful validation command
- any source-of-truth or ownership ambiguity found during the dry-run
