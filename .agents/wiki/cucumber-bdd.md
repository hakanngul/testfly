---
tags:
  - wiki
  - bdd
  - cucumber
  - gherkin
date: 2026-09-10
status: active
type: wiki
---

# Cucumber 7 BDD Entegrasyonu

TestFly, iş analistleri ve ürün sahiplerinin Gherkin sözdizimiyle (`Given / When / Then`) senaryo yazabilmesi için opsiyonel Cucumber 7 köprüsüne sahiptir.

---

## 1. Test Runner ve Step Tanımları

TestFly, Cucumber yaşam döngüsünü framework'ün ThreadLocal `DriverManager` ve `WaitEngine` altyapısına bağlayan temel sınıflar sunar:

```java
// TestNG Cucumber Runner
@CucumberOptions(
    features = "src/test/resources/features",
    glue = "io.testfly.examples.steps",
    plugin = {"pretty", "io.testfly.cucumber.CucumberStepLogger"}
)
public class RunCucumberTest extends BaseCucumberTest {
}
```

```java
// Step Tanımları
public class LoginSteps extends BaseCucumberSteps {

    @Given("kullanıcı giriş sayfasını açar")
    public void kullaniciGirisSayfasiniAcar() {
        open("/login");
    }

    @When("{string} ve {string} bilgileriyle giriş yapar")
    public void girisYapar(String user, String pass) {
        find("#username").type(user);
        find("#password").type(pass);
        find("#login-btn").click();
    }

    @Then("ana sayfa paneli görüntülenmelidir")
    public void anaSayfaGoruntulenmelidir() {
        assertThat(find("#dashboard")).isVisible();
    }
}
```

---

## 2. Senaryo Bazlı `@retryable` Desteği

Feature dosyalarında senaryo veya senaryo taslağı (Scenario Outline) seviyesinde `@retryable` etiketi kullanılarak geçici başarısızlıklar otomatik olarak yeniden denenir.

---

## İlgili Bağlantılar
- WebUI Test Mimarisi: `[[wiki/webui-testing]]`
- Ana Harita: `[[MAP]]`
- Wiki Dizin: `[[wiki/index]]`
