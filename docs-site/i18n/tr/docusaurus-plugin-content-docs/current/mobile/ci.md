---
id: ci
title: CI'da Mobil Testler
sidebar_position: 11
description: "Native mobil testleri GitHub Actions'ta KVM hızlandırmalı bir Android emülatörüyle veya macOS'ta bir iOS simülatörüyle çalıştırın; TestFly'ın kendi Mobile Emulator Smoke iş akışında olduğu gibi."
---

# CI'da Mobil Testler

TestFly'ın kendi iş akışı `.github/workflows/mobile-emulator-smoke.yml` (**Mobile Emulator Smoke**) çalışan bir şablondur. `development`'a mobil kodu veya örnekleri değiştiren push'larda ve elle tetiklenince çalışır. Ana CI iş akışını hiçbir zaman engellemez.

| İş | Runner | Ne yapar |
|---|---|---|
| Android emulator smoke (API 30, 34) | KVM'li `ubuntu-latest` | `reactivecircus/android-emulator-runner` ile bir emülatör açar, `scripts/ci/android-mobile-smoke.sh` çalıştırır |
| iOS simulator smoke | `macos-latest` | bir iPhone simülatörü açar, `scripts/ci/ios-mobile-smoke.sh` çalıştırır |

## Bir işin adımları {#steps-of-a-job}

1. Java 21 ve Node; `npm install -g mobilecli@1.0.20` (denediğiniz sürümü sabitleyin).
2. Derleme: `mvn -DskipTests install`.
3. Cihazı açın. Android: KVM'yi etkinleştirin, ardından `disable-animations: true` ile emülatör runner'ı. iOS: `xcrun simctl boot <udid>` ve `xcrun simctl bootstatus <udid> -b`.
4. mobilecli'yi başlatın ve `server.info`'yu bekleyin. Betikler, logunu (`target/mobilecli-server.log`) saklamak için onu açıkça başlatır; `autoStartServer` ile TestFly da başlatırdı.
5. Ajanı kurun: `mobilecli agent install --device <id>`.
6. **Yalnızca iOS: DeviceKit'i ısıtın.** Yeni bir simülatördeki ilk ekran okuması, DeviceKit açılırken bir dakikadan uzun sürebilir. Betik, Maven çalışmadan önce görünüm ağacını 180 sn sınırla bir kez okur:

   ```bash
   curl -sf --max-time 180 -X POST http://localhost:12000/rpc -H 'Content-Type: application/json' \
     -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"device.dump.ui\",\"params\":{\"deviceId\":\"$udid\"}}"
   ```

7. Testleri cihaz kimliğiyle çalıştırın: `MOBILE_DEVICE_ID=<id> mvn test -Dtestfly.profile=<profil> -Dtest=<Sınıf>`.
8. İş başarısız olsa da `target/surefire-reports/`, `target/reports/screenshots/` ve mobilecli logunu artifact olarak yükleyin.

## İpuçları

- CI'daki emülatörler yavaş oturur: örnek profiller `actionMs: 30000`, `pollMs: 500`, `rpcMs: 60000` kullanır.
- Ayarlar uygulamasının metinleri cihaz diline bağlıdır; CI imajları İngilizcedir.
- `Skipped` test bildiren bir iş cihaza ulaşmamıştır; bunu başarısızlık sayın.
- Cihaz kimlikleri: Android'de AVD adı (`mobilecli devices` çıktısından), iOS'ta simülatör UDID'si.
- Login testinin gizli bilgileri CI'ın secret deposuna konur ve teste ortam değişkeni olarak ulaşır.
