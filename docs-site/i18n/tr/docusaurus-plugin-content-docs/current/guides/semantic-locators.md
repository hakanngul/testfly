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

`Role`, 38 WAI-ARIA rolünü kapsar. Her biri hem rolü örtük olarak taşıyan yerel HTML öğeleriyle hem de açık bir `role` niteliği taşıyan herhangi bir öğeyle eşleşir — örn. `Role.BUTTON`, `<button>`, `<input type="submit">`, `<summary>` ve `[role="button"]` ile eşleşir.

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