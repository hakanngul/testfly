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
  enabled: true
  engine: auto            # auto | gatling | jdk
  defaultUsers: 10
  defaultRampUpSeconds: 5
  defaultDurationSeconds: 15
  targetRps: 100
  timeoutSeconds: 30
  reportEnabled: true
  feeder:
    path: "src/test/resources/data/users.csv"
    format: csv           # csv | json
```

---

## 2. Ayar Parametreleri Tablosu

| Parametre | Tip | Varsayılan | Açıklama |
| :--- | :--- | :--- | :--- |
| `enabled` | `boolean` | `true` | Yük testi modülünü etkinleştirir veya devre dışı bırakır. |
| `engine` | `string` | `auto` | Çalıştırma motoru: `auto` (varsa Gatling, yoksa JDK), `gatling` (kesinlikle Gatling), `jdk` (yerel sanal iş parçacıkları). |
| `defaultUsers` | `int` | `1` | Kodda belirtilmediğinde kullanılacak varsayılan eşzamanlı kullanıcı sayısı. |
| `defaultRampUpSeconds` | `int` | `0` | Zirve kullanıcı sayısına ulaşırken geçecek doğrusal artış süresi (saniye). |
| `defaultDurationSeconds`| `int` | `10`| Zirve kullanıcı sayısının korunacağı süre (saniye). |
| `targetRps` | `int` | `0` | İsteğe bağlı global istek hız sınırlayıcı (0 = sınırsız). |
| `timeoutSeconds` | `int` | `30`| HTTP bağlantı ve istek zaman aşımı süresi (saniye). |
| `reportEnabled` | `boolean` | `true` | Müstakil `loadtest-report.html` dosyasının ve Gatling bağlantılarının üretilmesini sağlar. |
| `feeder.path` | `string` | `null` | Varsayılan veri besleme dosyasının yolu (CSV veya JSON). |
| `feeder.format` | `string` | `csv` | Besleyici dosya formatı (`csv` veya `json`). |

---

## 3. Ortam Profilleri ile CI Ayarları

TestFly profillerini kullanarak yerel ortamda hızlı duman testleri, CI üzerinde ise yoğun yük testleri koşturabilirsiniz:

### `testfly.yml` (Yerel Geliştirici Ortamı)
```yaml
loadtest:
  engine: auto
  defaultUsers: 5
  defaultDurationSeconds: 5
```

### `testfly-performance.yml` (CI / Performans Ortamı)
```yaml
loadtest:
  engine: gatling
  defaultUsers: 250
  defaultRampUpSeconds: 30
  defaultDurationSeconds: 120
  reportEnabled: true
```

Profili Maven ile çalıştırma:
```bash
mvn test -Dtestfly.profile=performance
```

---

## 4. Master Anahtar (Feature Switchboard) Entegrasyonu

TestFly'ın anahtar panosu üzerinden yük testleri tüm pakette tek satırla kapatılabilir:

```yaml
features:
  loadtest: false # Tüm yük testi koşturmalarını devre dışı bırakır
```
