---
id: prompt-recipes
title: Yapay Zeka İçin Hazır Prompt Şablonları
sidebar_label: Hazır AI Prompt Şablonları
sidebar_position: 5
description: TestFly Page Object, TestNG, JUnit 5, Cucumber BDD ve self-healing testleri üretmek için test edilmiş hazır yapay zeka prompt şablonları.
---

# Yapay Zeka İçin Hazır Prompt Şablonları

**JetBrains AI Assistant**, **Claude Code** veya **GitHub Copilot** ile çalışırken doğrudan kullanabileceğiniz, TestFly Java 21 kod üretimine yol gösteren prompt şablonları:

Bu promptlar asistana yol gösterir; üretilen kod yine derlenmeli ve uygulamanızda çalıştırılmalıdır. Araç adları kurduğunuz MCP sunucusuna bağlıdır; `generate_testfly_code` yalnız bu bridge yapılandırılmışsa kullanılabilir.

---

## Yapay Zeka ile Çalışırken Altın Kural

> **AI'a her zaman önce tarayıcıyı açıp incelemesini söyleyin.**
> AI asistanınızdan doğrudan hayalinden kod yazmasını istemeyin. Önce yapılandırılmış tarayıcı MCP araçlarıyla sayfayı ziyaret etmesini, canlı DOM'u taramasını ve ardından `generate_testfly_code` araçlarını kullanmasını isteyin.

---

## Şablon 1: Page Object Modeli (`BasePage`)

TestFly Java 21 Page Object sınıfı üretmek için:

```text
https://www.saucedemo.com adresine git.
Giriş formu elemanlarını yapılandırılmış tarayıcı MCP araçlarıyla incele (erişilebilirlik niteliklerini tercih et).
io.testfly.examples.pages paketi altında BasePage extend eden 'LoginPage' adında bir TestFly Page Object üret.
Kullanıcı adı girme, şifre girme ve login butonuna tıklama için akıcı (fluent) eylem metotları ekle.
```

### Üretilen Kod Örneği
```java
package io.testfly.examples.pages;

import io.testfly.test.BasePage;
import io.testfly.locator.Role;
import org.openqa.selenium.By;

public class LoginPage extends BasePage {

    public LoginPage openLogin() {
        super.open("https://www.saucedemo.com");
        return this;
    }

    public LoginPage enterUsername(String username) {
        getByPlaceholder("Username").type(username);
        return this;
    }

    public LoginPage enterPassword(String password) {
        getByPlaceholder("Password").type(password);
        return this;
    }

    public void clickLogin() {
        getByRole(Role.BUTTON, "Login").click();
    }
}
```

---

## Şablon 2: TestNG Testi (`BaseTest`)

Otomatik bir TestNG testi yazdırmak için:

```text
SauceDemo girişi için TestFly TestNG testi yaz:
1. io.testfly.examples.testng paketi altında BaseTest extend etsin.
2. 'standard_user' ve 'secret_sauce' ile giriş yap.
3. assertThat(getDriver()).hasTitle(...) ile sayfa başlığının 'Swag Labs' olduğunu doğrula.
4. Ürünler başlığının 'Products' metnini içerdiğini doğrula.
```

### Üretilen Kod Örneği
```java
package io.testfly.examples.testng;

import io.testfly.test.BaseTest;
import io.testfly.examples.pages.LoginPage;
import org.openqa.selenium.By;
import org.testng.annotations.Test;

import static io.testfly.assertion.SeleniumAssert.assertThat;

public class SauceDemoLoginTest extends BaseTest {

    @Test(description = "Verify successful login to SauceDemo inventory")
    public void testSuccessfulLogin() {
        new LoginPage()
            .openLogin()
            .enterUsername("standard_user")
            .enterPassword("secret_sauce")
            .clickLogin();

        // Otomatik beklemeli sayfa doğrulaması
        assertThat(getDriver()).hasTitle("Swag Labs");
        assertThat(getDriver()).urlContains("/inventory.html");

        // Öğe doğrulaması
        assertThat(By.cssSelector(".title")).hasText("Products");
    }
}
```

---

## Şablon 3: JUnit 5 Testi (`BaseJUnit5Test`)

JUnit 5 testi üretmek için:

```text
SauceDemo giriş doğrulaması için io.testfly.examples.junit5 paketi altında BaseJUnit5Test extend eden bir JUnit 5 testi üret.
@Test ve @DisplayName anotasyonlarını kullan ve login sonrası sepet ikonunun görünür olduğunu doğrula.
```

---

## Şablon 4: Cucumber BDD Feature ve Adımları

Cucumber BDD senaryoları ve adım sınıfları üretmek için:

```text
SauceDemo login için Cucumber BDD süiti üret:
1. Geçerli login için Gherkin .feature dosyası oluştur.
2. io.testfly.examples.cucumber.steps paketi altında BaseCucumberSteps extend eden step definitions sınıfı yaz.
3. BaseCucumberTest extend eden TestNG Cucumber runner sınıfı oluştur.
```

---

## Şablon 5: Kırılan Seçicileri Onarma (Self-Healing)

Bir test locator değişikliği nedeniyle hata verirse:

```text
Test '#login-btn' seçicisinde NoSuchElementException hatası verdi.
Sayfaya git, canlı DOM'u inceleyip neyin değiştiğini tespit et ve getByRole veya getByTestId kullanan dayanıklı bir TestFly seçicisi öner.
```
