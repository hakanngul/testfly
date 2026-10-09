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
