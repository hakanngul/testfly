# TestFly Kapsamlı Mimari & Kod Denetim Raporu

**Tarih:** 7 Eylül 2026
**Kapsam:** 31 modül, 8 paralel analiz agent'ı
**Yöntem:** Kaynak kod incelemesi, thread-safety analizi, konfigürasyon tutarlılık kontrolü

---

## 📊 Özet Tablo

| Severity | Adet | Açıklama |
|----------|------|----------|
| 🔴 Critical | 1 | Paralel yürütmede veri kaybı/bozulma |
| 🟠 High | 10 | Fonksiyonel hata, kullanıcıyı etkileyen kırıklık |
| 🟡 Medium | 16 | Eksik özellik, performans, güvenlik yüzeyi |
| 🔵 Low | 18 | Kod kalitesi, best-practice, küçük iyileştirmeler |
| ✅ Pass | 12 | Sorunsuz doğrulanan alanlar |

---

## 🔴 CRITICAL — Acil Müdahale

### C1. HealingCache.save() Thread-Safe Değil
**Alan:** 3 (Self-Healing)
**Dosya:** `src/main/java/io/testfly/healing/HealingCache.java` — `save()` metodu (satır ~78-106)

**Sorun:** `HealingCache.save()` herhangi bir `synchronized` bloğu kullanmadan dosyaya okuma-yazma yapıyor. Paralel test yürütmesinde birden fazla thread aynı anda `healed-locators.json` dosyasına yazdığında:
- **Kayıp güncellemeler:** İki thread aynı dosya durumunu okuyup各自 ekler, ikinci yazma birincinin üzerine yazar.
- **Dosya bozulması:** Eşzamanlı `writeValue()` çağrıları dosyayı bozabilir.

**Kıyaslama:** `ActionCache.save()` doğru şekilde `synchronized (ActionCache.class)` kullanıyor.

**Aksiyon:**
```java
// HealingCache.save() metodunu synchronized yap
public synchronized void save() { ... }
// veya ActionCache ile aynı kalıbı kullan:
synchronized (HealingCache.class) { ... }
```

---

## 🟠 HIGH — Yüksek Öncelikli

### H1. GeminiProvider maxOutputTokens: 512 Çok Düşük
**Alan:** 2 (LLM Providers)
**Dosya:** `GeminiProvider.java` satır ~77

**Sorun:** Claude ve OpenAI 2048 token alırken, Gemini sadece 512 token ile sınırlı. Aksiyon planları, healing locator'ları ve hata analizleri gibi yapılandırılmış JSON yanıtları için yetersiz. Token truncation → JSON parse hatası → fonksiyon kırıklığı.

**Aksiyon:** `maxOutputTokens` değerini en az 2048'e yükselt veya config'den okunabilir yap.

---

### H2. WaitEngine StaleElementReferenceException'ı Ignore Etmiyor
**Alan:** 8 (WaitEngine)
**Dosya:** `WaitEngine.java` satır ~37-40, `LocatorAssert.java` (birden fazla metod)

**Sorun:** `WebDriverWait` oluşturulurken `.ignoring(StaleElementReferenceException.class)` çağrılmıyor. Selenium'un built-in `ExpectedConditions` internally yakalasa da, `LocatorAssert`'ın custom lambda'ları (`isEnabled`, `isDisabled`, `isChecked`, `hasAttribute`, `hasCssValue`, `isFocused`, `hasClass`) doğrudan `WebElement` metodlarına erişiyor. DOM asenkron güncellendiğinde (React/Vue/Angular) bu metodlar bekleme süresini doldurmadan hemen başarısız oluyor.

**Aksiyon:**
```java
// WaitEngine.createWait() içine ekle:
return new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
    .ignoring(StaleElementReferenceException.class);
```

---

### H3. TestClock Sayfa Yenilendiğinde Kayboluyor
**Alan:** 19 (Clock)
**Dosya:** `TestClock.java` satır ~119

**Sorun:** Clock mock'u `executeScript()` ile enjekte ediliyor. Sayfa yenilendiğinde veya navigasyon yapıldığında JavaScript context'i yok oluyor ve `Date` override'ı kayboluyor. `Page.addScriptToEvaluateOnNewDocument` (CDP) kullanılmıyor. Java tarafındaki `MOCK_TIME_MS` ThreadLocal değeri korunuyor ama tarayıcı tarafındaki mock kayboluyor → tutarsızlık.

**Aksiyon:**
```java
// Chromium için CDP ile kalıcı enjeksiyon:
devTools.send(Page.addScriptToEvaluateOnNewDocument(INJECT_JS, ...));
// Non-Chromium için fallback: her sayfa yüklemesinde yeniden enjekte
```

---

### H4. JUnit 5'te Quality Gate Exception Yutuluyor
**Alan:** 22 (CI)
**Dosya:** `TestFlyLauncherListener.java` satır ~49-55

**Sorun:** `BuildQualityGateException extends RuntimeException` ama catch bloğu sadece `IllegalStateException`'ı re-throw ediyor. JUnit 5 kullanıcıları için `failOnPassRateBelow` eşiği aşılsa bile build **asla fail olmuyor**. TestNG yolu doğru çalışıyor.

**Aksiyon:**
```java
// catch bloğunu düzelt:
} catch (BuildQualityGateException e) {
    throw e; // CI gate failures must propagate
} catch (IllegalStateException e) {
    throw e;
} catch (Exception e) {
    System.err.println("[TestFly] Report generation failed: " + e.getMessage());
}
```

---

### H5. VisualAssert System Property Adı Uyuşmazlığı
**Alan:** 11 (Visual)
**Dosya:** `VisualAssert.java` satır ~60

**Sorun:** Dokümantasyon `-Dtestfly.visual.updateBaselines=true` diyor ama kod `System.getProperty("updateBaselines")` okuyor. Dokümantasyonu takip eden kullanıcılar flag'in çalışmadığını görecek.

**Aksiyon:** System property adını `testfly.visual.updateBaselines` olarak düzelt veya her ikisini de kabul et.

---

### H6. Visual Diff Image HTML Raporuna Eklenmiyor
**Alan:** 11 (Visual)
**Dosya:** `VisualAssert.java` satır ~144-150

**Sorun:** Diff image diske kaydediliyor ama HTML rapora base64 olarak gömülmüyor. Reporting paketi ile visual diff arasında entegrasyon yok. Kullanıcı diff'i görmek için dosya sistemini manuel taramak zorunda.

**Aksiyon:** `VisualAssert`'ta diff oluştuğunda `StepLogger.stepWithScreenshot()` ile diff image'ı rapora ekle.

---

### H7-H8. SPA Geçişlerinde Performance Metrikleri Yanlış
**Alan:** 13 (Performance)
**Dosya:** `PerformanceCollector.java` satır ~27-60

**Sorun:**
- **H7:** Navigation Timing API sadece ilk sayfa yüklemesi için metrik üretiyor. SPA route geçişlerinde metrikler donmuş kalıyor.
- **H8:** LCP ve CLS sayfa yüklemesinden itibaren kümülatif. SPA transition'ı izole etmek imkansız.

**Aksiyon:** SPA geçişleri için `performance.measure()` tabanlı özel bir strateji ekle. Navigation Timing API'nin `navigationType` alanını kontrol et.

---

### H9. Config: `reporting.allure.enabled` YAML Yapısı Kodla Uyuşmuyor
**Alan:** 25 (Config)
**Dosya:** `configuration.md` satır ~194-195 vs `TestFlyConfig.java` satır ~422

**Sorun:** Dokümantasyon nested `reporting.allure.enabled` gösteriyor ama Java kodu flat `reporting.allureEnabled` bekliyor. Kullanıcı dokümantasyona göre config yazarsa Allure sessizce devre dışı kalır.

**Aksiyon:** YAML yapısını kodla eşleştir veya kodu nested yapıyı kabul edecek şekilde güncelle.

---

### H10. Config: `${VAR}` Environment Variable Çözümü Eksik
**Alan:** 25 (Config)
**Dosya:** `ConfigurationLoader.java`, `DotEnvLoader.java`

**Sorun:** Dokümantasyon "her scalar değer `${VAR_NAME}` kullanabilir" diyor ama gerçekte sadece ~%30 alan (API auth, ReportPortal, AI key) resolve ediliyor. Şu alanlar literal `${...}` string'i içeriyor:
- `execution.baseUrl`
- `execution.browserstack.username/accessKey`
- `execution.saucelabs.username/accessKey`
- `database.password`
- `email.*.apiToken`
- `testmanagement.*.apiKey/clientSecret`
- `notifications.slack/teams.webhookUrl`

**Aksiyon:** `ConfigurationLoader.load()` sonrası tüm config değerlerini recursive olarak resolve et veya her getter'da lazy resolve yap.

---

### H11. Config: `execution.mode: browserstack/saucelabs` Validator Tarafından Reddediliyor
**Alan:** 25 (Config)
**Dosya:** `ConfigurationLoader.java` satır ~86-89

**Sorun:** Validator sadece `local` ve `remote` modlarını kabul ediyor. `browserstack` ve `saucelabs` modları dokümantasyonda listelenmesine ve `TestFlyConfig` tarafından desteklenmesine rağmen startup'ta `IllegalStateException` fırlatılıyor.

**Aksiyon:**
```java
Set<String> validModes = Set.of("local", "remote", "browserstack", "saucelabs");
if (!validModes.contains(mode.toLowerCase())) { throw ... }
```

---

## 🟡 MEDIUM — Orta Öncelikli

| # | Alan | Sorun | Dosya |
|---|------|-------|-------|
| M1 | 1 (Agent) | ActionCompiler prompt scaffolding token budget'i dışında — toplam prompt model limitini aşabilir | `ActionCompiler.java` |
| M2 | 4 (Remediation) | LLM-generated patch: header validasyonu yok, path mismatch riski, context hallucination | `RemediationPatchGenerator.java` |
| M3 | 5 (PreCondition) | 401/403'te oturum cache'i otomatik invalidasyon yok — stale cookie'ler test hatasına yol açar | `PreconditionSessionCache.java` |
| M4 | 7 (Driver) | MultiSessionManager named session'lar semaphore'u bypass ediyor — paralel yürütmede 30 browser, 10 permit | `MultiSessionManager.java` |
| M5 | 8 (Locator) | `cssEscape()` yetersiz — CSS özel karakterleri (`:`, `.`, `#`, `[`, `]`) escape edilmiyor | `Locator.java:491` |
| M6 | 9 (Assert) | LocatorAssert custom By bridge'lerinde StaleElementReferenceException toleransı yok | `SeleniumAssert.java` |
| M7 | 10 (API) | OAuth2 form parametreleri URL-encode edilmiyor — özel karakterli credential'larda istek bozulur | `ApiAuth.java:178-207` |
| M8 | 11 (Visual) | İlk çalıştırmada baseline review gate yok — kırık UI otomatik baseline olabilir | `VisualAssert.java:117-122` |
| M9 | 13 (Perf) | Skipped performance metrikleri `System.out.println` ile loglanıyor, StepLogger'a gitmiyor | `PerformanceAssert.java:111-113` |
| M10 | 14 (Recording) | `GifEncoder.toCompatible()` — `Graphics2D.dispose()` çağrılmıyor, frame başına context leak | `GifEncoder.java:89` |
| M11 | 14 (Recording) | Fallback screenshot yolunda çözünürlük cap'i yok — yüksek DPI'da bellek şişer | `RecordingManager.java:243-252` |
| M12 | 16 (DB) | Table/column isimleri SQL'e string concat ile ekleniyor — injection yüzeyi | `DbClient.java:117-145` |
| M13 | 17 (TestData) | `@DataProvider`-style parametrik çalıştırma yok — `@TestData` sadece tek row yüklüyor | `TestDataLoader.java` |
| M14 | 20 (Browser) | `failOnConsoleErrors` sadece success path'te kontrol ediliyor — failure path'te JS hataları rapordan düşüyor | `TestExecutionListener.java` |
| M15 | 21 (Reporting) | Allure/ReportPortal adapter'ları SPI services dosyasında kayıtlı değil — ServiceLoader bulamıyor | `META-INF/services/` |
| M16 | 23 (JUnit5) | Retry `Method.invoke()` ile yapılıyor — diğer JUnit5 extension'ları retry'da atlanıyor | `TestFlyExtension.java:153-185` |
| M17 | 24 (Hooks) | `HookRegistry` dispatch metodları `ArrayList` üzerinde senkronizasyonsuz iterate — `ConcurrentModificationException` riski | `HookRegistry.java:14` |
| M18 | 25 (Config) | `screenshotOnFailure`, `htmlReport`, `blockUrls`, `baseUrls` alanları dokümante edilmemiş | `configuration.md` |
| M19 | 28 (Memory) | `jsErrorsLogged`/`failureArtifactsHandled` — `set(false)` yerine `remove()` çağrılmalı | `TestExecutionListener.java` |
| M20 | 28 (Memory) | `SuiteContext.STORE` — JVM crash'de static ConcurrentHashMap temizlenmeden kalır | `SuiteContext.java:20` |
| M21 | 31 (Quality) | JaCoCo coverage threshold yok — sıfır coverage'lı kod merge edilebilir | `pom.xml:340-355` |
| M22 | 31 (Quality) | SpotBugs/PMD concurrency kuralları eksik — `multithreading.xml`, `concurrency.xml` yok | `pom.xml:525-570` |

---

## 🔵 LOW — Düşük Öncelikli

| # | Alan | Sorun |
|---|------|-------|
| L1 | 2 (LLM) | ClaudeProvider hem `x-api-key` hem `Authorization: Bearer` gönderiyor — bazı proxy'lerde çakışma |
| L2 | 4 (Remediation) | SourceCodeLocator multi-module projelerde dosya bulamayabilir |
| L3 | 6 (Network) | Non-Chromium'da CDP uyarısı sadece `LOG.warning` — `SEVERE` veya exception daha uygun |
| L4 | 8 (Shadow) | `buildPierceJs()` dead code, `escapeJs()` yetersiz |
| L5 | 8 (Locator) | `resolveAll()` filter bloğunda dead code — ilk stream geçişi hemen üzerine yazılıyor |
| L6 | 9 (Assert) | `SoftAssertions.clear()` → `COLLECTOR.remove()` yerine `COLLECTOR.get().clear()` |
| L7 | 9 (Assert) | `SoftAssertionCollector.failures` ArrayList — thread-safe değil (normal kullanımda sorun yok) |
| L8 | 14 (Recording) | `CopyOnWriteArrayList` frame depolama için verimsiz — her add tüm array'i kopyalar |
| L9 | 15 (Email) | Polling `Thread.sleep()` kullanıyor, exponential backoff yok |
| L10 | 15 (Email) | `extractOtp()` metodu yok — OTP çıkarma desteklenmiyor |
| L11 | 16 (DB) | Connection pool değil, per-thread cache — yüksek thread count'ta DB limit aşılabilir |
| L12 | 17 (TestData) | DB source `Statement` kullanıyor (PreparedStatement değil); CSV multi-line field desteklemiyor |
| L13 | 18 (Flakiness) | `metrics-history/` sınırsız büyüyor — rotasyon/temizlik yok |
| L14 | 18 (Flakiness) | `FlakinessAnalyzer` export path'i `target/` hardcoded — `ReportPaths.baseDir()` kullanmıyor |
| L15 | 19 (Clock) | `executeReset()` exception'ı sessizce yutuyor |
| L16 | 20 (Browser) | `GeoLocation.clear()` JS path'te dead `delete` satırı |
| L17 | 21 (Report) | HTML template CDN bağımlılığı (Chart.js, Google Fonts) — air-gapped ortamda kırılır |
| L18 | 22 (CI) | Retry+fail testler flaky sayılmıyor — sadece retry+pass flaky gate'e giriyor |
| L19 | 23 (JUnit5) | `TestFlyExtension` `TestWatcher` implement etmiyor — aborted test statüsü yanlış olabilir |
| L20 | 24 (Plugin) | `FrameworkVersion.get()` IDE'de `"0.0.0"` dönüyor — plugin'ler yanlışlıkla reddediliyor |
| L21 | 24 (Plugin) | Versiyon karşılaştırma `-SNAPSHOT`, `-RC1` qualifier'ları siliyor |
| L22 | 24 (Hooks) | `HookRegistry.loadAll()` idempotency guard yok — tekrarlı çağrıda duplicate registration |
| L23 | 26 (POM) | `jcodec` optional değil — recording kullanmayan projeler gereksiz bağımlılık çekiyor |
| L24 | 28 (Memory) | `ApiClient` static interceptor list'leri asla temizlenmiyor |
| L25 | 29 (CI) | GitHub Actions HTML raporu artifact olarak upload etmiyor |
| L26 | 31 (Quality) | Checkstyle `failsOnError=false` — advisory-only, build'i engellemiyor |

---

## ✅ DOĞRULANAN ALANLAR (Sorunsuz)

| Alan | Konu | Durum |
|------|------|-------|
| 1 | ActionCache invalidate → disk JSON'dan siliniyor | ✅ |
| 1 | ActionExecutor LLM prefix/quote temizliği | ✅ |
| 2 | OpenAI endpoint URL joining | ✅ |
| 3 | Self-healing statik→AI geçiş mimarisi | ✅ |
| 5 | PreconditionSessionCache ThreadLocal izolasyonu | ✅ |
| 6 | CDP listener cleanup | ✅ |
| 7 | `DriverManager.quitDriver()` → `DRIVER.remove()` | ✅ |
| 9 | Soft assertion auto-flush (assertAll gereksiz) | ✅ |
| 12 | Accessibility raporlama (element + fix suggestion) | ✅ |
| 18 | Quarantine skip (açık mesajla SkipException) | ✅ |
| 26 | Optional dependency izolasyonu | ✅ |
| 26 | Credential leak yok | ✅ |
| 27 | FrameworkBootstrap init sırası | ✅ |
| 27 | Missing .env / corrupt cache graceful handling | ✅ |
| 28 | Core ThreadLocal cleanup (thorough) | ✅ |
| 29 | CI headless stability | ✅ |
| 29 | Report archiving with `always()` | ✅ |
| 30 | EN/TR doküman paritesi (72/72 dosya) | ✅ |
| 30 | Broken link policy | ✅ |

---

## 🎯 Öncelikli Aksiyon Planı

### Sprint 1 — Critical & High (1-2 hafta)

| Öncelik | Aksiyon | Tahmini Efor |
|---------|---------|-------------|
| 🔴 | `HealingCache.save()` synchronized yap | 15 dk |
| 🟠 | `WaitEngine.createWait()` → `.ignoring(StaleElementReferenceException.class)` ekle | 30 dk |
| 🟠 | `GeminiProvider.maxOutputTokens` 512 → 2048 | 5 dk |
| 🟠 | `TestFlyLauncherListener` catch bloğunu `BuildQualityGateException` için düzelt | 15 dk |
| 🟠 | `ConfigurationLoader` validator'ı `browserstack`/`saucelabs` modlarını kabul etsin | 15 dk |
| 🟠 | `VisualAssert` system property adını düzelt | 15 dk |
| 🟠 | Config `${VAR}` resolution'ı tüm alanlara yay | 2-3 saat |
| 🟠 | `reporting.allure` YAML nesting'i kodla eşleştir | 1 saat |
| 🟠 | `TestClock` → `Page.addScriptToEvaluateOnNewDocument` ekle | 2-3 saat |
| 🟠 | Visual diff image'ı HTML rapora ekle | 2-3 saat |
| 🟠 | SPA performance metrikleri için özel strateji | 4-6 saat |

### Sprint 2 — Medium (2-3 hafta)

| Öncelik | Aksiyon |
|---------|---------|
| 🟡 | `HookRegistry` dispatch metodlarını thread-safe yap |
| 🟡 | Allure/ReportPortal SPI services dosyasına kaydet |
| 🟡 | `GifEncoder.toCompatible()` → `g2d.dispose()` ekle |
| 🟡 | `cssEscape()` CSS spec-compliant yap |
| 🟡 | OAuth2 form parametrelerini URL-encode et |
| 🟡 | PreCondition 401/403 auto-invalidation ekle |
| 🟡 | MultiSessionManager named session'ları semaphore'a dahil et |
| 🟡 | `failOnConsoleErrors` failure path'te de kontrol et |
| 🟡 | JaCoCo coverage threshold ekle |
| 🟡 | SpotBugs/PMD concurrency kurallarını ekle |
| 🟡 | DB table/column isimleri için whitelist validation |
| 🟡 | `@TestData` → `@DataProvider` entegrasyonu |

### Sprint 3 — Low & Polish (devam eden)

- jcodec optional yap
- Metrics history rotasyonu ekle
- extractOtp() metodu ekle
- SoftAssertions.clear() → remove() yap
- HTML report CDN fallback ekle
- GitHub Actions HTML report upload ekle

---

## 📈 Kalite Skorları

| Kategori | Skor | Durum |
|----------|------|-------|
| Thread-Safety | 6/10 | HealingCache critical, HookRegistry medium |
| Config Tutarlılığı | 4/10 | 3 high severity config sorunu |
| Dokümantasyon Doğruluğu | 5/10 | YAML nesting, property adı, ${VAR} eksik |
| Hata Toleransı | 7/10 | StaleElement, SPA metrics, clock persist |
| CI/CD Doğruluğu | 7/10 | JUnit5 quality gate kırık |
| Bellek Yönetimi | 8/10 | Genel temiz, birkaç ThreadLocal hygiene |
| Bağımlılık İzolasyonu | 9/10 | Optional doğru, sadece jcodec eksik |
| Test Kapsamı | 5/10 | JaCoCo threshold yok |
