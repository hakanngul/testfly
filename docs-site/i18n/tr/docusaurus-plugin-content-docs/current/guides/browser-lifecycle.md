---
description: "Selenium tarayıcı yaşam döngüsünü kontrol edin: yürütme hızını test izolasyonuna karşı dengelemek için per-test veya per-suite WebDriver oturumlarını seçin."
id: browser-lifecycle
title: Tarayıcı Yaşam Döngüsü
sidebar_position: 7
---

# Tarayıcı Yaşam Döngüsü

WebDriver oturumunun ne zaman oluşturulduğunu ve kapatıldığını kontrol edin.

---

## Yapılandırma

```yaml title="testfly.yml"
browser:
  lifecycle: per-test   # per-test (default) | per-suite
```

---

## `per-test` (varsayılan)

Her test metodundan önce yeni bir tarayıcı açılır ve hemen ardından kapanır.

```
Test 1 starts → Chrome opens → test runs → Chrome closes
Test 2 starts → Chrome opens → test runs → Chrome closes
Test 3 starts → Chrome opens → test runs → Chrome closes
```

**En uygun:** Bağımsız testler, tam izolasyon, CI boru hatları.

---

## `per-suite`

Tarayıcı, thread başına bir kez açılır ve suite'in tamamı boyunca açık kalır. Suite bittiğinde temiz şekilde kapatılır.

```
Suite starts
  Thread 1: Chrome opens
    Test 1 runs → browser stays open
    Test 2 runs → browser stays open
    Test 3 runs → browser stays open
  Thread 1: Chrome closes
Suite ends
```

**En uygun:** Tarayıcı başlatma süresinin darboğaz olduğu büyük sıralı suite'ler veya testlerin kimliği doğrulanmış bir oturumu paylaştığı test akışları.

---

## Paralel + per-suite

Paralel çalıştırmada her thread, kendi tarayıcısını bağımsız olarak yönetir.

```
Thread 1: Chrome opens → runs Test A, B, C → Chrome closes
Thread 2: Chrome opens → runs Test D, E, F → Chrome closes
```

Semaphore, maksimum eşzamanlı tarayıcı sayısını (`maxActiveSessions`) yine de sınırlar.

---

## Testler arasında durum yönetimi

`per-suite` ile tarayıcı, önceki testten gelen tüm durumu (çerezler, URL, localStorage) korur.

Testleriniz bağımsızsa **durumu açıkça sıfırlayın**:

```java
@Test
public void independentTest() {
    open();  // navigate to baseUrl — resets the page
    getDriver().manage().deleteAllCookies();  // clear session if needed
    // ...
}
```

Bağımlı akışlar için duruma **kasıtlı olarak güvenin**:

```java
@Test(priority = 1)
public void login() {
    open("/login");
    new LoginPage(getDriver()).login("admin", "secret");
    // browser now has an authenticated session
}

@Test(priority = 2, dependsOnMethods = "login")
public void viewDashboard() {
    // no login needed — session cookie is still in the browser
    open("/dashboard");
    softAssert().that(new DashboardPage(getDriver()).isLoaded(), "Dashboard should be loaded");
}

@Test(priority = 3, dependsOnMethods = "viewDashboard")
public void editProfile() {
    open("/profile");
    // still authenticated
}
```

---

## Hata durumunda ekran görüntüleri

Ekran görüntüleri `per-suite` ile yine de doğru çalışır — hata ekran görüntüsü alındığında tarayıcı hâlâ açıktır.

---

## `per-suite` ile Retry

Bir test yeniden denendiğinde aynı tarayıcı örneği yeniden kullanılır. Yeniden başlatma gerçekleşmez. Önceki deneme tarayıcıyı bozuk bir durumda bıraktıysa, bilinen bir sayfaya dönmek için testinizin başında `open()` çağırın.