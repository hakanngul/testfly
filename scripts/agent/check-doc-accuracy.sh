#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

failed=0

reject() {
  local needle="$1"
  shift
  if rg -n -F -- "$needle" "$@"; then
    echo "documentation accuracy check: forbidden text found: $needle" >&2
    failed=1
  fi
}

require() {
  local needle="$1"
  shift
  if ! rg -q -F -- "$needle" "$@"; then
    echo "documentation accuracy check: required text missing: $needle" >&2
    failed=1
  fi
}

docs=(
  docs-site/docs
  docs-site/i18n/tr/docusaurus-plugin-content-docs/current
  docs-site/src/data/homeData.js
)

# Guard source-authenticated APIs and claims corrected by API_ACCURACY_AUDIT.md.
reject '.filter(hasText(' "${docs[@]}"
reject '$$(css)' "${docs[@]}"
reject '38 WAI-ARIA' "${docs[@]}"
reject 'target/reports/testfly-report.html' "${docs[@]}"
reject 'target/reports/loadtest-report.html' "${docs[@]}"
reject 'target/reports/loadtest/' "${docs[@]}"
reject 'network().route(' docs-site/src/data/homeData.js
reject 'api().auth(' docs-site/src/data/homeData.js
reject 'Zero-flakiness' docs-site/src/data/homeData.js
reject '100% feature parity' docs-site/src/data/homeData.js

reject 'K6 / Gatling Engine' docs-site/static/diagrams/testfly-k6-dataflow.json
reject 'JDK 21, K6, Gatling' docs-site/static/diagrams/testfly-architecture.json
reject 'K6 / Gatling Engine' docs-site/static/diagrams/testfly-k6-dataflow.html
reject 'test.k6.io' docs-site/static/diagrams/testfly-k6-dataflow.html
reject 'JDK 21, K6, Gatling' docs-site/static/diagrams/testfly-architecture.html

require 'ApiMockRule.builder()' docs-site/src/data/homeData.js
require '.assertJsonExists("$.orderId")' docs-site/src/data/homeData.js
require 'PRODUCT_IDS' docs-site/src/data/homeData.js
require '36 WAI-ARIA' docs-site/docs/guides/semantic-locators.md
require '36 WAI-ARIA' docs-site/i18n/tr/docusaurus-plugin-content-docs/current/guides/semantic-locators.md
require 'target/loadtest/<run-id>/' docs-site/docs/loadtest/reporting.md
require 'target/loadtest/<run-id>/' docs-site/i18n/tr/docusaurus-plugin-content-docs/current/loadtest/reporting.md
require 'failOnNewBaseline' docs-site/docs/configuration.md
require 'failOnNewBaseline' docs-site/i18n/tr/docusaurus-plugin-content-docs/current/configuration.md

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

echo "documentation accuracy check: PASS"
