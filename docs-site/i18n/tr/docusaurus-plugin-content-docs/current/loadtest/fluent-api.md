---
id: fluent-api
title: Akıcı (Fluent) Yük Testi API'si
description: "Tekil uç noktalardan çok adımlı kullanıcı yolculuklarına kadar TestFly akıcı API'si ile esnek yük senaryoları tasarlayın."
sidebar_position: 3
---

# Akıcı (Fluent) Yük Testi API'si

TestFly Yük Testi API'si, mevcut UI ve API test kalıplarıyla kusursuz bir uyum yakalamak üzere tasarlanmıştır. `load(url)` çağrısıyla başlayarak eşzamanlılık ayarlarını, başlıkları (headers), gövdeleri (payloads) ve çok adımlı eylemleri kolayca zincirleyebilirsiniz.

---

## 1. Tekil Uç Nokta Yük Testi

Mikroservis sağlık kontrolleri veya tekil REST metotları için `load(...)` metodunu doğrudan çağırın:

```java
load("https://api.example.com/items")
    .get()                                    // varsayılan GET'tir
    .header("Accept", "application/json")
    .queryParam("category", "electronics")
    .users(25)
    .rampUp(Duration.ofSeconds(5))
    .hold(Duration.ofSeconds(20))
    .run();
```

### JSON Gövdesi ile POST İsteği

```java
String jsonGovde = """
    {
      "sku": "PROD-998",
      "quantity": 1
    }
""";

load("/api/cart/add")
    .post(jsonGovde)
    .header("Content-Type", "application/json")
    .users(15)
    .run();
```

Desteklenen HTTP metotları: `.get()`, `.post(body)`, `.put(body)`, `.delete()`, `.patch(body)`.

---

## 2. Çok Adımlı Kullanıcı Yolculukları (User Journeys)

Gerçek kullanıcılar sadece tek bir adresi sorgulamaz; gezinir, sepete ekler ve ödeme yapar. TestFly bu akışları `step(name, request)` ile modeller:

```java
import io.testfly.loadtest.LoadScenario;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;
import java.time.Duration;

public class SiparisAkisiYukTesti extends BaseTest {

    @Test
    public void kullaniciAlisverisAkisi() {
        LoadScenario scenario = LoadScenario.named("E-Ticaret Kullanıcı Yolculuğu")
            .step("Katalog Listeleme", req -> req.get("/api/products?page=1"))
            .step("Ürün Detayı", req -> req.get("/api/products/42"))
            .step("Sepete Ekle", req -> req.post("/api/cart")
                                          .header("Content-Type", "application/json")
                                          .body("{\"productId\": 42, \"quantity\": 1}"))
            .step("Ödeme Yap", req -> req.post("/api/checkout")
                                       .header("Authorization", "Bearer mock-token"));

        load(scenario)
            .users(30)
            .rampUp(Duration.ofSeconds(10))
            .hold(Duration.ofSeconds(30))
            .run()
            .assertThroughputAbove(20.0)
            .assertStepP95Below("Ödeme Yap", 500)
            .assertErrorRateBelow(0.02);
    }
}
```

---

## 3. Eşzamanlılık Profili Ayarları

Kullanıcı geliş hızlarını ve artış eğrilerini özelleştirin:

```java
load("/api/search")
    .users(100)                     // Ulaşılacak zirve sanal kullanıcı sayısı
    .rampUp(Duration.ofSeconds(15)) // 1'den 100 kullanıcıya doğrusal çıkış süresi
    .hold(Duration.ofSeconds(45))   // Zirve yükün korunacağı süre
    .targetRps(250)                 // İsteğe bağlı üst limit: saniyede max 250 istek
    .run();
```

---

## 4. Metot Özeti

| Metot | Açıklama |
| :--- | :--- |
| `load(String url)` | Tek bir URL için yük testi başlatır. |
| `load(LoadScenario scenario)` | Çok adımlı kullanıcı senaryosu için yük testi başlatır. |
| `users(int count)` | Eşzamanlı sanal kullanıcı sayısını belirler. |
| `rampUp(Duration duration)` | Doğrusal artış süresini ayarlar. |
| `hold(Duration duration)` | Zirve kullanıcı sayısının korunacağı süreyi ayarlar. |
| `targetRps(int rps)` | Saniyedeki istek hızını sınırlar. |
| `header(String name, String value)` | HTTP başlığı ekler. |
| `queryParam(String name, String value)` | URL sonuna sorgu parametresi ekler. |
| `feed(LoadTestFeeder feeder)` | Senaryoya dinamik veri besleyici bağlar. |
| `engine(String engine)` | Motor seçimini geçersiz kılar (`"auto"`, `"gatling"`, `"jdk"`). |
| `run()` | Testi çalıştırır ve geriye `LoadTestAssert` döndürür. |
