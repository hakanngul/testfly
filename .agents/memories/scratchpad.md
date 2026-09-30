---
tags:
  - memory
  - scratchpad
  - ephemeral
date: 2026-09-29
status: active
char_limit: 2200
---

# Aktif Çalışma Not Defteri (Scratchpad)

### 1. Aktif Odak ve Son Durum
- **Konu:** Java 21 LTS & TestFly MCP Senkronizasyonu & Java 17 Kalıntı Temizliği
- **Durum:** TAMAMLANDI. Tüm Java 17 belirtileri kod ve dokümanlardan temizlendi, Java 21 LTS standardı pekiştirildi.
- **Yapılanlar:**
  1. **Kod Temizliği:** `GatlingBridge.java` (`isJavaLangOpened`), `GatlingRunConfig.java` ve `JdkLoadEngine.java` (`isAvailable`) sınıflarındaki Java 17+ referansları Java 21+ LTS olarak güncellendi.
  2. **CI & Docker Temizliği:** `distributed-docker-k8s.md` (Temurin 17 -> Temurin 21), `bitbucket-pipelines.md` (Temurin 17 -> Temurin 21) ve `changelog.md` (TR built into Java 21) güncellendi.
  3. **Önceki Adımlar:** Getting Started & CLI sayfaları npx tabanlı `@testfly/mcp` köprüsüne geçirildi, Java 21+ ve Gradle 8.5+ önkoşulları yazıldı.
  4. **Doğrulama:** `npm run build` ile EN ve TR doküman derlemesi sıfır hata ile doğrulandı.

### 2. Kaynaklar & Bağlantılar
- [[wiki/assertion-system]]
- [[wiki/architecture]]
- [[wiki/webui-testing]]
- [[MAP]]
