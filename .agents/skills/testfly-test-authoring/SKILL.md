---
name: testfly-test-authoring
description: >
  TestFly framework'ünde test yazma (test authoring) rehberi ve stili.
  Tüketici (consumer) projelerinde TestNG, JUnit 5, Cucumber runner'larını kullanarak
  yeni WebUI, API ve load test (yük testi) yazarken, Page Object Model (POM), locator,
  bekleme (wait) kurallarına ve framework yeteneklerine (API test, LoadScenario) erişim
  için bu kuralları uygula.
---

# TestFly ile Test Yazma (Consumer-Side Test Authoring)

Bu doküman, TestFly framework'ünü kullanan bir tüketici (consumer) projesinde **test yazarken** uyulması gereken kuralları, locator stratejilerini ve bekleme kurallarını tanımlar.
Framework mimarisi veya dahili geliştirme standartları için `[[testfly]]` skill'ine bakınız.

## 1. Karar Ağacı: Hangi Base Sınıf / Runner Kullanılmalı?

TestFly'da testlerinizin ihtiyacına göre doğru taban sınıfı seçmelisiniz:

- **Web UI Testleri (TestNG):** `BaseTest` (Tarayıcı otomatik başlar, kapatılır).
- **Sadece API Testleri (TestNG):** `BaseApiTest` (Tarayıcı açılmaz).
- **Yük/Performans Testleri (TestNG):** `BaseLoadTest` (Gatling/JDK engine çalıştırır, UI tetiklemez).
- **JUnit 5 Testleri:** `BaseJUnit5Test` / `BaseJUnit5ApiTest` (TestNG yerine JUnit 5 kullanılıyorsa).
- **Cucumber (BDD):** `BaseCucumberTest` runner sınıfı ve `BaseCucumberSteps` adım sınıfları.

## 2. Evrensel Kurallar (Universal Rules)

- **Statik State Yasaktır:** Hiçbir zaman `static WebDriver` veya statik test verisi tutmayın. Çoklu iş parçacığında (parallel execution) testler patlar.
- **Konfigürasyon:** Uygulama URL'i, timeout'lar ve ortam değişkenleri (sırlar dahil) `testfly.yml` içinde tutulur. Şifreler `${DB_PASS}` gibi environment placeholder'ları ile yönetilir.
- **Bir Test = Bir Davranış:** Mümkün olduğunca AAA (Arrange-Act-Assert) pattern'ine uygun, bağımsız testler yazın. Bir test diğerinin bıraktığı dataya güvenmemelidir.
- **Doğrulamalar Testte, Aksiyonlar Sayfada:** Page Object sınıfları (BasePage) içinde `assert` **asla yazılmaz**. Assertion sadece test metodunda olur.

## 3. Locator Önceliği ve Seçimi (Accessibility First)

Aşağıdaki sıraya göre locator seçilmelidir. İç içe (nested) veya DOM hiyerarşisine çok bağımlı CSS/XPath son çaredir.

1. **`getByRole(Role.BUTTON).withName("Submit")`:** En iyi yöntem (Erişilebilirlik odaklı).
2. **`getByLabel("Email Address")`:** Form elemanları için.
3. **`getByPlaceholder("Search...")`** veya **`getByText("Log in")`:** Görsel metne dayalı.
4. **`getByTestId("submit-btn")`:** `data-testid` gibi test attributeları varsa.
5. **CSS Sınıfları/ID:** `find(By.cssSelector(".btn-primary"))`.
6. **XPath:** Sadece yukarıdakilerle çözülemiyorsa (örneğin ebeveyn bulmak gerekiyorsa). Gerekçeli kullanılmalıdır.

## 4. Bekleme (Wait) Kuralları

- **`Thread.sleep()` KESİNLİKLE YASAKTIR.**
- **Auto-wait:** `click()`, `type()` gibi aksiyonlar otomatik olarak görünür ve tıklanabilir olmayı bekler. Ekstra bekleme yazmanıza gerek yoktur.
- **Açık Bekleme (Explicit):** DOM'da asenkron bir değişimi beklemeniz gerekiyorsa, `WaitEngine` kullanın (ör. `getWait().waitForVisible(locator)` veya `getWait().waitForInvisible(locator)`).
- **Web-First Assertions:** Durumu doğrulamak için (Polling).
  - `assertThat(buttonLocator).isVisible()` DOM'da görünene kadar bekler.
  - `assertTrue(driver.findElement(By.id("msg")).isDisplayed())` KULLANMAYIN (anlık (flaky) kontroldür).

## 5. İsimlendirme ve Tüketici Proje Dizilimi

- **Dizin Yapısı:**
  ```
  src/test/java/com/acme/
  ├── pages/        # Sayfa Nesneleri (extends BasePage)
  ├── tests/        # Test Senaryoları (extends BaseTest / BaseApiTest vb.)
  ├── data/         # Test veri modelleri, POJO'lar
  └── config/       # Projeye özel sabitler
  ```
- **İsimlendirme:** Test metotları neyin test edildiğini açıkça belirtmelidir (örneğin `shouldLoginWithValidCredentials`).

## 6. Anti-Pattern Tablosu

| Kötü (Anti-Pattern) | İyi (Best Practice) | Neden? |
|----------------------|----------------------|---------|
| `Thread.sleep(5000)` | `assertThat(loc).isVisible()` | Testi gereksiz uzatır, flaky (kırılgan) yapar. |
| Test içinde `new ChromeDriver()` | `extends BaseTest` | Framework'ün lifecycle (video, rapor) kancalarını bozar. |
| XPath: `//div/div/ul/li[2]/a` | `getByRole(Role.LINK).withName("Login")` | DOM değiştiğinde kırılır. |
| `assertEquals(text, "OK")` UI'da | `assertThat(loc).hasText("OK")` | İlki anlık çeker, ikincisi DOM'u bekler. |
| Page class'ında `Assert.assertTrue` | Metot boolean veya data döner, testte assert edilir | Sorumluluk ayrımı (Separation of Concerns). |

## 7. PR Öncesi Kontrol Listesi (Checklist)

- [ ] Hiçbir yerde `Thread.sleep` kullanılmamış.
- [ ] UI testleri `BaseTest` (veya varyantlarından), API testleri `BaseApiTest`'ten türüyor.
- [ ] Locatörler olabildiğince erişilebilirlik (role/label) tabanlı yazılmış.
- [ ] Ortam sırları koda gömülmemiş, `testfly.yml` içindeki `env` değişkenleriyle yönetilmiş.
- [ ] Testler birbirine bağımlı değil.

## Referanslar

- [[.agents/skills/testfly-test-authoring/references/webui.md|WebUI Testleri (POM, Locator, Assertion)]]
- [[.agents/skills/testfly-test-authoring/references/api.md|API Testleri (ApiClient, ApiResponse)]]
- [[.agents/skills/testfly-test-authoring/references/testng.md|TestNG Kullanımı (Retry, Data, Suite)]]
- [[.agents/skills/testfly-test-authoring/references/junit5.md|JUnit 5 Kullanımı]]
- [[.agents/skills/testfly-test-authoring/references/cucumber.md|Cucumber (BDD) Kullanımı]]
- [[.agents/skills/testfly-test-authoring/references/loadtest.md|Yük Testleri (LoadTest)]]
