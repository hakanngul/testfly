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
- **Konu:** Allure & ReportPortal Mükerrer Attachment Temizliği
- **Durum:** Mükerrer ekran görüntüsü ve video yükleme sorunu çözüldü. Testler başarıyla koşuldu ve hem Allure hem de ReportPortal'da tekil ve temiz attachment hiyerarşisi doğrulandı.

### 2. Çözülen Darboğazlar & Düzenlemeler
1. **ReportPortal Çift Yükleme Giderildi:**
   - Cucumber 7 ReportPortal ajanı (`agent-java-cucumber7`), `scenario.attach()` metodunu dinleyerek ekran görüntüsü ve MP4 videosunu otomatik olarak ReportPortal'a yüklüyordu.
   - `CucumberHooks.java` içindeki fazladan `ReportPortalAttachmentSender.sendImmediate(...)` çağrısı kaldırılarak RP tarafındaki 2x duplicate önlendi.
   - `ReportPortalAttachmentSender.sendImmediate` içerisine `isCucumber7AgentActive` kontrolü eklenerek olası mükerrer çağrılara karşı koruma sağlandı.
2. **Allure Raporu Step/Test Hiyerarşisi Düzeltildi:**
   - Hata alan step zaten `CucumberStepLogger` ile failure ekran görüntüsünü step seviyesinde (`Step -> Screenshot`) tutuyordu.
   - `AllureReportAdapter.java` içinde `hasStepScreenshot` kontrolü eklendi; step seviyesinde screenshot varsa test-level `attachments` dizinine mükerrer `Screenshot on Failure` eklenmesi engellendi. Artık test-level'da yalnızca `Execution Video` yer alıyor.
3. **Doğrulama (Customer_web_testfly):**
   - Allure JSON: Failing step içinde tek `Screenshot`, test seviyesinde yalnızca `Execution Video`.
   - ReportPortal Launch `19913`: After hooks altında tam olarak 2 attachment (`Failure Screenshot` + `Execution Video`).

### 3. Kaynaklar & Bağlantılar
- ReportPortal Paneli: `https://reportportal.starlettech.tech/ui/#demo-web/launches/all`
- Launch ID: 19913
