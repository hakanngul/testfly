---
tags:
  - wiki
  - quarantine
  - flakiness
  - retryable
  - ci-cd
date: 2026-09-10
status: active
type: wiki
---

# Flakiness ve Karantina Motoru (Quarantine Engine)

TestFly, kararsız (flaky) testlerin CI pipeline'larını gereksiz yere kırmasını engellemek için kurumsal bir **Karantina Motoru** ve **Flakiness Puanlama** sistemi barındırır.

---

## 1. Karantina Yapılandırması (`testfly-quarantine.yml`)

Proje kök dizininde bulunan `testfly-quarantine.yml` dosyası üzerinden testler izole edilir:

```yaml
quarantine:
  enabled: true
  cucumberTag: "@quarantine"
  tests:
    - className: io.testfly.examples.PaymentTest
      methodName: testStripeWebhook
      reason: "Üçüncü parti webhook servis gecikmesi (JIRA-4021)"
      owner: "@odeme-takimi"
```

### CI Davranışı
- Karantinaya alınan testler CI üzerinde çalıştırılmaya devam eder (böylece düzeltilip düzeltilmediği izlenebilir).
- Ancak başarısız olduklarında derlemeyi (build) kırmaz; raporda **QUARANTINED** statüsüyle işaretlenir.

---

## 2. Otomatik Yeniden Deneme (`@Retryable`)

Geçici ağ veya tarayıcı takılmalarına karşı TestNG ve Cucumber testlerine retry uygulanabilir:

```java
@Test
@Retryable(maxAttempts = 3)
public void odemeEkraniGeciciYukTesti() {
    open("/checkout");
    find("#pay-btn").click();
    assertThat(find("#success")).isVisible();
}
```

---

## 3. Flakiness Risk Puanlaması

TestFly, test koşum geçmişini (`target/flakiness-history.json`) analiz ederek her test için bir risk skoru üretir:
- **0.0 - 0.2:** Kararlı (Stable)
- **0.2 - 0.5:** Orta Risk (Warning)
- **0.5+:** Yüksek Flakiness Riski (Otomatik Karantina Önerisi)

---

## İlgili Bağlantılar
- CI Kalite Kapıları: `[[wiki/ci-quality-gates]]`
- SPI Eklentileri: `[[wiki/spi-extensions]]`
- Ana Harita: `[[MAP]]`
- Wiki Dizin: `[[wiki/index]]`
