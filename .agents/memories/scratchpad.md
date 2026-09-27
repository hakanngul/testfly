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
- **Konu:** Cucumber Driver Teardown (@AfterAll & Shutdown Hook)
- **Durum:** IntelliJ veya CLI üzerinden tek senaryo koşulduğunda browser'ın açık kalma sorunu çözüldü. TestFly 1.0.5 derlendi ve yerel depoya yüklendi.

### 2. Kök Neden & Çözülen Darboğazlar
1. **Per-Suite Yaşam Döngüsü ve IDE Koşuları:**
   - `testfly.yml` içinde `browser.lifecycle: per-suite` tanımlı olduğunda `DriverManager.shouldQuitAfterTest()` `false` dönüyordu.
   - Testler Maven/TestNG üzerinden değil de IntelliJ'den tek senaryo (`io.cucumber.core.cli.Main`) ile çalıştırıldığında TestNG'nin `SuiteExecutionListener` dinleyicisi tetiklenmiyordu.
   - Cucumber tarafında bir `@AfterAll` kancası bulunmadığı için tek senaryo bittiğinde JVM kapanırken browser oturumu kapatılmadan açık kalıyordu.
2. **Uygulanan Çözüm:**
   - `CucumberHooks.java`: `@AfterAll(order = 0)` metodu `afterAllScenarios()` eklenerek koşu bittiğinde `DriverManager.quitAllSuiteDrivers()` ve `forceQuitDriver()` çağrıldı.
   - `DriverManager.java`: JVM kapanış kancası (`Runtime.getRuntime().addShutdownHook`) ve `forceQuitDriver()` metodu eklenerek JVM'in sonlandığı her senaryoda driver'ların kapatılması garantiye alındı.
3. **Doğrulama:**
   - Tekil senaryo çalıştırıldı: `[TestFly] All suite drivers quit. Released 1 session slot(s).` doğrulanarak browser oturumu başarıyla serbest bırakıldı.

### 3. Kaynaklar & Bağlantılar
- Dosyalar: `CucumberHooks.java`, `DriverManager.java`
