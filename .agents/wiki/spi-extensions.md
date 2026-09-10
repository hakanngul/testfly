---
tags:
  - wiki
  - spi
  - extensibility
  - plugins
  - hooks
date: 2026-09-10
status: active
type: wiki
---

# Java SPI Eklenti Mimarisi (Extensibility)

TestFly, açık kaynak Java Service Provider Interface (SPI) standardını kullanarak framework çekirdeğini çatallamadan (fork) genişletme olanağı tanır.

---

## 1. Desteklenen SPI Genişleme Noktaları

| Eklenti Türü | Arayüz | Sorumluluk |
|:---|:---|:---|
| **Özel Driver Sağlayıcı** | `NamedDriverProvider` | Özel Selenium Grid veya özel tarayıcı yapılandırmaları. |
| **Rapor Adaptörü** | `ReportAdapter` | Allure, ReportPortal, Slack veya şirket içi rapor servisleri. |
| **Yaşam Döngüsü Hook** | `ExecutionHook` | Suite ve test öncesi/sonrası özel telemetri veya kaynak temizliği. |
| **Tam Framework Eklentisi** | `TestFlyPlugin` | Çoklu servis ve hook içeren bağımsız eklenti paketleri. |

---

## 2. SPI Kayıt Yöntemi

Geliştirilen sınıf `META-INF/services/` altında ilgili arayüzün tam adıyla kaydedilir:

Örnek: `META-INF/services/io.testfly.reporting.ReportAdapter`
```text
com.example.reporting.CustomSlackReportAdapter
```

Veya programatik olarak bootstrap anında kaydedilebilir:
```java
ReportAdapterRegistry.register(new CustomSlackReportAdapter());
HookRegistry.register(new CustomTelemetryHook());
```

---

## İlgili Bağlantılar
- Karantina Motoru: `[[wiki/quarantine-engine]]`
- Temel Mimari: `[[wiki/architecture]]`
- Ana Harita: `[[MAP]]`
- Wiki Dizin: `[[wiki/index]]`
