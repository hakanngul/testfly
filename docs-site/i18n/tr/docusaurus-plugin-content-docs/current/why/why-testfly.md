---
description: "Neden TestFly: Selenium'ın Spring Boot'u — sıfır kurulum, Playwright tarzı locator'lar ve otomatik bekleme, Selenium'u gizlemeden kurumsal özellikler. API'dan önce felsefe."
id: why-testfly
title: Neden TestFly?
sidebar_label: Neden TestFly?
sidebar_position: 1
---

# Neden TestFly?

Geliştiriciler bir API'yi benimsemeden önce bir felsefeyi benimser. Bu sayfa o felsefedir.

> TestFly, Selenium'ın Spring Boot'udur — sıfır kurulum, daha akıllı varsayılanlar, Playwright esintili API'ler ve Selenium'u gizlemeden kurumsal özellikler.

Selenium güçlüdür ve her yerdedir, ancak bilerek düşük seviyelidir: size bir `WebDriver` verir ve yolunuzdan çekilir. Gerisi — driver yaşam döngüsü, bekleme, retry, raporlama, CI entegrasyonu — size kalır. Her takım aynı iskeleti yeniden inşa eder. TestFly, bu iskeletin **bir kez, doğru yapılmış** halidir.

---

## Üç katmanlı fikir

TestFly fikir sahibidir ama kafes değildir. Tasarım katmanlıdır, eşit parçalar değil:

1. **Fikir sahibi çekirdek (birincil).** Yapılandırma yerine convention, varsayılan olarak sıfır boilerplate. Tek bir bağımlılık ekleyin, `BaseTest` / `BasePage` extend edin; framework zaten akıllıca kararlar vermiştir — driver yaşam döngüsü, bekleme, retry, raporlama, CI entegrasyonu. `testfly.yml` isteğe bağlıdır; hiç yazmasanız bile makul varsayılanlar sizi korur.
2. **Selenium'u asla gizlemez (kısıt).** Daha ağır abstraction'ların aksine, TestFly sizden asla ham `WebDriver`'ı almaz. Convention'lar uymadığında doğrudan `WebDriver` / `By` / `WebElement` seviyesine inebilirsiniz. Kafes olmadan fikir sahibi.
3. **Genişletilebilir araç takımı (kaçış kapısı).** SPI/registry plugin sistemi, güç kullanıcıları için modülerdir — özel driver'lar, rapor adaptörleri, lifecycle hook'ları. Çoğu kullanıcı buna hiç dokunmaz.

---

## Tek bağımlılıkla neler kazanırsınız

Önce sonuçlar — sonrasında bunları sağlayan API:

| Sonuç | Nasıl |
|---|---|
| **Bir daha asla `Thread.sleep()` yazmayın** | Otomatik bekleyen locator'lar + [`WaitEngine`](/docs/guides/wait-engine) |
| **Testleriniz CSS refactor'lerinden sağ çıksın** | [Erişilebilirlik-öncelikli locator'lar](/docs/guides/semantic-locators) — `getByRole`, `getByLabel`, `getByText` |
| **Flaky test'ler otomatik iyileşsin** | [`@Retryable`](/docs/guides/retry) + tek config satırı — `IRetryAnalyzer` tesisatı yok |
| **Bir test neden başarısız oldu tam olarak görün** | Başarısızlık anında otomatik ekran görüntüsü, [HTML raporuna](/docs/reporting/html-report) gömülü |
| **WebDriver binary'lerini yönetmeyin** | Selenium Manager driver'ları otomatik çözer |
| **Paralel çalıştırma kutudan çıkar** | ThreadLocal driver izolasyonu — [paralel çalıştırma](/docs/guides/parallel) |
| **Forklamadan genişletin** | Driver, rapor ve hook'lar için Java SPI plugin'leri |

---

## Kimler için

- **Selenium'a zaten yatırım yapmış takımlar**, Playwright tarzı bir ergonomiyi Selenium / Java / TestNG yığınını, ekibin yetkinliklerini veya Selenium Grid'ini terk etmeden isteyenler.
- **Kendi framework'ünü sürdüren takımlar** — bir `BaseTest`, bir `DriverFactory`, bir yığın wait utility'si — o tesisatı debug etmektense silmeyi tercih edenler.

Bu takımların gerçekten sorduğu iki "neden" sorusunun her biri kendi sayfasına sahip:

- [Neden düz Selenium değil?](/docs/why/why-not-plain-selenium) — boilerplate'ın gerçek maliyeti
- [Neden Playwright değil?](/docs/why/why-not-playwright) — dürüst bir karşılaştırma (onun yerini aldığımızı iddia etmiyoruz)

---

## Sonraki adımlar

- [Hızlı Başlangıç](/docs/getting-started) — 5 dakikada ilk test
- [BaseTest](/docs/guides/base-test) / [BasePage](/docs/guides/base-page) — extend ettiğiniz temel class'lar
- [Yapılandırma Referansı](/docs/configuration) — tam `testfly.yml` referansı
