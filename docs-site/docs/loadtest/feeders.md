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

There are no `fromCsv`, `fromList`, `fromSupplier`, `circular()` or parameterless `random()` factory/strategy methods. CSV and JSON feeders cycle automatically; the built-in CSV parser is a simple comma-delimited parser, not a full quoted-field CSV parser. Supply simple data without embedded commas.

## Missing or empty files fail fast

A `csv` or `json` feeder file is looked up in this order: on the classpath, then as a filesystem path, then under `src/test/resources/` + path.

If the file is not found, or it has no data rows (for example a CSV with only a header, or an empty JSON array), the run is rejected before any request is sent. It fails with an `IllegalArgumentException` that names the scenario and the path and says the file was "not found or has no rows". This applies to both engines and to every scenario of a [simulation](./simulations-sla.md).

`LoadTestFeeder.csv(..)` and `json(..)` themselves do not throw: they log a WARN and return the feeder, and the check runs when the scenario is validated.

:::caution Behaviour change in 1.1.0
Earlier versions ran a scenario with a missing or empty CSV/JSON feeder anyway, and every `${var}` stayed unresolved.
:::

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


CSV/JSON files may be on the classpath or the filesystem (see the lookup order above). Share synthetic credentials only; do not commit real passwords. See the [multi-step example](./examples.md) for using a feeder in a JSON login body.
