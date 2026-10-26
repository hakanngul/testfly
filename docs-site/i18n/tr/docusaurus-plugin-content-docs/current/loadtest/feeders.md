---
id: feeders
title: Veri Besleyiciler ve Parametrelendirme (Feeders)
sidebar_position: 5
---

# Feeder ve Parametrizasyon

Senaryoya bir feeder bağlanır. `${name}` değişkenleri path, header ve body içinde çözülür. URL parametrelerinde `queryParam` kullanın.

| Factory | Değerler |
| --- | --- |
| `csv("testdata/search.csv")` | CSV sütunları değişken olur; satırlar dosya sırasıyla döngüye girer. |
| `json("testdata/products.json")` | JSON nesne dizisi; satırlar dosya sırasıyla döngüye girer. |
| `random("userId", 1, 10000)` | Her iki sınır dahil rastgele tamsayı. |
| `uuid("requestId")` | Yeni UUID değeri. |
| `sequence("orderId", 1000, 1)` | 1000’den başlayan, 1 artan sıra. |
| `constant("tenant", "demo")` | Sabit string değeri. |

`fromCsv`, `fromList`, `fromSupplier`, `circular()` veya parametresiz `random()` factory/strateji metotları yoktur. CSV ve JSON otomatik döngü yapar. Yerleşik CSV ayrıştırıcısı basit virgülle ayırma kullanır; tam quoted-field CSV parser değildir. İçinde virgül bulunmayan basit veri kullanın.

## Eksik veya boş dosyalar hemen hata verir

Bir `csv` veya `json` feeder dosyası şu sırayla aranır: önce classpath'te, sonra dosya sistemi yolu olarak, sonra `src/test/resources/` + yol altında.

Dosya bulunamazsa veya hiç veri satırı yoksa (örneğin yalnızca başlık satırı olan bir CSV veya boş bir JSON dizisi), koşu herhangi bir istek gönderilmeden reddedilir. Senaryoyu ve yolu belirten ve dosyanın "not found or has no rows" (bulunamadı veya satırı yok) olduğunu söyleyen bir `IllegalArgumentException` ile başarısız olur. Bu, iki motor için ve bir [simülasyonun](./simulations-sla.md) her senaryosu için geçerlidir.

`LoadTestFeeder.csv(..)` ve `json(..)` kendileri exception fırlatmaz: bir WARN loglar ve feeder'ı döndürür; denetim senaryo doğrulanırken çalışır.

:::caution 1.1.0'da davranış değişikliği
Önceki sürümler eksik veya boş bir CSV/JSON feeder'ı olan senaryoyu yine de çalıştırıyordu ve her `${var}` çözümlenmemiş kalıyordu.
:::

## Örnek

```csv title="testdata/search.csv"
query,clientId
laptop,client-1
keyboard,client-2
```


```java
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadTestFeeder;
import org.testng.annotations.Test;

public class SearchLoadTest extends BaseLoadTest {
    @Test
    public void searchWithData() {
        loadScenario("Search").users(10)
            .feed(LoadTestFeeder.csv("testdata/search.csv"))
            .step("Search").get("/search")
                .queryParam("q", "${query}")
                .header("X-Client-ID", "${clientId}").and()
            .run().assertP95Below(300);
    }
}
```


CSV/JSON dosyaları classpath veya dosya sistemi üzerinde bulunabilir (yukarıdaki arama sırasına bakın). Sentetik kimlik bilgileri kullanın; gerçek parolaları commit etmeyin. JSON login body’sinde feeder kullanımı için [çok adımlı örneğe](./examples.md) bakın.
