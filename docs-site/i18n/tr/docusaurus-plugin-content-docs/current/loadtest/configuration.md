---
id: configuration
title: Yük Testi Konfigürasyonu
description: "testfly.yml dosyasında yük testi varsayılanlarını yapılandırın: motor seçimi, eşzamanlı kullanıcılar, kademeli artış ve raporlama ayarları."
sidebar_position: 2
---

# Yük Testi Konfigürasyonu

TestFly, `testfly.yml` içerisinde merkezi bir `loadtest:` bloğu sunar. Bu sayede yerel çalışma ortamı ile CI/CD hatları arasında tutarlı performans eşikleri ve varsayılanları tanımlanabilir.

---

## 1. `loadtest:` Konfigürasyon Bloğu

`testfly.yml` dosyanıza opsiyonel olarak `loadtest:` bloğunu ekleyin:

```yaml
loadtest:
  enabled: false          # yük testini etkinleştir (features.loadtest ile de yönetilebilir)
  baseUrl: https://api.example.com
  engine: auto            # auto (varsa Gatling, yoksa JDK) | gatling | jdk
  users: 10               # varsayılan eşzamanlı sanal kullanıcı sayısı
  rampUp: 10s             # kademeli kullanıcı artış süresi (örn: 10s, 1m)
  hold: 30s               # zirve yükün korunacağı süre (örn: 30s, 5m)
  cooldown: 5s            # kademeli soğuma süresi (örn: 5s)
  maxUsers: 1000          # izin verilen maksimum kullanıcı sayısı
  resultsDir: target/loadtest # çıktıların ve raporların kaydedileceği dizin
  reportEnabled: true     # HTML yük testi raporu oluştur
  requestTimeoutSeconds: 30 # HTTP istek zaman aşımı (saniye)
```

---

## 2. Ayar Parametreleri Tablosu

| Parametre | Tip | Varsayılan | Açıklama |
| :--- | :--- | :--- | :--- |
| `enabled` | `boolean` | `false` | Yük testi modülünü etkinleştirir veya devre dışı bırakır (`features.loadtest` ile geçersiz kılınabilir). |
| `baseUrl` | `string` | `null` | Yük testinde kullanılacak temel HTTP adresi (tanımsızsa test içindeki adres veya `execution.baseUrl` kullanılır). |
| `engine` | `string` | `auto` | Çalıştırma motoru: `auto` (varsa Gatling, yoksa JDK), `gatling` (kesinlikle Gatling gerektirir), `jdk` (yerel sanal iş parçacıkları / virtual threads). |
| `users` | `int` | `10` | Kodda belirtilmediğinde kullanılacak varsayılan eşzamanlı kullanıcı sayısı. |
| `rampUp` | `string` | `10s` | Zirve kullanıcı sayısına ulaşırken geçecek kademeli artış süresi (örn: `10s`, `1m`). |
| `hold` | `string` | `30s` | Zirve kullanıcı yükünün korunacağı süre (örn: `30s`, `5m`). |
| `cooldown` | `string` | `5s` | Test bitimindeki kademeli soğuma süresi (örn: `5s`). |
| `maxUsers` | `int` | `1000` | İzin verilen tavan kullanıcı sayısı (güvenlik sınırı). |
| `resultsDir` | `string` | `target/loadtest` | Yük testi metrik ve rapor dosyalarının yazılacağı dizin. |
| `reportEnabled` | `boolean` | `true` | Müstakil HTML yük testi raporunun ve Gatling bağlantılarının üretilmesini sağlar. |
| `requestTimeoutSeconds` | `int` | `30` | HTTP bağlantı ve istek zaman aşımı süresi (saniye). |

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
