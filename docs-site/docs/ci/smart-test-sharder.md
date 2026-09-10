---
id: smart-test-sharder
title: Smart Test Sharder (LPT Bin-Packing)
sidebar_label: Smart Test Sharder
sidebar_position: 6
description: "Distribute your test suite across parallel CI/CD nodes using the Longest Processing Time (LPT) bin-packing algorithm to eliminate pipeline bottlenecks."
---

# Smart Test Sharder

The **Smart Test Sharder** eliminates the "straggler bottleneck" in CI/CD pipelines. Rather than splitting tests naively (alphabetical or round-robin), it applies the **Longest Processing Time (LPT) Bin-Packing algorithm** to partition tests across parallel workers so that every worker finishes at virtually the exact same moment.

```
Without Smart Sharder (Round-Robin / Naive):
Worker 1 [4m]  ████
Worker 2 [18m] ██████████████████  ← Pipeline blocked for 18m
Worker 3 [2m]  ██
Worker 4 [5m]  █████

With Smart Test Sharder (LPT Bin-Packing):
Worker 1 [7m 15s] ███████
Worker 2 [7m 20s] ███████
Worker 3 [7m 18s] ███████
Worker 4 [7m 12s] ███████          ← Pipeline completes in 7m 20s (59% faster!)
```

---

## How It Works

1. **Historical Duration Resolution:** TestFly reads past test execution durations from `target/testfly-metrics.json` (or `testfly-metrics-history/`).
2. **LPT Sorting:** All test items are sorted in descending order of duration ($t_1 \ge t_2 \ge \dots \ge t_n$).
3. **Greedy Min-Heap Assignment:** Each test is assigned to the worker node with the lowest currently accumulated workload.
4. **4/3-Approximation Guarantee:** LPT is mathematically proven to provide near-optimal multiprocessor scheduling, minimizing the makespan (maximum completion time).

---

## Option 1 — Zero-Config TestNG Sharding (Recommended)

When using TestFly with TestNG, simply provide the `-Dtestfly.shard.total` and `-Dtestfly.shard.index` system properties. TestFly's built-in `ShardingMethodInterceptor` automatically filters the suite without requiring any custom XML files:

```bash
# Node 1 of 4 (Index 0)
mvn test -Dtestfly.shard.total=4 -Dtestfly.shard.index=0

# Node 2 of 4 (Index 1)
mvn test -Dtestfly.shard.total=4 -Dtestfly.shard.index=1
```

### Auto-Detection for CI Environments

TestFly automatically reads standard CI node environment variables if present:
- `CI_NODE_TOTAL`
- `CI_NODE_INDEX`

If these environment variables are set (e.g. in GitLab CI, CircleCI, or custom Kubernetes jobs), you do not even need to pass `-Dtestfly.shard.*` — TestFly shards automatically!

---

## Option 2 — CLI Dynamic Test Selection (`testfly shard`)

For custom Maven Surefire commands, JUnit, or shell scripts, you can use the TestFly CLI to dynamically generate the `-Dtest` pattern:

```bash
# Output comma-separated test class names for current shard
mvn test -Dtest=$(testfly shard --total 4 --index $CI_NODE_INDEX)
```

### CLI Options

```bash
testfly shard --help
```

| Flag | Description | Default |
| :--- | :--- | :--- |
| `--total`, `-t` | Total number of parallel worker shards. | `2` |
| `--index`, `-i` | 0-based index of the current worker node. | `0` |
| `--metrics`, `-m` | Path to `testfly-metrics.json`. | `target/testfly-metrics.json` |
| `--format`, `-f` | Output format: `surefire`, `json`, `testng-xml`, `dashboard`. | `surefire` |
| `--output`, `-o` | Optional file path to write output. | `None` |

---

## Configuration via `testfly.yml`

You can also specify default sharding behaviors in `testfly.yml`:

```yaml title="testfly.yml"
execution:
  sharding:
    enabled: true
    total: 4
    index: 0
    strategy: lpt # "lpt" (recommended) or "round-robin"
    metricsFile: target/testfly-metrics.json
```

---

## Weight Annotations (`@TestWeight`)

For newly created tests that do not have historical metrics yet, or especially long end-to-end flows, you can declare explicit estimates using `@TestWeight`:

```java
import io.testfly.sharding.TestWeight;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class CheckoutE2ETest extends BaseTest {

    @TestWeight(seconds = 90)
    @Test(description = "Full checkout flow with 3D Secure payment")
    public void testFullCheckoutFlow() {
        // ...
    }
}
```

---

## CI/CD Pipeline Examples

### GitHub Actions Matrix

```yaml title=".github/workflows/test.yml"
name: Parallel Test Suite

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        shard: [0, 1, 2, 3] # 4 parallel workers

    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'maven'

      # Cache previous run metrics for optimal LPT bin-packing
      - name: Restore TestFly Metrics
        uses: actions/cache@v4
        with:
          path: target/testfly-metrics.json
          key: testfly-metrics-${{ github.ref_name }}
          restore-keys: |
            testfly-metrics-

      - name: Run Sharded Tests
        run: |
          mvn test -Dtestfly.shard.total=4 -Dtestfly.shard.index=${{ matrix.shard }}
```

### GitLab CI Parallel Matrix

```yaml title=".gitlab-ci.yml"
test:
  stage: test
  parallel: 4 # GitLab sets CI_NODE_TOTAL=4 and CI_NODE_INDEX=1..4
  script:
    # TestFly automatically detects CI_NODE_TOTAL and CI_NODE_INDEX
    - mvn test
```

---

## Real-Time Console Output

When sharding is active, TestFly prints a clear load-balancing report at suite startup:

```text
================================================================================
✈  TestFly Smart Test Sharder — LPT Bin-Packing
================================================================================
Total Tests: 48 | Total Shards: 4 | Active Shard: 1 of 4 (Index: 0)
Suite Total Time: 22m 40s | Makespan (Bottleneck): 5m 45s | Efficiency: 98.6%
--------------------------------------------------------------------------------
Shard 0  [CURRENT NODE]:  12 tests ~ 5m 45s    (25.3% load)
Shard 1                :  12 tests ~ 5m 40s    (25.0% load)
Shard 2                :  12 tests ~ 5m 42s    (25.1% load)
Shard 3                :  12 tests ~ 5m 33s    (24.6% load)
================================================================================
```
