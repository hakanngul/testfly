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
- **Konfigürasyondan Önce Sözleşme:** Asgari YAML (`testfly.yml`) ile üretime hazır çalışma.
- **Ham Selenium'u Gizlememe:** Kullanıcı istediğinde `WebDriver`, `By` ve `WebElement` API'lerine doğrudan erişebilir.
- **ThreadLocal İzolasyonu:** Paralel test koşumlarında oturum çakışmasını engelleyen güvenli izole yapı.

## 2. Temel Katmanlar
1. **Sürücü Katmanı (`driver`):** `DriverManager`, yerel ve bulut (BrowserStack, Sauce Labs) sağlayıcıları.
2. **Bekleme Motoru (`wait`):** `WaitEngine` ile merkezi akıllı açık beklemeler (Explicit Waits). Asla `Thread.sleep()` kullanılmaz.
3. **Konumlandırıcılar (`locator`):** Rol, etiket ve erişilebilirlik odaklı fluent `Locator` API'si.
4. **Raporlama Katmanı (`reporting`):** HTML raporlama, JUnit XML ve başarısızlık anında ekran görüntüleri.

---

## İlgili Bağlantılar
- Ana Dizin: `[[wiki/index]]`
- Harita: `[[MAP]]`
- WebDriver Yaşam Döngüsü: `[[wiki/webdriver-lifecycle]]`
- Yapılandırma: `[[wiki/configuration]]`
