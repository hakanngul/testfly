---
tags:
  - wiki
  - architecture
  - testfly
  - design
date: 2026-09-10
status: active
type: wiki
---

# TestFly Mimari Tasarımı (Architecture)

TestFly, "Selenium'un Spring Boot'u" felsefesiyle tasarlanmış, sıfır konfigürasyonlu, fikir bildiren (*opinionated*) bir Java test otomasyon framework'üdür.

---

## 1. Temel İlkeler
- **Java 21 LTS Standardı:** Java 21 (`--release 21`), Pattern Matching, Switch Expressions, Record yapıları ve Stream `.toList()` üzerine modern kod tabanı.
- **Konfigürasyondan Önce Sözleşme:** Asgari YAML (`testfly.yml`) ile üretime hazır çalışma.
- **Ham Selenium'u Gizlememe:** Kullanıcı istediğinde `WebDriver`, `By` ve `WebElement` API'lerine doğrudan erişebilir.
- **ThreadLocal İzolasyonu:** Paralel test koşumlarında oturum çakışmasını engelleyen güvenli izole yapı.

## 2. Temel Katmanlar
1. **Sürücü Katmanı (`driver`):** `DriverManager`, yerel ve bulut (BrowserStack, Sauce Labs) sağlayıcıları.
2. **Bekleme Motoru (`wait`):** `WaitEngine` ile merkezi akıllı açık beklemeler (Explicit Waits). Asla `Thread.sleep()` kullanılmaz.
3. **Değişmez Konumlandırıcılar (`locator`):** `Locator.cssSelector()`, `byRole()` ve filtreleme zincirlerine sahip immutable `Locator` API'si.
4. **Hata Dayanıklılığı ve Self-Healing:** `FuzzyHealingEngine` (Levenshtein mesafe algoritması ile 0 token maliyetli yerel onarım), `SmartTriageEngine` (test başarısızlıklarının sistemsel flaky vs uygulama hatası olarak yerel sınıflandırılması) ve `AiHealingEngine`.
5. **Web-First Doğrulama:** `LocatorAssert` ve `PageAssert` ile DOM-polling doğrulamaları (ilkel veri doğrulamaları AssertJ/TestNG'ye devredilmiştir).
6. **Raporlama Katmanı (`reporting`):** HTML raporlama, JUnit XML ve başarısızlık anında ekran görüntüleri.

## 3. İnteraktif Mimari Şeması
Archify ile oluşturulmuş, karanlık/aydınlık tema ve sunum modu destekli interaktif mimari şeması:
- [TestFly Architecture Diagram (HTML)](file:///Users/hagul/Projects/TestFramework/testfly/docs-site/static/diagrams/testfly-architecture.html)

---

## İlgili Bağlantılar
- Ana Dizin: `[[wiki/index]]`
- Harita: `[[MAP]]`
- WebDriver Yaşam Döngüsü: `[[wiki/webdriver-lifecycle]]`
- Yapılandırma: `[[wiki/configuration]]`
- Archify Becerisi: `[[skills/archify/SKILL]]`
