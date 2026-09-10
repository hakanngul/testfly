---
id: feeders
title: Veri Besleyiciler ve Parametrelendirme (Feeders)
description: "CSV dosyalarından, JSON yapılarından, dairesel listelerden veya dinamik tedarikçilerden yük senaryolarına dinamik veri aktarımı."
sidebar_position: 5
---

# Veri Besleyiciler ve Parametrelendirme (Feeders)

Statik isteklerle yapılan yük testleri, veritabanı veya uygulama önbellekleri nedeniyle yanıltıcı derecede hızlı sonuç verebilir. TestFly'ın `LoadTestFeeder` mekanizması, sanal kullanıcılar arasında farklı verileri dolaştırarak gerçekçi senaryolar üretir.

---

## 1. Veri Besleyici Oluşturma

TestFly birden fazla veri kaynağını destekler:

### A. CSV Besleyicisi
Tablo biçimindeki verileri doğrudan sınıf yolundan veya dosya sisteminden yükleyin:

```java
import io.testfly.loadtest.LoadTestFeeder;

// data/kullanicilar.csv dosyasını okur (sütunlar: kullaniciAdi, sifre)
LoadTestFeeder kullaniciBesleyici = LoadTestFeeder.fromCsv("data/kullanicilar.csv");
```

### B. Bellek İçi Liste / Harita
```java
List<Map<String, Object>> aramaTerimleri = List.of(
    Map.of("query", "laptop", "maxPrice", 1200),
    Map.of("query", "klavye", "maxPrice", 80),
    Map.of("query", "monitor", "maxPrice", 300)
);

LoadTestFeeder aramaBesleyici = LoadTestFeeder.fromList(aramaTerimleri);
```

### C. Dinamik Lambda / Üretici Fonksiyon
Her istekte rastgele veya hesaplanmış yeni değerler üretin:

```java
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

LoadTestFeeder dinamikBesleyici = LoadTestFeeder.fromSupplier(() -> Map.of(
    "siparisId", UUID.randomUUID().toString(),
    "tutar", ThreadLocalRandom.current().nextInt(50, 1000)
));
```

---

## 2. Dağıtım Stratejileri

Kayıtların sanal kullanıcılar arasında nasıl dağıtılacağını belirleyin:

- **Dairesel / Round-Robin (Varsayılan):** Veri setinin sonuna gelindiğinde başa döner.
  ```java
  LoadTestFeeder feeder = LoadTestFeeder.fromCsv("kullanicilar.csv").circular();
  ```
- **Rastgele (Random):** Veri kümesinden rastgele seçim yapar.
  ```java
  LoadTestFeeder feeder = LoadTestFeeder.fromCsv("urunler.csv").random();
  ```

---

## 3. Besleyicileri Senaryoda Kullanma

Besleyiciler, `${degisken}` yer tutucularını kullanarak URL'lere, başlıklara ve JSON gövdelerine otomatik enjekte edilir:

```java
@Test
public void testGirisliArama() {
    LoadTestFeeder feeder = LoadTestFeeder.fromCsv("data/arama.csv");

    load("/api/search?q=${query}&limit=${limit}")
        .feed(feeder)
        .header("X-Musteri-No", "${musteriNo}")
        .users(20)
        .run()
        .assertP95Below(200);
}
```

### POST Gövdesine Veri Enjeksiyonu

```java
String sablon = """
    {
      "urunKodu": "${urunKodu}",
      "adet": ${adet},
      "postaKodu": "${postaKodu}"
    }
""";

load("/api/kargo/hesapla")
    .feed(LoadTestFeeder.fromCsv("data/gonderiler.csv"))
    .post(sablon)
    .header("Content-Type", "application/json")
    .users(15)
    .run()
    .assertErrorRateBelow(0.01);
```
