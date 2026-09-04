---
description: "Selenium'de erişilebilirlik testi: gruplanmış axe-core ile mevcut Selenium testlerinizin içinde tek satırda WCAG uyumluluğunu doğrulayın, ekstra araca gerek yok."
id: accessibility
title: Selenium Erişilebilirlik Testi
sidebar_label: Accessibility Assertions
sidebar_position: 17
---

# Erişilebilirlik Doğrulamaları (axe-core)

TestFly 2.5.0, [axe-core](https://github.com/dequelabs/axe-core) 4.10.2'yi doğrudan JAR içinde paketler. Sıfır ek bağımlılıkla bir WCAG taraması çalıştırmak için `open()` işleminden sonra `accessibility()` çağırın.

---

## Hızlı örnek

```java
public class CheckoutAccessibilityTest extends BaseTest {

    @Test
    public void checkout_passesWCAG21AA() {
        open("/checkout");

        accessibility()
            .withTags("wcag2a", "wcag21aa")   // WCAG 2.1 AA kuralları
            .withLevel(Impact.SERIOUS)         // SERIOUS veya CRITICAL'de hata ver
            .excluding("#cookie-banner")       // üçüncü parti widget'ı atla
            .run();
    }
}
```

---

## API başvurusu

### `accessibility()` — giriş noktası

`BaseTest` ve `BaseJUnit5Test` içinde mevcuttur. Bir `AccessibilityAssert` oluşturucu (builder) döndürür.

### Oluşturucu metotları

| Metot | Açıklama |
|---|---|
| `.withTags(String... tags)` | Yalnızca verilen axe-core etiketleriyle eşleşen kuralları çalıştırır. Yaygın etiketler: `"wcag2a"`, `"wcag2aa"`, `"wcag21aa"`, `"wcag22aa"`, `"best-practice"`. Varsayılan: tüm kurallar. |
| `.withLevel(Impact minimum)` | Yalnızca bu önem düzeyine eşit veya daha yüksek ihlaller için hata verir. Varsayılan: `Impact.MINOR` (tüm ihlaller hata üretir). |
| `.excluding(String... selectors)` | Taramadan hariç tutulacak CSS seçicileri. Bu öğelerde köklenen alt ağaçlar yok sayılır. |
| `.withContext(String selector)` | Taramayı bu CSS seçicisinde köklenen alt ağaçla sınırlar. |

### Uç (terminal) metotlar

| Metot | Açıklama |
|---|---|
| `.run()` | Taramayı çalıştırır ve yapılandırılan düzeyde ihlal bulunursa `AssertionError` fırlatır. |
| `.collect()` | Taramayı çalıştırır ve doğrulama yapmadan `AccessibilityResult` döndürür. |

---

## Önem düzeyleri

```java
Impact.CRITICAL   // yardımcı teknoloji kullanıcıları için erişilemez
Impact.SERIOUS    // geçici çözüm olasılığı olan ciddi engel
Impact.MODERATE   // tam blokaj olmadan bozulmuş deneyim
Impact.MINOR      // gerçek dünya etkisi düşük, en iyi uygulamadan sapma
```

Sıralama: `CRITICAL > SERIOUS > MODERATE > MINOR`.

---

## Kapsamlı taramalar

```java
// Yalnızca giriş formunu tara
accessibility()
    .withContext("#login-form")
    .run();

// Bilinen erişilemez üçüncü parti widget'ları hariç tut
accessibility()
    .excluding("#intercom-container", "#zendesk-widget")
    .run();
```

---

## Doğrulama yapmadan sonuçları toplama

```java
AccessibilityResult result = accessibility()
    .withTags("wcag2a", "wcag21aa")
    .collect();

System.out.println("İhlaller: " + result.violationCount());
System.out.println("Geçen kurallar: " + result.passCount());

for (AccessibilityViolation v : result.violations()) {
    System.out.println("[" + v.impact() + "] " + v.id() + " — " + v.help());
    v.nodes().forEach(n -> System.out.println("  " + n.target()));
}

// Yumuşak doğrulama — en fazla 2 küçük ihlale tolerans göster
softAssert().that(
    result.violationsAtLevel(Impact.SERIOUS).isEmpty(),
    "Serious ihlaller bulundu:\n" + result.violations()
);
```

---

## Hata mesajı biçimi

`.run()` ihlal bulduğunda tam bir rapor içeren bir `AssertionError` fırlatır:

```
[Accessibility] 2 violations found on: https://example.com/checkout
  Rules: wcag2a, wcag21aa
  Minimum impact: SERIOUS

  1. [CRITICAL] image-alt
     Ensures <img> elements have alternate text
     Fix: Images must have alternate text
     Docs: https://dequeuniversity.com/rules/axe/4.10/image-alt
     → img.hero-banner
       Fix any of the following: Element does not have an alt attribute

  2. [SERIOUS] color-contrast
     Ensures the contrast ratio between foreground and background colors meets thresholds
     Fix: Elements must have sufficient color contrast
     Docs: https://dequeuniversity.com/rules/axe/4.10/color-contrast
     → p.disclaimer-text
       Fix any of the following: Element has insufficient color contrast of 3.5 (Expected 4.5:1)
     … and 3 more node(s)
```

---

## Yaygın WCAG etiket kombinasyonları

| Amaç | Etiketler |
|---|---|
| WCAG 2.0 A | `"wcag2a"` |
| WCAG 2.0 AA (birçok ülkede asgari yasal gereklilik) | `"wcag2a"`, `"wcag2aa"` |
| WCAG 2.1 AA (AB Erişilebilirlik Yasası, ADA) | `"wcag2a"`, `"wcag21aa"` |
| WCAG 2.2 AA | `"wcag2a"`, `"wcag21aa"`, `"wcag22aa"` |
| En iyi uygulamalar (WCAG ötesi) | `"best-practice"` |

---

## Notlar

- axe-core her sayfa yüklendiğinde tarayıcıya bir kez enjekte edilir. Aynı sayfaya yapılan sonraki `accessibility()` çağrıları zaten enjekte edilmiş örneği yeniden kullanır.
- İnternet bağlantısı gerekmez — axe-core 4.10.2, sınıf yolu (classpath) kaynağı olarak JAR içinde paketlenmiştir.
- Ek bir Maven bağımlılığı gerekmez.