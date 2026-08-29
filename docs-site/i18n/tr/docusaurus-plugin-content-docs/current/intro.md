---
description: "TestFly, web, API, mobil ve AI destekli test otomasyonu için bir Java Test Otomasyon SDK'sıdır — sıfır kurulum, Playwright tarzı locator'lar ve otomatik bekleme, kurumsal özellikler ve Selenium'u gizlemeden."
id: intro
title: Giriş
sidebar_position: 1
slug: /
---

# TestFly

**Fikir sahibi, Spring Boot'tan esinlenilmiş bir Java test otomasyon çerçevesi.**

[![Maven Central](https://img.shields.io/maven-central/v/io.testfly/testfly)](https://central.sonatype.com/artifact/io.github.hakanngul/testfly)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://github.com/hakanngul/testfly/blob/master/LICENSE)

---

## TestFly Nedir?

TestFly, her Java Selenium projesinin tekrarladığı tekrarlayan kodları ortadan kaldırır — WebDriver kurulumu ve kapatma, bekleme yardımcıları, retry mantığı, ekran görüntüsü yakalama ve rapor üretimi — böylece test kodunuzda yalnızca test amacı kalır.

**Spring Boot felsefesinden** esinlenilmiştir: akıllı varsayılanlar, yapılandırmaya üstün gelen convention ve yaygın durumlar için sıfır kurulum.

```java
public class LoginTest extends BaseTest {

    @Test(description = "Geçerli kullanıcı giriş yapabilir")
    public void loginTest() {
        open();
        new LoginPage(getDriver()).login("admin", "secret");
        Assert.assertTrue(new DashboardPage(getDriver()).isLoaded());
    }
}
```

`WebDriver` kurulumu yok. `@AfterMethod` kapatma yok. Bekleme yardımcısı yok. Retry yapılandırması yok.
**Sadece test.**

---

## Tasarım Felsefesi

İnsanlar sıkça TestFly'nin fikir sahibi bir çerçeve mi, genişletilebilir bir araç seti mi, yoksa Selenium üzerine ince bir verimlilik katmanı mı olduğunu sorar. Cevap **Java test otomasyonunun Spring Boot'u** — ve bu cevap katmanlıdır, üçünün eşit karışımı değil:

1. **Fikir sahibi çekirdek (birincil).** Yapılandırmaya üstün gelen convention, varsayılan olarak sıfır tekrarlayan kod. Bir bağımlılık ekleyin, `BaseTest` / `BasePage` extend edin; framework sizin için akıllıca kararları çoktan vermiştir. `testfly.yml` isteğe bağlıdır — yazmasanız bile `TestFlyDefaults` sizi korur.
2. **Selenium'u asla gizlemez (kısıt).** Daha ağır soyutlamaların aksine, TestFly size ham `WebDriver`'ı asla elinizden almaz. Convention'lar uymadığında doğrudan `WebDriver` / `By` / `WebElement` seviyesine inebilirsiniz. Fikir sahibi ama kafes değil.
3. **Genişletilebilir araç seti (çıkış kapısı).** Bir SPI/registry plugin sistemi (`DriverProviderRegistry`, `PluginRegistry`, `ReportAdapterRegistry`) güç kullanıcıları için modülerlik sağlar — fikir sahibi çekirdeğe hizmet eder, onun yerini almaz. Çoğu kullanıcı bunu hiç dokunmaz.

### Selenium'a zaten yatırım yaptınız mı?

Playwright'ın ergonomisini sevmek için Selenium'dan vazgeçmeniz gerekmez. TestFly bu fikirleri Selenium ekosistemine getirir — böylece stack'inizi, grid'inizi ve ekibinizin yeteneklerini korursunuz:

| Playwright fikri | TestFly'da |
|---|---|
| Erişilebilirlik-öncelikli locator'lar | `getByRole`, `getByLabel`, `getByText`, `getByPlaceholder`, `getByTestId` — erişilebilirlik ağacını hedefler, CSS/DOM refactor'lerine dayanır |
| Otomatik bekleme | `WaitEngine` destekli aksiyonlar — `Thread.sleep()` ortadan kalkar |
| Web-öncelikli assertion'lar | doğru olana kadar otomatik yeniden deneyen `assertThat(...)` |
| Yapılandırmaya üstün gelen convention | Sıfır-boilerplate varsayılanlar, isteğe bağlı `testfly.yml` |

…tüm bunlar **ham Selenium'u gizlemeden**, mevcut Selenium / Java / TestNG stack'inizi, ekip yeteneklerinizi ve Selenium Grid'inizi koruyarak.

### Neden kendi çerçeveminizi inşa etmiyorsunuz?

Neredeyse her Java ekibinin bir tanesi vardır: ev yapımı bir `BaseTest`, bir `DriverFactory`, bir yığın bekleme utility'si ve bir raporlama çözümü — her yeni proje veya şirkette sıfırdan yeniden yazılır. Bu, sahip olduğunuz, hata ayıkladığınız ve sonsuza dek bakımını yaptığınız ücretsiz bir altyapıdır; nadiren test edilir veya paralel-güvenlidir.

TestFly **tam olarak bu çerçevedir** — çoktan inşa edilmiş, bakılan, test edilmiş, thread-safe ve dokümante edilmiş. Asıl size ait olan kısmı (test amacı) korur, altyapıyı siler:

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
