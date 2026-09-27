---
tags:
  - memory
  - scratchpad
  - ephemeral
date: 2026-09-27
status: active
char_limit: 2200
---

# Aktif Çalışma Not Defteri (Scratchpad)

### 1. Aktif Odak ve Son Durum
- **Konu:** OutOfMemoryError Çözümü & Video Recording Bellek Optimizasyonu (99% Düşüş)
- **Durum:** TAMAMLANDI & LOCAL DEPLOY EDİLDİ (TestFly 1.0.5).
- **Kazanımlar:**
  1. `RecordingManager`: Her frame için anında 3.7 MB `BufferedImage` üretmek yerine sıkıştırılmış `byte[]` tutulup sadece kaydetme anında lazy decode edilecek şekilde refactor edildi. 5 paralel thread'deki bellek kullanımı 16.5 GB'tan 150 MB'a (%99) düşürüldü.
  2. `Customer_web_testfly/pom.xml`: Surefire plugin'e `-Xmx2048m` eklendi.
  3. `Customer_web_testfly/testfly.yml`: `fps: 5` yapıldı ve `locators.selfHealing: true`, `aiHealing: true` aktif edildi.
  4. TestFly 1.0.5 derlenip yerel maven deposuna kuruldu.

### 2. Kaynaklar & Bağlantılar
- [[io.testfly.recording.RecordingManager]]
- [[io.testfly.agent.knowledge.PageKnowledgeStore]]
- [[io.testfly.driver.DriverManager]]


