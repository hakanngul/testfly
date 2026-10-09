---
id: distributed-docker-k8s
title: Distributed Load Testing (Docker & Kubernetes)
sidebar_label: Docker & Kubernetes
sidebar_position: 10
description: "Scale TestFly load and performance tests horizontally by running multiple Docker containers or Kubernetes pods as independent load generators."
---

# Distributed Load Testing with Docker & Kubernetes

A single machine is often enough for smoke and regression load tests. When one generator can no longer produce the load you need, you can scale **horizontally**: package the load suite in a container and run several copies in parallel with **Docker Compose** or as a **Kubernetes** Job.

Each container is an independent TestFly run. Achievable concurrency and throughput depend on hardware, the target service and the scenario. There is no fixed capacity guarantee, so measure your own load (see [Execution Engines](./engines.md)).

---

## 1. Passing Settings via Environment Variables

TestFly resolves `${VAR}` and `${VAR:-default}` placeholders in **string** values of `testfly.yml` (shell environment, `.env` or `-D` system property). Numeric and boolean keys such as `loadtest.users` or `reporting.reportportal.enabled` are not resolved from placeholders. Set them as literals, or read the variable in test code.

```yaml title="testfly.yml"
execution:
  mode: local
  baseUrl: ${TESTFLY_API_BASEURL:-https://staging.example.com}

loadtest:
  baseUrl: ${TESTFLY_API_BASEURL:-https://staging.example.com}
  engine: auto
  maxUsers: 5000          # upper bound per container; users above this are clamped
  resultsDir: target/loadtest
```

Read the per-container user count in the test and assert your SLOs with the fluent load assertions:

```java title="CheckoutLoadTest.java"
import io.testfly.loadtest.BaseLoadTest;
import org.testng.annotations.Test;

public class CheckoutLoadTest extends BaseLoadTest {

    private static final int USERS =
            Integer.parseInt(System.getenv().getOrDefault("TESTFLY_LOAD_VUSERS", "100"));

    @Test
    public void checkoutUnderLoad() {
        load("/api/checkout")
            .users(USERS)
            .run()
            .assertErrorRateBelow(0.001)   // 0.0–1.0 → 0.1%
            .assertP99Below(800)           // ms
            .assertP95Below(500);
    }
}
```

---

## 2. Containerizing TestFly Load Tests

Build a minimal, headless Docker container running your performance suite:

```dockerfile title="Dockerfile.loadtest"
FROM maven:3.9.6-eclipse-temurin-21

WORKDIR /app
COPY pom.xml .
COPY testfly.yml .
COPY src ./src

# Pre-fetch dependencies
RUN mvn dependency:go-offline -B

# Execute performance tests
ENTRYPOINT ["mvn", "test", "-Dtest=*LoadTest", "-B"]
```

Build the image:
```bash
docker build -t testfly-load-runner:latest -f Dockerfile.loadtest .
```

---

## 3. Multi-Worker Docker Compose

Run several workers locally or on a dedicated VM. Each worker writes its own results to a separate volume:

```yaml title="docker-compose.load.yml"
services:
  load-worker-1:
    image: testfly-load-runner:latest
    environment:
      - TESTFLY_LOAD_VUSERS=2500
      - TESTFLY_API_BASEURL=https://target-service.internal
    volumes:
      - ./target/load-results-1:/app/target

  load-worker-2:
    image: testfly-load-runner:latest
    environment:
      - TESTFLY_LOAD_VUSERS=2500
      - TESTFLY_API_BASEURL=https://target-service.internal
    volumes:
      - ./target/load-results-2:/app/target
```

---

## 4. Scaling Out on Kubernetes (Job manifest)

To spread the load across a cluster, run the same image as a parallel Kubernetes Job:

```yaml title="loadtest-job.yaml"
apiVersion: batch/v1
kind: Job
metadata:
  name: testfly-load-surge
spec:
  parallelism: 20       # 20 concurrent pods
  completions: 20
  template:
    spec:
      containers:
      - name: load-runner
        image: your-registry.io/testfly-load-runner:latest
        resources:
          requests:
            cpu: "2"
            memory: "4Gi"
          limits:
            cpu: "4"
            memory: "8Gi"
        env:
        - name: TESTFLY_LOAD_VUSERS
          value: "500"
        - name: TESTFLY_API_BASEURL
          value: "https://target-service.internal"
        - name: RP_API_KEY
          valueFrom:
            secretKeyRef:
              name: reportportal
              key: apiKey
      restartPolicy: Never
```

### Results and Quality Gates

- **Per-pod results:** every pod produces its own load metrics (`testfly-metrics.json`, the TestFly HTML report and, with `loadtest.reportEnabled: true`, `loadtest-report.html`) under its `target/` directory. TestFly does not merge metrics across pods. Collect each pod's `target/` (for example to a shared volume or object storage) and compare or aggregate the results yourself.
- **ReportPortal (optional):** with `reporting.reportportal.enabled: true`, `endpoint` and `apiKey: ${RP_API_KEY}` configured, results are uploaded by the ReportPortal TestNG agent. Each pod reports its own launch. The built-in `ReportPortalReportAdapter` only validates the configuration and prints a launch summary with the dashboard URL. It does not stream live latency percentiles.
- **SLO checks:** put latency and error-rate thresholds in the fluent load assertions (`assertP95Below`, `assertP99Below`, `assertErrorRateBelow`, `assertThroughputAbove`, …) so a pod fails its test when its SLO is violated.
- **Suite-level gate:** `BuildThresholdEnforcer` evaluates only `ci.failOnPassRateBelow` and `ci.maxFlakyTests` over test outcomes in each run. It does not check latency or error rates.

```yaml title="testfly.yml"
ci:
  failOnPassRateBelow: 100.0
  maxFlakyTests: 0
```
