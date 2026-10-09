#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

mode="${1:-}"
shift || true

run_code() {
  mvn -B -ntp test
}

run_spi() {
  mvn -B -ntp -Dtest=DriverProviderRegistryTest,HookRegistryTest,PluginRegistryTest,ReportAdapterRegistryTest,PreConditionRegistryTest test
}

run_docs() {
  scripts/agent/check-localization.sh
  if [[ ! -x docs-site/node_modules/.bin/docusaurus ]]; then
    npm --prefix docs-site ci --no-audit --no-fund
  fi
  npm --prefix docs-site run build
}

usage() {
  echo "usage: scripts/agent/verify.sh {agent|code|api|spi|consumer|docs|full} [argument]" >&2
  exit 2
}

case "$mode" in
  agent)
    scripts/agent/validate.sh
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
  full)
    scripts/agent/validate.sh
    run_code
    scripts/agent/check-public-api.sh "${1:-}"
    run_spi
    run_docs
    ;;
  *)
    usage
    ;;
esac
