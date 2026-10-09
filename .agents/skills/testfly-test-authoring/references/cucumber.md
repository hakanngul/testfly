# Cucumber (BDD) Testleri

## Ne Zaman Kullanılır?
İş birimlerinin (Product Owner, İş Analisti) de okuyabileceği, Given-When-Then formatındaki senaryoları (Gherkin syntax) koşmak için kullanılır. BDD süreçlerini benimsemiş ekipler için biçilmiş kaftandır.

## Anahtar Prensipler
- Cucumber test koşucusu (runner) için `BaseCucumberTest` kullanılır.
- Adım tanımlama (step definitions) sınıfları `BaseCucumberSteps` sınıfından miras almalıdır. Bu, TestFly'ın UI/API metotlarına doğrudan erişimi (mixin/support arayüzleri) sağlar.
- Adımlar (steps) arasında veri aktarmak veya durumu paylaşmak için statik değişkenler kullanılmaz; bunun yerine `CucumberContext` veya Spring/PicoContainer üzerinden enjeksiyon yapılır.
- Karantina altındaki veya spesifik olarak retry edilecek testleri kontrol etmek için senaryolara `@flaky`, `@quarantine` gibi etiketler (tags) verilir ve `testfly.yml` dosyasından eşleştirilir.

## Şablon: Runner ve Adım (Step) Tanımları

### Runner Sınıfı
```java
package com.acme.tests.runner;

import io.testfly.cucumber.BaseCucumberTest;
import io.cucumber.testng.CucumberOptions;

@CucumberOptions(
    features = "src/test/resources/features",
    glue = {"com.acme.steps", "io.testfly.cucumber"}, // io.testfly.cucumber kancalar (hooks) için zorunlu
    plugin = {"pretty"} // HTML vb. eklentiler TestFly ReportAdapter ile de üretilebilir
)
public class RunCucumberTest extends BaseCucumberTest {
    // İçerisi genellikle boş kalır
}
```

### Step Definition Sınıfı
```java
package com.acme.steps;

import io.testfly.cucumber.BaseCucumberSteps;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import com.acme.pages.LoginPage;

public class LoginSteps extends BaseCucumberSteps {

    private final LoginPage loginPage = new LoginPage();

    @Given("I am on the login page")
    public void goToLogin() {
        open("https://www.saucedemo.com");
    }

    @When("I login with {string} and {string}")
    public void login(String username, String password) {
        loginPage.loginAs(username, password);
    }

    @Then("I should see the inventory page")
    public void verifyDashboard() {
        assertThatPage().hasUrlContaining("inventory");
    }
}
```

## İyi vs Kötü Pratikler

| Kötü (Anti-Pattern) | İyi (Best Practice) |
|----------------------|----------------------|
| Adımlar arası statik değişkenlerle (`public static String id`) veri taşımak | Thread-safe çalışan Context/State nesneleri (PicoContainer veya `testContext`) kullanmak. |
| Cucumber runner'ına `io.testfly.cucumber` paketini glue'ya eklemeyi unutmak | Tarayıcı başlatma/kapatma (hooks) için glue parametresine eklemek. |
| Tüm projeyi UI senaryosu ile doldurmak | BDD'yi kritik User Journey senaryoları ile sınırlandırmak. |

## Checklist
- [ ] Runner sınıfı `BaseCucumberTest`'ten türedi mi?
- [ ] Step sınıfları `BaseCucumberSteps`'ten türedi mi?
- [ ] `@CucumberOptions` `glue` array'ine framework'ün paket adı (`io.testfly.cucumber`) eklendi mi?
- [ ] Adımlar (steps) içerisinde test bağımlılığı veya statik state ihlali engellendi mi?
