---
id: configuration
title: Yük Testi Konfigürasyonu
description: "testfly.yml dosyasında yük testi varsayılanlarını yapılandırın: motor seçimi, sanal kullanıcılar, kademeli artış ve raporlama ayarları."
sidebar_position: 2
---

# Yük Testi Konfigürasyonu

TestFly, `testfly.yml` içerisinde merkezi bir `loadtest:` bloğu sunar. Bu sayede yerel çalışma ortamı ile CI/CD hatları arasında tutarlı performans eşikleri ve varsayılanları tanımlanabilir.

---

## 1. `loadtest:` Konfigürasyon Bloğu

`testfly.yml` dosyanıza opsiyonel olarak `loadtest:` bloğunu ekleyin:

```yaml
loadtest:
  enabled: false          # ayrılmış bayrak; açık runner çağrıları henüz bu değerle engellenmez
  baseUrl: https://api.example.com
  engine: auto            # auto (varsa Gatling, yoksa JDK) | gatling | jdk
  users: 10               # rampUp boyunca başlatılan sanal kullanıcı sayısı (eşzamanlılık hedefi değildir)
  rampUp: 10s             # kullanıcıların eşit aralıklarla başlatıldığı süre (örn: 10s, 1m)
  hold: 30s               # her kullanıcının kendi başlangıcından itibaren döngüde kaldığı süre (örn: 30s, 5m)
  cooldown: 5s            # kabul edilir ama hiçbir motor uygulamaz (WARN loglanır)
  maxUsers: 1000          # güvenlik sınırı: users kırpılır; bu değeri aşan iş yükleri reddedilir
  resultsDir: target/loadtest # her koşu için bir koşu dizini (metrikler, raporlar, sonuç dosyası)
  reportEnabled: true     # HTML yük testi raporu oluştur
  requestTimeoutSeconds: 30 # istek başına zaman aşımı, saniye (iki motorda da)
```

---

## 2. Ayar Parametreleri Tablosu

| Parametre | Tip | Varsayılan | Açıklama |
| :--- | :--- | :--- | :--- |
| `enabled` | `boolean` | `false` | Ayrılmış etkin bayrak (`features.loadtest` ile geçersiz kılınabilir); mevcut `LoadTestRunner.run()` yolu bu değeri uygulamaz. |
| `baseUrl` | `string` | `null` | Senaryo/annotation önceliğinden sonraki hedef adres. Gatling `execution.baseUrl` değerine düşer; JDK motoru bu değeri veya senaryo/annotation adresini zorunlu tutar. |
| `engine` | `string` | `auto` | Çalıştırma motoru: `auto` (varsa Gatling, yoksa JDK), `gatling` (kesinlikle Gatling gerektirir), `jdk` (yerel sanal iş parçacıkları / virtual threads). |
| `users` | `int` | `10` | Kodda belirtilmediğinde `rampUp` boyunca eşit aralıklarla başlatılan sanal kullanıcı sayısı. Her kullanıcı senaryoyu `hold` süresince döngüde çalıştırır. Bu bir eşzamanlılık hedefi değildir: zirve eşzamanlılık yalnızca `rampUp <= hold` olduğunda `users` değerine eşittir. |
| `rampUp` | `string` | `10s` | Sanal kullanıcıların eşit aralıklarla başlatıldığı süre (örn: `10s`, `1m`). |
| `hold` | `string` | `30s` | Her sanal kullanıcının kendi başlangıcından itibaren senaryoyu döngüde çalıştırdığı süre (örn: `30s`, `5m`). Bir koşu yaklaşık `rampUp + hold` sürer. |
| `cooldown` | `string` | `5s` | Uyumluluk için kabul edilir ama iki motor da **uygulamaz**. Sıfırdan büyükse JVM başına bir kez WARN loglanır. |
| `maxUsers` | `int` | `1000` | Güvenlik sınırı. Çözümlenen `users` değeri bu değere kırpılır. Zirvesi bu değeri aşan bir [iş yükü](./fluent-api.md#workload-models) herhangi bir istekten önce reddedilir: kapalı iş yükünde zirve eşzamanlılık, açık iş yükünde zirve geliş hızı ve kullanıcı sayılı adımlar. Bir [simülasyon](./simulations-sla.md) zirvelerin toplamını denetler. |
| `resultsDir` | `string` | `target/loadtest` | Koşu dizinlerinin üst dizini. İki motorda da her koşu, `testfly-loadtest-result.json` sonuç dosyasıyla birlikte `<resultsDir>/<senaryo-veya-simülasyon>-<millis>/` dizinini oluşturur; bkz. [Raporlama](./reporting.md#run-directory). TestFly eski koşu dizinlerini silmez. |
| `reportEnabled` | `boolean` | `true` | Müstakil HTML yük testi raporunun ve Gatling bağlantılarının üretilmesini sağlar. |
| `requestTimeoutSeconds` | `int` | `30` | İstek başına zaman aşımı (saniye); iki motor da uygular (JDK: bağlantı ve istek zaman aşımı; Gatling: `requestTimeout`). Zaman aşımına uğrayan istek `-1` durum koduyla hatalı sayılır. |

:::note İş yükü modeli
`users`, `rampUp` ve `hold` açık (open) bir iş yükünü tanımlar: kullanıcılar `rampUp` boyunca eşit aralıklarla gelir ve her biri kendi başlangıcından itibaren `hold` süresince döngüde kalır. `users: 10`, `rampUp: 6s` ve `hold: 2s` ile ilk kullanıcılar son kullanıcılar başlamadan biter, yani aynı anda 10'dan az kullanıcı çalışır. `cooldown` etkisizdir. Karar depodaki `docs/adr/0003-load-test-workload-model.md` (ADR 0003) dosyasında kayıtlıdır.

Sabit eşzamanlılık, geliş hızı, ani yük sıçraması veya kademeli azalış için kodda `LoadScenario.workload(...)` ile açık bir iş yükü tanımlayın (yalnızca Gatling motoru). İş yükü yalnızca kodda tanımlanır: iş yükü olan bir senaryo bu bloktaki ve `@LoadTest` içindeki `users`, `rampUp`, `hold` ve `cooldown` değerlerini yok sayar. Bkz. [İş yükü modelleri](./fluent-api.md#workload-models).
:::

---

## 3. Ortam Profilleri ile CI Ayarları

TestFly profillerini kullanarak yerel ortamda hızlı duman testleri, CI üzerinde ise yoğun yük testleri koşturabilirsiniz:

### `testfly.yml` (Yerel Geliştirici Ortamı)
```yaml
loadtest:
  engine: auto
  users: 5
  rampUp: 2s
  hold: 5s
```

### `testfly-performance.yml` (CI / Performans Ortamı)
```yaml
loadtest:
  engine: gatling
  users: 250
  rampUp: 30s
  hold: 120s
  cooldown: 10s
  reportEnabled: true
```

Profili Maven ile çalıştırma:
```bash
mvn test -Dtestfly.profile=performance
```

---

## 4. Master Anahtar (Feature Switchboard) Entegrasyonu

TestFly'ın anahtar panosu üzerinden yük testleri tüm pakette tek satırla kapatılabilir veya zorla açılabilir:

```yaml
features:
  loadtest: false # Tüm yük testi koşturmalarını devre dışı bırakır
```
