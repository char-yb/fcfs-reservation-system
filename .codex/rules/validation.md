# Validation Rules

Use the smallest command that gives meaningful coverage for the touched surface, then run the full wrapper before handoff when feasible.

## Default Commands

- Harness wrapper: `./.codex/hooks/verify.sh`
- Full repository check: `CODEX_VERIFY_MODE=full ./.codex/hooks/verify.sh`
- Direct Gradle fallback: `./gradlew ktlintCheck compileKotlin compileTestKotlin :apps:api:test :storage:rdb:test`
- Compile-only fallback: `./gradlew ktlintCheck compileKotlin compileTestKotlin`

## Module-Focused Commands

- Domain changes: `./gradlew ktlintCheck :apps:domain:compileKotlin :apps:domain:compileTestKotlin`
- Application changes: `./gradlew ktlintCheck :apps:application:compileKotlin :apps:application:compileTestKotlin`
- API changes: `./gradlew ktlintCheck :apps:api:test`
- RDB adapter changes: `./gradlew ktlintCheck :storage:rdb:test`
- Redis adapter changes: `./gradlew ktlintCheck :storage:redis:compileKotlin :storage:redis:compileTestKotlin`
- Build logic or shared dependency changes: `./gradlew ktlintCheck compileKotlin compileTestKotlin :apps:api:test :storage:rdb:test`

## Runtime Assumptions

- The Gradle build declares Java toolchain `25`. On macOS, prefer `JAVA_HOME=$(/usr/libexec/java_home -v 25)` when multiple JDKs are installed.
- Network access may be restricted. If dependency resolution fails because artifacts are missing from local caches, report that validation was blocked by dependency download/network access.
- `:apps:application:test` is not part of the default full check until that module declares JUnit runtime dependencies. Use compile-test validation there unless adding executable tests and their dependencies in the same change.
- Do not mark validation as successful when only static reading was performed.

## Reporting

Always report:
- The exact command run.
- Whether it passed or failed.
- Any failure that appears unrelated to the current change.
- Any high-risk surface that was not covered by tests.
