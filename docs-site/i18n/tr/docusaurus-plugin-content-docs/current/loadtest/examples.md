---
id: examples
title: Yük Testi Örnekleri ve Tarifleri
description: "Üretime hazır örnekler: Tekil API uç noktaları, çok adımlı alışveriş sepeti akışları, CSV veri besleme ve hibrit UI + yük testi kalıpları."
sidebar_position: 9
---

# Yük Testi Örnekleri ve Tarifleri

Aşağıda TestFly ile yazılmış gerçek dünya senaryolarına uygun yük testi tarifleri yer almaktadır.

---

## 1. Hızlı Sağlık Kontrolü (Smoke Test)

Bir API ağ geçidinin 20 eşzamanlı bağlantı altında SLA değerlerini karşıladığını hızlıca test edin:

```java
package com.example.load;

import io.testfly.test.BaseTest;
import org.testng.annotations.Test;
import java.time.Duration;

public class SaglikKontroluYukTesti extends BaseTest {

    @Test
    public void testSaglikKontroluSla() {
        load("/actuator/health")
            .users(20)
            .rampUp(Duration.ofSeconds(2))
            .hold(Duration.ofSeconds(10))
            .run()
            .assertThroughputAbove(50.0)
            .assertP95Below(100)
            .assertNoStatus(500)
            .assertNoStatus(503);
    }
}
```

---

## 2. CSV Besleyicili Çok Adımlı Alışveriş Akışı

Gerçek kullanıcı davranışını simüle edin: arama yapma, detay görüntüleme, sepete ekleme ve sipariş tamamlama:

```java
package com.example.load;

import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadTestFeeder;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;
import java.time.Duration;

public class MusteriYolculuguYukTesti extends BaseTest {

    @Test
    public void testMusteriYolculugu() {
        LoadTestFeeder urunBesleyici = LoadTestFeeder.fromCsv("testdata/urunler.csv").random();

        LoadScenario senaryo = LoadScenario.named("Mağaza Müşteri Yolculuğu")
            .step("Ürün Arama", req -> req.get("/api/products?query=${aramaKelimesi}"))
            .step("Ürün Detayı", req -> req.get("/api/products/${urunId}"))
            .step("Sepete Ekle", req -> req.post("/api/cart")
                                          .header("Content-Type", "application/json")
                                          .body("{\"productId\": \"${urunId}\", \"qty\": 1}"))
            .step("Sipariş Ver", req -> req.post("/api/checkout")
                                          .header("Content-Type", "application/json")
                                          .body("{\"paymentMethod\": \"CARD\", \"amount\": ${fiyat}}"));

        load(senaryo)
            .feed(urunBesleyici)
            .users(30)
            .rampUp(Duration.ofSeconds(10))
            .hold(Duration.ofSeconds(30))
            .run()
            .assertThroughputAbove(15.0)
            .assertStepP95Below("Ürün Arama", 200)
            .assertStepP95Below("Sipariş Ver", 600)
            .assertErrorRateBelow(0.01);
    }
}
```

---

## 3. Hibrit UI ve Yük Testi Birlikte Çalışma Kalıbı

Arka planda API'ye yük bindirilirken, aynı anda ön yüzde Selenium WebDriver ile kullanıcı deneyiminin bozulmadığını doğrulayın:

```java
package com.example.hybrid;

import io.testfly.test.BaseTest;
import org.testng.annotations.Test;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class HibritUiVeYukTesti extends BaseTest {

    @Test
    public void testArkaPlanYukuAltindaOnYuzTepkiselligi() {
        // 1. Arka planda asenkron API yük simülasyonunu başlat
        CompletableFuture<Void> yukGorevi = CompletableFuture.runAsync(() -> {
            load("/api/orders/simulate")
                .users(50)
                .hold(Duration.ofSeconds(20))
                .run()
                .assertErrorRateBelow(0.02);
        });

        // 2. BaseTest üzerinden WebDriver ile tarayıcıda ön yüz testi yap
        open("/dashboard");
        assertVisible(getByRole("heading", "Sistem Durumu"));
        click(getByRole("button", "Metrikleri Yenile"));
        assertVisible(getByText("Tüm sistemler çalışıyor"));

        // 3. Arka plan yük testinin başarıyla bittiğinden emin ol
        yukGorevi.join();
    }
}
```
