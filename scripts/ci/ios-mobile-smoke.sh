#!/usr/bin/env bash
# Boots an iOS simulator and runs the native iOS example on it (ADR 0002 phase 3).
# Used by .github/workflows/mobile-emulator-smoke.yml on a macOS runner.
set -euo pipefail

udid="$(xcrun simctl list devices available -j | jq -r '[.devices[][] | select(.name | test("^iPhone"))][0].udid')"
[[ -n "$udid" && "$udid" != "null" ]] || { echo "no iPhone simulator available" >&2; exit 1; }
xcrun simctl boot "$udid" || true
xcrun simctl bootstatus "$udid" -b
echo "simulator: $udid"

mkdir -p target
mobilecli --version
mobilecli server start --listen localhost:12000 --insecure-storage > target/mobilecli-server.log 2>&1 &
for _ in $(seq 1 30); do
  curl -sf -X POST http://localhost:12000/rpc -H 'Content-Type: application/json' \
    -d '{"jsonrpc":"2.0","id":1,"method":"server.info"}' && break
  sleep 1
done
echo
mobilecli devices
mobilecli agent install --device "$udid"
mobilecli agent status --device "$udid"

MOBILE_DEVICE_ID="$udid" mvn test --no-transfer-progress -Djacoco.skip=true \
  -Dtestfly.profile=mobile-ios -Dtest=IosSettingsExampleTest -Dsurefire.failIfNoSpecifiedTests=false
