---
title: "SSL Yapılandırması"
---

# SSL Yapılandırması

Varsayılan JDK güven deposu ve HTTPS hostname kontrolü korunur. Özel truststore, o profilin varsayılan güven deposunun yerini alır.

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

İstek seçimi YAML SSL profilini geçersiz kılar. Aynı kapsamda trustAll ve truststore birlikte kullanılamaz. `trustAllCerts()` sertifika güvenini gevşetir; hostname uyuşmazlığını kabul etmez. İlk gerçek HTTPS gönderiminde WARN kaydı oluşur. JVM SSL ayarları değiştirilmez.

Eksik/bozuk dosya veya yanlış parola hazırlama hatasıdır; async çağrıda exceptional future döner. Özel profiller test boyunca paylaşılır ve kapanışta kapatılır; truststore değişiklikleri aynı testte otomatik yüklenmez. Parolalar loglanmaz.
