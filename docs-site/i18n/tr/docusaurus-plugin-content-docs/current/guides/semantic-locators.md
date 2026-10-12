---
description: "Erişilebilirlik öncelikli Selenium locator'ları: Playwright tarzı getByRole, getByText ve getByLabel, erişilebilirlik ağacını hedefler ve CSS refactor'lerine dayanır."
id: semantic-locators
title: Erişilebilirlik Öncelikli Locator'lar
sidebar_position: 10
---

# Erişilebilirlik Öncelikli Locator'lar

TestFly, kırılgan CSS sınıfları veya DOM yapısı yerine **erişilebilirlik ağacını** — kullanıcının gerçekte algıladığı şeyi — hedefleyen Playwright tarzı **semantik locator'lar** sunar. Sonuç: sayfa gibi okunan ve yeniden tasarımlara dayanan locator'lar.

```java
getByRole(Role.BUTTON).withName("Submit").click();
getByLabel("Email address").type("a@b.com");
getByPlaceholder("Search…").type("boots");
getByText("Forgot password?").click();
getByTestId("checkout-cta").click();
```

Her semantik locator, `$()` API tarafından kullanılan aynı zincirlenebilir, **otomatik bekleyen** `Locator`'ı döndürür — `Thread.sleep` yok, açık bekleme yok. Hem `BaseTest` hem de `BasePage` üzerinde kullanılabilirler.

---

## Neden semantik locator'lar?

```java
// Kırılgan — işaretleme yeniden düzenlendiği anda bozulur:
find(By.cssSelector("div.modal > form button.btn-primary")).click();

// Dayanıklı — kullanıcının gördüğü rolü + erişilebilir adı hedefler:
getByRole(Role.BUTTON).withName("Submit").click();
```

---

## Locator'lar

| Metod | Eşleşir |
|---|---|
| `getByRole(Role)` | ARIA rolüne göre öğeler (örtük HTML öğesi **veya** açık `role="…"`) |
| `getByText(String)` | Görünür metne göre öğeler |
| `getByLabel(String)` | İlişkilendirilmiş `<label>` metnine göre form kontrolleri |
| `getByPlaceholder(String)` | `placeholder` niteliğine göre öğeler |
| `getByTestId(String)` | test-id niteliğine göre öğeler (varsayılan `data-testid`) |
| `getByAltText(String)` | `alt` metnine göre öğeler (tipik olarak `<img>`) |
| `getByTitle(String)` | `title` niteliğine göre öğeler |

---

## `getByRole`

`Role`, 36 WAI-ARIA rolünü kapsar. Her biri hem rolü örtük olarak taşıyan yerel HTML öğeleriyle hem de açık bir `role` niteliği taşıyan herhangi bir öğeyle eşleşir — örn. `Role.BUTTON`, `<button>`, `<input type="submit">`, `<summary>` ve `[role="button"]` ile eşleşir.

```java
getByRole(Role.BUTTON).withName("Save").click();   // erişilebilir ad eşleşmesi
getByRole(Role.LINK, "Docs").click();              // tek çağrıda rol + ad
getByRole(Role.HEADING).withLevel(1).getText();    // başlık seviyesi
```

`.withName(...)` öğenin **erişilebilir adını** eşleştirir; ARIA önceliğine göre hesaplanır: `aria-label` → `aria-labelledby` → ilişkili `<label>` → metin içeriği → `value` / `alt` / `title`.

---

## Tam vs. alt dize

Metin, ad ve nitelik eşleştirmesi varsayılan olarak **büyük/küçük harfe duyarsız alt dize** biçimindedir. Büyük/küçük harfe duyarlı tam eşleşme için `.exact()` çağırın:

```java
getByText("submit");          // "Submit", "SUBMIT ORDER", … ile eşleşir
getByText("Submit").exact();  // yalnızca "Submit" ile eşleşir
```

---

## test-id niteliğini yapılandırma

`getByTestId` varsayılan olarak `data-testid` kullanır. `testfly.yml` içinde geçersiz kılın:

```yaml
locators:
  testIdAttribute: data-qa
```

Veya programatik olarak: `Locator.setTestIdAttribute("data-qa");`

## Standart CSS ve Zincirleme Filtreler

Erişilebilirlik öncelikli semantik seçicilere ek olarak `Locator`, standart CSS ve geleneksel seçiciler için değişmez (immutable) bir zincirleme API'si sunar:

```java
// Doğrudan CSS seçici fabrikası
Locator.cssSelector(".product-card")
    .filter(".in-stock")
    .withText("Kulaklık")
    .first()
    .click();

// Null-safe yazma (null veya boş string geçildiğinde NPE fırlatmadan öğeyi temizler)
getByPlaceholder("Arama").type(null);
```

---

## YAML ile locator tanımlama

`LocatorRepository`, adlandırılmış locator'ları bir YAML dosyasından yükler.
Böylece seçiciler tek yerde durur ve page object'ler ile testler arasında
paylaşılır:

```yaml
# src/test/resources/locators/register.yaml
page: register
elements:
  register_form:
    css: "#register-form"
    elements:
      email:
        css: "input[type=email]"
      submit_button:
        role: button
        accessibleName: "Register"
        exact: true
  login_link:
    text: "Already have an account?"
```

```java
LocatorRepository repo = LocatorRepository.load("locators/register.yaml");
repo.get("register_form.email").type("a@b.com");
repo.get("register_form.submit_button").click();
```

`load(String)` önce classpath'e, sonra dosya sistemine bakar. `load(Path)` ve
`load(InputStream, String)` de kullanılabilir.

**Stratejiler** — her eleman tam olarak birini tanımlar:
`css`, `xpath`, `id`, `byName`, `className`, `text`, `label`, `placeholder`,
`testId`, `altText`, `title`, `role`.

**Değiştiriciler**

| Anahtar | Geçerli olduğu yer | Karşılığı |
|---|---|---|
| `accessibleName` | `role` | `withName(...)` |
| `level` (1–6) | `role: heading` | `withLevel(...)` |
| `exact` | `text`, `label`, `placeholder`, `altText`, `title`, `role` | `exact()` |
| `nth` (0 tabanlı), `first`, `last` | hepsi (en fazla biri) | `nth(...)`, `first()`, `last()` |

HTML `name` niteliği için `byName`, bir rolün erişilebilir adı için
`accessibleName` kullanın; bunlar farklı şeylerdir.

**Kapsayıcılar** — `elements:` içeren bir eleman, çocuklarını `within(...)` ile
kapsar; böylece kapsayıcı seçicisini tekrar yazmazsınız. Kapsayıcı `css`,
`xpath`, `id`, `byName` veya `className` kullanmalı, değiştirici almaz ve yalnızca
tek seviye iç içe olabilir. Çocuklara `kapsayici.cocuk` biçiminde erişilir.

**Hızlı hata** — bilinmeyen anahtar, eksik veya çakışan strateji, geçersiz değer,
bulunamayan dosya ya da bilinmeyen eleman anahtarı, dosyayı ve elemanı belirten bir
`LocatorException` fırlatır. `${VAR}` ikamesi yoktur.

> YAML, tırnaksız `on`, `off`, `yes`, `no` ve sayıları metin olarak okumaz. Bu tür
> eleman adlarını tırnak içine alın (`"off":`).

Repository driver durumu tutmaz ve thread'ler arasında paylaşılabilir. `testId`,
locator çözümlenirken `locators.testIdAttribute` altındaki niteliği kullanır.

---

## Kaçış kapağı: `toBy()`

Her semantik locator, ham Selenium veya [`SmartLocator`](./smart-locator) ile birlikte çalışma için sentezlenmiş Selenium `By` nesnesini geri verebilir:

```java
By submitBtn = getByRole(Role.BUTTON).toBy();
WebElement el = driver.findElement(submitBtn);
```

> `toBy()` **temel seçiciyi** döndürür. `By` olarak ifade edilemeyen inceltmeler
> (örn. `.withName(...)`) yalnızca tamamen çözümlenmiş öğe üzerindeki terminal
> eylemleriyle (`click()`, `type()`, …) uygulanır.
