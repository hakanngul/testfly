---
id: distributed-docker-k8s
title: Distributed Load Testing (Docker & Kubernetes)
sidebar_label: Docker & Kubernetes
sidebar_position: 10
description: "Scale TestFly load and performance tests to 100,000+ RPS using Docker containers and distributed Kubernetes deployments."
---

# Distributed Load Testing with Docker & Kubernetes

While running load tests on a single developer machine is great for smoke and regression testing, enterprise peak simulations (e.g. 50,000–100,000+ RPS) require **distributed workload generators**.

TestFly's load testing module natively supports containerized execution via **Docker** and multi-pod scaling on **Kubernetes**.

---

## 1. Containerizing TestFly Load Tests

Build a minimal, headless Docker container running your performance suite:

```dockerfile title="Dockerfile.loadtest"
FROM maven:3.9.6-eclipse-temurin-17 AS builder

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

## 2. Multi-Worker Docker Compose

Simulate distributed traffic across multiple worker nodes locally or in a dedicated VM:

```yaml title="docker-compose.load.yml"
version: '3.8'

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

## 3. Scaling to 100,000+ RPS on Kubernetes (Helm)

For high-throughput enterprise load testing, deploy worker pods across a Kubernetes cluster:

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
          value: "5000"
        - name: REPORTPORTAL_ENABLED
          value: "true"
      restartPolicy: Never
```

### Real-Time Metric Aggregation

When running across distributed Kubernetes pods:
- Each pod streams live latency percentiles (P50, P90, P95, P99) to **ReportPortal** using TestFly's built-in `ReportPortalReportAdapter`.
- TestFly's **`BuildThresholdEnforcer`** verifies that global error rates remain under 0.1% and P99 response times do not exceed your SLO thresholds.
