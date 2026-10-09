#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
english_root="$repo_root/docs-site/docs"
turkish_root="$repo_root/docs-site/i18n/tr/docusaurus-plugin-content-docs/current"

[[ -d "$english_root" ]] || { echo "localization check: missing $english_root" >&2; exit 1; }
[[ -d "$turkish_root" ]] || { echo "localization check: missing $turkish_root" >&2; exit 1; }

missing=0
while IFS= read -r english_file; do
  relative="${english_file#"$english_root"/}"
  if [[ ! -f "$turkish_root/$relative" ]]; then
    echo "localization check: missing Turkish counterpart for $relative" >&2
    missing=$((missing + 1))
  fi
done < <(find "$english_root" -type f -name '*.md' -print | sort)

if [[ "$missing" -ne 0 ]]; then
  exit 1
fi

count="$(find "$english_root" -type f -name '*.md' | wc -l | tr -d ' ')"
echo "localization check: PASS ($count English pages have Turkish counterparts)"
