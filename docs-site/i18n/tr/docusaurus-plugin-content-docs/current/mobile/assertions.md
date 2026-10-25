---
id: assertions
title: Doğrulamalar
sidebar_position: 5
description: "expect(locator) ile yeniden deneyen mobil doğrulamalar: durum, metin, değer ve sayı eşleyicileri, olumsuzlama, özel süre ve mesaj, her değer için expectPoll ve soft doğrulamalar."
---

# Doğrulamalar

Mobil doğrulamalar **web-first** çalışır: koşul sağlanana veya süre (`mobile.timeouts.actionMs`, varsayılan 5000 ms) dolana kadar ekranı yeniden okurlar. Hâlâ yüklenen veya animasyondaki bir ekran testi düşürmez ve `sleep` gerekmez.

```java
import static io.testfly.mobile.MobileAssertions.expect;   // BaseMobileTest, MobilePage ve MobileSupport'ta zaten var

expect(screen().getByText("Tekrar hoş geldiniz")).toBeVisible();
expect(screen().getByTestId("balance")).toHaveText(Pattern.compile("^\\d+,\\d{2} ₺$"));
expect(screen().getByRole(MobileRole.LISTITEM)).toHaveCount(3);
```

Başarısız bir doğrulama, son nedeni ve görülen öğeleri içeren bir `MobileAssertionError` fırlatır:

```
expect(getByTestId("balance")).toHaveText(/^\d+,\d{2} ₺$/) failed after 5000 ms: text differs: android.widget.TextView id="balance" text="--" [40,300 600x80]
```

## Eşleyiciler

| Eşleyici | Ne zaman geçer |
|---|---|
| `toBeVisible()` | tam olarak bir eşleşme görünür |
| `toBeHidden()` | görünür eşleşme yok (hiç olmaması da gizli sayılır) |
| `toBeEnabled()`, `toBeDisabled()` | tek eşleşme etkin / devre dışı |
| `toBeChecked()` | tek eşleşme işaretli (checkbox, radio, switch) |
| `toBeSelected()` | tek eşleşme seçili (sekme, segment, liste satırı) |
| `toBeFocused()` | tek eşleşmede giriş odağı var |
| `toHaveText("Hoş geldiniz")` | görünen metin (text, yoksa label, yoksa value) dizeye eşit |
| `toHaveText("hoş geldiniz", true)` | aynısı; büyük/küçük harfi yok sayar, boşlukları normalleştirir (art arda boşluklar, baştaki/sondaki boşluklar) |
| `toHaveText(Pattern)` | görünen metin düzenli ifadeye uyar (`find()`) |
| `toContainText("geldiniz")` | görünen metin dizeyi içerir, büyük/küçük harf duyarsız |
| `toHaveValue("ada@example.com")` | `value` dizeye eşit (metin alanları, slider'lar) |
| `toBeEmpty()` | öğenin metni ve değeri yok; yalnızca placeholder gösteren bir iOS alanı boş sayılır |
| `toHaveCount(n)` | sorgu tam olarak `n` öğeyle eşleşir |

Tek öğeli eşleyiciler [strict mode](locators#strict-mode) uygular; `toBeHidden()` ve `toHaveCount(n)` tüm eşleşmelere bakar.

## Olumsuzlama, süre ve mesaj

```java
expect(screen().getByText("Yükleniyor")).not().toBeVisible();        // toBeHidden() ile aynı
expect(screen().getByTestId("username")).not().toBeEmpty();
expect(screen().getByText("Rapor hazır")).withTimeout(Duration.ofSeconds(30)).toBeVisible();
expect(screen().getByTestId("total"), "kupondan sonra sepet toplamı").toHaveText("90,00 ₺");
```

- `not()` sonraki eşleyiciyi olumsuzlar. `not().toBeVisible()`, görünür eşleşme olmadığında geçer.
- `withTimeout(Duration)` yalnızca bu doğrulamanın yeniden deneme süresini değiştirir. Varsayılan olarak locator'ın kendi `withTimeout`'u kullanılır.
- `expect(locator, mesaj)`, `mesaj`'ı hatanın ilk satırına koyar.

Page object'lerde doğrulamaları niyet metotlarında tutun (`shouldShowBalance()`); testler okunur kalır.

## Herhangi bir değeri yeniden denemek: `expectPoll` {#expect-poll}

`expectPoll(supplier)` herhangi bir Java değerini yeniden dener; tek bir öğeyle ilgili olmayan kontroller için: cihaz durumu, birkaç locator'dan bir sayı, bir backend çağrısı.

```java
import static io.testfly.mobile.MobileAssertions.expectPoll;

device().setOrientation(MobileOrientation.LANDSCAPE);
expectPoll(device()::orientation).toBe(MobileOrientation.LANDSCAPE);

expectPoll(() -> screen().getByRole(MobileRole.LISTITEM).count())
        .withTimeout(Duration.ofSeconds(10))
        .matches(n -> n >= 20, "kaydırdıktan sonra en az 20 satır");
```

- `toBe(beklenen)` `equals` ile karşılaştırır; `matches(predicate, açıklama)` herhangi bir koşulu alır.
- Varsayılan süre 5 saniyedir; `withTimeout(Duration)` değiştirir. Supplier her 200 ms'de çağrılır.
- Supplier'ın fırlattığı istisna başarısız deneme sayılır; hata mesajı son değeri veya son hatayı gösterir.

## Soft doğrulamalar {#soft-assertions}

Soft doğrulama hatayı kaydeder ve testin devam etmesine izin verir; tek koşu bir ekranın tüm hatalı alanlarını raporlar. `MobileAssertions.softly()` ile bir toplayıcı oluşturun, onun üzerinden doğrulayın ve sonda `assertAll()` çağırın:

```java
MobileSoftAssertions soft = MobileAssertions.softly();
soft.expect(profile.name).toHaveText("Ada Lovelace");
soft.expect(profile.email).toHaveText("ada@example.com");
soft.expect(profile.badge, "premium rozeti").toBeVisible();
soft.assertAll();
```

- `assertAll()` tüm hataları listeleyen tek bir `MobileAssertionError` fırlatır (her biri ayrıca suppressed istisna olarak eklenir), sonra listeyi temizler. Hata yoksa bir şey yapmaz.
- `failures()` o ana kadar toplanan hataları döndürür.
- Toplayıcı thread-safe değildir: test başına bir tane oluşturun.
- `assertAll()`'u unutmak hataları gizler; page object'te değil, testte çağırın.

Base sınıflardaki `softAssert()`, TestFly'ın web/genel soft doğrulama API'sidir; mobil locator'lar için `MobileAssertions.softly()` kullanın.
