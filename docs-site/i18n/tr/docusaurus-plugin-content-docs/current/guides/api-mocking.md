---
title: "İstemci Mock Kuralları"
---

# İstemci Mock Kuralları

`ApiMockRule`, hazırlanmış isteği eşleştirip ağa çıkmadan sentetik yanıt üretir. Önce istek, sonra test kapsamındaki kurallar değerlendirilir; ilk eşleşme kazanır. Eşleşme yoksa normal HTTP gönderimi yapılır.

```java
ApiMockRule rule = ApiMockRule.builder()
    .match(r -> r.method().equals("POST") && r.uri().getPath().equals("/payment"))
    .respond(r -> ApiResponse.builder().request(r).status(200)
        .header("Content-Type", "application/json")
        .body("{\"status\":\"mocked\"}").build())
    .build();
ApiClient.post("http://localhost:1/payment").mockRule(rule).send().assertStatus(200);
ApiClient.addMockRule(rule);
ApiClient.post("http://localhost:1/payment").sendAsync().join().assertStatus(200);
ApiClient.clearMockRules();
```

Kurallar çağrı başında yakalanır; interceptor değişikliklerinden sonra ve legacy request hook’larından önce çalışır. Sentetik sonuç response hook ve YAML status retry akışına katılır. Predicate/factory hataları retry edilmez. Test kapanışı kayıtları temizler. Paralel kullanımda predicate/factory thread-safe olmalıdır. WireMock desteği bağımsızdır.

Çalıştırılabilir örnek: `mvn test -Dtest=ApiMockExamplesTest`.

## WireMock sunucusu (`ApiMockServer`)

`MockSupport` uygulayan sınıflar (`BaseApiTest` dahil) `mockServer()` ve `stubGet(...)` metotlarını alır. Opsiyonel `org.wiremock:wiremock-standalone` bağımlılığı test classpath'inde olmalıdır.

```java
mockServer().stubJson("POST", "/orders", 201, "{\"id\":7}");
mockServer().stubFor(WireMock.get("/health").willReturn(WireMock.ok("UP")));
ApiClient.to(mockServer().getBaseUrl()).path("/orders").post().body("{}").send().assertStatus(201);
```

Sunucu JVM genelinde tekildir. `MockSupport.resetMocks()` her TestNG test metodundan sonra çalışır ve **tüm** stub'ları silen `reset()`'i çağırır. Paralel testlerde stub'ları `stubFor`, `stubJson` veya `stubGet` ile kaydedin ve `resetMocks()`'u yalnızca mevcut thread'in stub'larını silecek şekilde override edin:

```java
@Override
@AfterMethod(alwaysRun = true)
public void resetMocks() {
    mockServer().resetOwnStubs();
}
```

Statik `WireMock.stubFor(...)` DSL'i aynı sunucuyu hedefler ama paralel testler için güvenli değildir.
