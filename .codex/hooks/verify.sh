#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"

if [[ ! -x ./gradlew ]]; then
  echo "Missing executable ./gradlew" >&2
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" && "$(uname -s)" == "Darwin" && -x /usr/libexec/java_home ]]; then
  if /usr/libexec/java_home -v 25 >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
  fi
fi

if [[ "$#" -gt 0 ]]; then
  exec ./gradlew "$@"
fi

run_full() {
  exec ./gradlew \
    ktlintCheck \
    compileKotlin \
    compileTestKotlin \
    :apps:domain:jacocoTestCoverageVerification \
    :apps:application:jacocoTestCoverageVerification \
    :apps:api:test \
    :storage:rdb:test
}

mode="${CODEX_VERIFY_MODE:-smart}"
if [[ "$mode" == "full" ]]; then
  run_full
fi

changed=""
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  tracked_changed="$(git diff --name-only --diff-filter=ACMR HEAD || true)"
  untracked_changed="$(git ls-files --others --exclude-standard || true)"
  changed="$tracked_changed"
  if [[ -n "$untracked_changed" ]]; then
    changed="${changed}${changed:+$'\n'}${untracked_changed}"
  fi
fi

if [[ -z "$changed" ]]; then
  exec ./gradlew ktlintCheck test
fi

tasks=("ktlintCheck")
full=0

add_task() {
  local task="$1"
  local existing
  for existing in "${tasks[@]}"; do
    if [[ "$existing" == "$task" ]]; then
      return
    fi
  done
  tasks+=("$task")
}

while IFS= read -r path; do
  case "$path" in
    build.gradle.kts|settings.gradle.kts|gradle.properties|gradle/*|.codex/hooks/verify.sh)
      full=1
      ;;
    apps/domain/*)
      add_task ":apps:domain:compileKotlin"
      add_task ":apps:domain:compileTestKotlin"
      add_task ":apps:domain:jacocoTestCoverageVerification"
      ;;
    apps/application/*)
      add_task ":apps:application:compileKotlin"
      add_task ":apps:application:compileTestKotlin"
      add_task ":apps:application:jacocoTestCoverageVerification"
      ;;
    apps/api/*|src/main/kotlin/com/reservation/api/*)
      add_task ":apps:api:test"
      ;;
    storage/rdb/*)
      add_task ":storage:rdb:test"
      ;;
    storage/redis/*)
      add_task ":storage:redis:compileKotlin"
      add_task ":storage:redis:compileTestKotlin"
      ;;
    *.kt|*.kts)
      full=1
      ;;
  esac
done <<< "$changed"

if [[ "$full" == "1" ]]; then
  run_full
fi

exec ./gradlew "${tasks[@]}"
