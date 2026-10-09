#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

baseline="${1:-}"
if [[ -z "$baseline" ]]; then
  baseline="$(git describe --tags --abbrev=0 HEAD 2>/dev/null || true)"
fi
[[ -n "$baseline" ]] || { echo "public API check: pass a baseline ref; no reachable tag found" >&2; exit 2; }
git rev-parse --verify "${baseline}^{commit}" >/dev/null

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/testfly-api-check.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT
baseline_dir="$work_dir/baseline"
mkdir -p "$baseline_dir"
git archive "$baseline" | tar -x -C "$baseline_dir"

echo "public API check: building current tree"
mvn -B -ntp -DskipTests -Dgpg.skip=true package
echo "public API check: building baseline $baseline"
mvn -B -ntp -DskipTests -Dgpg.skip=true -f "$baseline_dir/pom.xml" package

api_classes() {
  local source_root="$1"
  local java_root="$source_root/src/main/java"
  local source_file
  local relative
  while IFS= read -r source_file; do
    relative="${source_file#"$java_root"/}"
    relative="${relative%.java}"
    printf '%s\n' "${relative//\//.}"
  done < <(rg -l '^[[:space:]]*@TestFlyApi([[:space:]]*\(|[[:space:]]*$)' "$java_root" -g '*.java') \
    | sort -u
}

normalize_api() {
  local classpath="$1"
  local class_name="$2"
  javap -classpath "$classpath" -public -s "$class_name" \
    | sed -e '/^Compiled from /d' -e '/^{$/d' -e '/^}$/d' -e 's/^[[:space:]]*//' \
    | rg '^(public|protected|descriptor:)' \
    | awk '!(/^public / && $0 ~ / (class|interface|enum|record) /)' \
    | sort -u
}

type_relations() {
  local classpath="$1"
  local class_name="$2"
  local header
  header="$(javap -classpath "$classpath" -public "$class_name" \
    | awk '/^public / && $0 ~ / (class|interface|enum|record) / { print; exit }')"
  { printf '%s\n' "$header" | rg -o '[[:alnum:]_$]+(\.[[:alnum:]_$]+)+' || true; } \
    | awk -v own_type="$class_name" '$0 != own_type' \
    | sort -u
}

current_classes="$repo_root/target/classes"
baseline_classes="$baseline_dir/target/classes"
removed=0

while IFS= read -r class_name; do
  [[ -n "$class_name" ]] || continue
  class_path="$(printf '%s' "$class_name" | tr '.' '/')"
  class_path="$class_path.class"
  if [[ ! -f "$current_classes/$class_path" ]]; then
    echo "public API check: removed stable type $class_name" >&2
    removed=1
    continue
  fi

  old_api="$work_dir/old.api"
  new_api="$work_dir/new.api"
  old_relations="$work_dir/old.relations"
  new_relations="$work_dir/new.relations"
  normalize_api "$baseline_classes" "$class_name" > "$old_api"
  normalize_api "$current_classes" "$class_name" > "$new_api"
  type_relations "$baseline_classes" "$class_name" > "$old_relations"
  type_relations "$current_classes" "$class_name" > "$new_relations"
  if ! comm -23 "$old_api" "$new_api" > "$work_dir/removed.api"; then
    exit 1
  fi
  if [[ -s "$work_dir/removed.api" ]]; then
    echo "public API check: removed or changed members in $class_name" >&2
    sed 's/^/  /' "$work_dir/removed.api" >&2
    removed=1
  fi
  if ! comm -23 "$old_relations" "$new_relations" > "$work_dir/removed.relations"; then
    exit 1
  fi
  if [[ -s "$work_dir/removed.relations" ]]; then
    echo "public API check: removed supertypes in $class_name" >&2
    sed 's/^/  /' "$work_dir/removed.relations" >&2
    removed=1
  fi
done < <(api_classes "$baseline_dir")

if [[ "$removed" -ne 0 ]]; then
  exit 1
fi

echo "public API check: PASS against $baseline"
