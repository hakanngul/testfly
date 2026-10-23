#!/usr/bin/env bash
# Runs the native mobile smoke test on an Android emulator that is already booted
# (ADR 0002 phase 3). Used by .github/workflows/mobile-emulator-smoke.yml.
set -euo pipefail

mobilecli --version
mobilecli server start --listen localhost:12000 --insecure-storage > target/mobilecli-server.log 2>&1 &
for _ in $(seq 1 30); do
  curl -sf -X POST http://localhost:12000/rpc -H 'Content-Type: application/json' \
    -d '{"jsonrpc":"2.0","id":1,"method":"server.info"}' && break
  sleep 1
done
echo

mobilecli devices
device_id="$(mobilecli devices | jq -r '.data.devices[] | select(.platform=="android" and .state=="online") | .id' | head -n 1)"
if [[ -z "$device_id" ]]; then
  echo "no online Android device found by mobilecli" >&2
  exit 1
fi
echo "device: $device_id"

mobilecli agent install --device "$device_id"
mobilecli agent status --device "$device_id"

MOBILE_DEVICE_ID="$device_id" mvn verify -Preal-backends --no-transfer-progress \
  -Djacoco.skip=true \
  -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false \
  -Dit.test=AndroidEmulatorSmokeIntegrationTest \
  -Dtestfly.mobile.smoke=true

# The user-facing example, through BaseMobileTest and the TestNG bridge.
MOBILE_DEVICE_ID="$device_id" mvn test --no-transfer-progress -Djacoco.skip=true \
  -Dtestfly.profile=mobile-android -Dtest=AndroidSettingsExampleTest -Dsurefire.failIfNoSpecifiedTests=false
