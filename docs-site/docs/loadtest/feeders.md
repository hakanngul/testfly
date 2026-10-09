---
id: feeders
title: Data Feeders & Parameterization
sidebar_position: 5
---

# Data Feeders & Parameterization

Attach one feeder per scenario. Variables are substituted in paths, headers and payloads using `${name}`. Use `queryParam` for URL parameters.

| Factory | Values |
| --- | --- |
| `csv("testdata/search.csv")` | CSV columns become variables; rows cycle in file order. |
| `json("testdata/products.json")` | JSON array of objects; rows cycle in file order. |
| `random("userId", 1, 10000)` | Random integer, both bounds inclusive. |
| `uuid("requestId")` | New UUID value. |
| `sequence("orderId", 1000, 1)` | Sequence starting at 1000, incrementing by 1. |
| `constant("tenant", "demo")` | Fixed string value. |

There are no `fromCsv`, `fromList`, `fromSupplier`, `circular()` or parameterless `random()` factory/strategy methods. CSV and JSON feeders cycle automatically; the built-in CSV parser is a simple comma-delimited parser, not a full quoted-field CSV parser. Supply simple data without embedded commas and verify the file exists before execution.

## Example

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


CSV/JSON files may be on the filesystem or classpath. Share synthetic credentials only; do not commit real passwords. See the [multi-step example](./examples.md) for using a feeder in a JSON login body.
