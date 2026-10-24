---
id: webviews
title: WebView'ler (Hibrit Uygulamalar)
sidebar_position: 7
description: "Hibrit uygulamaların (WKWebView, Android WebView, Cordova, Capacitor, Ionic) ve Chrome/Safari sekmelerinin web içeriğini sürün: DOM locator'ları, sayfa gezintisi ve JavaScript."
---

# WebView'ler (Hibrit Uygulamalar)

Birçok uygulama arayüzünün bir kısmını web içeriği olarak gösterir: bir ödeme sayfası, koşullar, yardım merkezi veya Cordova, Capacitor, Ionic ile uygulamanın tamamı. Native görünüm ağacı böyle bir webview'i tek bir öğe olarak gösterir. TestFly mobilecli'nin webview komutlarıyla **sayfanın DOM'una** ulaşır; böylece HTML öğelerine CSS seçicileri ve rollerle tıklayabilir, doldurabilir ve onları okuyabilirsiniz.

```java
MobileWebView pay = device().webView();
pay.getByRole("textbox", "Kart numarası").fill("4111 1111 1111 1111");
pay.getByRole("button", "Öde").click();
expect(screen().getByText("Ödeme alındı")).toBeVisible();     // native ekrana geri dönüldü
```

## Gereksinimler {#requirements}

- **Android:** uygulamanın webview'i debug edilebilir olmalı (`WebView.setWebContentsDebuggingEnabled(true)`; debug build'lerde varsayılan). Chrome sekmeleri de çalışır.
- **iOS simülatör:** uygulamanın `WKWebView`'i inspect edilebilir olmalı (iOS 16.4+ için `isInspectable = true`; debug build'lerde varsayılan). Safari sekmeleri de çalışır.
- **Gerçek iPhone:** ayrıca **Ayarlar > Uygulamalar > Safari > Gelişmiş > Web Denetçisi** açık olmalı.

## Webview'i bulmak

| Metot | Döndürür |
|---|---|
| `device().webViews()` | öndeki uygulamanın tüm webview'leri ve görünen Chrome/Safari sekmeleri, o anki listesiyle |
| `device().webView()` | görünen tek webview; hiç yoksa veya birden fazlaysa hata verir |

`MobileWebView`'de `id()`, `isVisible()` var; listelenen URL ve başlık `toString()`'tedir. Yalnızca görünen sekme betiklere cevap verir.

## Sayfadaki locator'lar

| Metot | Eşleşen |
|---|---|
| `locator(css)` | CSS seçicisine uyan öğeler: `locator("#email")`, `locator("form .total")` |
| `getByTestId("pay")` | `[data-testid="pay"]` |
| `getByText("Şimdi öde")` | görünen metni (veya `aria-label`, placeholder, input değeri) bu metne eşit olan en içteki öğeler; boşluklar normalleştirilir |
| `getByRole("button")` | bir ARIA rolünün öğeleri, native HTML dahil |
| `getByRole("button", "Öde")` | adı (metin, `aria-label` veya placeholder) `Öde` olan rol öğeleri |

Roller ve kapsadıkları HTML:

| Rol | Öğeler |
|---|---|
| `button` | `button`, `[role=button]`, `input[type=button]`, `[type=submit]`, `[type=reset]` |
| `link` | `a[href]`, `[role=link]` |
| `textbox` | metin türü `input`'lar (text, email, password, search, tel, url, number, türsüz), `textarea`, `[role=textbox]`, `[contenteditable=true]` |
| `checkbox`, `radio` | `input[type=checkbox]`, `input[type=radio]`, `[role=checkbox]`, `[role=radio]` |
| `heading` | `h1`…`h6`, `[role=heading]` |
| `img` | `img`, `[role=img]` |
| `combobox` | `select`, `[role=combobox]` |
| diğerleri | `[role=<ad>]` |

## Eylemler ve okumalar

`MobileWebViewLocator`, (`mobile.timeouts.actionMs` içinde) **tam olarak bir görünür** öğe eşleşene kadar bekler, sonra:

| Metot | Ne yapar |
|---|---|
| `click()` | öğeyi görünür alana kaydırır ve tıklar |
| `fill(text)` | alana odaklanır, değerini ayarlar, `input` ve `change` olaylarını tetikler (React/Vue/Angular kontrollü input'ları ve `contenteditable` ile çalışır) |
| `getText()` | görünen metin, boşluklar normalleştirilmiş |
| `getValue()` | input'un `value`'su; yoksa `null` |
| `getAttribute(ad)` | bir öznitelik; yoksa `null` |
| `waitFor()` | yalnızca bekler |

Beklemeden: `count()` (eşleşme sayısı) ve `isVisible()` (tam olarak bir görünür eşleşme). Süre içinde hiç veya birden fazla öğe eşleşirse `MobileTimeoutException` hangisi olduğunu söyler.

```java
MobileWebView terms = device().webView();
terms.getByText("Kabul ediyorum").click();
assertEquals(terms.locator("h1").getText(), "Kullanım Koşulları");
assertEquals(terms.getByTestId("accept").getAttribute("aria-disabled"), "false");
```

Native doğrulamalar (`expect(...)`) yalnızca native locator'larda çalışır. DOM durumu için bu metotlarla okuyup test framework'ünüzle doğrulayın veya [`expectPoll`](assertions#expect-poll) ile yeniden deneyin:

```java
expectPoll(() -> pay.getByTestId("status").getText()).toBe("Ödendi");
```

## Sayfa gezintisi ve JavaScript

| Metot | Ne yapar |
|---|---|
| `url()`, `title()`, `content()` | o anki URL, başlık ve HTML |
| `navigate(url)`, `reload()`, `goBack()`, `goForward()` | gezinti |
| `waitForLoad()` | sayfanın `load` olayını bekler |
| `query(css)` | eşleşen öğeler, map olarak (`tag`, `text`, `id`, `class`, `value`, `href`) |
| `evaluate("document.title")` | bir ifadeyi çalıştırır ve değerini düz Java değerleri olarak döndürür (`Map`, `List`, `String`, sayılar, boolean'lar) |
| `evaluate(body, args...)` | `body`'yi `arguments` ile bir fonksiyon gövdesi olarak çalıştırır: `evaluate("return document.querySelector(arguments[0]).value", "#email")` |

Locator'ları tercih edin; `evaluate`'i onların karşılamadığı işler için kullanın (local storage, canvas, uygulamanın bir fonksiyonunu çağırmak).
