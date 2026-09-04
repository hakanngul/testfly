---
description: "SauceDemo üzerinde modern TestFly web-öncelikli doğrulamalar, soft assertion'lar ve Gemini AI hata analizi örnekleri."
id: saucedemo-assertions-example
title: "Tarif: SauceDemo Doğrulamaları ve AI Örneği"
sidebar_label: SauceDemo Doğrulamaları
---

# Tarif: SauceDemo Doğrulamaları ve AI Örneği

Bu tarifte, [SauceDemo](https://www.saucedemo.com/) sitesi üzerinde TestFly'ın modern assertion mimarisini ve Gemini AI hata analizini nasıl kullanacağınızı görebilirsiniz.

```java
import io.testfly.test.BaseTest;
import org.openqa.selenium.By;
import org.testng.annotations.Test;
import java.time.Duration;

public class SauceDemoAssertionsExampleTest extends BaseTest {

    private static final String SAUCE_DEMO_URL = "https://www.saucedemo.com/";

    @Test
    public void testGirisSayfasiDogrulari() {
        open(SAUCE_DEMO_URL);

        // 1. Özel bekleme süresi ve açıklayıcı hata mesajı
        assertThat(By.id("login-button"))
                .as("Giriş butonu açılışta görünür olmalı")
                .within(Duration.ofSeconds(5))
                .isVisible();

        // 2. Durum ve CSS matcher'ları
        assertThat(By.id("login-button"))
                .isEnabled()
                .hasValue("Login")
                .hasCssValue("cursor", "pointer");

        // 3. Öznitelik varlık kontrolü
        assertThat(By.id("user-name"))
                .hasAttribute("placeholder");
    }

    @Test
    public void testUrunlerSayfasiSoftAssertions() {
        open(SAUCE_DEMO_URL);

        // Giriş yap
        find(By.id("user-name")).type("standard_user");
        find(By.id("password")).type("secret_sauce");
        find(By.id("login-button")).click();

        // Fluent soft assertions: test durmaz, tüm hataları toplar ve en sonda raporlar
        softAssert(By.className("title")).hasText("Products");
        softAssert(By.id("shopping_cart_container")).isVisible();
        softAssert(By.id("react-burger-menu-btn")).isEnabled();

        // Ürün sayısı kontrolü
        assertThat(By.className("inventory_item")).count(6);
    }
}
```

---

## Google Gemini ile AI Hata Analizi

Bir test fail ettiğinde, TestFly hata mesajını, stack trace'i ve adımları otomatik olarak Google Gemini'ye ileterek kök neden analizi üretir.

`testfly.yml` dosyanızda etkinleştirin:

```yaml
ai:
  failureAnalysis: true
  provider: gemini
  apiKey: ${GEMINI_API_KEY}
  model: gemini-2.0-flash
  language: tr
```

Test başarısız olduğunda HTML test raporunun altında Türkçe kök neden analizi yer alır:

```markdown
**Kök Neden:** By.id("checkout-btn") locator'ı 10 saniye boyunca bulunamadı çünkü sepet boş durumdaydı.
**Önerilen Çözüm:**
- Ödeme adımına geçmeden önce sepete en az bir ürün ekleyin.
- Buton id'sinin [data-test="checkout"] olarak güncellenip güncellenmediğini kontrol edin.
```
