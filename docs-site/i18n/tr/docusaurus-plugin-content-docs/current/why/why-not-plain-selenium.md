---
description: "Neden sade Selenium değil? Ham Selenium tasarım gereği düşük seviyelidir — her ekip aynı driver fabrikasını, bekleme yardımcılarını, yeniden deneme analizörünü ve raporlamayı yeniden inşa eder. TestFly, Selenium'u gizlemeden, bakımı yapılan ve test edilen o framework'tür."
id: why-not-plain-selenium
title: Neden sade Selenium değil?
sidebar_label: Why not plain Selenium?
sidebar_position: 2
---

# Neden sade Selenium değil?

Selenium'da yanlış bir şey yok — TestFly **onun üzerine inşa edilmiştir** ve onu asla gizlemez. Soru "Selenium mu değil mi?" değil: *Selenium'un kasıtlı olarak dışarıda bıraktığı iskeleti kim yazar ve sürdürür?*

Ham Selenium size bir `WebDriver` uzatır ve orada durur. Bunu gerçek bir test paketine dönüştürmek, her ekibin yeniden inşa ettiği aynı parça setini inşa etmek — ve sonsuza dek sahiplenmek — anlamına gelir.

---

## Sade Selenium ile sahip olduğunuz boilerplate

Hemen hemen her Java Selenium projesi bunun kendi kopyasını büyütür:

- Paralel çalıştırmalar için `ThreadLocal<WebDriver>` el değiştirmeli bir **`DriverFactory`**
- Driver ikili dosya yönetimi (tarihsel olarak WebDriverManager)
- Her yere içe aktarılan `WebDriverWait` / `ExpectedConditions` çevresindeki bir **`WaitUtils`** sarmalayıcısı
- Takılma durumundaki testler için onu ekleyecek bir listener ile birlikte bir **`IRetryAnalyzer`**
- Başarısızlıkta ekran görüntüsü alan bir **`ITestListener`**
- Raporlama bağlantıları (ExtentReports / Allure) ve CI tutkalı

Bu ücretsiz bir altyapıdır: yazarsınız, hata ayıklarsınız ve bakımını yaparsınız — ve nadiren test edilir veya paralel-güvenlidir. **Her yeni projede veya şirkette sıfırdan yeniden yazılır.**

---

## TestFly neyi değiştirir

TestFly, **zaten inşa edilmiş, sürdürülmüş, test edilmiş, thread-güvenli ve belgelenmiş** o framework'tür. Gerçekte sizin olan kısmı (test niyeti) tutar ve altyapıyı silersiniz:

| Sade Selenium (siz inşa eder & sürdürürsünüz) | TestFly |
|---|---|
| `DriverFactory` + `ThreadLocal<WebDriver>` | `BaseTest`'i genişletin — yaşam döngüsü yönetilir, paralel-güvenli |
| WebDriverManager / driver ikilileri | Selenium Manager, otomatik |
| `WaitUtils` / `WebDriverWait` yardımcıları | Otomatik bekleme yapan locator'lar + [`WaitEngine`](/docs/guides/wait-engine) |
| Kırılgan CSS/XPath seçicileri | [Erişilebilirlik öncelikli locator'lar](/docs/guides/semantic-locators) |
| `IRetryAnalyzer` + listener | [`@Retryable`](/docs/guides/retry) + tek bir yapılandırma satırı |
| Başarısızlıkta ekran görüntüsü listener'ı | Başarısızlıkta otomatik |
| ExtentReports/Allure bağlantıları | Dahil edilen [HTML raporu](/docs/reporting/html-report) + JUnit XML |
| Proje başına özel CI bağlantıları | GitHub Actions / Jenkins / CircleCI'yi otomatik algılar |
| Hataları siz düzeltirsiniz | Framework düzeltmeleri sunar |

---

## …Selenium'u gizlemeden

Çoğu "üretkenlik katmanının" sorunu, `WebDriver`'ı elinizden almaları ve soyutlama sızdığında sizi tuzağa düşürmeleridir. TestFly bunu yapmaz. Gelenekler uymadığında `getDriver()` size ham `WebDriver`'ı verir ve doğrudan `By` / `WebElement`'e inersiniz. Görüşlü varsayılanlar, tam bir kaçış kapağı.

Selenium'un kendisi olduğu için benimseme aşamalıdır — tek bir test sınıfını `BaseTest`'e işaret edebilirsiniz ve yarı taşınmış bir paket yine de çalışır.

---

## Sonraki adımlar

- [Selenium + TestNG'den taşının](/docs/migration/from-selenium-testng) — yan yana "altyapıyı silme" rehberi
- [Neden TestFly?](/docs/why/why-testfly) — genel felsefe
- [Hızlı Başlangıç](/docs/getting-started) — 5 dakikada ilk test