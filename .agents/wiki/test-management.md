---
tags:
  - wiki
  - test-management
  - testrail
  - xray
  - jira
date: 2026-09-10
status: active
type: wiki
---

# Test Yönetim Entegrasyonu (TestRail & Xray)

TestFly, test koşum sonuçlarını **TestRail** ve **Xray (Jira)** sistemlerine test metodları içinde hiçbir raporlama kodu yazmaya gerek kalmadan, tek bir anotasyonla otomatik gönderir.

---

## 1. Test Anotasyonları

```java
public class CheckoutTest extends BaseTest {

    @Test
    @TestRailCase("C4102")
    @XrayTest("PAY-108")
    public void krediKartiylaOdemeBasariliOlmali() {
        open("/checkout");
        find("#card-number").type("4111222233334444");
        find("#submit-btn").click();
        assertThat(find("#success-banner")).isVisible();
    }
}
```

---

## 2. Yapılandırma (`testfly.yml`)

```yaml
testmanagement:
  testrail:
    enabled: true
    url: https://sirket.testrail.io
    username: sdet@sirket.com
    apiKey: ${TESTRAIL_API_KEY}
    projectId: 1
    suiteId: 10
    runName: "Automated Suite — CI Run"

  xray:
    enabled: true
    mode: cloud # veya server/datacenter
    clientId: ${XRAY_CLIENT_ID}
    clientSecret: ${XRAY_CLIENT_SECRET}
```

Test bittiğinde TestFly, testin durumunu (`PASSED` / `FAILED`), hata mesajını ve ekran görüntüsü ekini ilgili test yönetim aracına anında iletir.

---

## İlgili Bağlantılar
- CI Kalite Kapıları: `[[wiki/ci-quality-gates]]`
- Temel Mimari: `[[wiki/architecture]]`
- Ana Harita: `[[MAP]]`
- Wiki Dizin: `[[wiki/index]]`
