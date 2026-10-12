---
description: "Thread.sleep() olmadan Selenium'da açık bekleme (explicit wait): WaitEngine, testfly.yml süreniz tarafından yönlendirilen akıcı ve otomatik yapılandırılmış beklemeler sağlar."
id: wait-engine
title: Selenium Beklemeleri (WaitEngine)
sidebar_label: WaitEngine
sidebar_position: 3
---

# WaitEngine

`WaitEngine`, `WaitEngine.waitForXxx(...)` çağrılarıyla kullanılan statik bir yardımcı sınıftır. `testfly.yml` içindeki süre (`timeouts.explicit`) ile önceden yapılandırılmıştır.

```java
import io.testfly.wait.WaitEngine;
```

---

## Kullanılabilir metodlar

### Öğe görünürlüğü

```java
WaitEngine.waitForVisible(By.id("modal"));
WaitEngine.waitForInvisible(By.cssSelector(".spinner"));  // yükleme göstergelerinin kaybolmasını bekler
```

### Tıklanabilirlik

```java
WaitEngine.waitForClickable(By.id("submit"));
```

### Actionability (etkileşime hazır olma)

`Locator` eylemleri (`click`, `hover`, `type`, `clear`, `append`), Playwright'ın
actionability kontrolleri gibi, öğe o etkileşime hazır olana kadar bekler.
Doğrudan da çağırabilirsiniz:

```java
WaitEngine.waitForActionable(By.id("submit"));                       // CLICK
WaitEngine.waitForActionable(By.id("email"), ActionKind.EDIT);
```

| Kontrol | `CLICK` | `HOVER` | `EDIT` |
|---|:-:|:-:|:-:|
| Görünür | evet | evet | evet |
| Etkin | evet | hayır | evet |
| Kararlı (iki yoklama arasında konum ve boyut değişmemiş) | evet | evet | evet |
| Olayı alır (başka bir öğe tarafından örtülmemiş) | evet | evet | hayır |

Öğenin merkezi ekran dışındaysa veya örtülüyse öğe görünür alana kaydırılır.
Hiç hazır olmazsa hata, öğeyi ve nedeni belirtir:

```
NotActionableException: Element <button#submit> is not actionable for CLICK after 10s:
obscured by <div.modal-backdrop>
```

Sayfa, öğe çözümlendikten sonra yeniden çizilirse veya kontrolden sonra bir
katman belirirse, locator zinciri yeniden çözülür (en fazla üç deneme).

Kaçış yolları, en az müdahaleciden en fazlasına:

- `find("#x").force().click()` kararlılık ve örtülmeme kontrollerini atlar. Öğe
  yine de görünür ve etkin olmalıdır.
- `find("#x").jsClick()` örtülü olsa bile JavaScript ile tıklar.
- `actionability.enabled: false` önceki beklemeleri geri getirir (yalnızca görünür ve etkin).

Kontroller "fail-open" çalışır: betik çalıştırılamazsa eylem devam eder. Her
eyleme yaklaşık bir yoklama aralığı (`actionability.stabilityIntervalMs`, 100 ms)
ekler.

Kapsam dışı: `BasePage.jsType`, `upload` (dosya girdileri çoğu zaman gizlidir) ve
`withinFrame`.

### Etkin / devre dışı

```java
WaitEngine.waitForEnabled(By.id("submit"));   // etkileşime hazır
WaitEngine.waitForDisabled(By.id("submit"));  // buton pasif (gri)
```

### Seçili

```java
WaitEngine.waitForSelected(By.id("terms"));   // checkbox veya radio seçili
```

### Metin içeriği

```java
WaitEngine.waitForText(By.cssSelector("h1"), "Welcome back");
```

### Öznitelik değeri

```java
WaitEngine.waitForAttributeContains(By.id("status"), "class", "active");  // alt dize
WaitEngine.waitForAttribute(By.id("status"), "aria-expanded", "true");    // tam eşleşme
```

### Metin eşleşmesi (regex)

```java
// Öğenin görünen metni bir düzenli ifadeyle eşleşene kadar bekler
WaitEngine.waitForTextMatches(By.cssSelector(".total"), "\\$\\d+\\.\\d{2}");
```

### URL eşleşmesi (regex)

```java
WaitEngine.waitForUrlContains("/orders");            // alt dize
WaitEngine.waitForUrlMatches(".*/orders/\\d+");      // düzenli ifade
```

### DOM eskiliği (stale)

```java
WebElement old = getDriver().findElement(By.id("row-1"));
WaitEngine.waitForStaleness(old);  // DOM değişimini / AJAX yenilemesini bekler
```

### Sayfa yükleme

```java
WaitEngine.waitForPageLoad();  // document.readyState === "complete" olana kadar bekler
```

### Pencereler ve çerçeveler (frame)

```java
WaitEngine.waitForNumberOfWindowsToBe(2);   // yeni sekme açıldı
WaitEngine.waitForFrameAvailableAndSwitchToIt(By.id("payment-iframe"));
```

### Minimum öğe sayısı

Zaman uyumsuz olarak büyüyen listeler ve sonsuz kaydırmalı (infinite-scroll) akışlar için kullanışlıdır:

```java
WaitEngine.waitForMinimumElementCount(By.cssSelector(".product-card"), 10);
```

### Özel koşul

```java
// Kaçış yolu — herhangi bir ExpectedCondition verin
WaitEngine.wait(ExpectedConditions.numberOfWindowsToBe(2));
```

---

## Özel süre (timeout)

`WaitEngine` her zaman `testfly.yml` içindeki global süreyi kullanır. Tek seferlik özel bir süreye ihtiyacınız varsa doğrudan bir `WebDriverWait` oluşturun:

```java
// Özel süre — doğrudan bir WebDriverWait oluşturun
new WebDriverWait(getDriver(), Duration.ofSeconds(30))
    .until(ExpectedConditions.visibilityOfElementLocated(By.id("slow-element")));
```

---

## Yapılandırma

```yaml title="testfly.yml"
timeouts:
  explicit: 10   # saniye — tüm WaitEngine çağrıları için varsayılan
  pageLoad: 30   # saniye — tarayıcı sayfa yükleme süresi

actionability:
  enabled: true              # click/hover/type öncesi kararlılık ve örtülmeme kontrolleri
  stabilityIntervalMs: 100   # öğenin oturmasını beklerken yoklama aralığı
```

---

## Kaçınılması gereken anti-pattern'ler

```java
// ❌ asla böyle yapmayın
Thread.sleep(3000);

// ✅ bunun yerine şunu yapın
WaitEngine.waitForVisible(By.id("result"));
```

```java
// ❌ ham WebDriverWait — framework süre yapılandırmasını atlar
new WebDriverWait(getDriver(), Duration.ofSeconds(10))
    .until(ExpectedConditions.visibilityOfElementLocated(By.id("result")));

// ✅ WaitEngine kullanın — süreyi yapılandırmadan okur
WaitEngine.waitForVisible(By.id("result"));
```
