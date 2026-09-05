---
description: "TestFly, web, API, mobil ve AI destekli test otomasyonu için bir Java Test Otomasyon SDK'sıdır — sıfır kurulum, Playwright tarzı locator'lar ve otomatik bekleme, kurumsal özellikler ve Selenium'u gizlemeden."
id: intro
title: Giriş
sidebar_position: 1
slug: /
---

# TestFly

**Spring Boot felsefesinde, sıfır boilerplate ile tasarlanmış modern Java test otomasyon platformu.**

[![Maven Central](https://img.shields.io/maven-central/v/io.testfly/testfly)](https://central.sonatype.com/artifact/io.github.hakanngul/testfly)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://github.com/hakanngul/testfly/blob/master/LICENSE)

---

## TestFly Nedir?

TestFly, her Java test otomasyon projesinde tekrar tekrar yazılan altyapı kodlarını ortadan kaldırır — WebDriver kurulumu ve kapatılması, bekleme yardımcıları, retry mekanizması, ekran görüntüsü yakalama, raporlama ve paralel çalıştırma — böylece test sınıflarınızda yalnızca test senaryonuzun gerçek niyeti kalır.

**Spring Boot felsefesinden** esinlenilmiştir: Akıllı varsayılanlar, yapılandırma yerine uzlaşı (convention over configuration) ve yaygın senaryolar için sıfır ön hazırlık.

```java
public class LoginTest extends BaseTest {

    @Test(description = "Geçerli kullanıcı giriş yapabilir")
    public void loginTest() {
        open();
        new LoginPage(getDriver()).login("admin", "secret");
        softAssert().that(new DashboardPage(getDriver()).isLoaded(), "Dashboard should be loaded");
    }
}
```

`WebDriver` kurulum kodu yok. `@AfterMethod` kapatma kargaşası yok. Manuel bekleme yardımcıları yok. Retry yapılandırması yok.
**Sadece iş mantığı ve test.**

---

## Tasarım Felsefesi

TestFly; kuralcı bir çatı mı, genişletilebilir bir araç seti mi, yoksa Selenium üzerine kurulu modern bir verimlilik katmanı mı? Cevap: **Java test otomasyonunun Spring Boot'u** — akıllı varsayılanlarla başlar, ihtiyaç duyduğunuzda derinlemesine özelleştirilebilir:

1. **Akıllı ve Kuralcı Çekirdek (Convention over Configuration).** Sıfır altyapı kodu. Tek bir bağımlılık ekleyin, `BaseTest`, `BaseJUnit5Test` veya `BaseCucumberSteps` extend edin; framework sizin adınıza en iyi mimari kararları otomatik olarak uygular. `testfly.yml` isteğe bağlıdır — hiçbir şey yazmasanız bile `TestFlyDefaults` devreye girer.
2. **Selenium'u Asla Gizlemez (Esneklik).** TestFly ham `WebDriver` erişimini asla kısıtlamaz. İhtiyaç duyduğunuz anda `getDriver()` ile doğrudan `WebDriver` / `By` / `WebElement` seviyesine inebilirsiniz. Standartları belirler ama sizi bir kafese hapsetmez.
3. **Genişletilebilir Ekosistem (SPI Desteği).** Java SPI tabanlı registry mimarisi (`DriverProviderRegistry`, `PluginRegistry`, `ReportAdapterRegistry`) ile özel driver'lar, rapor adaptörleri ve yaşam döngüsü hook'ları ekleyebilirsiniz.

### Selenium Altyapınızı Korumaya Devam Edin

Playwright'ın akıcı ergonomisini kullanmak için mevcut Selenium ekosisteminizden, Selenium Grid'inizden veya kurumsal Java birikiminizden vazgeçmeniz gerekmez. TestFly bu modern yaklaşımı doğrudan Selenium dünyasına getirir:

| Playwright Ergonomisi | TestFly Karşılığı |
|---|---|
| Erişilebilirlik odaklı seçiciler | `getByRole`, `getByLabel`, `getByText`, `getByPlaceholder`, `getByTestId` — DOM refactor'lerine dirençli |
| Otomatik bekleme (Auto-waiting) | `WaitEngine` destekli aksiyonlar — `Thread.sleep()` tamamen tarih olur |
| Web-öncelikli doğrulamalar | Koşul sağlanana kadar otomatik yeniden deneyen `assertThat(...)` |
| Sıfır kurulum / Akıllı varsayılanlar | Sıfır-boilerplate defaults, isteğe bağlı `testfly.yml` |

…tüm bunlar **ham Selenium'u gizlemeden**, mevcut Selenium / Java / TestNG / JUnit 5 stack'inizi, ekip yeteneklerinizi ve test altyapınızı koruyarak çalışır.

### Neden Kendi Framework'ünüzü Sıfırdan Yazmamalısınız?

Neredeyse her kurumsal Java ekibinin ev yapımı bir `BaseTest`'i, bir `DriverFactory`'si, bir yığın flaky bekleme utility'si ve bakım gerektiren özel bir raporlama kodu vardır — ve her yeni projede bunlar sıfırdan kopyalanıp yapıştırılır. Zamanla bu kodlar test edilmemiş, paralel çalışmada çöken ve sürekli hata veren bir teknik borca dönüşür.

TestFly **bu tekerleği yeniden icat etme derdini bitirir** — endüstri standartlarında inşa edilmiş, thread-safe, paralel yürütmeye hazır, kapsamlı test edilmiş ve dokümante edilmiştir:

| Kendin yaz | TestFly |
|---|---|
| Driver lifecycle, bekleme, retry yaz ve bakımını yap | Sağlanır, thread-safe, sıfır config |
| Raporlama katmanını sıfırdan inşa et | HTML rapor + JUnit XML dahil |
| Her projeye özel CI bağlantısı kur | GitHub Actions / Jenkins / CircleCI'ı otomatik tanır |
| Onboarding = "iç wiki'mizi okuyun" | Onboarding = public dokümanlar + tek bağımlılık |
| Bug'ları siz düzeltirsiniz | Framework düzeltmeleriyle birlikte gelir |

> TestFly, Selenium'un Spring Boot'udur — sıfır kurulum, daha akıllı varsayılanlar, Playwright'tan esinlenmiş API'ler ve kurumsal özellikler, Selenium'u gizlemeden.

---

:::tip AI destekli test yazımı — Yakında
**TestFly MCP** — Claude veya GitHub Copilot'ın gerçek bir tarayıcıyı kontrol ederek oturumunuzu kaydedip hazır TestFly test kodu üretmesini sağlayan MCP sunucusu — **yakında** yayında olacak. Takipte kalın.
:::

---

## Kutudan çıkanlar

Önce sonuçlar — sağdaki API'ye doğrudan atlayabilirsiniz.

| Ne kazanırsınız | Nasıl |
|---|---|
| **Driver kurulumu/kapatması tekrar yazmayın** | Her test için bir driver, testten önce oluşturulur, sonra kapatılır |
| **Kodu dokunmadan ortam değiştirin** | YAML config — browser, parallel, timeout, retry tek dosyada |
| **`Thread.sleep()` tekrar yazmayın** | Otomatik bekleme destekli `WaitEngine`, 10+ hazır koşul |
| **Flaky test'ler build'ı bozmasın** | Global, metot başı `@Retryable`, veya Cucumber senaryo başı `@retryable` tag'i |
| **Test neden fail etti tam görün** | Fail anında otomatik ekran görüntüsü, raporda base64 gömülü |
| **Test'i spec gibi okuyun** | `StepLogger` adımları, isteğe bağlı adım başı ekran görüntüsü |
| **Paydaşların okuyacağı rapor verin** | Sekmeli HTML dashboard — özet, test case'ler, hatalar, flakiness radar |
| **Ekstra araç olmadan her CI'ya takılın** | JUnit XML Jenkins, GitHub Actions, GitLab CI tarafından doğal olarak parse edilir |
| **CI kendini yapılandırsın** | CI algılandığında headless zorlanır, thread sayısı otomatik ayarlanır |
| **Fork etmeden genişletin** | SPI tabanlı plugin'ler — özel driver, hook, rapor adapter'ı |
| **Kendi test runner'ınızı getirin** | `BaseJUnit5Test` veya `@ExtendWith(TestFlyExtension.class)` ile tam JUnit 5 parity |
| **Ürün ekibinin okuyabileceği spec yazın** | BDD / Cucumber — `BaseCucumberSteps`, `CucumberHooks`, raporda senaryo başı adımlar |
| **UI ve API'yi aynı suite'te test edin** | `BaseApiTest`, fluent `ApiClient`, JSONPath, schema validation, hibrit UI+API |
| **Elementi akıcıca sabitleyin** | `find("selector").filter().nth().withText()` — Playwright tarzı zincirlenebilir locator'lar |
| **Test'ler CSS/DOM refactor'lerinden sağ çıksın** | Erişilebilirlik-öncelikli locator'lar — `getByRole(Role.BUTTON).withName("Submit")`, `getByText`, `getByLabel`, `getByPlaceholder`, `getByTestId` |
| **Assertion'lar timing yüzünden flake olmasın** | Web-öncelikli `assertThat(By.id("x")).isVisible()` — timeout'a kadar otomatik retry |
| **Bir testte admin ve kullanıcı akışlarını test edin** | `withSession("admin", () -> { ... })` — bir testte iki browser |
| **DB'ye gerçekten ne düştüğünü doğrulayın** | `db().assertRowExists()`, `db().query().assertValue()` — plain JDBC, ORM yok |
| **Uygulamanın gönderdiği e-postayı doğrulayın** | `mailbox().waitForEmail(to("user@test.com"))` — Mailhog, Mailtrap, Outlook, IMAP |
| **UI gerektirmeyen testlerde browser'ı atlayın** | `@NoBrowser` — DB assertion, API kontrolleri, dosya işlemleri, WebDriver yok |
| **Gerçek cloud browser'larda değişiklik yapmadan çalıştırın** | `execution.mode: browserstack` / `saucelabs` — oturum URL'si HTML raporda |
| **Hataları kazmadan anlayın** | AI failure analysis — Claude test neden fail etti açıklar ve çözüm önerir |
| **Locator'lar kendi kendini onarsın** | Bir locator fail ettiğinde self-healing fallback stratejileri |
| **Hangi test'lerin gelecekte flaky olacağını bilin** | Flakiness prediction — geçmiş çalışmalardan risk skoru, raporda radar grafik |
| **Mevcut verilerinizden test sürdürün** | Harici test verisi — `@TestData("csv:...")`, `@TestData(value="excel:...", sheet="Login")`, `@TestData("db:SELECT...")` |
| **Zamana bağlı davranışı deterministik test edin** | Clock mocking — `clock().set("2030-01-01T00:00:00Z")`, JS `Date` override, otomatik reset |

---

## Felsefe

1. **Sıfır tekrarlayan kod** — kullanıcı bir şeyi etkinleştirmek için 1 satırdan fazla yazıyorsa, varsayılan olmalı
2. **Yapılandırmaya üstün gelen convention** — akıllı varsayılanlar, gelişmiş davranışlar için YAML opt-in
3. **Gerekli harici servis yok** — çevrimdışı çalışır, çekirdekte cloud API yok
4. **Opt-in karmaşıklık** — gelişmiş özellikler config flag'lerinin arkasında, varsayılan kapalı
5. **Tek bağımlılık** — `testfly` ekle, başka bir şey gerekmez
6. **Test kodu temiz kalsın** — lifecycle iç işler halleder; test metotlarında yalnızca amaç olur

---

## Sonraki Adımlar

- [Hızlı Başlangıç](/docs/getting-started) — 5 dakikadan kısa sürede kur ve ilk testini çalıştır
- [Yapılandırma Referansı](/docs/configuration) — tüm YAML config seçenekleri
- [Temel Rehberler](/docs/guides/base-test) — her özelliğin derinlemesine anlatımı
