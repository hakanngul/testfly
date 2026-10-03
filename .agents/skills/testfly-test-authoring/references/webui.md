# Web UI Testleri (BaseTest, Locator, Assertion)

## Ne Zaman Kullanılır?
Uygulamanın tarayıcı üzerindeki (frontend) fonksiyonel, görsel ve entegrasyon senaryolarını test etmek için kullanılır. `BaseTest` kullanıldığında framework otomatik olarak WebDriver yaşam döngüsünü, video kayıtlarını ve ekran görüntülerini yönetir.

## Anahtar Prensipler
- Her test sınıfı `BaseTest` (TestNG) veya `BaseJUnit5Test`'ten (JUnit 5) türemelidir.
- Sadece `Locator` ve `WaitEngine` API'sini kullanın; ham `WebDriver` metotlarından kaçının.
- Page Object Model (POM) kesinlikle uygulanmalıdır (`BasePage`).
- Doğrulamaları (assert) `BasePage` sınıfında yapmayın, test metodunda yapın.
- Erişilebilirlik odaklı locator (ör. `getByRole`, `getByLabel`) tercih edin.

## Şablon: BaseTest & BasePage

### Page Object (BasePage)
```java
package com.acme.pages;

import io.testfly.test.BasePage;
import io.testfly.locator.Locator;
import io.testfly.locator.Role;

public class LoginPage extends BasePage {

    // Locator tanımları
    private final Locator usernameField = getByPlaceholder("Username");
    private final Locator passwordField = getByPlaceholder("Password");
    private final Locator loginBtn = getByRole(Role.BUTTON).withName("Login");
    private final Locator errorMsg = getByTestId("error-message");

    public void loginAs(String username, String password) {
        step("Kullanıcı girişi yapılıyor: " + username);
        usernameField.type(username);
        passwordField.type(password);
        loginBtn.click();
    }
    
    public Locator getErrorMessage() {
        return errorMsg;
    }
}
```

### Test Sınıfı (BaseTest)
```java
package com.acme.tests;

import io.testfly.test.BaseTest;
import com.acme.pages.LoginPage;
import org.testng.annotations.Test;

public class LoginUiTest extends BaseTest {

    private final LoginPage loginPage = new LoginPage();

    @Test
    public void shouldLoginSuccessfully() {
        open("https://www.saucedemo.com");
        
        loginPage.loginAs("standard_user", "secret_sauce");
        
        // Web-first assertion (Otomatik bekleme/polling yapar)
        assertThatPage().hasUrl("https://www.saucedemo.com/inventory.html");
    }

    @Test
    public void shouldShowErrorOnInvalidCredentials() {
        open("https://www.saucedemo.com");
        
        loginPage.loginAs("locked_out_user", "secret_sauce");
        
        // Element doğrulama
        assertThat(loginPage.getErrorMessage()).isVisible();
        assertThat(loginPage.getErrorMessage()).hasText("Epic sadface: Sorry, this user has been locked out.");
    }
}
```

## İyi vs Kötü Pratikler

| Kötü (Anti-Pattern) | İyi (Best Practice) |
|----------------------|----------------------|
| `driver.findElement(By.id("btn")).click()` | `getByTestId("btn").click()` veya `getByRole(Role.BUTTON).withName("Gönder").click()` |
| `Thread.sleep(3000);` | `getWait().waitForVisible(myLocator)` |
| `Assert.assertTrue(element.isDisplayed())` | `assertThat(myLocator).isVisible()` (Web-first polling yapar) |
| Test sınıfında locator tanımlamak | Locator'ları Page Object (`BasePage`) içine gizlemek |

## Checklist
- [ ] Testler `BaseTest` (veya `BaseJUnit5Test`) sınıfından miras alıyor mu?
- [ ] Sayfalar `BasePage` sınıfından miras alıyor mu?
- [ ] Explicit statik `sleep()` çağrısı kullanılmamış mı?
- [ ] Web-first assertions (örn. `assertThat(loc).isVisible()`) kullanılmış mı?
- [ ] Tüm locator'lar en erişilebilir seçicilerle (`getByRole`, `getByLabel` vs.) tanımlanmış mı?
