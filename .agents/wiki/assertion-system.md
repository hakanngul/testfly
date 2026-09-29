---
tags:
  - wiki
  - assertion-system
  - locator-assert
  - page-assert
  - architecture-decision
date: 2026-09-29
status: active
type: wiki
---

# TestFly Doğrulama Sistemi (Assertion System & Architecture Decision)

TestFly, web otomasyonu için özel olarak geliştirilmiş **Web-First (DOM Polling)** doğrulama mimarisine sahiptir.

---

## 1. Mimari Karar ve Sınırlar (ADR: Assertion Boundary)

- **Karar:** `AssertionSupport`, `BaseTest` ve `BasePage` içerisine `assertTrue`, `assertEquals`, `assertNotNull` gibi standart Java ilkel veri doğrulama metotları **eklenmez**.
- **Gerekçe:**
  1. **API Şişkinliğini Önleme:** Java ekosisteminde AssertJ, JUnit ve TestNG gibi çok olgun ilkel assertion araçları zaten mevcuttur. TestFly'ın bunları yeniden yazması ("reinventing the wheel") gereksiz kod yükü yaratır.
  2. **Tek Sorumluluk:** TestFly'ın asıl uzmanlık alanı, asenkron web sayfalarında elementlerin render edilmesini, görünürlüğünü, metin değişimlerini otomatik yeniden deneme (polling) ile beklemektir.
  3. **Tavsiye Edilen Kullanım:**
     - Web UI & DOM doğrulamaları: `assertThat(locator)`, `assertThat(By)`, `assertThatPage()` (TestFly).
     - Genel veri, liste, sayı ve POJO doğrulamaları: `org.assertj.core.api.Assertions.assertThat(...)` veya `org.testng.Assert`.

---

## 2. Temel Doğrulama Bileşenleri

1. **`LocatorAssert` (`assertThat(Locator)` / `assertThat(By)`):**
   - Elemanın görünürlüğü (`isVisible`, `isHidden`), metni (`hasText`, `containsText`), durumu (`isEnabled`, `isDisabled`, `isChecked`) ve nitelikleri (`hasAttribute`, `hasValue`) üzerinde `timeouts.explicit` süresince DOM'u sorgular.
2. **`PageAssert` (`assertThatPage()`):**
   - Sayfa başlığı (`hasTitle`, `titleContains`) ve URL (`hasUrl`, `urlContains`, `urlMatches`) üzerinde otomatik bekleme yapar.
3. **`SoftAssertions`:**
   - Test adımları boyunca çökmeyen, ThreadLocal izoleli yumuşak doğrulama toplayıcısı.
4. **Semantik AI Doğrulamaları (`satisfiesAi` / `violatesAi`):**
   - Kırılgan CSS/metin kontrolleri yerine LLM tabanlı doğal dil doğrulaması.

---

## İlgili Bağlantılar
- WebUI Mimarisi: `[[wiki/webui-testing]]`
- Temel Mimari: `[[wiki/architecture]]`
- Ana Harita: `[[MAP]]`
- Wiki Dizin: `[[wiki/index]]`
