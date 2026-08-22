---
description: "Thread.sleep() olmadan Selenium'da açık beklemeler (explicit wait): WaitEngine, testfly.yml süreniz tarafından yönlendirilen akıcı ve otomatik yapılandırılmış beklemeler sağlar."
id: wait-engine
title: Selenium Beklemeleri (WaitEngine)
sidebar_label: WaitEngine
sidebar_position: 3
---

# WaitEngine

`WaitEngine`, akıcı açık beklemeler (explicit wait) sağlar. `testfly.yml` içindeki süre (`timeouts.explicit`) ile önceden yapılandırılmıştır ve her `BasePage` içinde `getWait()` aracılığıyla kullanılabilir.

---

## Kullanılabilir metodlar

### Öğe görünürlüğü

```java
getWait().waitForVisible(By.id("modal"));
getWait().waitForInvisible(By.cssSelector(".spinner"));  // wait for loaders to disappear
```

### Tıklanabilirlik

```java
getWait().waitForClickable(By.id("submit"));
```

### Etkin / devre dışı

```java
getWait().waitForEnabled(By.id("submit"));   // ready to interact
getWait().waitForDisabled(By.id("submit"));  // button is greyed out
```

### Seçili

```java
getWait().waitForSelected(By.id("terms"));   // checkbox or radio is checked
```

### Metin içeriği

```java
getWait().waitForText(By.cssSelector("h1"), "Welcome back");
```

### Öznitelik değeri

```java
getWait().waitForAttributeContains(By.id("status"), "class", "active");  // substring
getWait().waitForAttribute(By.id("status"), "aria-expanded", "true");    // exact match
```

### Metin eşleşmesi (regex)

```java
// Wait until the element's visible text matches a regular expression
getWait().waitForTextMatches(By.cssSelector(".total"), "\\$\\d+\\.\\d{2}");
```

### URL eşleşmesi (regex)

```java
getWait().waitForUrlContains("/orders");            // substring
getWait().waitForUrlMatches(".*/orders/\\d+");      // regular expression
```

### DOM eskiliği (stale)

```java
WebElement old = driver.findElement(By.id("row-1"));
getWait().waitForStaleness(old);  // wait for DOM replacement / AJAX reload
```

### Sayfa yükleme

```java
getWait().waitForPageLoad();  // waits until document.readyState === "complete"
```

### Pencereler ve çerçeveler (frame)

```java
getWait().waitForNumberOfWindowsToBe(2);   // new tab opened
getWait().waitForFrameAvailableAndSwitchToIt(By.id("payment-iframe"));
```

### Minimum öğe sayısı

Zaman uyumsuz olarak büyüyen listeler ve sonsuz kaydırmalı (infinite-scroll) akışlar için kullanışlıdır:

```java
getWait().waitForMinimumElementCount(By.cssSelector(".product-card"), 10);
```

### Özel koşul

```java
// Escape hatch — pass any ExpectedCondition
getWait().wait(ExpectedConditions.numberOfWindowsToBe(2));
```

---

## Süre (timeout) geçersiz kılma

Global yapılandırmayı değiştirmeden tek bir bekleme için özel bir süre kullanın:

```java
getWait(30).waitForVisible(By.id("slow-element"));  // 30-second timeout
```

---

## Yapılandırma

```yaml title="testfly.yml"
timeouts:
  explicit: 10   # seconds — default for all WaitEngine calls
  pageLoad: 30   # seconds — browser page load timeout
```

---

## Kaçınılması gereken anti-pattern'ler

```java
// ❌ never do this
Thread.sleep(3000);

// ✅ do this instead
getWait().waitForVisible(By.id("result"));
```

```java
// ❌ raw WebDriverWait — bypasses framework timeout config
new WebDriverWait(driver, Duration.ofSeconds(10))
    .until(ExpectedConditions.visibilityOf(...));

// ✅ use getWait() — reads timeout from config
getWait().waitForVisible(By.id("result"));
```