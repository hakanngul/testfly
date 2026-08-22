---
description: "Playwright'dan TestFly'a geçiş: bir değiştirme değil, bir köprü. Tanıdık olanlar — getByRole/getByLabel locator'ları, otomatik bekleme, web-first assertThat — ve gerçekten farklı olanlar: mimari, dil/çalışma zamanı ve Selenium Grid."
id: coming-from-playwright
title: Playwright'dan Geçiş
sidebar_label: Playwright'dan Geçiş
sidebar_position: 3
---

# Playwright'dan Geçiş

Bu bir **köprü, geçiş rehberi değil**. TestFly, Playwright'ı değiştirmeye **çalışmaz** — Playwright, gerçekten farklı bir mimariye sahip mükemmel bir araçtır ve ekibinize iyi hizmet ediyorsa kullanmaya devam edin.

Bu sayfa farklı bir durum için: ekibiniz **Selenium / Java / JVM** dünyasında yaşıyor — mevcut suite'ler, Selenium Grid, TestNG, şirket içi altyapı — ama Playwright'ın ergonomisine hayranlık duyuyor ve bunları *o ekosistemden ayrılmadan* istiyorsunuz. TestFly, Playwright'ın en iyi fikirlerini Selenium'a getirir. İşte size tanıdık gelecekler ve dürüstçe farklı olanlar.

:::info Tek cümlelik çerçeve
TestFly, Selenium'un Spring Boot'udur — sıfır kurulum, daha akıllı varsayılanlar, Playwright'tan ilham alan API'ler ve Selenium'u gizlemeden kurumsal özellikler. Playwright'tan fikirler ödünç alır; Playwright'ı yeniden uygulamaz.
:::

---

## Tanıdık olanlar

Playwright'ın API'sini biliyorsanız, TestFly'ın büyük bir kısmı neredeyse aynı okunur.

### Erişilebilirlik öncelikli locator'lar

Playwright'ın `getByRole` / `getByLabel` felsefesi — kırılgan CSS yerine erişilebilirlik ağacını hedeflemek — TestFly'da birinci sınıf bir özelliktir.

```java
// Playwright gibi hissettirir:
getByRole(Role.BUTTON).withName("Submit").click();
getByLabel("Email address").type("a@b.com");
getByPlaceholder("Search…").type("boots");
getByText("Forgot password?").click();
getByTestId("checkout-cta").click();
```

| Playwright (JS/TS) | TestFly (Java) |
|---|---|
| `getByRole('button', { name: 'Submit' })` | `getByRole(Role.BUTTON).withName("Submit")` |
| `getByLabel('Email address')` | `getByLabel("Email address")` |
| `getByText('Forgot password?')` | `getByText("Forgot password?")` |
| `getByPlaceholder('Search…')` | `getByPlaceholder("Search…")` |
| `getByTestId('checkout-cta')` | `getByTestId("checkout-cta")` |
| `getByAltText('Logo')` | `getByAltText("Logo")` |
| `getByTitle('Close')` | `getByTitle("Close")` |

Bkz. [Erişilebilirlik Öncelikli Locator'lar](/docs/guides/semantic-locators).

### Otomatik bekleme

Playwright gibi, eylemler etkileşimden önce öğenin etkileşime hazır olmasını bekler — `Thread.sleep()` yok, elle oluşturulmuş bekleme yok.

```java
find("#login").click();   // öğenin tıklanabilir olması için otomatik bekler
```

Açık bir koşula ihtiyaç duyduğunuzda, [`WaitEngine`](/docs/guides/wait-engine) size akıcı ve önceden yapılandırılmış bir bekleme sunar — Playwright'ın `expect(...).toBeVisible()` beklemesinin Selenium karşılığı.

### Web-first doğrulamalar

Playwright'ın otomatik yeniden deneyen `expect()` işlevinin doğrudan karşılığı vardır: `assertThat(...)`, koşul doğru olana veya zaman aşımı geçene kadar yeniden dener.

```java
assertThat(getByRole(Role.HEADING)).hasText("Welcome back");
assertThat(find(".cart-count")).hasText("3");
```

### Ayarlardan çok kural (Convention over configuration)

Her iki araç da törenden çok mantıklı varsayılanları tercih eder. TestFly'da bir bağımlılık eklersiniz, `BaseTest`'i genişletirsiniz ve driver yaşam döngüsü, bekleme, yeniden deneme ve raporlama zaten bağlanmıştır — `testfly.yml` opsiyoneldir.

---

## Gerçekten farklı olanlar

Dürüstlüğün önemli olduğu yer burası. Bunlar pazarlama değil, mimari gerçeklerdir — TestFly'ın bir Playwright klonu değil, bir *köprü* olmasının nedenleridir.

### Mimari

- **Playwright**, tarayıcıları kendi protokolü üzerinden kalıcı bir bağlantıyla yönlendirir; hızlı, izole **tarayıcı bağlamları** ve ağ müdahalesi ile otomatik yönetilen tarayıcı ikili dosyaları gibi özellikler bu modele gömülüdür.
- **TestFly**, **Selenium WebDriver** ve **W3C WebDriver protokolü** üzerine kuruludur. Tüm Selenium ekosistemini elde edersiniz — gerçek `WebDriver`, `By`, `WebElement` ve istediğiniz zaman ham Selenium'a inmek için bir kaçış kapağı — ancak Playwright'ın kontrol modelini değil, WebDriver'ın modelini miras alırsınız. Bazı Playwright kolaylıkları (ör. bağlam modeli, yerleşik izleme) birebir eşlenmez.

### Dil ve çalışma zamanı

- **Playwright** çok dillidir (JS/TS, Python, .NET, Java) ve indirip yönettiği kendi yamalı tarayıcı derlemelerini sunar.
- **TestFly** **yalnızca JVM'dir** — Java, TestNG ile (ve opsiyonel bir JUnit 5 köprüsü). Selenium Manager aracılığıyla **normalde kurulu tarayıcılarınızı** (Chrome/Firefox/Edge) çalıştırır. Mesele şu: yeni bir çalışma zamanı tanıtmak yerine bir Java ekibinin mevcut yapısına uyar.

### Grid ve ölçeklendirme

- **Playwright**, kendi worker/sharding modeliyle paralelleştirir.
- **TestFly**, **Selenium Grid** ve TestNG paralelliliğini kullanır. Kuruluşunuz zaten Grid (veya bulut tabanlı bir Selenium sağlayıcısı) çalıştırıyor varsa, TestFly doğrudan buna oturur — [paralel çalıştırma](/docs/guides/parallel) ve [bulutta çalıştırma](/docs/cloud-execution) birinci sınıftır. Selenium altyapınız yoksa ve buna gerek de yoksa, bu Playwright lehine bir noktadır, bizim lehimize değil.

---

## Hangisini ne zaman kullanmalı

| Şunu seçin… | Eğer… |
|---|---|
| **Playwright** | Yeşil alandaysanız ve Selenium yatırımınız yoksa, bağlam modelini / izlemeyi istiyorsanız ya da ekibiniz Node/Python ve onun çalışma zamanından memnunsa. |
| **TestFly** | Ekibiniz **Selenium / Java / TestNG** üzerindeyse, **Selenium Grid** çalıştırıyor (veya istiyorsa) ve bu yapıyı terk etmeden Playwright tarzı ergonomi istiyorsa. |

Her ikisi de meşrudur. TestFly, "Selenium ekibiyiz" ifadesinin artık "erişilebilirlik öncelikli locator'larımız, otomatik beklememiz ve web-first doğrulamalarımız olamaz" anlamına gelmemesi için vardır.

---

## Sonraki adımlar

- [Erişilebilirlik Öncelikli Locator'lar](/docs/guides/semantic-locators) — `getByRole`/`getByLabel` ailesi
- [WaitEngine](/docs/guides/wait-engine) — `Thread.sleep()` olmadan açık bekleme
- [Başlarken](/docs/getting-started) — 5 dakikada ilk test
- [Selenium + TestNG'den Geçiş](/docs/migration/from-selenium-testng) — mevcut bir Selenium suite'ini birleştiriyorsanız