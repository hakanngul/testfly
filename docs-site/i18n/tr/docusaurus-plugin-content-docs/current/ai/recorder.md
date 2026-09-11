---
id: recorder
title: İnteraktif Kaydedici & Chrome Companion
sidebar_label: İnteraktif Kaydedici
sidebar_position: 3
description: Google Chrome üzerinde canlı kullanıcı etkileşimlerini kaydedin; temiz, üretime hazır TestFly Java testleri ve Page Object sınıfları üretin.
---

# İnteraktif Kaydedici & Chrome Companion

**TestFly İnteraktif Kaydedici (Interactive Recorder)**, gerçek kullanıcı tarayıcı etkileşimlerini doğrudan üretime hazır Java test otomasyon koduna dönüştüren canlı bir refakatçi stüdyosudur.

Chrome DevTools Protocol (CDP) üzerinden enjekte edilen bir Chrome refakatçi penceresi ile gerçek zamanlı Server-Sent Events (SSE) akışını birleştirir. Yapılan her tıklama, metin girişi, açılır menü seçimi ve assertion anında stüdyoya iletilir ve **TestFly Java 17+** standartlarında derlenir.

```bash
testfly record https://www.saucedemo.com
```

---

## Mimari Genel Bakış

```mermaid
flowchart LR
    subgraph Browser ["Google Chrome (Refakatçi)"]
        DOM[Canlı Web Uygulaması]
        Injected[Enjekte Edilen Kaydedici JS]
        DOM --> Injected
    end

    subgraph Server ["TestFly MCP Sunucusu (Yerel)"]
        Runner[Port & Süreç Yöneticisi]
        HTTP[/api/event & /api/mode]
        SSE[SSE Akışı /api/stream]
        Codegen[TestFly Java Kod Üretici]
    end

    subgraph Studio ["TestFly Web Studio (:8765)"]
        Timeline[Kayıtlı Adımlar Zaman Çizelgesi]
        LocatorTester[Akıllı Seçici Test Edici]
        CodePreview[Çoklu Mimari Kod Merkezi]
    end

    subgraph Project ["Test Projeniz"]
        Pages["src/test/java/.../pages/"]
        Tests["src/test/java/.../tests/"]
        Features["src/test/resources/features/"]
    end

    Injected -- "POST /api/event (tıklama, yazma, doğrulama)" --> HTTP
    HTTP --> Codegen
    Codegen --> SSE
    SSE -- Gerçek zamanlı kod & adımlar --> Studio
    Studio -- "Save to Project" --> Project
```

---

## Kaydediciyi Başlatma

### Komut Satırı Kullanımı

```bash
# Herhangi bir web sitesinde canlı kaydı başlat
testfly record https://example.com

# Özel port belirterek başlat (port doluysa otomatik artırılır)
testfly record https://example.com --port 9000

# Masaüstü tarayıcısını otomatik açmadan başlat
testfly record https://example.com --no-browser
```

### Otomatik Chrome Companion Başlatma
`testfly record` komutu çalıştırıldığında:
1. **Port Seçimi:** TestFly varsayılan olarak `8765` portunu dinler. Port başka bir uygulama tarafından kullanılıyorsa, sonraki 20 portu (`8766`, `8767`, ...) otomatik tarar ve ilk boş porta bağlanır.
2. **Web Güvenlik Parametreleri:** Google Chrome, izole bir kullanıcı profiliyle ve gevşetilmiş yerel güvenlik bayraklarıyla (`--disable-web-security`, `--allow-running-insecure-content`) açılır. Böylece yerel sunucuya yapılan çağrılarda CORS veya preflight engelleri yaşanmaz.
3. **CDP Betik Enjeksiyonu:** Chrome DevTools Protocol (`Page.addScriptToEvaluateOnNewDocument`) üzerinden `injected_recorder.js` betiği enjekte edilir. Bu sayede sayfa yenilendiğinde veya başka sayfalara geçildiğinde kaydedici kesintisiz çalışmaya devam eder.

---

## Etkileşimlerin Kaydedilmesi

### 1. Doğal Kullanıcı Eylemleri
Refakatçi Chrome penceresinde tıpkı bir son kullanıcı gibi gezinin:
- **Tıklamalar:** Erişilebilirlik öncelikli seçicilerle (`data-testid`, rol, ARIA etiketi, ID, CSS) yakalanır.
- **Metin Girişi:** Tuş vuruşları akıllı birleştirme (debouncing) ile toplanır. Her karakter ayrı bir işlem oluşturmak yerine ardışık girişler tek bir `enterUsername("standard_user")` adımında birleştirilir.
- **Form Kontrolleri:** Açılır menüler, onay kutuları ve butonlar akıcı TestFly metotlarına dönüştürülür.

### 2. Assertion (Doğrulama) Kayıt Modları
TestFly Studio, kod yazmadan görsel olarak doğrulama adımları eklemeniz için özel araç çubuğu butonları sunar:

| Doğrulama Modu | Eylem / Kısayol | Üretilen Kod |
| :--- | :--- | :--- |
| **👁️ Assert Visible** | İnceleme modunda öğeye tıklayın | `assertThat(getByTestId("item-header")).isVisible();` |
| **✅ Assert Enabled** | Etkinlik modunda öğeye tıklayın | `assertThat(getByTestId("submit-btn")).isEnabled();` |
| **💬 Assert Text** | Öğeye tıklayın; açılan modalda metni onaylayın | `assertThat(getByTestId("price")).hasText("29.99");` |

> [!TIP]
> **Assert Text** modunda bir öğeye tıkladığınızda Stüdyo üzerinde bir doğrulama penceresi açılır. Öğenin mevcut metnini kontrol edebilir, **Tam Eşleşme** (`hasText(...)`) veya **İçerik Eşleşmesi** (`containsText(...)`) tercihinizi belirleyebilirsiniz.

---

## Akıllı Seçici Test Edici (Smart Locator Tester)

Zaman çizelgesi panelinin alt kısmında canlı bir **Smart Locator Tester** bulunur:
1. Herhangi bir seçici ifadesi yazın veya yapıştırın (örn. `getByTestId("login-button")`, `//button[@type='submit']`, `#user-name`).
2. Durum rozeti, canlı sayfadaki eşleşme sayısını anında raporlar (`1 match`, `0 matches`, `N matches`).
3. Doğruladığınız seçiciyi **📋 Kopyala** butonuna tıklayarak panonuza alabilirsiniz.

---

## Çoklu Mimari Java Kod Üretimi

Tarayıcıda işlem yaptıkça sağ taraftaki kod paneli 4 farklı mimari için eşzamanlı olarak renklendirilmiş kod üretir:

### 1. Page Object Model (`pom`)
Sorumlulukları Page Object sınıfları ve test sınıfları olarak temiz bir şekilde ayırır:
- **BasePage Sınıfı:** Erişilebilirlik öncelikli `Locator` alanlarını ve akıcı aksiyon metotlarını barındırır.
- **BaseTest Sınıfı:** `open("/")` çağrısı ve web-first doğrulamalarla test akışını yürütür.

```java
// ============================================================
// File: com/example/pages/HomePage.java
// ============================================================
package com.example.pages;

import io.testfly.test.BasePage;
import io.testfly.locator.Locator;
import org.openqa.selenium.WebDriver;

public class HomePage extends BasePage {

    private final Locator username = getByTestId("username");
    private final Locator password = getByTestId("password");
    private final Locator login = getByTestId("login-button");
    private final Locator continueElement = getByTestId("continue");

    public HomePage(WebDriver driver) {
        super(driver);
    }

    public HomePage enterUsername(String text) {
        username.type(text);
        return this;
    }

    public HomePage clickLogin() {
        login.click();
        return this;
    }

    public HomePage clickContinueElement() {
        continueElement.click();
        return this;
    }
}
```

### 2. Bağımsız TestNG (`testng`)
TestFly'ın sürücü yaşam döngüsünü yönettiği, `BaseTest` genişleten tek dosyalı TestNG sınıfı üretir.

### 3. Bağımsız JUnit 5 (`junit5`)
`BaseJUnit5Test` sınıfını genişleten tek dosyalı JUnit 5 test sınıfı üretir.

### 4. Cucumber BDD (`cucumber`)
Eksiksiz üç katmanlı BDD paketi üretir:
- **Gherkin Feature:** `src/test/resources/features/*.feature`
- **Step Definitions:** `BaseCucumberSteps` genişleten `@When`, `@Then` adımları
- **Test Runner:** `BaseCucumberTest` genişleten koşucu sınıf

---

## Projeye Kaydetme & Temiz Dizin Yönlendirmesi

Stüdyoda **💾 Save to Project** butonuna tıkladığınızda kodlar tek bir dosya içerisine dökülmez; Maven/Gradle dizin standartlarına uygun olarak ayrı dosyalara yazılır.

### Dizin Yapısı
TestFly proje kökünü otomatik algılar ve dosyaları doğru dizinlere yerleştirir:

```
projeniz/
├── pom.xml
└── src/
    └── test/
        ├── java/
        │   └── com/example/
        │       ├── pages/
        │       │   └── HomePage.java            <-- Page Object (BasePage)
        │       └── tests/
        │           ├── HomeTest.java            <-- POM Testi (BaseTest)
        │           ├── RecordedWebTest.java     <-- TestNG / JUnit5 Testi
        │           ├── steps/
        │           │   └── HomeSteps.java       <-- Cucumber Step Tanımları
        │           └── runners/
        │               └── RunCucumberTest.java <-- Cucumber Koşucu
        └── resources/
            └── features/
                └── home.feature                 <-- Gherkin Feature Dosyası
```

### Derleyici Güvenlik Önlemleri
- **Java Rezerve Kelime Koruması:** Java dilinde anahtar kelime olan isimler (`continue`, `break`, `return`, `class`, `default`, `switch`, `goto`) otomatik olarak `continueElement`, `clickContinueElement()` gibi geçerli tanımlayıcılara dönüştürülür.
- **Sayısal Ön Ek Koruması:** Rakamla başlayan öğeler Java kurallarına uygun olarak `el1Items` şeklinde ön ek alır.
- **Yorum Satırlı Dosya Ayraçları:** Tüm dosya başlıkları Java için `//`, Gherkin için `#` yorum satırlarıyla işaretlenir; derleme sırasında sözdizimi hatalarının önüne geçilir.
