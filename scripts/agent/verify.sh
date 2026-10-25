#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

mode="${1:-}"
shift || true

run_agent() {
  scripts/agent/validate.sh
  python3 -I scripts/agent/test_agent_infra.py
}

run_code() {
  mvn -B -ntp test
}

run_spi() {
  mvn -B -ntp -Dtest=DriverProviderRegistryTest,HookRegistryTest,PluginRegistryTest,ReportAdapterRegistryTest,PreConditionRegistryTest test
}

run_docs() {
  scripts/agent/check-doc-accuracy.sh
  scripts/agent/check-localization.sh
  if [[ ! -x docs-site/node_modules/.bin/docusaurus ]]; then
    npm --prefix docs-site ci --no-audit --no-fund
  fi
  npm --prefix docs-site run build
}

usage() {
  echo "usage: scripts/agent/verify.sh {agent|code|api|spi|consumer|docs|release|full} [baseline-or-checkout] [consumer-checkout]" >&2
  exit 2
}

case "$mode" in
  agent)
    run_agent
    ;;
  code)
    run_code
    ;;
  api)
    scripts/agent/check-public-api.sh "${1:-}"
    run_code
    ;;
  spi)
    run_spi
    run_code
    ;;
  consumer)
    scripts/agent/verify-consumer.sh "${1:-}"
    ;;
  docs)
    run_docs
    ;;
  release)
    run_agent
    scripts/agent/check-release.sh
    mvn -B -ntp verify
    scripts/agent/check-public-api.sh "${1:-}"
    run_docs
    scripts/agent/verify-consumer.sh "${2:-}"
    ;;
  full)
    run_agent
    run_code
    scripts/agent/check-public-api.sh "${1:-}"
    run_spi
    run_docs
    ;;
  *)
    usage
    ;;
esac
