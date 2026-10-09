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

`fromCsv`, `fromList`, `fromSupplier`, `circular()` veya parametresiz `random()` factory/strateji metotları yoktur. CSV ve JSON otomatik döngü yapar. Yerleşik CSV ayrıştırıcısı basit virgülle ayırma kullanır; tam quoted-field CSV parser değildir. İçinde virgül bulunmayan basit veri kullanın ve dosyanın varlığını çalıştırmadan önce doğrulayın.

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


CSV/JSON dosyaları filesystem veya classpath üzerinde bulunabilir. Sentetik kimlik bilgileri kullanın; gerçek parolaları commit etmeyin. JSON login body’sinde feeder kullanımı için [çok adımlı örneğe](./examples.md) bakın.
