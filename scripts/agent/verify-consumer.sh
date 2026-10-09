#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
consumer_source="${1:-${TESTFLY_CONSUMER_DIR:-}}"
consumer_tests="${TESTFLY_CONSUMER_TESTS:-ApiDemoTest,ApiSchemaDemoTest}"

if [[ -z "$consumer_source" && -f "$repo_root/../testfly-test/pom.xml" ]]; then
  consumer_source="$repo_root/../testfly-test"
fi
[[ -n "$consumer_source" ]] || {
  echo "consumer check: provide a checkout path or set TESTFLY_CONSUMER_DIR" >&2
  exit 2
}
[[ -f "$consumer_source/pom.xml" ]] || {
  echo "consumer check: $consumer_source has no pom.xml" >&2
  exit 2
}

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/testfly-consumer-check.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT
consumer_dir="$work_dir/consumer"
mkdir -p "$consumer_dir"

if git -C "$consumer_source" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git -C "$consumer_source" archive HEAD | tar -x -C "$consumer_dir"
else
  cp -R "$consumer_source"/. "$consumer_dir"/
fi

cd "$repo_root"
mvn -B -ntp -DskipTests -Dgpg.skip=true install
version="$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)"

cd "$consumer_dir"
mvn -B -ntp versions:use-dep-version \
  -Dincludes=io.github.hakanngul:testfly \
  -DdepVersion="$version" \
  -DforceVersion=true \
  -DgenerateBackupPoms=false
mvn -B -ntp -DskipTests compile
mvn -B -ntp -Dtest="$consumer_tests" -DskipITs test

echo "consumer check: PASS with TestFly $version (tests: $consumer_tests)"
