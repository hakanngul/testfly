---
title: "SSL Configuration"
---

# SSL Configuration

Default JDK certificate trust and HTTPS hostname checks remain enabled. A custom truststore replaces the default trust anchors for that profile.

```yaml
api:
  ssl:
    trustAll: false
    trustStore:
      path: certs/staging.p12
      type: PKCS12
      password: ${TESTFLY_TRUSTSTORE_PASSWORD}
```

```java
ApiClient.get("https://localhost/health")
    .trustStore(Path.of("certs/staging.p12"), passwordChars).send();
ApiClient.get("https://localhost/health").trustAllCerts().send();
// JKS:
ApiClient.get("https://localhost/health")
    .trustStore(Path.of("certs/staging.jks"), passwordChars, "JKS").send();
```

Request selection overrides the complete YAML SSL profile. TrustAll and truststore cannot coexist in the same scope. `trustAllCerts()` relaxes certificate trust but still rejects hostname mismatches. The first actual HTTPS exchange produces a WARN. JVM SSL settings are not changed.

Missing/corrupt stores and incorrect passwords fail preparation; async calls return exceptional futures. Custom profiles are shared within the test and closed at cleanup; changed truststore files are not reloaded during that test. Passwords are not logged.
