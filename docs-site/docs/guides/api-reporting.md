---
title: "Logging & Reporting"
---

# Logging & Reporting

Every `ApiClient` request is automatically logged in the step timeline:

```
[API] GET /api/users/1 → 200 (143ms)
[API] POST /api/orders → 201 (89ms)
[API] DELETE /api/orders/5 → 404 (12ms)   ← logged as FAIL
```

Enable body and cURL logging in `testfly.yml`:

```yaml
api:
  logBody: true
  logCurl: true
```

With `logBody: true` and `logCurl: true` enabled:
- The HTML report renders requests with an expandable **cURL snippet** ready to copy-paste directly into your terminal or Postman.
- Formatted request and response JSON payloads are highlighted in preformatted blocks inside the test's execution drawer.

## Interceptor and retry reporting

Every physical HTTP exchange is logged as INFO with an exchange number; retry decisions are WARN. The final logical response uses the existing PASS/FAIL status rule. Thus a recovered 401 followed by 200 does not create an intermediate FAIL step. Synthetic responses are marked `[synthetic]`.

`durationMs()` measures the returned transport attempt (or supplied synthetic duration); the report also shows total logical-call time including retry/backoff. Header and cURL output use the actual sent request and case-insensitive masking, including Authorization, Cookie and X-Api-Key. Enable cURL output explicitly with `api.logCurl: true`; masked commands require credentials to be supplied before replay.
