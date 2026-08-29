# Integration Tests

These tests exercise real framework subsystems (database, HTTP clients, external APIs)
with actual I/O — no Mockito mocks. They are **excluded from the default `mvn test` run**
and only execute through the `real-backends` Maven profile.

## Running

```bash
# Run all integration tests
mvn verify -Preal-backends

# Run a single integration test class
mvn verify -Preal-backends -Dit.test=DbClientIntegrationTest

# Run with a specific testfly profile
mvn verify -Preal-backends -Dtestfly.profile=staging
```

The default `mvn test` command skips these tests entirely — they live under
`src/test/java/io/testfly/integration/` which is excluded by the surefire plugin.

## Test suites

| Test | Subsystem | What it covers |
|------|-----------|----------------|
| `ai/AiFailureAnalyzerIntegrationTest` | AI failure analysis | Real DeepSeek API call; requires `AI_API_KEY` env var |
| `db/DbClientIntegrationTest` | Database assertions | H2 in-memory DB; connection factory, query execution, row assertions, connection cleanup |
| `email/MailhogIntegrationTest` | Email (Mailhog) | Local HTTP stub; message parsing, request construction, error handling (5xx, connection refused) |
| `testmanagement/TestRailIntegrationTest` | TestRail reporter | Local HTTP stub; Basic auth, payload mapping, status ID mapping, 401/500 error handling |

## Design principles

- **Self-contained:** each test spins up its own dependencies (H2, JDK HttpServer stubs).
  No Docker, no external services, no network access required (except the AI test).
- **TestNG groups:** all tests use `groups = {"integration"}` for surefire/failsafe filtering.
- **Reflection:** some framework classes (`MailhogProvider`, `TestRailClient`) are
  package-private. Integration tests access them via reflection to verify real HTTP behavior
  without widening the public API surface.

## Dependencies

- **H2 Database** (`com.h2database:h2`, test scope) — required for `DbClientIntegrationTest`.
  Must be added to `pom.xml` as a test dependency.
- All other integration tests use only JDK built-ins (`com.sun.net.httpserver.HttpServer`).
