---
description: "Neden WaitEngine? Çünkü Thread.sleep() ve ham WebDriverWait kırılgan, gürültülü ve yavaştır. TestFly'ın WaitEngine'i, config tarafından yönetilen zaman aşımları ve kendi kendini iyileştirme yedeği ile açık beklemeleri merkezileştirir."
id: why-waitengine
title: Neden WaitEngine?
sidebar_label: Why WaitEngine?
sidebar_position: 5
---

# Neden WaitEngine?

Takılma durumundaki Selenium testlerinin tek en büyük kaynağı **zamanlamadır**. Sayfa hazır değildir, öğe henüz tıklanabilir değildir, AJAX çağrısı dönmemiştir veya yükleyici kaybolmamıştır. Ekipler bunu genellikle iki yanlış yoldan biriyle çözer:

1. `Thread.sleep(3000)` — basit, tahmin edilebilir ve güvenilir biçimde yavaş.
2. Her page object'ine kopyalanan, dağınık bir `WebDriverWait` yardımcıları koleksiyonu.

`WaitEngine`, TestFly'ın cevabıdır: her testin ve page object'inin kullandığı tek bir merkezi, config tarafından yönetilen, açık bekleme API'si.

---

## `Thread.sleep()` ile ilgili sorun nedir

```java title="Yanlış yol"
Thread.sleep(3000); // umarım sayfa hazırdır
driver.findElement(By.id("submit")).click();
```

Sorunlar:

- **Hızlı ortamlarda yavaş.** Öğe 100ms'de hazır olsa bile her zaman tam 3 saniye beklersiniz.
- **Yavaş ortamlarda hızlıdır.** CI container'ları, bulut tarayıcıları veya yoğun grid'ler 4 saniye gerektirebilir ve test başarısız olur.
- **Gerçek hataları gizler.** Bir uyku, uygun bir bekleme koşulu tarafından yakalanacak başarısızlıkları maskeler.
- **Kötü ölçeklenir.** Bir pakette 100 uyku olsa, toplam çalışma süresi şişer ve takılmalar asla gitmez.

---

## Dağınık `WebDriverWait` ile ilgili sorun nedir

```java title="Biraz daha iyi, yine de dağınık"
new WebDriverWait(driver, Duration.ofSeconds(10))
    .until(ExpectedConditions.elementToBeClickable(By.id("submit")))
    .click();
```

Bu izole olarak doğrudur, ancak her page object kendi zaman aşımını icat ettiğinde ekipler şunlarla kalır:

- Paket genelinde tutarsız zaman aşımları
- Yinelenen bekleme mantığı
- Küresel olarak ayarlanması zor zaman aşımları
- Framework'un config'ini atlayan testler

---

## WaitEngine bunu nasıl çözer

```java title="TestFly yolu"
io.testfly.wait.WaitEngine.waitForClickable(By.id("submit"));
```

Tek satır:

- Zaman aşımını `testfly.yml`'den okur (`timeouts.explicit`)
- Koşul doğru olana kadar onu yoklar
- Koşul asla doğru olmazsa net bir mesajla başarısız olur
- Locator başarısız olursa kendi kendini iyileştirme yedeğini tetikler (`locators.selfHealing: true` ise)
- `BasePage` ve `BaseTest`'in erişebildiği her yerde kullanılabilir

---

## Bekleme metotları nerede bulunur

- **`WaitEngine`** (`io.testfly.wait.WaitEngine`) **statik** metotlardan oluşan bir yardımcı sınıftır. Mevcut thread'in framework tarafından yönetilen driver'ını ve `timeouts.explicit` değerini kullanır; bu yüzden her page object, test veya yardımcı sınıftan çağrılabilir.
- **`BasePage` ve `BaseTest`**, `WaitEngine`'e yönlendiren birkaç örnek (instance) kısayolu ekler: `waitForPageLoad()`, `waitForUrlContains(String)`, `waitForUrlMatches(String)`, `waitForTitle(String)` ve `waitForTitleContains(String)`.
- **`getWait()`** (`BasePage` ve `BaseTest` üzerinde), `timeouts.explicit` ile oluşturulmuş sade bir Selenium `WebDriverWait` döndürür. `waitFor...` metotları yoktur; özel koşullar için `.until(...)` kullanın.

```java title="LoginPage.java"
import io.testfly.test.BasePage;
import io.testfly.wait.WaitEngine;
import org.openqa.selenium.By;

public class LoginPage extends BasePage {

    public void submit() {
        WaitEngine.waitForClickable(By.id("submit")).click();
        WaitEngine.waitForInvisible(By.cssSelector(".spinner"));
        waitForUrlContains("/dashboard");   // BasePage kısayolu → WaitEngine.waitForUrlContains
    }
}
```

## Kendiniz yazmak zorunda olmadığınız koşullar

`WaitEngine`, kutudan çıktığı haliyle yaygın koşullarla birlikte gelir:

| Koşul | Kullanım durumu |
|---|---|
| `waitForVisible(By)` | Öğe görünür |
| `waitForInvisible(By)` | Loader/spinner kaybolur |
| `waitForClickable(By)` | Öğe görünür ve etkin |
| `waitForText(By, String)` | Öğe metni verilen metne tam olarak eşittir |
| `waitForTextMatches(By, String)` | Metin bir regex ile eşleşir |
| `waitForAttribute(By, String, String)` | Öznitelik bir değere eşittir |
| `waitForAttributeContains(...)` | Öznitelik bir alt dize içerir |
| `waitForUrlContains(String)` / `waitForUrlMatches(String)` | Gezinme tamamlandı |
| `waitForPageLoad()` | `document.readyState === "complete"` |
| `waitForStaleness(WebElement)` | Eski DOM düğümü AJAX ile değiştirilir |
| `waitForNumberOfWindowsToBe(int)` | Yeni bir sekme veya pencere açıldı |
| `waitForAlert()` | JavaScript uyarısı mevcut |

Özel bir şeye mi ihtiyacınız var? Kaçış kapağı herhangi bir Selenium `ExpectedCondition` kabul eder ve yapılandırılmış zaman aşımını kullanmaya devam eder:

```java
WaitEngine.wait(ExpectedConditions.numberOfElementsToBe(By.cssSelector(".row"), 5));
// veya aynı şekilde, ham WebDriverWait ile:
getWait().until(ExpectedConditions.numberOfElementsToBe(By.cssSelector(".row"), 5));
```

---

## Config tarafından yönetilen, sabit kodlanmış değil

```yaml title="testfly.yml"
timeouts:
  explicit: 10   # saniye — her WaitEngine çağrısı tarafından kullanılır
```

Tek bir sayıyı değiştirin, paketin tamamının bekleme davranışı değişir. Page object düzenlemesi gerekmez.

Tek bir yavaş işlem için config'e dokunmadan geçersiz kılın:

```java
new WebDriverWait(getDriver(), Duration.ofSeconds(30))
    .until(ExpectedConditions.visibilityOfElementLocated(By.id("heavy-report")));
```

---

## Gerçek karşılık

`WaitEngine` üzerine inşa edilmiş bir paket:

- **Daha hızlıdır** — yalnızca gerektiği kadar bekler
- **Daha istikrarlıdır** — tahmin etmek yerine gerçek koşulları yoklar
- **Bakımı daha kolaydır** — zaman aşımları tek bir yerde yaşar
- **Daha dürüsttür** — başarısızlıklar, uykunun çok kısa olduğu değil, koşulun hiç karşılanmadığı anlamına gelir

---

## Sonraki adımlar

- [WaitEngine Rehberi](/docs/guides/wait-engine) — tam yöntem başvurusu ve örnekler
- [Neden sade Selenium değil?](/docs/why/why-not-plain-selenium) — daha geniş boilerplate hikayesi
- [Yeniden Deneme](/docs/guides/retry) — bir bekleme yeterli olmadığında ne yapmalı