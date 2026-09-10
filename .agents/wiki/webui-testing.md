---
tags:
  - wiki
  - webui-testing
  - dataflow
  - selenium
  - smartlocator
  - waitengine
date: 2026-09-10
status: active
type: wiki
---

# TestFly WebUI Test Mimarisi

TestFly, Selenium WebDriver üzerine inşa edilmiş, ThreadLocal izolasyonlu, otomatik beklemeli ve semantik konumlandırıcılı kurumsal bir WebUI otomasyon mimarisi sunar.

---

## 1. Temel İş Akışı ve Yaşam Döngüsü

1. **Konfigürasyon ve TestNG Başlatma (`BaseTest`):** Test metodu tetiklendiğinde `TestFlyConfig` (`testfly.yml`) okunarak tarayıcı türü, headless modu ve CDP yetenekleri yüklenir.
2. **İzole Sürücü Yönetimi (`DriverManager`):** Her test iş parçacığı (thread) için bağımsız bir `WebDriver` oturumu oluşturulur; paralel testlerde durum kirlenmesi engellenir.
3. **Sayfa Nesne Modeli (`BasePage`):** Ekranlar bileşen odaklı Page Object sınıfları halinde modellenir; WebDriver oturumu doğrudan sayfaya bağlanır.
4. **Semantik Konumlandırma (`SmartLocator`):** Erişilebilirlik öncelikli seçiciler (`getByRole`, `getByText`, `getByLabel`) ile DOM elemanları kırılgan CSS/XPath bağımlılıklarından kurtarılır.
5. **Dinamik Bekleme Motoru (`WaitEngine`):** `Thread.sleep()` yerine elemanın tıklanabilirliği, görünürlüğü ve DOM'a eklenmesi otomatik olarak beklenir; geçici hatalarda `@Retryable` devreye girer.
6. **Doğrulama & Kanıt Toplama:**
   - `SeleniumAssert`: Eleman metinleri, stiller ve görsel durumlar akıcı şekilde doğrulanır.
   - Hata anında `ScreenshotManager` otomatik tam sayfa ekran görüntüsü alır ve TestFly HTML raporuna iliştirir.

---

## 2. İnteraktif Veri Akışı Şeması (Archify Dataflow)

Archify ile derlenmiş bağımsız, karanlık/aydınlık tema ve trace animasyon destekli WebUI veri akışı şeması:
- [TestFly WebUI Test Dataflow Diagram (HTML)](file:///Users/hagul/Projects/TestFramework/testfly/docs-site/static/diagrams/testfly-webui-dataflow.html)

---

## İlgili Bağlantılar
- API Test Mimarisi: `[[wiki/api-testing]]`
- Hibrit Test: `[[wiki/api-webui-testing]]`
- WebDriver Yaşam Döngüsü: `[[wiki/webdriver-lifecycle]]`
- Temel Mimari: `[[wiki/architecture]]`
- Ana Harita: `[[MAP]]`
- Wiki Dizin: `[[wiki/index]]`
- Archify Becerisi: `[[skills/archify/SKILL]]`
