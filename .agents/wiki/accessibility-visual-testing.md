---
tags:
  - wiki
  - accessibility
  - axe-core
  - visual-assert
  - wcag
date: 2026-09-10
status: active
type: wiki
---

# Erişilebilirlik (axe-core) ve Görsel Regresyon (VisualAssert)

TestFly, WebUI testleri sırasında tek satır kodla WCAG 2.2 standartlarında erişilebilirlik taraması yapmayı ve piksel düzeyinde görsel karşılaştırma yapmayı destekler.

---

## 1. Dahili axe-core Erişilebilirlik Taraması (`accessibility()`)

TestFly çekirdeğinde **axe-core 4.10.2** yerleşiktir. `BaseTest` üzerinden harici kütüphane bağımlılığı olmadan çağrılır:

```java
@Test
public void odemeEkraniWcagAAUyumluOlmali() {
    open("/checkout");

    accessibility()
        .withTags("wcag2a", "wcag21aa", "wcag22aa")
        .withLevel(Impact.SERIOUS) // SERIOUS ve CRITICAL hatalarda testi patlatır
        .excluding("#third-party-chat-widget")
        .run();
}
```

---

## 2. Piksel Hassasiyetinde Görsel Regresyon (`VisualAssert`)

TestFly `VisualAssert` motoru ile tüm sayfa veya spesifik DOM elemanlarının ekran görüntüsü referansla karşılaştırılır:

```java
@Test
public void anaSayfaGorselRegresyonTesti() {
    open("/");

    // %1 tolerans payı ile tam sayfa karşılaştırması
    VisualAssert.assertScreenshot("ana-sayfa", VisualTolerance.of(1));

    // Belirli bir kart veya butonun piksel kontrolü
    VisualAssert.assertScreenshot("login-card", By.id("login-card"));
}
```

- **Referans Üretimi:** İlk koşumda otomatik `baseline` ekran görüntüsü oluşturulur.
- **Fark Tespiti:** Uyuşmazlık durumunda diff görseli `target/visual-diffs/` klasörüne yazılır.
- **Güncelleme:** `-DupdateBaselines=true` ile referanslar tek komutla yenilenir.

---

## İlgili Bağlantılar
- WebUI Test Mimarisi: `[[wiki/webui-testing]]`
- Ana Harita: `[[MAP]]`
- Wiki Dizin: `[[wiki/index]]`
