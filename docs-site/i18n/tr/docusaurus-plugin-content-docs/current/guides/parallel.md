---
description: "Paralel Selenium test yürütme: thread sayısını testfly.yml içinde ayarlayın; thread güvenli, izole WebDriver örnekleri otomatik olarak sağlanır."
id: parallel
title: Selenium Paralel Test
sidebar_label: Paralel Yürütme
sidebar_position: 5
---

# Paralel Yürütme

TestFly paralel test yürütmeyi hazır olarak destekler. Thread sayısını `testfly.yml` içinde yapılandırın; framework thread güvenli driver yönetimini otomatik olarak halleder.

---

## Yapılandırma

```yaml title="testfly.yml"
execution:
  mode: local
  parallel: methods       # none (varsayılan) | methods | classes | tests | instances
  threadCount: 4          # eşzamanlı tarayıcı oturumu sayısı
  maxActiveSessions: 4    # eşzamanlı tarayıcılar için semaphore tavanı

browser:
  name: chrome

timeouts:
  explicit: 10
  pageLoad: 30
```

`parallel`, `threadCount` ve `maxActiveSessions` değerlerinin tümü `execution:` altında yer alır — bkz. [Yapılandırma Referansı](/docs/configuration#execution). `timeouts.explicit` ve `timeouts.pageLoad`, paralel olsun olmasın her `testfly.yml` için zorunludur.

`maxActiveSessions`, eşzamanlı tarayıcıların üzerinde katı bir tavan görevi görür. `threadCount` 4 ancak `maxActiveSessions` 2 ise, aynı anda en fazla 2 tarayıcı çalışır.

---

## TestNG suite dosyası

Paralel yürütme bir TestNG suite XML dosyası gerektirir:

```xml title="testng.xml"
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<suite name="TestFly" parallel="methods" thread-count="4">
    <test name="All Tests">
        <classes>
            <class name="com.example.tests.LoginTest"/>
            <class name="com.example.tests.CheckoutTest"/>
            <class name="com.example.tests.SearchTest"/>
        </classes>
    </test>
</suite>
```

Maven Surefire ile çalıştırın:

```xml title="pom.xml"
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
    <configuration>
        <suiteXmlFiles>
            <suiteXmlFile>testng.xml</suiteXmlFile>
        </suiteXmlFiles>
    </configuration>
</plugin>
```

---

## Thread güvenliği nasıl çalışır

| Konu | TestFly bunu nasıl halleder |
|---|---|
| WebDriver örneği | `ThreadLocal<WebDriver>` — her thread'in kendi driver'ı vardır |
| Yapılandırma erişimi | `AtomicReference` — okumalar kilitsizdir |
| Adım kaydı | Test kimliğiyle anahtarlanmış `ConcurrentHashMap` |
| Oturum sınırlama | `Semaphore` (adil) — driver oluşturulmadan önce edinilir, quit sonrası serbest bırakılır |
| Ekran görüntüsü yakalama | Driver'ı `ThreadLocal`'dan okur — her zaman doğru örnek |

Testlerinizde özel bir şey yapmanız gerekmez. `getDriver()` her zaman çağıran thread'in driver'ını döndürür.

---

## Paralel modlar

`execution.parallel`, hiçbir test çalışmadan önce suite başlangıcında TestNG'nin kendi paralel modlarına karşı doğrulanır. Tanınmayan bir değer, hem reddedilen değeri hem de kabul edilenleri belirten bir mesajla anında başarısız olur.

| Mod | Açıklama | Önerilir |
|---|---|---|
| `none` | Sıralı yürütme (varsayılan) | |
| `methods` | Her test metodu kendi thread'inde çalışır | En iyi genel seçim |
| `classes` | Her test sınıfı bir thread'de çalışır | Bir sınıf içindeki testlerin sıralı olması gerektiğinde kullanın |
| `tests` | Suite XML'deki her `<test>` bir thread'de çalışır | Suite düzeyindeki gruplamaları izole etmek için kullanın |
| `instances` | Her test sınıfı örneği bir thread'de çalışır | Nadiren gerekir — factory tabanlı suite'ler |

---

## Paralel + suite başına yaşam döngüsü

`browser.lifecycle: per-suite` paralel yürütmeyle birleştirildiğinde, her thread kendi tarayıcısını bir kez açar ve onu o thread'deki tüm testler için yeniden kullanır.

```
Thread 1: Chrome açılır → Test A → Test B → Test C → Chrome kapanır
Thread 2: Chrome açılır → Test D → Test E → Test F → Chrome kapanır
```

`maxActiveSessions` toplam eşzamanlı tarayıcıları yine de sınırlar.

---

## Paralel güvenli testler yazma

**Statik değiştirilebilir durum kullanmayın** — statik alanlar thread'ler arasında paylaşılır:

```java
// ❌ thread güvenli değil
public class LoginTest extends BaseTest {
    private static LoginPage loginPage;   // paylaşılan — thread'ler birbirinin üzerine yazar

    @Test
    public void login() {
        loginPage = new LoginPage(getDriver());
        loginPage.login("admin", "secret");
    }
}
```

```java
// ✅ thread güvenli — örnek alanı, her thread'in kendi test örneği vardır
public class LoginTest extends BaseTest {
    private LoginPage loginPage;

    @Test
    public void login() {
        loginPage = new LoginPage(getDriver());
        loginPage.login("admin", "secret");
    }
}
```

TestNG, thread başına her test sınıfının yeni bir örneğini oluşturur; bu nedenle örnek alanları güvenlidir.

---

## Paralel yürütmeyi devre dışı bırakma

```yaml
execution:
  parallel: none
```

Ya da `execution.parallel` değerini kısaca atlayın — `none` (sıralı yürütme) varsayılandır.