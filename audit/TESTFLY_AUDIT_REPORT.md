# TestFly — Kapsamlı Proje Denetim Raporu (Full Project Audit)

| Alan | Değer |
|---|---|
| Denetim tarihi | 2026-10-08 |
| Depo / dal | `/Users/hagul/Projects/TestFramework/testfly`, dal `chore/docs-cloudflare-workers` (HEAD `67305e4`) |
| Gerçek sürüm kaynağı | `pom.xml:7-9` → `io.github.hakanngul:testfly:1.0.7` (Java `--release 21`, Selenium 4.48.0, TestNG 7.9.0) |
| Ortam | Temurin 21.0.12, Maven 3.9.16 |
| Denetim türü | Yalnızca analiz (audit-only). Kaynak/doküman/konfigürasyon değiştirilmedi, git işlemi yapılmadı. |
| Eşlik eden belge | [`TESTFLY_REMEDIATION_PLAN.md`](./TESTFLY_REMEDIATION_PLAN.md) (aynı bulgu ID'leri kullanılır) |
| Ham kanıtlar | `target/audit-scratch/` (git'te yok sayılır): `discovery.md`, `build-test.md`, `area-a-core.md` … `area-f-security.md`, tekrar üretim programları |

Sınıflandırma etiketleri (tüm raporda):

- **DOĞRULANDI**: Kod okuma ve/veya çalıştırılmış kanıt (repro, javac, bytecode, ağ sorgusu) ile doğrulandı.
- **GÜÇLÜ ŞÜPHE**: Güçlü kod kanıtı var, uçtan uca çalıştırılmadı.
- **İYİLEŞTİRME ÖNERİSİ**: Hata değil, tasarım/bakım önerisi.
- Öncelik: **P0** Blocker, **P1** Critical, **P2** Major, **P3** Minor. Efor: **S** (≤1 gün), **M** (2-5 gün), **L** (>1 hafta).

---

## 1. Executive Summary

### 1.1 Genel teknik durum

Proje derleniyor ve birim test paketi yeşil: `mvn test` → `Tests run: 1361, Failures: 0, Errors: 0, Skipped: 0`. Ancak bu yeşil sonuç **güvenilirliğin kanıtı değildir**. Denetimde 164 benzersiz bulgu kaydedildi (1 P0, 36 P1, 83 P2, 44 P3). Birçok P1 kusur, yeşil testlerin tam da kapsamadığı yerlerde duruyor:

- Gerçek `DriverManager` yaşam döngüsünün hiç birim testi yok (yalnızca `mockStatic`). Bu yüzden oturum izni (permit) sızıntısı, per-suite iyileştirme (self-healing) etkisizliği gibi kusurlar 1361 testten geçti (ARCH-002, ARCH-003, ARCH-020, TST-002).
- JaCoCo kapsama kapısı sessizce devre dışı: `jacoco.exec` hiç üretilmiyor (BLD-009 / TST-001). Gerçek kapsama bilinmiyor.
- Bazı testler yanlış davranışı kodluyor (ör. 500 dönen endpoint "başarılı" sayılıyor, API-002; `maxAttempts=1` bir yeniden deneme yapıyor, API-020).
- Hiçbir test gerçek tarayıcıda `Locator`/`WaitEngine` çalıştırmıyor (API-033, TST-012).

Dokümantasyon tarafında ise Docusaurus build'i başarılı olsa da Java örnekleri doğrulanmıyor. 597 Java kod bloğunun 315'i javac'ta derlenmedi. Ham sayı hata sayısı değildir: çoğu parça/üçüncü taraf kodudur. Elle sınıflandırma sonrası ~30 ayrı çerçeve düzeyi hata kaldı (DOC-001…DOC-029).

### 1.2 En kritik riskler (öncelik sırasıyla)

1. **Kurulum yolu kırık (P0/P1).** Dokümanda yazan sürüm (`1.0.7`) Maven Central'da yok; Central'daki son sürüm `1.0.4`. Ayrıca birçok sayfa Central'da bulunmayan `io.testfly` groupId'sini veriyor. Yeni kullanıcının ilk bağımlılık çözümlemesi başarısız olur (BLD-001, BLD-002, BLD-003, DOC-001, DOC-002).
2. **Çekirdek driver yaşam döngüsü hataları (P1).** `quit()` hata verince izin sızıyor ve 30 sn'lik zincirleme zaman aşımı oluşuyor; `*UploadTest`/`*DownloadTest` sınıfları "loadtest" alt dizgisi yüzünden tarayıcısız kalıyor; per-suite modda ölü driver geri veriliyor; JUnit5/Cucumber sınıf sonu global `quitAllSuiteDrivers()` çağırıyor; TestNG'de `@BeforeMethod` driver'dan önce çalışıyor (ARCH-001…ARCH-007).
3. **Yanlış-olumlu (false-positive) üreten yük testi motoru (P1).** JDK motoru HTTP durum kodlarını hiç kaydetmiyor, 4xx/5xx'i başarı sayıyor, `extract()` besleyicisiz her iterasyonda patlıyor, `assertStatus(n)` anlamı ters (API-001…API-004).
4. **Locator/WaitEngine sözleşmesi ihlali (P1).** `Locator` terminal eylemleri "auto-wait" vaat ediyor ama eleman yoksa anında `LocatorException` fırlatıyor; `getByText()` XPath'i ata elemanları (`<html>`) eşleştiriyor (API-011, API-012).
5. **Konfigürasyon sözleşmesi dokümandakiyle uyumsuz (P1).** Profil dosyası "deep merge" edilmiyor (profil dosyası tabanı **değiştiriyor**), `-Dbrowser.name`/`TESTFLY_*` geçersiz kılmaları yok; CI belgelerindeki tarayıcı matrisi aynı tarayıcıyı N kez çalıştırıp yeşil raporluyor (BLD-019, BLD-020).
6. **Güvenlik (P1/P2).** HTML rapor veri bloğu `</script>` ile kırılabiliyor (stored XSS, SEC-001/API-024); `jackson-databind 2.21.6` (compile scope) için OSV'de 2 HIGH advisory (BLD-005); IMAP sunucu kimliği doğrulanmıyor (SEC-003); ApiMockServer tüm arayüzlerde açık admin API ile dinliyor (SEC-007); release iş akışında script injection ve geri döndürülemez otomatik yayın (BLD-011, SEC-008).
7. **Retry sözleşmesi belirsiz ve varsayılan davranış riskli (P1).** Varsayılan olarak başarısız her test bir kez daha çalıştırılıyor, belge "1 = yeniden deneme yok" diyor; TestNG yeniden denemeleri metriğe yazılmıyor, `ci.maxFlakyTests` kapısı fiilen etkisiz (API-020, ARCH-006, ARCH-011, TST-004).

### 1.3 Önce müdahale edilecek alanlar

| Sıra | Alan | Gerekçe | Bulgu ID'leri |
|---|---|---|---|
| 1 | Yayın/koordinat/sürüm kararı + release hattının güvenli hale getirilmesi | Onboarding tamamen kırık; yeniden yayın geri alınamaz | BLD-001…003, BLD-010, BLD-011, SEC-008 |
| 2 | Driver yaşam döngüsü + izin sızıntısı + testleri | Kaynak sızıntısı, zincirleme CI hataları | ARCH-001…007, ARCH-020, TST-002/003 |
| 3 | Rapor güvenliği ve bağımlılık advisory'leri | Tek P1 güvenlik bulgusu + HIGH advisory | SEC-001/002, API-024, BLD-005, BLD-007 |
| 4 | Yük testi motoru düzeltmeleri ve yük test dokümanı | Yanlış-olumlu sonuçlar, tamamen derlenmeyen örnekler | API-001…005, DOC-005…008 |
| 5 | Locator/WaitEngine otomatik bekleme sözleşmesi | Ana kullanıcı senaryosu | API-011, API-012, TST-005 |
| 6 | Konfigürasyon yükleyici | Dokümante edilen sözleşme uygulanmamış | BLD-019…022, DOC-003 |
| 7 | Retry sözleşmesi | Varsayılan davranış ve CI kapıları | API-020, ARCH-006/011, TST-004 |
| 8 | Doküman P1 sayfaları ve kapı (gate) altyapısı | Derlenmeyen örnekler; tekrarı önlemek için CI | DOC-001…014, DOC-027 |

### 1.4 Önceki (Codex) bulguların durumu — kısa özet

Önceki incelemenin A-D grubu bulguları büyük ölçüde **doğrulandı**; üç düzeltme/nüans var: (i) `.runAsync()` bir TestFly metodu değil JDK `CompletableFuture.runAsync` çağrısı, dolayısıyla "eksik API" değil; (ii) `getWait().waitFor*` hatası EN `guides/wait-engine.md` için geçerli değil (o sayfa doğru), yanlış kullanım başka sayfalarda ve TR'de; (iii) kaynak kod yerine "stale" çıkan taraf bazı durumlarda AGENTS.md'dir (sürüm, groupId, Selenium sürümü). Ayrıntı: Bölüm 6.

---

## 2. Bulgu Özet Tablosu (öncelik / kategori)

### 2.1 Sayısal özet

| Alan (ID öneki) | P0 | P1 | P2 | P3 | Toplam |
|---|---|---|---|---|---|
| A. Çekirdek mimari (`ARCH-`) | 0 | 7 | 15 | 5 | 27 |
| B. Otomasyon motoru & API (`API-`) | 0 | 9 | 20 | 4 | 33 |
| C. Build, bağımlılık, konfigürasyon (`BLD-`) | 1 | 5 | 13 | 4 | 23 |
| D. Test kalitesi (`TST-`) | 0 | 5 | 7 | 5 | 17 |
| E. Dokümantasyon & DX (`DOC-`) | 0 | 9 | 15 | 4 | 28 |
| F. Güvenlik (`SEC-`) | 0 | 1 | 9 | 11 | 21 |
| F. Performans (`PERF-`) | 0 | 0 | 3 | 5 | 8 |
| F. Sürdürülebilirlik (`MAINT-`) | 0 | 0 | 1 | 6 | 7 |
| **Toplam** | **1** | **36** | **83** | **44** | **164** |

Not: `DOC-004` kasıtlı olarak kullanılmıyor (DOC-003 ile birleştirildi). Aynı kök nedeni paylaşan bulgular farklı alan adımlarında ayrı ID aldı; ID'ler korunmuştur. Kopyalar "Aynı kök neden" sütunu ve Bölüm 2.3'teki **Konsolidasyon Grupları** ile bağlanmıştır. Aynı gruptaki bulgularda düzeltme planı (REMEDIATION_PLAN) en yüksek öncelikli üyeye göre sıralar.

### 2.2 Tüm bulgular (tek tablo)

Sınıf kısaltmaları: **D** = DOĞRULANDI, **G** = GÜÇLÜ ŞÜPHE, **İ** = İYİLEŞTİRME ÖNERİSİ. Efor: S/M/L. "Grup" = Bölüm 2.3'teki konsolidasyon grubu.

| ID | Kategori | Öncelik | Sınıf | Efor | Grup | Başlık |
|---|---|---|---|---|---|---|
| BLD-001 | Sürüm/Yayın | P0 | D | S-M | G-01 | 1.0.5–1.0.7 Maven Central'da yok; son sürüm 1.0.4 |
| ARCH-001 | Driver yaşam döngüsü | P1 | D | S | G-02 | "loadtest" alt dizgi sezgisi `*UploadTest`/`*DownloadTest` için tarayıcıyı engelliyor |
| ARCH-002 | Driver yaşam döngüsü | P1 | D | S | G-02 | per-suite modda self-healing etkisiz (ölü driver dönüyor) |
| ARCH-003 | Driver yaşam döngüsü | P1 | D | S | G-02 | `quit()` hata verince oturum izni sızıyor, 30 sn zincirleme zaman aşımı |
| ARCH-004 | Driver yaşam döngüsü | P1 | D | M | G-02/G-18 | Global `quitAllSuiteDrivers()` sınıf-bazlı hook'lardan çağrılıyor |
| ARCH-005 | Driver yaşam döngüsü | P1 | D | M | G-02 | TestNG'de `@BeforeMethod` driver'dan önce, `@AfterMethod` quit'ten sonra |
| ARCH-006 | Retry/Metrik | P1 | D | S | G-03 | TestNG yeniden denemeleri metriğe yazılmıyor; denenen deneme SKIPPED raporlanıyor |
| ARCH-007 | Driver yaşam döngüsü | P1 | D | S-M | G-02 | Sabit 30 sn slot beklemesi; `threadCount` ↔ `maxActiveSessions` doğrulaması yok |
| ARCH-008 | Listener | P2 | G | S-M | G-02 | `TestExecutionListener` temizliğinde try/finally yok |
| ARCH-009 | Konfigürasyon | P2 | D | S | G-02 | `2×CPU` thread sınırı remote/cloud moduna da uygulanıyor |
| ARCH-010 | SPI/Rapor | P2 | D | S | G-18 | SPI dosyası Allure/ReportPortal adaptörlerini koşulsuz yüklüyor; çift çalışma |
| ARCH-011 | Retry | P2 | D | S | G-03 | `@Retryable` semantiği bozuk/ölü kod, sınıf seviyesi yok sayılıyor |
| ARCH-012 | Listener | P2 | D | S | G-03 | `jsErrorsLogged` ThreadLocal bayrağı bayat kalıyor |
| ARCH-013 | PreCondition | P2 | G | M | G-18 | `@PreCondition` çerez/localStorage geri yüklemesi boş tarayıcıda sessizce işlemiyor |
| ARCH-014 | PreCondition | P2 | D | S | G-18 | Sınıf seviyesi `@PreCondition` TestNG yolunda yok sayılıyor |
| ARCH-015 | Driver sağlayıcı | P2 | D | S-M | G-02 | `BrowserContext` (matris) Remote/BrowserStack/SauceLabs'ta yok sayılıyor |
| ARCH-016 | Metrik | P2 | D | M | G-03 | `ExecutionMetrics` anahtarı DataProvider/`invocationCount` ile çakışıyor |
| ARCH-017 | Driver yaşam döngüsü | P2 | G | M | G-02 | `getDriver()` gizli sağlık kontrolü yapıp tarayıcıyı sessizce değiştirebiliyor |
| ARCH-018 | Bootstrap | P2 | G | M | G-17 | Suite yaşam döngüsü uyumsuzlukları, kısmi init geri alınamıyor |
| ARCH-019 | API sözleşmesi | P2 | D | M | G-13 | `@TestFlyApi(since)` metaverisi sürümle uyumsuz; iç yardımcılar "stable" |
| ARCH-020 | Test boşluğu | P2 | D | M | G-02 | Gerçek `DriverManager` yaşam döngüsünün sıfır testi |
| ARCH-021 | Mimari | P2 | İ | L | G-17 | SRP/DRY: god-class, 3× temizleme kopyası, katman tersine çevrilmesi |
| ARCH-022 | Loglama | P3 | İ | M | G-14 | Log parçalanması ve ~120 yutulan istisna |
| ARCH-023 | Driver yaşam döngüsü | P3 | G | S | G-02 | Provider kısmi hata sonrası tarayıcı sızıntısı; çift permit bırakma riski |
| ARCH-024 | Bağlam | P3 | D | S | G-02 | `clearCurrentTestId()` sınıf/metot ThreadLocal'larını bayat bırakıyor |
| ARCH-025 | Eşzamanlılık | P3 | İ | S | G-17 | Statik registry okumaları senkronsuz, public `Class` üzerinde kilit |
| ARCH-026 | Driver yaşam döngüsü | P2 | G | S | G-02 | Shutdown hook yalnızca per-suite driver'ları biliyor |
| ARCH-027 | Locator | P3 | İ | S | G-05 | `SmartLocator` beklemiyor; diğer ThreadLocal'lar temizlenmiyor |
| API-001 | Load test | P1 | D | S | G-04 | JDK motoru HTTP durum kodlarını hiç kaydetmiyor |
| API-002 | Load test | P1 | D | S | G-04 | 4xx/5xx başarı sayılıyor → `assertErrorRateBelow` yanlış-olumlu |
| API-003 | Load test | P1 | D | S | G-04 | `LoadScenario.assertStatus(n)` anlamı yanlış |
| API-004 | Load test | P1 | D | S | G-04 | `extract()` besleyicisiz her iterasyonda `UnsupportedOperationException` |
| API-005 | Load test/Doküman | P1 | D | M | G-04 | Yük testi dokümanı olmayan API'yi anlatıyor |
| API-006 | Load test | P2 | D | S | G-04 | `LoadScenario`/`BaseLoadTest`/`LoadTestSupport` Javadoc zincirleri derlenmiyor |
| API-007 | Load test | P2 | D | S | G-04 | `load()` yalnızca `BaseLoadTest`'te; doküman 4 tabanda iddia ediyor |
| API-008 | Load test | P2 | D | S | G-02 | İsim-tabanlı "loadtest" sezgisi WebDriver'ı yasaklıyor (ARCH-001 ile aynı) |
| API-009 | Load test | P2 | D | S-M | G-04 | `loadtest.enabled` etkisiz, baseUrl fallback yalnız Gatling'de, cooldown yok |
| API-010 | Load test | P2 | D | M | G-04 | JDK motoru: el yazması JSON/JSONPath, URL encode yok, hatalar yutuluyor |
| API-011 | Locator | P1 | D | M | G-05 | `Locator` terminal eylemleri varlık beklemiyor, stale yeniden çözmüyor |
| API-012 | Locator | P1 | D | S | G-05 | `getByText()` XPath'i ata elemanları (ilk eşleşme `<html>`) eşleştiriyor |
| API-013 | Locator | P2 | D | S | G-05 | `type()/clear()` her işletim sisteminde Cmd+A ve Ctrl+A gönderiyor, hata yutuyor |
| API-014 | Locator | P2 | D | S-M | G-05 | `Locator extends By` ama `findElements(SearchContext)` bağlamı yok sayıyor |
| API-015 | Assertion | P2 | D | S | G-05 | Locator assertion'ları yalnız görünür elemanı sayıyor, `LocatorException` retry yok |
| API-016 | Locator | P3 | D | S | G-05 | `SmartLocator` beklemiyor, istisnaları yutuyor, stdout'a yazıyor |
| API-017 | Doküman/Javadoc | P2 | D | S | G-10 | Javadoc'ta deprecated `$()` ve `By`-öncelikli örnekler |
| API-018 | Locator | P3 | D | S | G-10 | `Role` CSS yaklaşımı, "erişilebilirlik ağacı" olarak pazarlanıyor |
| API-019 | Doküman | P1 | D | M | G-10 | Docs yük-dışı olmayan API'ye başvuruyor (`io.testfly.core.*`, `RoleOptions`, `.fill/.val`) |
| API-020 | Retry | P1 | D | S | G-03 | Retry off-by-one ve varsayılan olarak her başarısız test yeniden çalışıyor |
| API-021 | Retry | P2 | D/G | M | G-03 | `@Retryable` TestNG/JUnit5 arasında tutarsız (d maddesi çürütüldü, bkz. 6) |
| API-022 | Listener | P2 | G | S-M | G-18 | Soft-assert flush `ITestResult` durumunu `onTestSuccess` içinde çeviriyor |
| API-023 | Cucumber | P2 | G | S | G-18 | `@After(order=20000)` driver'ı kullanıcı `@After` hook'larından önce kapatıyor |
| API-024 | Rapor | P2 | D | S | G-07 | HTML rapor JSON bloğu `</script>` kaçışsız (SEC-001 ile aynı) |
| API-025 | Rapor | P2 | D | M | G-07 | Sabit `target/…` yolları `ReportPaths`'i yok sayıyor; ekran görüntüsü temp sızıntısı |
| API-026 | Loglama | P3 | D | M | G-14 | Log tutarsızlığı, koşulsuz tanıtım afişi |
| API-027 | AI | P2 | D | S | G-12 | AI sağlayıcıları el yazması JSON kullanıyor |
| API-028 | AI | P2 | İ | M | G-12 | `DomPruner` DOM'u LLM'e göndermeden önce gizli veri temizlemiyor |
| API-029 | Doküman | P2 | D | S | G-10 | Repo'da Playwright/Appium/MCP kodu yok; README/pom "mobile" ve "first-class MCP" diyor |
| API-030 | SPI | P3 | D | S | G-18 | SPI sağlamlığı: `onLoad` korumasız, `FrameworkVersion` yanlış grup yolu |
| API-031 | Doküman | P2 | D | S | G-10 | Custom driver dokümanı hatalı |
| API-032 | API sözleşmesi | P2 | D | S-M | G-13 | `@TestFlyApi(since)` değerleri (3.0.0'a kadar) 1.0.7 ile çelişiyor |
| API-033 | Test boşluğu | P2 | İ | L | G-05 | Gerçek tarayıcı testi yok; bazı testler yanlış davranışı kodluyor |
| BLD-002 | Sürüm/Yayın | P1 | D | S | G-01 | Var olmayan `io.testfly` groupId'si (EN+TR) |
| BLD-003 | Sürüm/Yayın | P1 | D | M | G-01 | Sürüm dizgeleri pom/README/AGENTS/CHANGELOG/docs arasında tutarsız |
| BLD-004 | Sürüm/Yayın | P2 | D | S | G-01 | AGENTS/docs'taki bağımlılık/araç gerçekleri pom'dan sapmış |
| BLD-005 | Bağımlılık güvenliği | P1 | D | S | G-15 | `jackson-databind 2.21.6` için 2 HIGH advisory (OSV) |
| BLD-006 | Bağımlılık | P2 | D/G | S-M | G-08 | Guava 30.1/ByteBuddy 1.14.12 Selenium'unkini eziyor; guice yanlış `optional` |
| BLD-007 | Bağımlılık | P2 | G | S | G-15 | Jackson modül sürüm kayması (2.14–2.17 ↔ 2.21.6) |
| BLD-008 | Test altyapısı | P2 | G | M | G-08 | Mockito dinamik agent + experimental bayrağı; JDK 24+/25 hazırlığı doğrulanmadı |
| BLD-009 | Test altyapısı | P2 | D | S | G-08 | JaCoCo kapısı sessizce devre dışı (`argLine` eziliyor) |
| BLD-010 | Build | P2 | G | S | G-09 | GPG imzası varsayılan `verify`'a bağlı |
| BLD-011 | Release | P2 | D | M | G-09 | `release.yml`: script injection, korumasız tetikleyici, pom↔tag kontrolü yok |
| BLD-012 | CI | P2 | D | S | G-09 | CI iş akışları atlandığında yeşil kalıyor; consumer repo 404 |
| BLD-013 | CI | P2 | D/İ | M | G-09 | CI'da docs build, quality profili, JDK matrisi, pinli action yok |
| BLD-014 | CI | P3 | D | S | G-09 | Jenkinsfile/dokümanda JDK ve dal varsayımları yanlış |
| BLD-015 | Build | P2 | G | M | G-09 | `quality` profili Java 21 için bozuk/etkisiz, hiç çalıştırılmıyor |
| BLD-016 | Test altyapısı | P3 | İ | S-M | G-08 | Surefire paralel/fork yapılandırma sorunları |
| BLD-017 | Build | P3 | İ | M | G-08 | POM hijyeni: enforcer yok, jcodec opsiyonel değil, tekrar üretilebilir build yok |
| BLD-018 | Bağımlılık güvenliği | P2 | D | M | G-15 | Netty/log4j/Rhino advisory'leri (opsiyonel bağımlılıklar) |
| BLD-019 | Konfigürasyon | P1 | D | M | G-06 | Dokümante edilen profil "deep merge" uygulanmamış |
| BLD-020 | Konfigürasyon | P1 | D | M | G-06 | `-Dbrowser.name`, `TESTFLY_*` geçersiz kılmaları dokümante ama yok |
| BLD-021 | Konfigürasyon | P2 | D | M | G-06 | `${VAR}` iç içe map/list'te çözülmüyor; çözülmeyen belirteç sessiz |
| BLD-022 | Konfigürasyon | P2 | D | S-M | G-06 | `TestFlyDefaults` doğrulamadan sonra uygulanıyor |
| BLD-023 | Konfigürasyon | P3 | İ/D | S-M | G-06 | `.env` önceliği ters, sistem özelliği yayını, classpath-önce arama |
| TST-001 | Test altyapısı | P1 | D | S | G-08 | JaCoCo veri toplamıyor; %40/%30 kapısı asla başarısız olamaz |
| TST-002 | Test boşluğu | P1 | D | M | G-02 | `DriverManager` yaşam döngüsünün doğrudan birim testi yok |
| TST-003 | Test boşluğu | P1 | D | S | G-02 | `quit()` hata verince permit sızıntısı (ARCH-003 ile aynı) |
| TST-004 | Test/Retry | P1 | D | S | G-03 | `RetryListener` sözleşmesi Javadoc ile çelişiyor; testler kaçırıyor |
| TST-005 | Test boşluğu | P1 | D | M | G-05 | `WaitEngine` çekirdek beklemeleri, zaman aşımı, heal yolları test edilmemiş |
| TST-006 | Flaky | P2 | G | S | G-08 | `forkCount=4` altında `target/testfly-reports/*` üzerinde JVM'ler arası yarış |
| TST-007 | Test boşluğu | P2 | D | M | G-03 | Gerçek TestNG/JUnit5 koşusu ile listener entegrasyon testi yok |
| TST-008 | Yanlış-olumlu test | P2 | D | M | G-08 | Assertion'sız testler (69/1399) |
| TST-009 | Yanlış-olumlu test | P2 | D | S | G-08 | ReportPortal "geçerli" testi bozuk JSON kullanıyor, hiçbir şey doğrulamıyor |
| TST-010 | Test boşluğu | P2 | D | M | G-06 | `ConfigurationLoaderTest` sığ; doğrulama/öncelik/env yolları testsiz |
| TST-011 | Test boşluğu | P2 | D | L | G-08 | 38 üretim sınıfının hiç test referansı yok |
| TST-012 | CI | P2 | D | S | G-09 | CI "entegrasyon" işi `AI_API_KEY` yoksa her şeyi atlıyor; gerçek tarayıcı yok |
| TST-013 | Paralel izolasyon | P3 | G | S | G-08 | `singleThreaded` olmayan sınıflar global statiklere dokunuyor |
| TST-014 | Test altyapısı | P3 | İ | S | G-08 | Surefire: zaman aşımı yok, thread aşırı abonelik, `reuseForks=false` sızıntıyı gizliyor |
| TST-015 | Flaky | P3 | G | S | G-08 | Zamanlama/`Thread.sleep` bağımlı testler |
| TST-016 | Test stili | P3 | İ | S | G-08 | Assertion stili kusurları (expected/actual ters, yutulan catch) |
| TST-017 | CI | P3 | D | S | G-09 | `-Pquality` ve examples CI'da hiç çalışmıyor |
| DOC-001 | Doküman kurulum | P1 | D | S-M | G-01 | Central'da olmayan koordinat dokümanlarda |
| DOC-002 | Doküman kurulum | P1 | D | S | G-01 | Sürüm çelişkileri; 1.0.5–1.0.7 Central'da yok |
| DOC-003 | Doküman kurulum | P1 | D | S | G-06 | Getting-started `testfly.yml` `execution.mode` eksik → başlangıçta hata |
| DOC-005 | Doküman load | P1 | D | S | G-04 | Tüm load örnekleri `BaseTest` genişletiyor (`load()` yok) |
| DOC-006 | Doküman load | P1 | D | S | G-04 | `@LoadTest`/`@LoadEngine` sözleşmesi kurgusal |
| DOC-007 | Doküman load | P1 | D | M | G-04 | Akıcı (fluent) load DSL kurgusal |
| DOC-008 | Doküman load | P2 | D | S | G-04 | Feeder API hatalı |
| DOC-009 | Doküman load | P2 | D/G | S | G-04 | Dağıtık yük sayfası: etkisiz env değişkenleri, yanlış enforcer iddiaları |
| DOC-010 | Doküman API | P1 | D | S | G-10 | `prompt-recipes` / `video-recording` derlenmiyor |
| DOC-011 | Doküman API | P2 | D | S | G-10 | İç/olmayan paket ve getter'lar plugin dokümanında |
| DOC-012 | Doküman API | P2 | D | S | G-10 | MCP codegen örneğinde `RoleOptions` |
| DOC-013 | Doküman API | P1 | D | S | G-10 | `getWait().waitFor*` / `getWait().wait(...)` |
| DOC-014 | Doküman API | P1 | D | S | G-10 | cucumber/junit5 API örnekleri hatalı |
| DOC-015 | Doküman API | P2 | D | S | G-10 | `Locator.first()` `WebElement` gibi kullanılıyor |
| DOC-016 | Doküman API | P2 | D/G | S | G-10 | `BasePage` protected yardımcıları testlerde kullanılabilir gibi gösteriliyor |
| DOC-017 | Doküman API | P2 | D | S | G-10 | Çeşitli olmayan üyeler (`hasUrlContains`, `softAssertThatPage` …) |
| DOC-018 | Doküman API | P2 | D/İ | S | G-10 | Deprecated `$()` yeni örneklerde ve Javadoc'ta |
| DOC-019 | Doküman DX | P2 | İ | M | G-10 | Page object'ler `By` + manuel `WebDriver` enjeksiyonunu varsayılan öğretiyor |
| DOC-020 | Doküman API | P2 | D | S | G-10 | Landing sayfası `Role.STATUS`, `hasText` kullanıyor |
| DOC-021 | Doküman parite | P2 | D | S-M | G-11 | 4 EN sayfanın TR karşılığı yok |
| DOC-022 | Doküman parite | P2 | D | M | G-11 | Mevcut EN/TR sayfalar API ve olgularda ayrışıyor |
| DOC-023 | Doküman link | P2 | D | S | G-11 | `file:///`, README `docs/features/*`, AGENTS ölü referanslar |
| DOC-024 | Doküman konfig | P2 | D | S | G-06 | Yükleyicinin yok saydığı YAML anahtarları dokümanda |
| DOC-025 | Doküman | P3 | D | S | G-01 | Bayat olgular (JDK17, Gatling 3.10, Selenium 4.40) |
| DOC-026 | API sözleşmesi | P3 | D | M | G-13 | `@TestFlyApi(since)` gerçek sürümle uyumsuz |
| DOC-027 | Önleyici kontrol | P2 | İ | M-L | G-09 | Doküman doğruluğu için otomatik koruma yok |
| DOC-028 | Doküman kurulum | P3 | G/İ | S | G-10 | Getting-started build/çalıştırma boşlukları |
| DOC-029 | Doküman DX | P3 | İ | M | G-10 | Örnekler kendi içinde tam değil |
| SEC-001 | Güvenlik | P1 | D | S | G-07 | HTML rapor veri bloğu `</script>` ile kırılabiliyor (stored XSS) |
| SEC-002 | Güvenlik | P2 | D | S | G-07 | Şablon yer tutucuları gömülü veri içinde yeniden yerine konuyor |
| SEC-003 | Güvenlik | P2 | D | S | G-16 | IMAP SSL sunucu kimliğini doğrulamıyor |
| SEC-004 | Güvenlik | P2 | D | M | G-16 | `ApiClient.withCookies()` çerez kavanozu host-scope'lu değil |
| SEC-005 | Güvenlik | P2 | D | M | G-12 | API loglarında sır sızıntısı (query, gövde, curl, dar maske listesi) |
| SEC-006 | Güvenlik | P2 | D | M | G-12 | AI özellikleri hassas veriyi redaksiyonsuz 3. taraf LLM'e gönderiyor |
| SEC-007 | Güvenlik | P2 | D | S | G-16 | `ApiMockServer` (WireMock) tüm arayüzlerde, açık admin API |
| SEC-008 | Güvenlik/Release | P2 | D | M | G-09 | Release iş akışı sertleştirme |
| SEC-009 | Güvenlik | P2 | D | M | G-09 | Tedarik zinciri / statik analiz kapısı yok |
| SEC-010 | Güvenlik | P3 | D | S | G-06 | Sır taşıma: `.env` sistem özelliği olarak yayınlanıyor |
| SEC-011 | Güvenlik | P3 | G | S | G-12 | URL userinfo/token raporlara yazılıyor |
| SEC-012 | Güvenlik | P3 | D/İ | S | G-16 | Konteynerde otomatik `--no-sandbox` |
| SEC-013 | Güvenlik | P3 | D | S | G-16 | DB yardımcılarında ham SQL |
| SEC-014 | Güvenlik | P3 | D | S | G-16 | Yıkıcı mailbox `clear()` |
| SEC-015 | Güvenlik | P3 | D | S | G-12 | TestRail/Xray istemcileri: zaman aşımı, şema doğrulama, gövde sızıntısı |
| SEC-016 | Güvenlik | P2 | G | M | G-15 | Bağımlılık CVE çıkarımı (tarayıcı çalışmadı; bkz. BLD-005 ile uzlaştırma) |
| SEC-017 | Güvenlik | P3 | D | S | G-12 | Auth yardımcıları (cache anahtarı sırrı yok sayıyor, sahte `digest()`) |
| SEC-018 | Güvenlik | P3 | D | S | G-12 | Sağlayıcı istemcilerinde el yapımı JSON |
| SEC-019 | Güvenlik | P3 | D | S | G-16 | ReportPortal anahtarı açık metin dosyaya yazılabiliyor |
| SEC-020 | Güvenlik | P3 | D | S | G-16 | Multipart başlık enjeksiyonu |
| SEC-021 | Güvenlik | P3 | İ | S | G-16 | `trustAll` semantiği/uyarısı |
| PERF-001 | Performans | P2 | D | M | G-07 | Base64 ekran görüntüleri/kayıtlar heap'te, geçmiş dosyalarında ve HTML'de tekrar tekrar |
| PERF-002 | Performans | P2 | D | S | G-07 | `reports/testfly-report-*.html` arşivi hiç döndürülmüyor |
| PERF-003 | Performans | P2 | D | S | G-12 | HTTP istemci hijyeni: AI çağrısı başına yeni `HttpClient`, zaman aşımı yok |
| PERF-004 | Performans | P3 | D | M | G-04 | Load motoru istek başına iki boxed `double[]`; Gatling fork zaman aşımsız |
| PERF-005 | Performans | P3 | D | S | G-12 | `DomPruner` her eleman için `getComputedStyle`, canlı DOM'u değiştiriyor |
| PERF-006 | Performans | P3 | D | S | G-17 | `assertBodyMatches` ikinci dereceden regex |
| PERF-007 | Performans | P3 | D | S | G-07 | Sabit `target/...` çıktı dizinleri (API-025 ile aynı) |
| PERF-008 | Performans | P3 | G | S | G-17 | ThreadLocal'lar `remove()` edilmiyor (8 sınıf) |
| MAINT-001 | Sürdürülebilirlik | P2 | D | S | G-06 | `${VAR}` çözümlemenin beş ıraksak kopyası |
| MAINT-002 | Sürdürülebilirlik | P3 | D | M | G-12 | El yazması JSON ayrıştırıcıları (Jackson varken) |
| MAINT-003 | Sürdürülebilirlik | P3 | D | M | G-14 | Üç loglama mekanizması |
| MAINT-004 | Sürdürülebilirlik | P3 | D | L | G-17 | God class'lar ve statik global durum |
| MAINT-005 | Sürdürülebilirlik | P3 | D | S | G-14 | Ölü/kullanılmayan kod |
| MAINT-006 | Sürdürülebilirlik | P3 | D | S | G-14 | `.testfly/healed-locators.json` git'te izleniyor |
| MAINT-007 | Sürdürülebilirlik | P3 | D | S | G-07 | JUnit XML geçersiz kontrol karakterleri |

### 2.3 Konsolidasyon grupları (aynı kök neden / birlikte çözülmesi gereken bulgular)

| Grup | Konu | Üye ID'ler |
|---|---|---|
| G-01 | Sürüm, koordinat ve yayın durumu | BLD-001, BLD-002, BLD-003, BLD-004, DOC-001, DOC-002, DOC-025 |
| G-02 | Driver yaşam döngüsü, kaynak sızıntısı, lifecycle testleri | ARCH-001…005, 007, 008, 009, 015, 017, 020, 023, 024, 026, API-008, TST-002, TST-003 |
| G-03 | Retry sözleşmesi ve metrik/izlenebilirlik | ARCH-006, 011, 012, 016, API-020, API-021, TST-004, TST-007 |
| G-04 | Yük testi (motor + doküman) | API-001…010, DOC-005…009, PERF-004 |
| G-05 | Locator / WaitEngine / assertion | API-011…016, ARCH-027, TST-005, API-033 |
| G-06 | Konfigürasyon yükleyici ve sır çözümleme | BLD-019…023, DOC-003, DOC-024, SEC-010, MAINT-001, TST-010 |
| G-07 | Rapor üretimi: güvenlik, boyut, yollar | SEC-001, SEC-002, API-024, API-025, PERF-001, PERF-002, PERF-007, MAINT-007 |
| G-08 | Build/test altyapısı ve test kalitesi | BLD-006, 008, 009, 016, 017, TST-001, 006, 008, 009, 011, 013…016 |
| G-09 | CI/CD, release ve önleyici kapılar | BLD-010…015, SEC-008, SEC-009, TST-012, TST-017, DOC-027, BLD-014 |
| G-10 | Doküman API sapması ve DX | DOC-010…020, 028, 029, API-017, 018, 019, 029, 031 |
| G-11 | EN/TR parite ve bağlantılar | DOC-021, DOC-022, DOC-023 |
| G-12 | Gizlilik, redaksiyon, JSON ve HTTP hijyeni | SEC-005, 006, 011, 015, 017, 018, API-027, 028, PERF-003, 005, MAINT-002 |
| G-13 | `@TestFlyApi` sözleşme metaverisi | ARCH-019, API-032, DOC-026 |
| G-14 | Loglama, yutulan istisna, ölü kod | ARCH-022, API-026, MAINT-003, 005, 006 |
| G-15 | Bağımlılık güvenliği ve sürüm hizalama | BLD-005, BLD-007, BLD-018, SEC-016 |
| G-16 | Diğer güvenlik sertleştirmeleri | SEC-003, 004, 007, 012, 013, 014, 019, 020, 021 |
| G-17 | Mimari borç ve statik durum | ARCH-018, 021, 025, MAINT-004, PERF-006, PERF-008 |
| G-18 | TestNG/JUnit5/Cucumber köprü tutarlılığı | ARCH-004, 010, 013, 014, API-022, 023, 030 |

---

## 3. Findings Report — Doğrulanmış Bulgular (kategorilere göre)

Okuma kılavuzu: Her bulgu için **Konum** (dosya:satır), **Kanıt**, **Etki**, **Çözüm** ve gerekirse **Bağımlılık/Regresyon** verilir. P0/P1 bulgular tam ayrıntıyla, P2 bulgular kısaltılmış, P3 bulgular tek satırla yazılmıştır (özet tablo Bölüm 2.2'dedir). "Repro" komutları `target/audit-scratch/` altındaki gerçek çıktılardan alınmıştır.

### 3.1 Sürüm, koordinat ve yayın (Build/Release)

#### BLD-001 — P0 — DOĞRULANDI (olgu) / GÜÇLÜ ŞÜPHE (neden) — S-M
**Problem:** pom, README, docs-site ve landing sayfası `io.github.hakanngul:testfly:1.0.7` kurmayı söylüyor, ancak Central'da yalnızca 1.0.0–1.0.4 var. Git etiketleri yalnızca `v1.0.2`, `v1.0.4`, `v1.0.6`; `v1.0.5`/`v1.0.7` yok. `target/` içinde yerel imzalı `testfly-1.0.6*.jar/.asc` duruyor (yayınlanmamış).
**Konum:** `pom.xml:9`, `README.md:5,31,184`, `docs-site/docs/getting-started.md:52`, `docs-site/src/pages/index.js:459` ("Maven Central v1.0.7" etiketi yanlış), `CHANGELOG.md:10`.
**Kanıt (rapor yazımı sırasında yeniden çalıştırıldı):**
```
$ curl -s https://repo1.maven.org/maven2/io/github/hakanngul/testfly/maven-metadata.xml | grep -E "latest|release|lastUpdated|<version>"
    <latest>1.0.4</latest>
    <release>1.0.4</release>
      <version>1.0.0</version> … <version>1.0.4</version>
    <lastUpdated>20260907122817</lastUpdated>
$ git tag --list | tail -3        → v1.0.2  v1.0.4  v1.0.6
```
CHANGELOG'a göre 1.0.5 = 2026-09-12 ve 1.0.6/1.0.7 = 2026-09-29; Central `lastUpdated` bunlardan en az bir ay eski. Yayın başarısızlığının nedeni (workflow hiç çalışmadı mı, tetiklenmedi mi, başarısız mı) **GitHub Actions/Central Portal erişimi olmadığı için doğrulanamadı**.
**Etki:** README/Getting-Started'ı izleyen kullanıcı `Could not find artifact io.github.hakanngul:testfly:jar:1.0.7` hatası alır.
**Çözüm:** (1) Actions çalışma geçmişi ve Central Portal'dan kök nedeni bul; (2) niyet edilen sürümü yayınla ya da yeniden etiketle; (3) o zamana dek dokümanlarda "son yayınlanmış sürüm"ü göster; (4) rozet/landing metnini düzelt; (5) BLD-003'teki sürüm tutarlılık denetimini CI'a ekle.
**Bağımlılık:** BLD-010/BLD-011/SEC-008 (yayın hattı güvenli olmadan yeniden yayın yapma), karar kapısı D-01.

#### BLD-002 / DOC-001 — P1 — DOĞRULANDI — S (/S-M)
**Problem:** Java paketi `io.testfly` iken Maven groupId'si `io.github.hakanngul`. Docs ve AGENTS karışık kullanıyor; `io.testfly:testfly` Central'da yok.
**Kanıt:** `https://repo1.maven.org/maven2/io/testfly/testfly/maven-metadata.xml` → HTTP 404; doğru yol 200.
**Konum (EN, TR aynı satırlar):** `getting-started.md:50,61,75`; `gradle.md:32,49,115,131,277`; `junit5.md:24,51`; `cucumber.md:22`; `migration/from-selenium-testng.md:27`; `migration/from-serenity.md:124`; `migration/from-selenide.md:131`; `changelog.md:206,271`; `intro.md:13` (rozet URL'si); `CONTRIBUTING.md:76`; `CHANGELOG.md:180,253`; `AGENTS.md:44`.
**Etki:** Maven ve Gradle sekmeleri bağımlılık çözümlemesinde başarısız olur; tüm Gradle dokümanları bozuk.
**Çözüm:** Tüm kurulum örneklerinde `io.github.hakanngul:testfly`; tarihsel `io.testfly` yalnızca changelog geçmişinde "eski" işaretiyle kalsın; CI'da grep kapısı (bkz. REMEDIATION Önleme Stratejisi).
**Repro:** `grep -rnE "io\.testfly:testfly|<groupId>io\.testfly</groupId>" docs-site CONTRIBUTING.md AGENTS.md CHANGELOG.md`.

#### BLD-003 / DOC-002 — P1 — DOĞRULANDI — M (/S)
Sürüm dizgeleri tutarsız (kaynak: `pom.xml` = 1.0.7; Central = 1.0.4). Tam döküm:

| Konum | Değer |
|---|---|
| `README.md:797` | "Current release: **v1.1.0**" (yanlış) |
| `AGENTS.md:45` | `1.1.0` (yanlış), `AGENTS.md:44` yanlış grup |
| `docs-site/docs/junit5.md:26` / `:51` | Maven 1.0.7 / Gradle **1.0.4** (TR Gradle **1.0.0**) |
| `docs-site/docs/gradle.md:32,49,115,131,277` | **1.0.0** (TR: **2.6.0**, hiç var olmayan sürüm) |
| `docs-site/docs/cucumber.md:22`, `migration/from-selenium-testng.md:27` | **1.0.0** |
| `.github/profile/README.md:89` | **1.0.2** |
| `docs-site/docs/ai/testfly-mcp.md:91` | "TestFly 1.0.6" |
| `CHANGELOG.md` | 1.0.1–1.0.3 (yayınlanmış!) için kayıt yok; 1.0.5–1.0.7 yayınlanmamış |
| TR `changelog.md` | Farklı sürüm şeması (3.3.0 … 0.5.0) |

**Kök neden:** Tek-kaynak mekanizması yok; AGENTS "version-bump checklist"i dosyaların bir alt kümesini listeliyor (`cucumber.md`, `gradle.md`, `loadtest/getting-started.md`, `from-selenium-testng.md`, `.github/profile/README.md` ve tüm TR aynaları eksik).
**Çözüm:** Karar D-01 sonrası tek geçişte düzelt; sürümü `pom.xml`'den üretilen tek sabite bağla; checklist'i tam listeye genişlet; CHANGELOG 1.0.1–1.0.3'ü tamamla.
**Bağımlılık:** BLD-001, BLD-002.

#### BLD-004 (P2) — DOĞRULANDI — S
AGENTS.md ve docs'taki olgular pom'dan sapmış: Selenium 4.40.0 ↔ pom 4.48.0 (`pom.xml:50`); Jackson 2.21.0 ↔ 2.21.6; jakarta.mail 2.0.1 ↔ 2.0.2; POI 5.2.5 ↔ 5.4.0 (`external-test-data.md:117`, `gradle.md:246`); Gatling 3.10.x ↔ 3.13.5 ve dört artifact (`pom.xml:220-247`); `AGENTS.md:290` workflow adı `testfly.yml` ↔ gerçek `testfly-ci.yml`; `AGENTS.md:326` "yayın manuel" ↔ `release.yml` var; `docs-site/docs/ci/jenkins.md:21` `jdk 'JDK17'` ↔ `--release 21` (`pom.xml:322`); `CONTRIBUTING.md:101` "PR against master" ↔ dallar `main`/`development`; `CONTRIBUTING.md` "src/test/resources/testfly.yml git'te yok sayılır" ↔ dosya izleniyor. **Çözüm:** Sabit sürüm yerine "bkz. pom.xml"; satırları düzelt.

### 3.2 Çekirdek mimari ve driver yaşam döngüsü (Alan A)

Not: ARCH-002/003/001 tek kullanımlık repro programlarıyla (`target/audit-scratch/area-a-tmp/`) çalıştırılarak doğrulandı; gerçek tarayıcı/ızgara olmadığından ARCH-013/017/026 ve ARCH-004'ün JUnit-paralel kısmı uçtan uca çalıştırılmadı.

#### ARCH-001 (+API-008) — P1 — DOĞRULANDI — S
`contains("loadtest")` alt dizgi sezgisi dört kopyada: `driver/DriverManager.java:439`, `listeners/TestExecutionListener.java:410-411`, `junit5/TestFlyExtension.java:518`, `cucumber/CucumberHooks.java:432`. `FileUploadTest`, `DownloadTest` veya `com.acme.uploadtests` paketi bu sezgiye takılır.
**Kanıt (repro):** `loadtest-heuristic: true / true / pkg com.acme.uploadtests: true`. Rapor sırasında kaynakta `DriverManager.java:439` ve `TestExecutionListener.java:411` satırları yeniden doğrulandı.
**Etki:** Dosya yükleme/indirme testleri driver almaz; `open()` ilk çağrıda `WebDriver is not available during Load Testing` ile düşer ve mesaj load testi suçlar.
**Çözüm:** İsim çıkarımını kaldır; yalnızca açık sinyaller (`BaseLoadTest`, `LoadTestSupport`, `@LoadTest`, `LoadTestRunner.isExecuting()`); tek bir `LoadTestDetector`; negatif birim testleri (`UploadTest`, `DownloadTest`). **Regresyon:** isim kuralına güvenen kullanıcılar `@LoadTest`/taban sınıf eklemeli (docs kontrol edilmeli).

#### ARCH-002 — P1 — DOĞRULANDI — S
`getDriver()` → `recreateDriver()` = `quitDriver()` + `createDriver()`; per-suite modda `quitDriver()` hemen döner (`DriverManager.java:356-358`) ve `createDriver()` idempotans korumasıyla (`:116-118`) erken çıkar.
**Kanıt (Mockito driver, `getTitle()` → `NoSuchSessionException`):**
```
RESULT lifecycle=per-test  providerCalls=2 getDriver()==deadFirstDriver:false
RESULT lifecycle=per-suite providerCalls=1 getDriver()==deadFirstDriver:true
```
**Etki:** per-suite modda çöken oturum thread'deki tüm sonraki testleri zehirler; log "Recreating…" yazar ama yeniden yaratma olmaz.
**Çözüm:** `recreateDriver()` `forceQuitDriver()` benzeri teardown (SUITE_DRIVERS'tan çıkar, permit'i bir kez bırak) kullansın. **Bağımlılık:** ARCH-003, ARCH-020.

#### ARCH-003 (+TST-003) — P1 — DOĞRULANDI — S
`DriverManager.java:364-365`: `driver.quit(); getOrInitSemaphore().release();` aynı `try` içinde; `quit()` fırlatırsa `release()` atlanır. `forceQuitDriver` (`:385-386`) aynı kalıp.
**Kanıt (`maxActiveSessions=1`, mock `quit()` fırlatır):**
```
[TestFly] Driver quit failed: grid unreachable
RESULT second createDriver FAILED: Timed out waiting for an available session slot after 30s...
RESULT waited ms=30001
```
**Etki:** Tek başarısız quit slotu kalıcı kaybeder; `maxActiveSessions=5` iken beş olay sonrası sonraki her test 30 sn bekleyip düşer (altyapı arızası gibi görünür; grid/cloud sorunlu olduğunda en olası).
**Çözüm:** `finally { release(); DRIVER.remove(); }`, permit'i driver başına tek sefer takip et. **Test:** `quit()` fırlatan mock ile `activeSessions()==0` ve ikinci `createDriver()` başarılı.

#### ARCH-004 — P1 — DOĞRULANDI (kod okuma) — M
`quitAllSuiteDrivers()` JVM-genel `SUITE_DRIVERS` kümesini `parallelStream` ile kapatır (`DriverManager.java:408-412`) ama `junit5/TestFlyExtension.java:347-353` (`afterAll`, her test sınıfı sonu) ve `cucumber/CucumberHooks.java:445-463` (`@AfterAll`) tarafından çağrılır.
**Etki:** JUnit5 paralel sınıf çalıştırmada A sınıfı bitince B'nin canlı tarayıcısı kapanır; sıralı çalışmada bile per-suite fiilen per-class'a dönüşür; yalnızca çağıran thread'in `DRIVER` ThreadLocal'ı temizlenir (diğerleri ölü driver'ı tutar; ARCH-002 ile birleşir). Cucumber'da `SuiteExecutionListener.onFinish` ile çift rapor üretimi (bkz. ARCH-010).
**Çözüm:** Global teardown'u launcher düzeyi hook'a (`TestFlyLauncherListener.testPlanExecutionFinished` veya root `ExtensionContext` `CloseableResource`) taşı. **Regresyon:** orta (per-suite JUnit kullanıcıları için davranış değişir). **Uçtan uca çalıştırılmadı.**

#### ARCH-005 — P1 — DOĞRULANDI (TestNG repro) — M (S)
Driver `TestExecutionListener.onTestStart`'ta (`:96-135`) yaratılır, `onTestSuccess/Failure/Skipped`'ta kapatılır; TestNG `@BeforeMethod`'u `onTestStart`'tan önce, `@AfterMethod`'u `onTestSuccess`'ten sonra çalıştırır.
**Kanıt (çerçevesiz TestNG koşusu):** `EVT @BeforeMethod → onTestStart → @Test → onTestSuccess → @AfterMethod`. `DriverManager.getDriver()` driver yokken `WebDriver not initialized for current thread.` fırlatır (`:289-292`).
**Etki:** `@BeforeMethod`'da `open()`/login yapan veya `@AfterMethod`'da `getDriver()` kullanan kod çalışmaz. `docs-site/docs/guides/precondition.md:19-24` bu kalıbı "çözülen sorun" olarak gösterir; `MockSupport.resetMocks()` bir `@AfterMethod` varsayılan metodudur. Kısıt hiçbir yerde belgelenmemiş.
**Çözüm:** (a) `IInvokedMethodListener.beforeInvocation` ile config metotları için driver'ı tembel yarat (M) ya da (b) en azından eyleme dönük istisna mesajı + doküman (S). **Karar kapısı D-06.**

#### ARCH-006 — P1 — DOĞRULANDI — S
`ExecutionMetrics.recordRetry` (`metrics/ExecutionMetrics.java:241`) yalnızca `CucumberHooks.java:88` ve `TestFlyExtension.java:382`'den çağrılıyor; TestNG yolunda çağıran yok. `BuildThresholdEnforcer.java:58` flaky'yi `retryCount > 0 && PASSED` ile sayar.
**Kanıt (retry analyzer bir kez true):** `onTestStart → afterInvocation status=2 → onTestSkipped wasRetried=true → onTestStart → … onTestSuccess`. `TestExecutionListener.onTestSkipped` (`:309-330`) denenen denemeyi "SKIPPED" kaydeder, `HookRegistry.onTestEnd(..,"SKIPPED")` çağırır ve TestRail/Xray'e SKIPPED iter.
**Etki:** `ci.maxFlakyTests` kapısı ve flakiness raporlaması ana (TestNG) çalıştırıcıda sessizce etkisiz; dış sistemlere sahte SKIPPED.
**Çözüm:** `onTestSkipped` içinde `wasRetried()` ise `recordRetry`, yalnızca temizlik, çık. Birim test ekle.

#### ARCH-007 — P1 — DOĞRULANDI — S-M
Sınıf Javadoc'u "slot için bekler" diyor ama `DriverManager.java:131` ve `:213` `tryAcquire(30, SECONDS)` ile fırlatıyor. Varsayılan `maxActiveSessions=5` (`TestFlyConfig.java:218`); `ExecutionValidator` (`:38-42`) `threadCount`'u yalnızca CPU ile karşılaştırır. Adlandırılmış oturumlar aynı havuzdan çeker: `threadCount=4, maxActiveSessions=5` ve iki oturum kullanan testlerde hold-and-wait kilitlenmesi mümkün.
**Çözüm:** `execution.sessionWaitSeconds` yapılandırılabilir; `threadCount <= maxActiveSessions` doğrulaması; boyutlandırma kuralı belgelenmeli.

#### ARCH-008…ARCH-027 (P2/P3) — kısa kayıtlar
- **ARCH-008 (P2, G):** `TestExecutionListener.onTestSuccess` (`:138-217`) ~20 ifade, `finally` yok; `HookRegistry` yalnızca `Exception` yakalar (`NoClassDefFoundError` kaçar). Bir fırlatma `quitDriver()`'ı atlar → tarayıcı + permit sızıntısı. Çözüm: ortak `try { … } finally { cleanup }`. Çalıştırılmadı.
- **ARCH-009 (P2, D):** `execution/ExecutionValidator.java:38-42` `execution.mode`'dan bağımsız `2×CPU` sınırı atar; 2 çekirdekli CI konteyneri ızgarada >4 oturum çalıştıramaz. Çözüm: sınırı yalnız `local` için uygula.
- **ARCH-010 (P2, D):** `META-INF/services/io.testfly.reporting.ReportAdapter:3-4` Allure ve ReportPortal adaptörlerini listeliyor; `AllureReportAdapter.generate` `enabled` kontrolü yapmıyor → `target/allure-results` koşulsuz yazılıyor; etkinleştirildiğinde `FrameworkBootstrap.java:74` ikinci örneği kaydediyor (çift sonuç dosyası). Cucumber'da `SuiteExecutionListener.onFinish` + `CucumberHooks.afterAllScenarios` raporları iki kez üretiyor (G). Çözüm: SPI satırlarını sil ya da adaptörleri self-gating yap; teardown'u idempotent yap.
- **ARCH-011 (P2, D):** `RetryListener.java:56-60` `isGlobalRetry` her zaman true (ölü dal); `retry.enabled` varsayılanı **true**, `maxAttempts=1` (`TestFlyConfig.java:497-499`); `@Retryable` `@Target({METHOD,TYPE})` ama yalnızca metot bakılıyor. Javadoc ile davranış çelişiyor. Ayrıntı API-020, TST-004. Karar kapısı D-02.
- **ARCH-012 (P2, D):** `TestExecutionListener.java:147` `jsErrorsLogged=true` yalnız normal yolda temizleniyor (`:217`); `failOnConsoleErrors` ve soft-assert yönlendirmeleri `onTestFailure(result); return;` ile bayrağı bırakıyor; sıradaki başarısız test konsol hatası toplamayı atlıyor (`:245`). Çözüm: `onTestStart`'ta ve `finally`'de sıfırla.
- **ARCH-013 (P2, G):** `PreconditionSessionCache.restore` (`:56-70`): `about:blank` üzerinde `addCookie` `InvalidCookieDomainException`, `localStorage` `SecurityError` verir; ikisi de `catch (Exception ignored)` ile yutulur; "Restoring cached session" yazılır ama hiçbir şey geri yüklenmez. Birim testleri driver'ı mock'lar. Gerçek tarayıcıda doğrulanmadı.
- **ARCH-014 (P2, D):** `PreConditionRunner.run(ITestResult)` yalnız metot anotasyonuna bakar (`:39`); JUnit aşırı yüklemesi sınıfa düşer (`:88-93`). Aynı anotasyon, runner'a göre farklı davranış.
- **ARCH-015 (P2, D):** `DriverProviderFactory.getProvider` `BrowserContext`'ten tarayıcıyı hesaplar ama `Remote/BrowserStack/SauceLabs` sağlayıcılarını bundan önce döndürür (`RemoteDriverProvider.java:20`, `BrowserStackProvider.java:52` kullanmaz); `ExecutionMetrics.recordBrowser` ise matris tarayıcısını yazar: raporlar hiç çalışmamış tarayıcıları gösterir.
- **ARCH-016 (P2, D anahtar / çıkarım aşağı akış):** `TestExecutionListener.java:99` `getQualifiedName()`; DataProvider satırları ve `invocationCount>1` aynı kimliği paylaşır, durum/süre üstüne yazılır (`ExecutionMetrics.START_TIMES.remove`, `:286`). Çözüm: parametre özeti/çağrı sayısı ekle; flakiness geçmişi anahtarları değişir.
- **ARCH-017 (P2, G):** `DriverManager.getDriver()` (`:278-312`) her çağrıda `driver.getTitle()` (`:338`) ile sağlık kontrolü yapar; açık alert `UnhandledAlertException` doğurur (Chrome `unhandledPromptBehavior=ignore`, `LocalChromeDriverProvider.java:119`) ve tarayıcı sessizce değiştirilir. Çözüm: `getDriver()` saf erişimci olsun; `NoSuchSessionException`'da yeniden dene.
- **ARCH-018 (P2, G):** `FrameworkBootstrap.initialize()` JVM başına bir kez (`:38-40`) fakat `SuiteExecutionListener.onFinish` suite başına `PluginRegistry.unloadAll()` çağırır; çoklu suite'te eklentisiz koşu. `TestFlyContext.initialize(config)` (`:50`) registry yüklemelerinden önce; hata sonrası yarı yapılandırılmış durum. `onFinish` (`:235-258`) `try/finally`siz.
- **ARCH-019 (P2, D):** 189 `@TestFlyApi(since=…)` içinde 146'sı pom 1.0.7'den yeni sürüm iddia ediyor (3.0.0'a kadar). `DriverManager`/`MultiSessionManager` tür düzeyinde "stable" ve iç yardımcıları (`acquirePermit`, `releasePermit`, `pushSessionOverride`, `recreateDriver`, `quitAllSuiteDrivers`) donduruyor. Ayrıntı G-13 (API-032, DOC-026).
- **ARCH-020 (P2, D):** Gerçek `DriverManager` yaşam döngüsünün sıfır testi: tüm kullanım `mockStatic(DriverManager)` (22 kullanım; TST-002 24 dosya diyor, sayım yöntemi farkı). ARCH-002/003/023'ün 1361 yeşil testten geçmesinin nedeni.
- **ARCH-021 (P2, İ):** `TestExecutionListener` 664 satır god-class; aynı ~10 satırlık temizlik üç kez (L205-217, 294-305, 320-330) ve zaten ıraksamış (`NetworkMock.cleanup()` skipped'ta yok); aynı yaşam döngüsü `TestFlyExtension` (634) ve `CucumberHooks` (469) içinde yeniden yazılmış (ARCH-006/012/014 kökü); `DriverManager` yukarı doğru `io.testfly.loadtest.*`'a bağımlı. Çözüm: runner-bağımsız `TestLifecycle`; yük-test bağımlılığını `DriverGuard` arayüzü ile ters çevir. **Önkoşul: ARCH-020 testleri.**
- **ARCH-026 (P2, G):** Shutdown hook (`DriverManager.java:56-63`) yalnızca per-suite driver'ları kapatır; per-test/remote oturumlar SIGTERM'de açık kalır (bulutta faturalanır).
- **P3:** ARCH-022 (156 `System.out/err`, 25 JUL dosyası, 3 SLF4J, ~120 yutulan istisna), ARCH-023 (sağlayıcı kısmi hatasında tarayıcı sızıntısı; `DRIVER.set` sonrası fırlatmada çift permit bırakma, `DriverManager.java:157,192`), ARCH-024 (`clearCurrentTestId()` yalnızca `CURRENT_TEST`'i temizler, `TestFlyContext.java:72` vs `:88-92`), ARCH-025 (senkronsuz registry okumaları; public `Class` üzerinde kilit, `FrameworkBootstrap.java:36-37`), ARCH-027 (`SmartLocator` beklemiyor; `PreConditionRunner.clearAll()` yalnız çağıran thread'i temizliyor).

### 3.3 Otomasyon motoru & API (Alan B)

**Yük testi — gerçek `@LoadTest` sözleşmesi** (`loadtest/LoadTest.java:44-83`): `int users() default -1; String rampUp(), hold(), cooldown(), engine(), baseUrl()` (süre dizgeleri `"30s"`, `"2m"`). Çözümleme sırası: YAML `loadtest:` < sınıf `@LoadTest` < metot `@LoadTest` < akıcı `LoadScenario` ayarları. **Yok:** `rampUpSeconds`, `durationSeconds`, `targetRps`, `scenarioName`, `@LoadEngine`, `LoadScenario.get()` (argümansız), `step(name, Consumer)`.

Yeniden üretim donanımı (`target/audit-scratch/tng/LoadRepro.java`, JDK `HttpServer` + `LoadTestRunner.run`) ham çıktısı:
```
500-endpoint: total=4078 failed=0 errorRate=0.0 statusCodes={}
assertStatus(404) on 500-only endpoint: PASSED (false positive)
extract w/o feeder: total=7255 failed=7255
LoadTestAssert.assertStatus(200) on healthy 200 endpoint: [LoadTest] ok: status 200 count 0 is below minimum 1
```

- **API-001 (P1, D, S):** `JdkLoadEngine.java:102` `statusCounts` oluşturulur, `:224` kopyalanır, hiçbir yer yazmaz; `:310-313` yorumu ("caller seviyesinde izleniyor") yanlış, `executeStep` `void` döner. Sonuç: `assertStatus(200)` sağlıklı endpoint'te düşer, `assertNoStatus(x)` her zaman geçer, HTML rapor durum dağılımı boş (Gatling yolu `GatlingResultsParser.java:242` doldurur). Çözüm: `executeStep` `int` dönsün, çağıran sayaç artırsın; taşıma hatası için sentetik kod.
- **API-002 (P1, D, S):** `JdkLoadEngine.java:155-163` istisna atmayan her yanıtı başarı sayar; yalnızca `check(...)` fırlatır. Repro: 4078 istek 500 → `failed=0 errorRate=0.0`. `JdkLoadEngineTest.testErrorEndpoint` (`:156-170`) bunu "HTTP seviyesinde başarılı" diye kodlar. Gatling motoru farklı davrandığından `auto` seçiminde sonuç motora bağlı. Çözüm: `status>=400` başarısız (açık `status()` kontrolü yoksa). **Regresyon:** bugün geçen paketler düşebilir; CHANGELOG notu. Karar kapısı D-04.
- **API-003 (P1, D, S):** `LoadScenario.java:221-223` `assertNoStatus(expected == 200 ? 500 : expected)`; Javadoc "tüm yanıtlar verilen kodu döndü" diyor. `expected=404` için "404 yok" iddia eder (ters).
- **API-004 (P1, D, S):** `JdkLoadEngine.java:146-148` `Collections.emptyMap()`; `:324` `vars.put(...)` → `UnsupportedOperationException`, `:163` genel `catch` ile başarısız istek. Repro: 7255 istekten 7255 başarısız. Belgelenen "login → token çıkar → sipariş" senaryosu JDK motorunda %100 hata. Çözüm: iterasyon başına `new HashMap<>(feederRow)`.
- **API-005 (P1, D, M):** Yük sayfaları (`loadtest/annotations.md:26-50,69,90,100`, `getting-started.md:106`, `fluent-api.md:20-26,40,65-71,96,111`, `feeders.md`, `engines.md:65`, `changelog.md:92-94`) var olmayan API'yi anlatıyor; TR aynıdır ve 4 TR sayfa eksik. Ayrıntı: DOC-005…DOC-008.
- **P2 (kısa):** API-006 Javadoc zincirleri `step()`'in `LoadStep`'te olmaması yüzünden derlenmez (`LoadScenario.java:15-33`, `BaseLoadTest.java:56-70`); `LoadStep.step(String)` eklemek ya da Javadoc'u düzeltmek. API-007 `load()` yalnızca `BaseLoadTest` (`BaseLoadTest.java:79-85`); `BaseTest.java:40-43`, `BaseApiTest.java:41` uygulamıyor. API-008 = ARCH-001. API-009 `loadtest.enabled` hiç okunmuyor; baseUrl fallback yalnız `GatlingEngine.java:52` (JDK `:81-85` fırlatır); cooldown JDK'da yok; `engine` büyük/küçük harf duyarlı (`LoadTestRunner.java:97-114`); `resolveFor` `Duration.ofMillis(500)`'ü `"0s"`ye çevirir (`LoadTestConfig.java:109-111`). API-010 el yazması JSON (`:473-495`) ve JSONPath (`:~347-400`), URL kodlama yok, hatalar yutuluyor (`:163`), 0 istekli koşu vakumla geçiyor (`:214`).

**Locator / WaitEngine / assertion**
- **API-011 (P1, D kod, M):** `Locator.java:358` "tüm eylemler auto-wait" der; gerçek: `click()` = `waitForClickable(resolve()).click()` (`:362-364`); `resolve()` (`:517-533`) `driver.findElements` bir kez çağırır, boşsa anında `LocatorException` (`:528`). `WaitEngine.waitForVisible(WebElement)` (`:112,149`) yalnız bulunmuş elemanı bekler. Tüm sağlayıcılar `implicitlyWait(Duration.ZERO)` ayarlar (`LocalChromeDriverProvider.java:24`), `WaitEngine.java:30` "implicitlyWait kullanma" der: başka bekleme katmanı yok. SPA'da sonradan çıkan eleman anında hata verir; staleness'ta bayat tutamak üzerinde beklenir. `BasePage.click(By)` → `find(by).click()` aynı yol. Repro (tarayıcı gerek): 2 sn sonra `<button id=b>` ekleyen sayfa; `Locator.id("b").click()` anında `LocatorException`, `WaitEngine.waitForClickable(By.id("b"))` başarılı. Çözüm: `resolve()`'u `WebDriverWait(timeouts.explicit)` ile her iterasyonda yeniden çözen yoklamaya çevir; `count()/isVisible()` bekletmesiz kalsın. Regresyon orta.
- **API-012 (P1, D XPath semantiği, S):** `Locator.java:737-745` `.//*[contains(translate(normalize-space(.),…),'text')]`; `normalize-space(.)` alt ağacın tüm metni olduğundan her ata eşleşir, `resolve()` `getFirst()` (`:538`) = en dıştaki. JDK XPath repro (`XP.java`): `<button>Sign In</button>` için `html, body, div, form, button` (5 düğüm; `html` ilk). Mevcut testler yalnızca üretilen XPath dizgesini doğrular (`SemanticLocatorTest.java:63-85`). Etki: `getByText("Sign In").click()` `<html>`/`<body>` merkezine tıklar. Çözüm: `text()` düğümü veya `[not(.//*[contains(…)])]` ile en içteki eşleşme.
- **P2 (kısa):** API-013 `robustClear` (`Locator.java:386-399`) hem `COMMAND` hem `CONTROL` + `a`, 3 round-trip, hata Türkçe uyarıyla yutuluyor. API-014 `findElements(SearchContext)` bağlamı yok sayıyor (`:508-511`). API-015 `SeleniumAssert.LocatorBy` `Locator.elements()` kullanır ve `isDisplayed` süzer (`:136-141`; `Locator.java:498-502`); `LocatorException` `RuntimeException` (`NotFoundException` değil) → `WebDriverWait` ilk `nth(i)` kaçırmasında bırakır; `LocatorAssert.java:433` null korumasız. API-017 `$()` 11 Javadoc satırında (`Locator.java:228,242,255,268`, `LocatorAssert.java:38`, `SeleniumAssert.java:27`, `SessionSupport.java:35,40`, `MultiSessionManager.java:22`, `BaseCucumberTest.java:37`, `LocatorSupport.java:11`); `BasePage.java:35-49` kanonik POM `By` sabitleri. API-033 gerçek tarayıcı testi yok.
- **P3:** API-016 `SmartLocator` (`SmartLocator.java:46-63`), API-018 `Role` CSS yaklaşımı (`Role.java:26-73`; `@since 3.1.0` vs `@TestFlyApi(since="1.0.0")`).

**Retry, köprüler**
- **API-020 (P1, D, S):** Docs `configuration.md:182` "1 = yeniden deneme yok, 2 = 1 ilk + 1 yeniden", `:554` varsayılan `1`. Kod: `RetryListener.java:82` `if (attempt < maxAttempts) { attempt++; return true; }` (rapor sırasında yeniden doğrulandı) → `maxAttempts=1` iki yürütme. `TestFlyConfig.Retry` varsayılanı `enabled=true, maxAttempts=1` (`:497-499`). `RetryListenerTest.retry_globalEnabled_singleAttempt_retriesOnce` (`:89-97`) bunu kodlar. JUnit5 aynı (`TestFlyExtension.java:365-381`). Etki: sıfır konfigürasyonla her başarısız test iki kez çalışır, flakiness maskelenir, `ci.maxFlakyTests` bozulur. Çözüm: `maxAttempts` toplam deneme olsun (`attempt + 1 < maxAttempts`) ve/veya `retry.enabled=false` varsayılan (opt-in `@Retryable`). **Davranış değişikliği: karar kapısı D-02.**
- **API-021 (P2):** (a) `RetryListener.java:56-60` ölü dal; (b) TestNG'de `retry.enabled=false` `@Retryable`'i de kapatır, JUnit5 (`TestFlyExtension.java:420-427`) etmez; (c) JUnit5 `catch (Throwable t)` (~`:410`) `TestAbortedException`/assumption'ı yeniden dener ve `@BeforeEach` durumu kaybolur. **(d) "TestNG analyzer'ı DataProvider satırları arasında paylaşır" iddiası ARCH-027'deki betik koşusuyla ÇÜRÜTÜLDÜ** (her satır için ayrı analyzer örneği oluşturuluyor).
- **API-022 (P2, G):** `TestExecutionListener.java:164-176` `onTestSuccess` içinde `result.setStatus(FAILURE)` + elle `onTestFailure`. TestNG 7.9.0 deneyinde (`T.java`): `TestNG.getStatus()=1, hasFailure()=true` ama `TestListenerAdapter` `passed=1, failed=0` raporlar. Surefire altında etki doğrulanmadı.
- **API-023 (P2, G):** `CucumberHooks.java:123` `@After(order=20000)` ekran görüntüsü + metrik + **driver quit**; Javadoc "yüksek order önce çalışır" diyor: kullanıcı `@After(10000)` hook'ları driver kapalıyken çalışır. Cucumber 7.20.1 ile çalıştırılmadı.

**Rapor, loglama, AI, SPI**
- **API-024 = SEC-001 (aşağıda).**
- **API-025 (P2, D):** `ReportPaths.baseDir()` `-Dtestfly.reports.dir` ve Gradle'ı destekler ama 19 yer atlar: `ScreenshotManager.java:25`, `AllureReportAdapter.java:56`, `TraceRecorder.java:259`, `RecordingManager.java:155`, `HealLog.java:53`, `FlakinessAnalyzer.java:167`, `HtmlReportGenerator.java:188`, `ShardingMethodInterceptor.java:89`, `RemediationPatchGenerator.java:93`, `VisualAssert.java:330`, `TestFlyConfig.java:303,1263,2332`, `JUnitXmlReporter.java:38-41`. `ScreenshotManager.capture` (`:47-60`) `OutputType.FILE` geçici dosyasını silmez; dosya adı `currentTimeMillis` ile paralel satırlarda çakışır (`FileAlreadyExistsException` yutulur, ekran görüntüsü kaybolur).
- **API-027 (P2, D):** `ClaudeProvider.escapeJson` (`:~122-129`) `\r`'i düşürür, diğer kontrol karakterlerini (ANSI `\u001b`) kaçırmaz → geçersiz JSON → HTTP 400 → sessiz `null`; `extractContent` `\uXXXX` çözmez; Gemini/OpenAI aynı. `ClaudeProvider.java:65-66` anahtarı hem `x-api-key` hem `Authorization: Bearer` ile yollar.
- **API-028 (P2, İ):** `DomPruner` (`ai/DomPruner.java:12-60`) script/style/svg/yorum/olayları siler ama `value`, gizli CSRF, `data-*` kalır. AI çağrıları opt-in (`failureAnalysis=false`, `generatePatch=false` varsayılan, fakat `ai.enabled=true`), dolayısıyla aktif sızıntı değil tasarım boşluğu.
- **API-029 (P2, D):** Repo'da Playwright/Appium/MCP kodu yok (`grep -rniE "playwright|appium" src/main pom.xml` yalnızca Javadoc yorumları ve pom yorumu `pom.xml:201`; `io.appium`/`com.microsoft.playwright` 0 isabet). Buna karşın `README.md:3` "web, API, mobile, and AI-powered", `pom.xml:13`, `docs/intro.md:2`, `README.md:96-97` "first-class MCP server" (sunucu ayrı npm paketi/depo `hakanngul/testfly-mcp`, burada denetlenemez). "mobile" yalnızca kullanıcı yazımlı Appium `NamedDriverProvider` ile mümkün.
- **API-030 (P3, D):** `PluginRegistry.loadAll` (`:28-40`) `onLoad` ve `ServiceConfigurationError` korumasız; `checkVersion` (`:74-82`) fırlatmak yerine stderr + atlama (Javadoc "fail fast" der); `FrameworkVersion.java:90-105` `META-INF/maven/io.testfly/testfly/pom.properties` arar ama grup `io.github.hakanngul`, bu yüzden IDE/`target/classes`'ta sürüm `"0.0.0"` ve `minFrameworkVersion` olan her eklenti atlanır.
- **API-031 (P2, D):** `custom-drivers.md`: "`browser.mode: remote`" yanlış anahtar (`execution.mode`, `DriverProviderFactory.java:17-27`); remote/cloud modlarında özel sağlayıcılar **hiç sorgulanmaz**; `DriverProvider.createDriver()` kontrollü istisna bildirmez ama örnek `new AndroidDriver(new URL(...))` yazar → derleme hatası.
- **API-032 (P2, D):** `@TestFlyApi(since)` değerleri 1.10.0 (22×), 1.9.0 (18×), 1.6.0 (23×), 2.x (11×), 3.0.0 (3×), 1.13.0…; artifact 1.0.7. `$()` `@Deprecated(since="1.1.0", forRemoval=true)` (`LocatorSupport.java:74,84,95`), yani mevcut sürümden sonraki bir sürümde "kaldırılmak üzere" deprecated. Karar kapısı D-05.

### 3.4 Konfigürasyon (Alan C)

- **BLD-019 (P1, D, M):** `ConfigurationLoader.load()` (`config/ConfigurationLoader.java:36-62`) tek dosya seçer (`testfly.yml` YA DA `testfly-<profile>.yml`); birleştirme yok. `docs-site/docs/configuration.md:70-86` "profil dosyaları yalnızca geçersiz kılacakları bildirir; TestFly bunları `testfly.yml` üstüne birleştirir" der. Reponun kendi `src/test/resources/testfly-stage.yml` dosyası (4 satır) tam böyle kısmi dosyadır.
  **Repro:** `-Dtestfly.profile=stage` → `FAIL: java.lang.IllegalStateException: Browser configuration must be specified` (profil çözümleme satırları rapor sırasında `ConfigurationLoader.java:36-40` ile görüldü).
  Çözüm: temel `testfly.yml` + profil derin birleştirme (YAML `Map` üzerinde), sonra `${VAR}`, sonra doğrulama; ya da dokümanı "profil dosyası eksiksiz olmalı" diye düzelt. Karar kapısı D-03.
- **BLD-020 (P1, D, M):** Yalnızca `testfly.profile`, `testfly.config` (+ `testfly.merge`, shard/quality bayrakları) okunuyor. Dokümante ama **yok**: `-Dbrowser.name` (`ci/github-actions.md:121`, `ci/jenkins.md:95,100`, `gradle.md:183`), `-Dtestfly.browser.headless` (`ci/bitbucket-pipelines.md:41`), `TESTFLY_LOAD_VUSERS`/`TESTFLY_API_BASEURL` (`loadtest/distributed-docker-k8s.md:54-95`), `TESTFLY_BROWSER_NAME` (`docs/public-api.md:124`). Repro (`cfg/O.java`): `browser.name after -Dbrowser.name=firefox: chrome`. **Etki:** dokümandaki CI tarayıcı matrisleri aynı tarayıcıyı N kez çalıştırıp yeşil raporlar (yanlış-olumlu boru hattı). Çözüm: `-Dtestfly.<path>` ve `TESTFLY_<PATH>` katmanı (öncelik `-D` > env > profil > taban > varsayılan) ya da dokümandan kaldırıp `${BROWSER:-chrome}` örneği ver.
- **BLD-021 (P2, D, M):** `resolveFields` (`:180,198`) JDK türlerini (`LinkedHashMap`/`ArrayList`) atlar → iç içe `bstack:options.userName/accessKey` çözülmez. Repro: `top=/Users/hagul`, `nested={userName=${HOME}}`, `list=[${HOME}]`, `apiKey=${NOPE_UNSET_VAR}`. Çözülmeyen belirteç sessiz (`DotEnvLoader.java:204-207`); sekiz ayrı el yapımı çözümleyici (bkz. MAINT-001).
- **BLD-022 (P2, D, S-M):** `FrameworkBootstrap.java:45-46` önce `load()` (doğrular, fırlatır) sonra `TestFlyDefaults.applyMissing`; Javadoc örnekleri (`browser.name`, `timeouts.explicit`) kurtaramaz; `browser.headless`/`retry.enabled` uygulanmamış; `(Integer)` dönüşümleri `Long` ile `ClassCastException`; `BrowserMatrixListener.java:41` hatayı yutar.
- **DOC-003 (P1, D, S):** `getting-started.md:98-109` `execution.mode` içermiyor. Repro: `gs.yml -> IllegalStateException: Execution mode must be specified`; README örneği (`mode: local` var) `OK`.
- **DOC-024 (P2, D):** `configuration.md:33`, `guides/testfly-yml-guide.md:185-194` üst düzey `browserstack:` (gerçek: `execution.browserstack`, `TestFlyConfig.java:267`); `ci/jenkins.md:78` `browser.binaryPath`; TR `browser.type` (`browser.name must be specified` verir); `reporting/allure.md:21` `reporting.allure.resultsDir` yok; `cloud-execution.md:13` bayat uyarı. Bilinmeyen anahtarlar yalnız uyarı (`LenientPropertyUtils`).
- **BLD-023 (P3):** `.env` > kabuk env > `-D` (12-factor'ın tersi); `.env` değerleri sistem özelliği olarak yayınlanıyor (`DotEnvLoader.java:250-253`); classpath çalışma dizininden önce aranıyor (`ConfigurationLoader.java:48-56`; `src/test/resources/testfly.yml` kök dosyayı gölgeler); `applyCiOverrides` CI'da `headless=true` zorlar (`FrameworkBootstrap.java:118-128`); katı mod yok.

### 3.5 Bağımlılık ve build (Alan C)

- **BLD-005 (P1, D, S):** OSV.dev sorgusu (yalnızca genel Maven koordinatları gönderildi): `com.fasterxml.jackson.core:jackson-databind@2.21.6` → `GHSA-cxp5-3px4-pw24` (HIGH) ve `GHSA-wv8q-qhhj-9h54` (HIGH), düzeltme 2.21.7 / 2.22.3; `pom.xml:66-69`, compile scope, tüm tüketicilere geçer. Dependabot dalı `…jackson-databind-2.22.3` birleştirilmemiş. Ham yanıt `target/audit-scratch/osv.json` (rapor sırasında dosyada `jackson-databind 2.21.6` için bu iki GHSA kimliği görüldü). Uygulanabilirlik (ApiClient yanıtlarını işlerken erişilebilirlik) analiz edilmedi.
- **BLD-006 (P2, D çözümleme / G çalışma zamanı):** `guice:5.1.0` (`optional`, `pom.xml:195-199`) `guava:30.1-jre` getirir ve Selenium'un 33.6.0-jre'sini ezer; `mockito-core` `byte-buddy:1.14.12`'yi, `byte-buddy-agent` ise 1.18.12'ye zorlanmış (`pom.xml:288-293`). Kanıt: `mvn -o dependency:tree -Dverbose` → `guava:jar:33.6.0-jre … omitted for conflict with 30.1-jre`. Scratch tüketici pom'u `guava:33.6.0-jre` ve `byte-buddy:1.18.11` çözer: kendi 1361 testi tüketicinin çalıştırdığından farklı bir classpath'te. Guice hiç import edilmiyor; `optional` zaten geçişli olmaz (pom yorumu yanlış).
- **BLD-007 (P2, G):** `jackson-dataformat-yaml:2.17.1`, `jsr310:2.14.2`, `jdk8:2.15.2` (opsiyonel) ↔ çekirdek 2.21.6; BOM yok.
- **BLD-008 (P2, G):** `test.log` 47 kez "A Java agent has been loaded dynamically" (rapor sırasında `grep -c` ile yeniden doğrulandı); `-Dnet.bytebuddy.experimental=true` (`pom.xml:338`); CI yalnızca JDK 21.
- **BLD-009 / TST-001 (P2 / P1, D, S):** `pom.xml:338` düz metin `<argLine>` (`@{argLine}` yok) JaCoCo'nun `argLine` özelliğini ezer. Kanıt (`test.log`): satır 13 `argLine set to -javaagent:…jacoco.agent-0.8.12-runtime.jar=destfile=…/target/jacoco.exec`, satır 1362 `Skipping JaCoCo execution due to missing execution data file.`; `ls target/jacoco.exec` → `No such file or directory` (rapor sırasında yeniden doğrulandı). `verify` fazındaki LINE 0.40 / BRANCH 0.30 kapısı (`pom.xml:480,485`) hiç uygulanmıyor; `release.yml`'deki `mvn verify` de aynı. Gerçek kapsama **bilinmiyor**; eşikler ölçümden sonra belirlenmeli.
- **BLD-010 (P2, G):** `maven-gpg-plugin:sign` `verify` fazında koşulsuz (`pom.xml:414-433`); `CONTRIBUTING.md:102`, `testfly-ci.yml:106`, `ci/Jenkinsfile:68` `-Dgpg.skip` olmadan `verify` çalıştırır. Anahtarsız makinede beklenen `gpg: signing failed: No secret key` (çalıştırılmadı).
- **BLD-011 / SEC-008 (P2, D):** `release.yml:40-41` `${{ inputs.version }}` doğrudan `run:` betiğine enterpolasyon (script injection; `GPG_PRIVATE_KEY` zaten iş ortamında `:34`); `push: tags: ['v*']` (`:6`) her dal/commit'ten yayın yapar, `environment:` onay kapısı yok; `versions:set` sonrası pom↔tag kontrolü yok, `workflow_dispatch` etiket oluşturmaz (v1.0.2/v1.0.4/v1.0.6 ↔ pom 1.0.7 kayması); eklenti sürümleri sabitlenmemiş (`versions:set`, `versions:use-dep-version`, `help:evaluate`); `Verify` adımı tam testi çalıştırır, `deploy` (`:54`) yaşam döngüsünü tekrar koşar; `autoPublish=true`+`waitUntil=published` (`pom.xml:442-443`) Central'a geri alınamaz yayın.
- **BLD-012 / TST-012 (P2, D):** `testfly-ci.yml:101-106` `AI_API_KEY` boşsa `exit 0` (iş yeşil, hiçbir şey çalışmaz); `:154` consumer checkout `continue-on-error: true`; `api.github.com/repos/hakanngul/testfly-test` ve `testfly/testfly-test` → 404 (özel depo olabilir). `dorny/test-reporter@v1` `fail-on-error:false`, `fail-on-empty:false`.
- **BLD-013 (P2, D/İ):** Workflow'larda docs build, `-Pquality`, JDK matrisi, `timeout-minutes`, `concurrency` yok; Dependabot `github-actions` yok; action'lar değişken major etiketlerle.
- **BLD-015 (P2, G):** `quality` profili: Checkstyle 3.4.1 (Checkstyle 9.3, Java 21 sözdizimi yok), PMD 3.23.0 (PMD 6.x), `google_checks.xml` uyarı seviyesinde → kapı başarısız olamaz; CI hiç çalıştırmıyor (çalıştırılmadı).
- **BLD-018 (P2, D):** OSV: `netty-codec-http2:4.1.119.Final` 8 advisory (HIGH `GHSA-93wv-jw9v-4972`), `netty-handler` 5 (HIGH `GHSA-3qp7-7mw8-wx86`), `netty-codec-http` 18, `log4j-api:2.24.3` `GHSA-qv9r-c865-cp47`, `rhino:1.7.7.2` `GHSA-3w8q-xq97-5j7x`; hepsi opsiyonel bağımlılıklardan (Gatling/POI/swagger). Docs ise daha eski sürümleri öneriyor (Gatling 3.10.3, POI 5.2.5).
- **P3:** BLD-014 (Jenkinsfile JDK aracı yok, `master` varsayılan, aşağı akış tetikleyicisi her dalda), BLD-016 (`forkCount=4`+`reuseForks=false`+`parallel=methods`+`perCoreThreadCount`; `integration` profili dışlamaları düşürür; `testfly.profile` Surefire'a geçmiyor; testler kaynak ağacına yazıyor), BLD-017 (enforcer, wrapper, `outputTimestamp` yok; `jcodec` opsiyonel değil; `selenium-java` şemsiyesi).

### 3.6 Test kalitesi (Alan D)

- **TST-001 = BLD-009.**
- **TST-002 (P1, D, M):** `DriverManager` (450 LOC) yalnızca `mockStatic` ile; `DriverManager.createDriver()` yalnızca `integration/network/NetworkMockIntegrationTest.java:73`'te (varsayılan çalışmadan hariç). Gerçekten test eden tek şey `pushSessionOverride` (`MultiSessionTest.java:54-86`).
- **TST-003 = ARCH-003** (statik okuma; ARCH-003'te çalıştırılarak doğrulandı).
- **TST-004 (P1, D, S):** Javadoc (`RetryListener.java:~21`) "`@Retryable` genel retry kapalıyken metot bazlı opt-in sağlar" der; kod `enabled=false`'ta döner (`:48-52`). `RetryListenerTest` (5 test) `@Retryable`+`enabled=false`, `@Retryable(maxAttempts=k)` geçersiz kılma, `maxAttempts=0`, Cucumber dalı (`:70-77`) eksik; `retry_retryableAnnotation_withGlobalEnabled_retries` anotasyon olmadan da geçer. Reponun kendi `src/test/resources/testfly.yml:28` `retry.enabled: false`.
- **TST-005 (P1, D, M):** `WaitEngine` (471 LOC, 27 genel metot); `WaitEngineTest` (148 LOC, 12 test) yalnızca mutlu yolları kapsar. `waitForClickable`, `waitForInvisible`, `waitForStaleness`, `waitForText(`, `waitForTitle`, `waitForUrlContains`, `waitForAlert`, `tryHeal` için 0 isabet; `waitForVisible` yalnız bir stack-trace dizgesinde. Hiçbir test `TimeoutException` beklemiyor; `catch (TimeoutException|NoSuchElementException) → tryHeal` (`WaitEngine.java:89-98`) hiç çalışmıyor; `waitForPageLoad_*` (`:136-146`) assertion'sız.
- **P2:** TST-006 (G) `forkCount=4` altında `target/testfly-reports/*` üzerinde `HtmlReportGeneratorTest.java:33,36-59`, `JUnitXmlReporterTest.java:33-53`, `ExecutionMetricsTest.java:257-270`; JVM içi `synchronized(ReportPaths.class)` JVM'ler arası koruma sağlamaz; ayrıca yüklenen CI raporu "son koşan test"in çıktısıdır. TST-007 (D) hiçbir test gerçek `TestNG`/JUnit Launcher çalıştırmaz; `RetryAnnotationTransformer` referanssız; retry→SKIPPED→TestRail zinciri doğrulanamaz. TST-008 (D) 1399 `@Test` metodundan 69'u assertion'sız (`ExecutionMetricsTest.java:113-160`, `ApiClientFeaturesTest.java:86-134`, `ReportPortal*Test` ~20 test; `test.log`'da `[TestFly] ReportPortal 'beforeAll' failed: null` görünürken testler geçer). TST-009 (D) `ReportPortalReportAdapterTest.java:102-122` bir fazla `}` ile geçersiz JSON yazar ve hiçbir şey doğrulamaz. TST-010 (D) `ConfigurationLoaderTest` 7 test; profil testi yalnızca `assertNotNull`; validate dalları, öncelik sırası, `${VAR}` ağaç çözümü, `LenientPropertyUtils` testsiz. TST-011 (D) 246 sınıftan 38'i adla referanssız (`PerformanceCollector` 350 LOC, `BrowserSessionCache`, `FrameworkVersion`, `ApiMockServer`, `SmartLocator` …); JaCoCo ölü olduğundan doğrulanamaz. TST-012 = BLD-012.
- **P3:** TST-013 (`ApiClientFeaturesTest` `singleThreaded` değil ama global interceptor listelerine dokunuyor), TST-014 (`reuseForks=false` sızıntıyı gizliyor; `forkedProcessTimeoutInSeconds` yok; `AnnotationTransformer already set` ×134 (rapor sırasında `grep -c` ile doğrulandı)), TST-015 (wall-clock `elapsed < 1000`, `Thread.sleep(50/20)` assertion'sız), TST-016 (ters `assertEquals`, `assertNotNull(fluentReturn)`, 15 yutulan catch), TST-017 (`-Pquality`, `-Pexamples` hiç çalışmıyor).
- **Sayı uzlaştırması:** 1399 `@Test` − 39 (`integration/`, varsayılandan hariç) = 1360 ≈ 1361 çalıştırılan; yani sessizce atlanan yok.

### 3.7 Dokümantasyon ve DX (Alan E)

Yöntem: `docs-site/docs` (EN), TR `i18n/tr/.../current` ve `README.md` içinden 1 213 kod bloğu; 597'si Java. 204 `io.testfly` import çözümlendi, 17'si çözümlenemedi (8 ayrı sınıf). Her Java bloğu sarmalanıp `target/classes` + `classpath.txt` ile `javac -Xlint:removal,deprecation` altında derlendi: **282 temiz, 315 hatalı** (ham sayı, hata sayısı değildir; çoğu parça veya üçüncü taraf kodu, bkz. Bölüm 6.2). 229 YAML bloğu gerçek `ConfigurationLoader.load()`'dan geçirildi. EN/TR dosya listesi, çit sayısı ve Java-token farkı karşılaştırıldı; bağlantılar çözümlendi.

**P1 doküman bulguları**
- **DOC-005:** Tüm yük örnekleri `extends BaseTest`; `load()` yok (`LoadTestSupport.java:33,49`; `BaseLoadTest.java:79-85`). 35 javac hatası `cannot find symbol: method load(String)`; taban sınıf `BaseLoadTest`'e çevrilince gerçek DSL hataları (DOC-006/007/008) ortaya çıkar. Konum: `loadtest/annotations.md:23,65,92`, `examples.md:25,57,101`, `fluent-api.md:60`, `getting-started.md:68,103`; `changelog.md:92` iddiası 4 tabandan 3'ü için yanlış.
- **DOC-006:** `@LoadTest(users, rampUp, hold, cooldown, engine, baseUrl)` gerçek; dokümanda `rampUpSeconds/durationSeconds/targetRps/scenarioName` (`annotations.md:19-50`), `@LoadEngine` (`:55-102`, `engines.md:65`). javac: `cannot find symbol: method durationSeconds() | location: @interface LoadTest`; `class LoadEngine`. Varsayılan tablosu da yanlış (gerçek `users=-1`, boş dizgeler).
- **DOC-007:** `step(name, req -> …)`, `load(LoadScenario)`, argümansız `.get()`, `.post(payload)`, `.targetRps(250)`, `getByRole("heading","System Status")` (String aşırı yüklemesi yok), olmayan `assertVisible(...)` (`fluent-api.md`, `examples.md:116-118`, `assertions.md:78-81`).
- **DOC-010:** `ai/prompt-recipes.md:37-38,82-83` `io.testfly.core.BasePage/BaseTest`, `io.testfly.locators.Role`; `:43` `public LoginPage open()` `NavigationSupport.open()` ile çakışır; `:49,54` `.fill()`; `guides/video-recording.md:101,110-124` `io.testfly.core.BaseTest`, `$(...).val(...)` (12 removal uyarısı). Bu sayfa kullanıcıların LLM'e yapıştırdığı şablon: hata üretilen koda yayılır.
- **DOC-013:** `NavigationSupport.getWait()` Selenium `WebDriverWait` döndürür (`:47`); `waitFor*` statik `WaitEngine`'de. Yanlış yerler: `why/why-waitengine.md:56,90`, `recipes/oauth-sso.md:74,80,132`, `recipes/infinite-scroll.md:36,69,106`, `migration/from-selenium-testng.md:120-121`; **TR'de ayrıca tüm `guides/wait-engine.md` (~20 çağrı) ve `guides/base-page.md:85,97,122`** (EN `wait-engine.md` doğru).
- **DOC-014:** `ScenarioContext.put/get` statik çağrılıyor (`cucumber.md:200,206,233`; gerçek: örnek metotlar, `ctx()` ile); `apiPost("/api/users", json)` iki argüman ve `.assertThat().statusCode(201)` (`cucumber.md:229-231`, `junit5.md:178-181,212-213`; gerçek: `apiPost(path)` → `.body(json).send().assertStatus(201).json("$.id")`); `db().table("users").where(...).assertExists()` (`junit5.md:185-187`) yok.
- **DOC-001/002/003:** Bölüm 3.1 ve 3.4.

**P2/P3 doküman bulguları (kısa)**
- DOC-008 feeder API (`fromCsv`×6, `fromList`, `.circular()/.random()/.batch()`; gerçek `LoadTestFeeder.csv/json/random/uuid/sequence/constant`, `LoadScenario.feedCsv/feedJson`).
- DOC-009 `loadtest/distributed-docker-k8s.md:46-77`: `TESTFLY_LOAD_VUSERS`, `TESTFLY_API_BASEURL` etkisiz (`grep -rn "TESTFLY_" src/main/java` yalnız `TESTFLY_ENV`); "BuildThresholdEnforcer hata oranı ve P99'u doğrular" yanlış (`ci/BuildThresholdEnforcer.java:41-66` yalnız geçme oranı + flaky sayısı); "100.000+ RPS" kaynaksız; canlı yüzdelik akışı (G).
- DOC-011 `extensibility/plugins.md:71-72` `io.testfly.context.TestFlyContext` (gerçek `io.testfly.internal.TestFlyContext`, kararsız iç paket), `:35` `config.getBrowser().getBaseUrl()` yok; TR `recipes/oauth-sso.md:21` `io.testfly.api.ApiResponse` (gerçek `io.testfly.client.ApiResponse`).
- DOC-012 `ai/testfly-mcp.md:107-121` `new RoleOptions().setName("Username")` (üretici ayrı depoda, denetlenemedi).
- DOC-015 `recipes/drag-and-drop.md:31,32,51,76,77`, `recipes/tables.md:32`: `WebElement x = find(...).first()`; `Locator.first()` `Locator` döndürür (`Locator.java:278`), `.element()` gerekir (12 javac hatası).
- DOC-016 `recipes/alerts.md:17-28` `BaseTest` altında protected `acceptAlert()` (`BasePage.java:231`); `junit5.md:306-312` `BaseConditions` `find(String)` sunmaz. Diğerleri parça (G). Karar kapısı D-09.
- DOC-017 `README.md:340,636` `assertThatPage().hasUrlContains(...)` (gerçek `urlContains`, `PageAssert.java:165`); `from-selenide.md:96` `assertThat(find("#status"), 5)`; `agentic-testing.md:118-119` `softAssertThatPage()` (gerçek `softAssertPage()`); `from-restassured.md:100` `.assertMatchesSchema` (gerçek `assertSchema`, `ApiResponse.java:261`); `$$()` yok (`junit5.md:102`, `cucumber.md:168`).
- DOC-018 `$()` deprecated (`LocatorSupport.java:74-100`, "2.0.0'da kaldırılacak"): `video-recording.md:110-124`, `assertions.md:81`, `cloud-execution.md:20` … ve 11 Javadoc satırı; 18 removal uyarısı.
- DOC-019 `guides/base-page.md:15-37`, README:285-337, `intro.md:30-31`, `parallel.md:124,137`, `precondition.md:22` vb. `By` sabitleri + `super(driver)` + `getDriver()` enjeksiyonu; hiçbir blokta argümansız `BasePage()` yok (`BasePage.java:74` sağlıyor). EN Java bloklarında 157 `By.*` ↔ 134 `find/getBy*`. Framework'ün farklılaştırıcısı (yönetilen thread-yerel driver, semantik `Locator`) kopyalanan kalıp değil.
- DOC-020 `docs-site/src/data/homeData.js:68` `.filter(hasText(itemName))`, `:432,439` `Role.STATUS` (enum'da yok).
- DOC-021 TR'de eksik 4 sayfa: `ci/bitbucket-pipelines`, `loadtest/distributed-docker-k8s`, `migration/from-restassured`, `reporting/allure` (liste eksiksiz; TR-yalnız sayfa yok; hepsi sidebar'da).
- DOC-022 EN/TR ıraksaması: `guides/wait-engine.md`, `guides/base-page.md`, `guides/assertions.md` (TR'de `assertThat/assertThatPage/assertWithAi/satisfiesAi/violatesAi` blokları yok), `recipes/oauth-sso.md`, `ci/github-actions.md:59` ve `ci/jenkins.md:70,78` (TR `browser.type: chrome`), `changelog.md` (TR 3.3.0…0.5.0, 653 satır ↔ EN 0.24.0…0.1.0, 444 satır). Diğer ~80 ortak sayfa API-token düzeyinde aynı.
- DOC-023 `guides/assertions.md:27-28` `file:///src/main/java/...` (EN+TR; `#L43` bağlantısı `Locator`'ı gösteriyor, ilgili sınıfı değil); README `docs/features/README.md` (`:10,819,843`) ve RFC `docs/features/01..05-*.md` (`:813-817`) yok (commit `7199ae9`'de silindi; README bunları `v1.2.0/v1.3.0` diye ilan ediyor); `AGENTS.md:377-378` `docs/ci-execution.md`, `docs/configuration.md` yok; `AGENTS.md:277` `testfly/testfly-test` ↔ README `hakanngul/testfly-test`. Docusaurus iç bağlantılarında 0 kırık (`onBrokenLinks: 'throw'`; `onBrokenMarkdownLinks: 'warn'`).
- DOC-025 `ci/jenkins.md:21` JDK17, `loadtest/engines.md:18` Gatling 3.10.x, AGENTS Selenium 4.40.0. DOC-026 `since` ↔ sürüm (G-13). DOC-027 CI'da docs/snippet/parite/sürüm koruması yok; prototip bu denetimde ~1 saatte 30+ çerçeve düzeyi hata buldu. DOC-028 getting-started `testng.xml` bağlanmıyor, `junit-platform-launcher` eksik (G). DOC-029 ~60 `cannot find symbol` (`LoginPage`, `User`, `res` …) otomatik denetimi engelliyor.

### 3.8 Güvenlik, performans ve sürdürülebilirlik (Alan F)

**SEC-001 / API-024 (P1, D — PoC) — S.** `HtmlReportGenerator.buildHtml` Jackson çıktısını `{{TESTFLY_DATA_JSON}}` ile `<script id="testfly-data" type="application/json">…</script>` içine ham yerleştirir (`HtmlReportGenerator.java:507`; `report-template.html:1735`). Jackson `<`, `/`, `>`'yı kaçırmaz; `</script>` içeren herhangi bir dize (test/adım adı, Selenium hata mesajı, konsol hatası, sayfa başlığı, AI analizi, CI commit mesajı) bloğu sonlandırır.
**Kanıt (`secpoc/P.java`, Jackson 2.21.6):** `<script id="testfly-data" type="application/json">{"errorMessage":"Unable to locate <div></script><img src=x onerror=alert(1)>"}</script>`; saldırgan metin script elemanı içinde ham çıkıyor.
**Etki:** Rapor paylaşılan artifact (CI yükleme, Slack/Teams, arşiv): rapor origin'inde keyfi JS; meşru bir hata mesajı bile raporu boşaltabilir.
**Çözüm:** `<` → `\u003c` (ve `\u2028/9`) kaçışı, `RUN_HISTORY_JSON` dahil; `</script><img onerror>` içeren hata mesajıyla birim test. **Regresyon:** düşük (`JSON.parse(textContent)` şeffaf çözer).
**Repro:** `java -cp <jackson 2.21.6> target/audit-scratch/secpoc/P.java`.

**SEC-002 (P2, D PoC):** ~20 zincirleme `String.replace` (`:507-530`) veriyi önce yerleştirip sonra tüm dizgede yer tutucuları değiştirir; `expected {{PASSED}} items` → `expected 42 items`. Çözüm: veriyi **son** yerleştir veya tek geçişli `appendReplacement`.

**P2 güvenlik (kısa):**
- SEC-003 (D bytecode): `ImapProvider.openStore` (`:83-93`) yalnız `mail.imap.ssl.enable=true`; `jakarta.mail:2.0.2` `SocketFetcher` `ssl.checkserveridentity` varsayılanı `false` (`javap -c`: `PropUtil.getBooleanProperty(props, prefix+".ssl.checkserveridentity", false)`); `ssl:false` için STARTTLS yolu yok. Çözüm: `checkserveridentity=true`, `starttls.enable/required`, gerekirse `email.imap.trust`.
- SEC-004: `ApiClient` `COOKIE_JAR` thread-yerel, yalnız ada göre; `Domain/Path/Secure/Expires` yok; tüm sonraki isteklere (farklı host dahil) eklenir (`ApiClient.java:76,615-619,805-815`). Çözüm: JDK `CookieManager`.
- SEC-005: `ApiAuth.apiKeyQuery` anahtarı query string'e koyar; URL maskesiz loglanır (`ApiClient.java:604,641-643,832`; `ApiException.java:22,38`); `toCurl` gövdeyi ham basar (`:855-857`); `logBody=true` yanıt gövdesi maskesiz (`:840-846`); maske listesi yalnız `Authorization/Cookie/X-Api-Key` (`:821`). Varsayılanlar güvenli (`logBody=false`, `logCurl=false`). Çözüm: merkezi `Redactor`.
- SEC-006: `AiFailureAnalyzer.buildPrompt` URL, başlık, hata, 30 stack satırı ve tüm adım adlarını gönderir; `DomPruner` `value`/CSRF'i bırakır; `SourceCodeLocator` ±10 satır yerel kaynak gönderir; `http://` `baseUrl` kabul edilir; Claude anahtarı iki başlıkta (`ClaudeProvider.java:62-63`); `ai.enabled=true` varsayılan (`TestFlyConfig.java:1105`) yalnızca `failureAnalysis/generatePatch=false` olduğu için güvenli.
- SEC-007: `ApiMockServer.java:34,46` `bindAddress` yok → WireMock tüm arayüzler + `/__admin`. Çözüm: `127.0.0.1` varsayılan. Bağlama adresi çalıştırılmadı.
- SEC-008 / SEC-009 / SEC-016: bkz. BLD-011, BLD-013, BLD-005. SEC-009: `.github/dependabot.yml` yalnız `maven`+`npm`; CodeQL/OSV/SBOM yok; SpotBugs/PMD yalnız opt-in profilde.
- **PERF-001 (P2, D mekanizma):** `StepLogger.record` → `captureAsBase64()` (`StepLogger.java:92-94`) → `TestTiming` statik `TIMINGS` içinde tüm suite boyunca → `timingToMap` her adıma base64 kopyalar (`ExecutionMetrics.java:411-413`) → `INDENT_OUTPUT` ile `primary` (`:561`) ve `metrics-history/testfly-metrics-<ts>.json` (`:570`; varsayılan 30 saklama `:602-626`) → `HtmlReportGenerator` her geçmiş dosyayı tam `JsonNode` olarak okur (`:68-84`, `:414-440`) ve her ekran görüntüsü/kayıt MP4/GIF'i Base64'ler (`:100-124`) → tek `String reportDataJson` + şablon `replace` zinciri ek tam kopyalar + üç kez yazım. Büyüklük ölçülmedi (200 test × 10 ekran görüntüsü ile `-Xmx256m` repro önerilir).
- PERF-002: `target/reports/testfly-report-<ts>.html` hiç döndürülmüyor (`HtmlReportGenerator.java:272-283`); aynı saniyedeki koşular üstüne yazar. PERF-003: AI çağrısı başına yeni `HttpClient` (`ClaudeProvider.java:59`, `GeminiProvider.java:53`, `OpenAiCompatibleProvider.java:80`); `Mailhog/Mailtrap/Outlook` (ve TestRail/Xray) zaman aşımsız; `OutlookProvider.fetchAll` yalnız ilk 100 mesaj.
- MAINT-001 (P2): `${VAR}` çözümlemenin ıraksak kopyaları (`DbConnectionFactory.java:112`, `ImapProvider.java:131`, `MailtrapProvider.java:84`, `OutlookProvider.java:185`, `BrowserStackProvider.java:124`; altıncı sarmalayıcı `AiFailureAnalyzer.resolveApiKey`) kabuk env > sistem özelliği önceliğiyle; `DotEnvLoader` `.env > env > sysprop`. (BLD-021 sekiz kopya sayıyor; sayım farkı arama yöntemindendir, tek bir `Secrets.resolve()` her ikisini de çözer.)

**P3 güvenlik/perf/bakım (tek satır):** SEC-010 `.env` değerleri JVM sistem özelliği ve `.env` kabuktan baskın (`DotEnvLoader.java:250-253`); SEC-011 `CiEnvironmentDetector.java:205-214` `GIT_URL` vb. userinfo ile raporlanabilir (G); SEC-012 konteynerde otomatik `--no-sandbox` (`LocalChromeDriverProvider.java:83`, `LocalEdgeDriverProvider.java:54`); SEC-013 `DbClient.assertRowCount(table, where, n)` ham SQL (`DbClient.java:183-186`), `db:` test verisi keyfi SQL; SEC-014 `ImapProvider.clear()`/`OutlookProvider.clear()` koruma yok; SEC-015 TestRail/Xray zaman aşımı yok, HTTPS zorlaması yok, `XrayClient` belirteç ayrıştırma hatasında `response.body()` (belirtecin kendisi) basılıyor; SEC-017 `OAuth2TokenCache` anahtarı sırrı yok sayar (`ApiAuth.java:149,168`), `digest()` (`:81`) sahte; SEC-018 el yapımı JSON (`OpenAiCompatibleProvider.java:111-117`); SEC-019 `ReportPortalPropertiesWriter.writeToFile` (`:114-125`) anahtarı açık metin yazar, `src/main` çağıranı yok; SEC-020 multipart `Content-Disposition` kaçışsız (`ApiClient.java:773,785`); SEC-021 `trustAll` uyarısı kapsam başına bir kez; PERF-004 `JdkLoadEngine.java:103-104` iki `double[]`/istek, `GatlingEngine.java:158` `process.waitFor()` zaman aşımsız; PERF-005 `DomPruner` canlı DOM'a `data-tf-hide` yazar; PERF-006 `ApiResponse.java:323` `"(?s).*"+regex+".*"`; PERF-007 = API-025; PERF-008 8 sınıfta `ThreadLocal.remove()` yok (`PreconditionSessionCache`, `BasePage`, `ContextSupport`, …); MAINT-002 el yazması JSON; MAINT-003 156 `System.out/err`, 25 JUL, 3 SLF4J (bağlama yok → NOP); MAINT-004 `TestFlyConfig` 2473 LOC, `ApiClient` 897, üç shutdown hook (`SuiteContext.java:32`, `DriverManager.java:57`, `ApiExecution.java:16`); MAINT-005 `ConfigurationException/DriverException/ExecutionException` referanssız, `SessionCache` deprecated; MAINT-006 `.testfly/healed-locators.json` izleniyor (commit `7f9baf3`) ama kardeş önbellekler `.gitignore:82-83`'te; MAINT-007 `JUnitXmlReporter.escapeXml` (`:163-170`) geçersiz XML 1.0 karakterlerini silmez.

---

## 4. Doğrulanamayan Şüpheler (GÜÇLÜ ŞÜPHE)

Bu bölümdeki maddeler **doğrulanmış hata olarak sayılmamalı**. Her biri için neyin eksik olduğu ve nasıl doğrulanacağı yazılmıştır. Bu maddeleri doğrulamak, ilgili düzeltme fazının ilk adımıdır.

| ID | Şüphe | Neden doğrulanamadı | Nasıl doğrulanır |
|---|---|---|---|
| ARCH-004 (JUnit-paralel kısmı) | Sınıf A bitince sınıf B'nin tarayıcısı kapanır | JUnit paralel koşu + gerçek/mock driver çalıştırılmadı | `junit.jupiter.execution.parallel.enabled=true` ile iki sınıf, mock `NamedDriverProvider` |
| ARCH-008 | Hook zincirindeki fırlatma `quitDriver()`'ı atlar | Hook'a zorla istisna enjekte eden gerçek TestNG koşusu yok | Fırlatan `ExecutionHook` kaydedip `activeSessions()` izle |
| ARCH-010 (Cucumber çift rapor) | `SuiteExecutionListener.onFinish` + `@AfterAll` raporu iki kez üretir | `cucumber-testng`'in `@AfterAll`'ı çağırması çalıştırılmadı | Tek feature ile Cucumber+TestNG koşusu, rapor üretim sayacı |
| ARCH-013 | `@PreCondition` çerez/localStorage geri yüklemesi boş tarayıcıda işlemez | Gerçek tarayıcı yok | Headless Chrome ile `about:blank` üzerinde restore, yutulan istisnayı logla |
| ARCH-017 | `getDriver()` sağlık kontrolü açık alert'te tarayıcıyı değiştirir | Gerçek tarayıcı yok | Alert açıkken `getDriver()`; yeni oturum oluşuyor mu |
| ARCH-018 | Çoklu suite'te eklentisiz koşu; yarı init kurtarılamaz | Çoklu suite koşusu yok | İki `<suite>`'li testng.xml ve kayıt yüklemesinde zorla hata |
| ARCH-023 | Sağlayıcı kısmi hatasında tarayıcı sızıntısı; çift permit bırakma | Fırlatma enjeksiyonlu sağlayıcı testi yok | `timeouts()` fırlatan sahte sağlayıcı |
| ARCH-026 | SIGTERM'de per-test/remote oturumlar kapanmaz | Gerçek bulut oturumu yok | Uzak/mocked oturum açıp JVM'e SIGTERM |
| API-021 (a-c kısmen, d ÇÜRÜTÜLDÜ) | JUnit5 retry assumption'ı yeniden dener; `@BeforeEach` durumu kaybolur | JUnit5 koşusu çalıştırılmadı | `Assumptions.assumeTrue(false)` + retry |
| API-022 | Soft-assert flush Surefire/rapor sonuçlarını ıraksatır | Surefire altında çalıştırılmadı | Soft assert başarısız test, `mvn test` çıkış kodu ve XML |
| API-023 | Cucumber driver, kullanıcı `@After` hook'larından önce kapanır | Cucumber 7.20.1 ile çalıştırılmadı | `@After(order=10000)` içinde `getDriver()` kullan |
| BLD-006 (çalışma zamanı) | Selenium kodu `NoSuchMethodError` verebilir (Guava) | Selenium tamamen mock'lanıyor | Gerçek Selenium sınıf yolu ile smoke test |
| BLD-007 | Jackson 2.14–2.17 modülleri 2.21 çekirdekle uyumsuz | Gerçek şema yüklemesi test edilmiyor | YAML şema/OpenAPI spec yükleyen test |
| BLD-008 | JDK 24/25'te Mockito dinamik agent engellenir | Yalnız JDK 21 kurulu | CI matrisine JDK 25 ekle |
| BLD-010 | `mvn verify` anahtarsız makinede imzada düşer | `verify` çalıştırmak imza/yükleme tetikler | GPG anahtarı olmayan runner'da `mvn -o -DskipTests verify` |
| BLD-015 | `quality` profili Java 21'de çalışmıyor/başarısız olamıyor | Çevrimdışı önbellekte eklenti yok | `mvn -Pquality -DskipTests -Dgpg.skip=true verify` |
| TST-006 | JVM'ler arası rapor dizini yarışı | Gözlenen hata yok (1361/0/0) | Bölüm 3.6'daki döngülü eşzamanlı koşu |
| TST-013, TST-015 | Paralel izolasyon ve zamanlama bağımlılığı flaky yapar | Tek koşu mevcut | Tekrarlı koşu, CPU kısıtlı |
| SEC-011 | CI/grid URL'lerindeki userinfo rapora yazılır | Özel CI/URL yapılandırması gerekir | `GIT_URL=https://u:t@h/x` ile rapor üret |
| SEC-016 | Bağımlılık CVE çıkarımı | Alan F'de tarayıcı çalıştırılmadı; yalnızca bellekten ipuçları | `osv-scanner`, `dependency-check`, `npm audit` (bkz. REMEDIATION_PLAN "Prevention Strategy") |
| PERF-008 | ThreadLocal'lar sızdırır | Dış sahibin `remove()` yapıp yapmadığı denetlenmedi | Her yaşam döngüsü hook'unu denetle |
| DOC-003 (liste) | `testfly-yml-guide.md:50`, `from-selenium-testng.md:220`, `from-serenity.md:64`, `from-webdrivermanager.md:69`, `video-recording.md:143`, `custom-drivers.md:61,105`, `junit5.md:371` `mode` eksik | Parça mı tam dosya mı tek tek doğrulanmadı | Her bloğu `ConfigurationLoader` ile çalıştır |
| DOC-009 (canlı akış) | "Her pod canlı yüzdelikleri ReportPortal'a akıtır" | `reporting/reportportal/*` tüm kodu okunmadı | Kod incelemesi |
| DOC-016 | Parça halindeki protected yardımcılar | Yalnız iki tam sınıf derleme hatası verdi | Parçaları sahip sınıfla sarıp derle |
| DOC-028 | Getting-started uçtan uca çalışmıyor | Uçtan uca çalıştırılmadı | Temiz dizinde adımları izle |

**Önemli uyarı (SEC-016 ↔ BLD-005):** Alan F, bağımlılık CVE'lerini bellekten çıkardığını ve "compile bağımlılıkları için kritik advisory bilmiyorum" dediğini belirtti. Alan C ise OSV.dev toplu sorgusuyla `jackson-databind 2.21.6` için iki HIGH advisory buldu. **Bu rapor BLD-005'i (OSV çıktısı, `osv.json` içinde mevcut) bağlayıcı kabul eder; SEC-016'nın "bilinen kritik yok" ifadesi geçersizdir.** Diğer SEC-016 ipuçları (Guava CVE-2023-2976, WireMock gölgeli Jetty, swagger zinciri, logback, h2) OSV ile eşleşmedi ya da sorgulanmadı: özellikle `wiremock-standalone`, `h2`, `logback-classic` OSV'de boş döndü (`osv.json`), dolayısıyla bu üçü için bellek-tabanlı ipuçları OSV ile desteklenmedi.

---

## 5. Architecture & API Assessment

### 5.1 Mimari zayıflıklar

1. **Yaşam döngüsü mantığı üç yerde yeniden yazılmış.** TestNG (`TestExecutionListener`, 664 satır), JUnit5 (`TestFlyExtension`, 634) ve Cucumber (`CucumberHooks`, 469) aynı mantığı ayrı ayrı uyguluyor: driver kurma/kapama, karantina, retry, PreCondition, konsol hatası. Sonuç, runner'lar arasında davranış sapmaları: retry sayacı yalnız JUnit5/Cucumber'da (ARCH-006), sınıf düzeyi `@PreCondition` yalnız JUnit5'te (ARCH-014), `@Retryable` kapalıyken farklı davranış (API-021), temizlikte `try/finally` yalnız JUnit5'te (ARCH-008). Kök neden ARCH-021. Önerilen yön: runner-bağımsız bir `TestLifecycle` (begin / end / cleanup) ve ince adaptörler.
2. **Statik global durum.** `TestFlyContext`, `DriverManager.DRIVER/SESSION_SEMAPHORE/SUITE_DRIVERS`, `ExecutionMetrics.TIMINGS`, `ApiClient` interceptor listeleri, registry'ler. `TestFlyContext.initialize` yarı yapılandırılmış durumu görünür kılıyor (ARCH-018); üç ayrı JVM shutdown hook'u sıralanmamış (MAINT-004); registry okumaları senkronsuz (ARCH-025). `reuseForks=false` bu sızıntıları test paketinde gizliyor (TST-014).
3. **Katman tersine çevrilmesi.** `DriverManager` (çekirdek) `io.testfly.loadtest.*` ve `test.support.LoadTestSupport`'a bağımlı (`DriverManager.java:429-440`). Yük testi bir eklenti gibi olmalı; çekirdek bir `DriverGuard` arayüzü sunmalı. İsim-tabanlı sezgi (ARCH-001) bu yanlış katmanlamanın dışa vuran belirtisi.
4. **God class'lar.** `TestFlyConfig` 2473 LOC (tüm iç sınıflar), `ApiClient` 897 (builder + taşıma + loglama + çerez + 4 ThreadLocal), `Locator` 891, `ExecutionMetrics` 704, `BasePage` 688. Bakım ve test edilebilirlik maliyeti yüksek (MAINT-004).
5. **Yutulan hata kültürü.** ~120 `catch (… ignored)`, 156 `System.out/err`, SLF4J bağlaması yok (NOP) → kullanıcılar logları susturamaz/yönlendiremez ve başarısızlıklar kaybolur (ARCH-022, API-026, MAINT-003). ARCH-013 (sessizce işlemeyen çerez geri yüklemesi) ve API-027 (geçersiz JSON nedeniyle sessizce kaybolan AI analizi) bu "sessiz başarısızlık" kalıbının somut örnekleridir.
6. **Gizli sağlık kontrolü ve sessiz iyileştirme.** `getDriver()` her çağrıda `getTitle()` çağırıp tarayıcıyı sessizce değiştirebilir (ARCH-017); bu, testlerin yanıltıcı "element bulunamadı" ile düşmesine yol açar. Self-healing hem driver (ARCH-002) hem locator düzeyinde (`healing`) sessiz çalışıyor.
7. **Rapor boru hattı O(testler × adımlar × geçmiş).** Base64 medya, geçmiş dosyalarına 30× kopyalanıp HTML'e gömülüyor (PERF-001, PERF-002). Rapor şeması değişikliği riskli, golden-file testleri yok.
8. **Test altyapısı mimarisi.** Gerçek `DriverManager`, `Locator`, `WaitEngine` ve listener yaşam döngüsü yalnızca mock'larla test ediliyor; JaCoCo ölü; kapsama kapısı yok (TST-001/002/005/007, API-033). Mimari düzeltmelerin güvenle yapılabilmesi için önce bu test iskelesi gerekli.

### 5.2 Public API tutarsızlıkları

| Tutarsızlık | Kaynak | Kanıt |
|---|---|---|
| `@TestFlyApi(since)` değerleri 3.0.0'a kadar, artifact 1.0.7; `$()` `forRemoval=true` mevcut sürümden sonra | ARCH-019, API-032, DOC-026 | 189 anotasyonun 146'sı pom'dan yeni sürüm; DOC-026 daha geniş sayımda 329 anotasyon/Javadoc (sayım kapsamı farklı) |
| Tür düzeyinde `@TestFlyApi` iç yardımcıları donduruyor | ARCH-019 | `DriverManager.acquirePermit/releasePermit/pushSessionOverride/recreateDriver/quitAllSuiteDrivers` |
| `@Retryable` `@Target({METHOD,TYPE})` ama sınıf düzeyi okunmuyor; `@PreCondition` aynı: runner'a göre farklı | ARCH-011, ARCH-014, API-021 | `RetryListener`, `PreConditionRunner.java:39` |
| `maxAttempts` adı "toplam deneme" çağrıştırır, davranış "yeniden deneme sayısı" | API-020 | `RetryListener.java:82` |
| `Locator extends By` fakat `findElements(SearchContext)` bağlamı yok sayar | API-014 | `Locator.java:508-511` |
| `LocatorException` `RuntimeException`, `NotFoundException` değil → `WebDriverWait` ile uyumsuz | API-015 | `LocatorException.java:6` |
| `Locator.elements()` yalnız görünürleri döner, `count()` farklı; assertion `count` bunu miras alır | API-015 | `Locator.java:498-502` |
| `getWait()` Selenium `WebDriverWait` döner; `waitFor*` statik `WaitEngine`'de | DOC-013 | `NavigationSupport.java:47` |
| `load()` yalnız `BaseLoadTest`; changelog 4 tabandan bahseder | API-007, DOC-005 | `BaseLoadTest.java:79-85` |
| `LoadScenario.assertStatus(n)` ↔ `LoadTestAssert.assertStatus(n)` farklı anlam | API-003 | `LoadScenario.java:221-223` |
| `DriverProvider.createDriver()` kontrollü istisna bildirmez; docs örneği bildirir; remote/cloud modunda özel sağlayıcı sorgulanmaz | API-031 | `DriverProviderFactory.java:17-30` |
| `BasePage` yardımcıları protected: testlerde kullanılamaz, docs gösterir | DOC-016 | `BasePage.java:231` |
| `PluginRegistry.checkVersion` "fail fast" yerine atlama; `FrameworkVersion` yanlış grup yolu → IDE'de `0.0.0` | API-030 | `PluginRegistry.java:74-82`, `FrameworkVersion.java:90-105` |
| Konfigürasyon sözleşmesi: profil birleştirme, `-D` ve env geçersiz kılmaları dokümante ama yok | BLD-019, BLD-020 | Bölüm 3.4 |
| `Role` "erişilebilirlik ağacı" iddiası ↔ CSS yaklaşımı; `Role.STATUS` yok | API-018, DOC-020 | `Role.java:26-73` |
| Marketing iddiaları: "mobile", "first-class MCP server" ↔ repo'da kod yok | API-029 | `README.md:3,96-97` |

### 5.3 Refactoring ihtiyaçları (öncelik sırasıyla)

1. **Test iskelesi önce (ARCH-020, TST-001, TST-002, TST-005, TST-007).** Sahte `NamedDriverProvider` + Mockito `WebDriver` ile gerçek `DriverManager` testleri; JaCoCo `@{argLine}`; mini-suite TestNG/JUnit Launcher koşuları. Tüm sonraki refactoring bunlara dayanır.
2. **`DriverManager` sağlamlaştırma.** Permit sahipliği (`Holder{driver, permitHeld}`), `forceQuit` benzeri teardown, saf `getDriver()`, tüm driver'ları kapsayan JVM-genel küme, yapılandırılabilir slot bekleme süresi, `DriverGuard` ile yük-test sezgisinin değişimi (ARCH-001/002/003/007/017/023/026).
3. **`TestLifecycle` çıkarımı (ARCH-021).** Üç adaptör için ortak begin/end/cleanup, ortak `RetryPolicy` çözücü (TestNG/JUnit5/Cucumber), ortak `PreConditionResolver`.
4. **`ConfigurationLoader` yeniden yazımı (BLD-019/020/021/022/023, MAINT-001).** Sıralı işlem: YAML → derin birleştirme → env/`-D` katmanı → defaults → `${VAR}` çözümü (iç içe) → çözülmemiş belirteç kontrolü → doğrulama. Tek `Secrets.resolve()`.
5. **`Locator.resolve()` yoklamalı çözümleme (API-011/012/014/015).** Her yoklamada yeniden çözüm (staleness'ı da çözer); `getByText` en içteki eşleşme; `LocatorException` → `NoSuchElementException` alt sınıfı; `findElements(context)`.
6. **Yük testi motoru (API-001…004, 009, 010).** Durum kodu sayımı, 4xx/5xx başarısız, iterasyon başına değişken haritası, Jackson ile JSON/JSONPath, URL kodlama, hata örneklerini logla.
7. **Rapor boru hattı (SEC-001/002, PERF-001/002, API-025, MAINT-007).** Tek geçişli şablon, `<` kaçışı, medya dosya referansı, geçmişten medya çıkarma, `ReportPaths` ile tüm yollar.
8. **Redaksiyon katmanı (SEC-005/006/011/015, API-028).** `Redactor` + `UrlRedactor` ve Jackson tabanlı JSON yardımcısı (API-027, SEC-018, MAINT-002).
9. **Loglama (ARCH-022, API-026, MAINT-003).** SLF4J cephesi ve en az `debug` düzeyinde yutulan istisnalar.

### 5.4 Geriye dönük uyumluluk (backward compatibility) riskleri

| Düzeltme | Kullanıcıya görünen değişiklik | Risk | Önerilen yaklaşım |
|---|---|---|---|
| API-002: 4xx/5xx = başarısız (JDK motoru) | Bugün geçen yük testleri düşebilir | Orta | CHANGELOG + `loadtest.failOnHttpError` (geçişte varsayılan uyar-logla) |
| API-020 / ARCH-011: retry `maxAttempts` anlamı ve varsayılan `enabled` | Varsayılan olarak yeniden deneme azalır/kalkar | **Yüksek** (tüm tüketiciler) | D-02 kararı; major/minor notu; geçiş bayrağı |
| ARCH-001: isim-tabanlı load sezgisinin kalkması | İsimle load testi yazanlar `@LoadTest`/taban sınıf eklemeli | Düşük-orta | Önce uyarı logu, sonraki sürümde kaldır |
| ARCH-004: global `quitAllSuiteDrivers` hook'u taşınması | per-suite JUnit kullanıcılarının sürücü ömrü değişir | Orta | Eşdeğerlik testleri; bayrakla geçiş |
| ARCH-005: `@BeforeMethod`'da driver mevcudiyeti | Yaşam döngüsü sırası ve metrik sırası değişir | Orta | Konfigürasyon bayrağı |
| BLD-019/020: profil birleştirme ve geçersiz kılma katmanı | Mevcut tam profil dosyaları çalışmaya devam eder; öncelik netleşir | Orta | Listeler/map'ler için birleştirme kuralı belgele |
| BLD-021: çözülmeyen `${VAR}` fırlatır | Kasıtlı literal `${…}` kullananlar düşer | Orta | Bir minor boyunca yalnız uyarı |
| BLD-023 / SEC-010: `.env` önceliği çevirme | Gizli bilgi çözümü değişir | Orta-yüksek | Yapılandırılabilir bayrak, CHANGELOG |
| API-032 / ARCH-019: `since` yeniden yazımı, `forRemoval` indirme | Yalnız metaveri | Düşük | Betikle tek geçiş |
| ARCH-019: iç yardımcıların `internal`'a taşınması | Public kullanan tüketiciler kırılır | Orta | Önce deprecate, `internal` paket + yönlendirme |
| BLD-017: `jcodec` opsiyonel | Kayıt kullananlar bağımlılık eklemeli | Orta | Minor + CHANGELOG, net hata mesajı |
| SEC-003: IMAP sunucu kimliği | Kendinden imzalı/IP tabanlı IMAP düşer | Orta | `email.imap.trust` |
| SEC-007: WireMock `127.0.0.1` | Konteynerler arası erişim kırılır | Düşük-orta | Opt-in bind adresi |
| PERF-001: rapor şeması (`screenshotBase64` → dosya yolu) | Metrik JSON tüketicileri, `SmartTestSharder` etkilenir | Orta | Golden dosya testleri, sürümlü şema |
| ARCH-016: metrik kimliği değişimi | Flakiness geçmişi anahtarları değişir | Orta | Geçiş: eski anahtarı da oku |

**Kural:** AGENTS.md'ye göre `@TestFlyApi` öğelerinin imzaları aynı major'da değiştirilemez, kaldırma önce ≥1 minor deprecate gerektirir. 1.0.x hattında `since` değerleri tutarsız olduğundan (API-032) önce bu kural uygulanabilir hale getirilmelidir; aksi halde hangi öğelerin gerçekten "kararlı" olduğu belirsizdir.

### 5.5 Güçlü yanlar (bulgu çıkmayan, doğrulanan alanlar)

- Opsiyonel bağımlılıklar `Class.forName` yoklamaları veya `NoClassDefFoundError` korumaları arkasında (`client/SchemaValidator.java:62`, `client/OpenApiValidator.java:62`, `api/mock/ApiMockServer.java:112`, `loadtest/internal/GatlingBridge.java:23-26`, `junit5/ReportPortalJUnit5Bridge.java:78`, `cucumber/BaseCucumberTest.java:68`, `testdata/TestDataLoader.java:275,299`, `email/EmailProviderFactory.java:29`). Java 21 tabanı `pom.xml:322` ile README ile uyumlu. Central metaverisi (ad, açıklama, url, lisans, geliştirici, scm) eksiksiz.
- SnakeYAML 2.2 mevcut kullanımda güvenli: `!!javax.script.ScriptEngineManager` → `ComposerException: Global tag is not allowed`, 60 seviyeli alias bombası → `YAMLException`.
- Sabit kodlanmış sır bulunmadı (regex taraması: `sk-…`, `AKIA…`, `ghp_…`, `xox…`, `AIza…`, PEM, JWT; `.env` yok sayılıyor ve geçmişte dosya adıyla hiç eklenmemiş).
- `HttpClient` yönlendirmeleri `Authorization`/`Cookie`'yi farklı host'a taşımıyor (JDK 21.0.12 PoC).
- Süreç yürütme tek yerde argüman listesiyle (`GatlingEngine.java:153`); SQL değerleri `PreparedStatement`; kayıt/ekran görüntüsü dosya adları temizleniyor.
- `RetryAnnotationTransformer` `META-INF/services/org.testng.ITestNGListener` ile doğru kayıtlı; `TestFlyExtension.afterEach` `finally` ile temizlik yapıyor; `MultiSessionManager.withSession` `try/finally` ile.
- Varsayılanlar güvenli: `api.logBody=false`, `logCurl=false`, `ai.failureAnalysis=false`, patch üretici yama dosyası yazar ama uygulamaz.

---

## 6. Çürütülen / Güncelliğini Yitiren Önceki Bulgular

### 6.1 Önceki (Codex) bulguların yeniden doğrulama sonuçları

| # | Önceki iddia | Sonuç | Kanıt / düzeltme |
|---|---|---|---|
| 1 | `io.testfly.core.BasePage` → `io.testfly.test.BasePage` | **DOĞRULANDI** | `ai/prompt-recipes.md:37`, ayrıca `io.testfly.core.BaseTest` (`:82`, `video-recording.md:101`); `src/main/java/io/testfly/core` yok |
| 2 | `io.testfly.locators.Role` → `io.testfly.locator.Role` | **DOĞRULANDI** | `ai/prompt-recipes.md:38`; javac: `package io.testfly.locators does not exist` |
| 3 | `RoleOptions` yok | **DOĞRULANDI** | `ai/testfly-mcp.md:110,119-121`; gerçek: `getByRole(Role, String)` |
| 4 | `.fill()` / `.val()` yok | **DOĞRULANDI** | `prompt-recipes.md:49,54`; `video-recording.md:110-123` |
| 5 | `getWait().waitFor*` yanıltıcı | **DOĞRULANDI, kapsam daraltıldı** | EN `guides/wait-engine.md` doğru (statik `WaitEngine`); yanlış yerler DOC-013 listesinde, TR sayfa tamamen yanlış |
| 6 | `@LoadEngine` yok | **DOĞRULANDI** | `annotations.md:10,55,60,68,91`, `engines.md:65`; `io.testfly.loadtest` içinde tür yok |
| 7 | Eski `@LoadTest` parametreleri | **DOĞRULANDI** | gerçek: `users, rampUp, hold, cooldown, engine, baseUrl` |
| 8a | `.targetRps()` yok | **DOĞRULANDI** | `fluent-api.md:96,100`; javac `cannot find symbol targetRps()` |
| 8b | `.fromCsv()` yok | **DOĞRULANDI** | 12 derleme hatası; gerçek `LoadTestFeeder.csv(path)` |
| **8c** | **`.runAsync()` yok** | **ÇÜRÜTÜLDÜ (nüansla)** | `loadtest/examples.md:106` `CompletableFuture.runAsync(...)` (JDK) çağırıyor; TestFly metodu değil. Örnek yine de başka nedenlerle bozuk (DOC-005, API-007/008). "Eksik `.runAsync()`" bulgu listesine **alınmamıştır**. |
| 9 | Deprecated `$()` öneriliyor | **KISMEN DOĞRULANDI / çoğunlukla GÜNCEL DEĞİL** | `from-selenide.md:23-29,141` zaten `$` → `find` söylüyor; kalıntı kullanım DOC-018'de; `$$()` listelendiği yerlerde var olmayan bir API |
| 10 | `By` + manuel `WebDriver` temel mimari gibi | **KISMEN DOĞRULANDI** | API geçerli; README hızlı başlangıç `BaseTest` (framework-managed) kullanıyor; sorun yönlendirme eğikliği (DOC-019) |
| 11 | TR'de 4 sayfa eksik | **DOĞRULANDI, liste eksiksiz** | `comm` farkı tam bu 4'ü verdi, TR-yalnız sayfa yok |
| 12 | Geçersiz `file:///` bağlantıları | **DOĞRULANDI** | `guides/assertions.md:27-28` (EN+TR), 3 bağlantı |
| 13 | Sürüm/koordinat tutarsızlığı | **DOĞRULANDI, varsayılandan kötü** | BLD-001/002/003: 1.0.5–1.0.7 Central'da yok; TR Gradle'da 2.6.0 |
| 14 | Docusaurus build Java'yı doğrulamaz | **DOĞRULANDI** | 597 Java bloğunda 315 derleme hatası (ham); CI'da docs job yok |

### 6.2 Bu denetimde çürütülen veya "hata değil" çıkan iddialar

| İddia | Sonuç | Dayanak |
|---|---|---|
| Playwright / Appium entegrasyonları mevcut (görev tanımındaki "Selenium, Playwright, Appium") | **Yok** (yalnız Selenium) | `grep -rniE "playwright\|appium" src/main pom.xml` yalnız Javadoc/pom yorumu; `io.appium`/`com.microsoft.playwright` 0 isabet (API-029) |
| API-021(d): TestNG `RetryListener.attempt` örneği DataProvider satırları arasında paylaşılır | **ÇÜRÜTÜLDÜ** | ARCH-027: betik koşusu, TestNG satır başına ayrı analyzer yaratıyor |
| `followRedirects(NORMAL)` `Authorization`/`Cookie`'yi başka host'a taşır | **ÇÜRÜTÜLDÜ** | JDK 21.0.12 PoC: hedef `null`/`null` gördü |
| SnakeYAML güvensiz yapıcı (CVE-2022-1471 sınıfı) | **ÇÜRÜTÜLDÜ** | `Global tag is not allowed`, alias limiti |
| Repo'da sabit kodlanmış sır var | **ÇÜRÜTÜLDÜ** | Regex taraması + geçmiş dosya adı taraması (tam içerik taraması yapılmadı) |
| SEC-016: "compile bağımlılıkları için bilinen kritik advisory yok" | **GÜNCEL DEĞİL** | OSV: `jackson-databind 2.21.6` iki HIGH (BLD-005) |
| Docs HTML-doğrulayan harness 315 hata = 315 doküman hatası | **Yanlış okuma** | Çoğu parça/üçüncü taraf; elle sınıflandırma sonrası ~30 çerçeve hatası |
| AGENTS.md: "Yayın manuel `mvn deploy`" | **Güncel değil** | `release.yml` (tag/dispatch) mevcut (BLD-004) |
| AGENTS.md: docs `main`'de Cloudflare'e `docs-site/**` değişince dağıtılır | **Doğrulanamadı** | Repo içinde böyle bir workflow yok (`wrangler.jsonc` mevcut); tetikleyici harici olabilir |
| AGENTS.md: "Selenium 4.40.0, Jackson 2.21.0, 1.1.0" | **Güncel değil** | pom: 4.48.0 / 2.21.6 / 1.0.7 |
| Desteklenen deprecated API (`$()`) yalnız eski olduğu için hata | **Hata olarak sınıflandırılmadı** | `@Deprecated(since="1.1.0", forRemoval=true)` desteklenen API; yalnız yeni örneklerde kullanımı ve `forRemoval`/`since` tutarsızlığı bulgu (DOC-018, API-032) |

**Harness gürültüsü (DOC bulgusu sayılmadı):** `quarantine.md`/`junit5.md:421`/`cucumber.md:337` YAML `ConstructorException` (blok `testfly-quarantine.yml`, `testfly.yml` değil); `plugins.md:84,95` ve `README.md:771` "method does not override" (üye parçalar `BaseTest` ile sarılmıştı; gerçek `TestFlyPlugin` varsayılan `getName/minFrameworkVersion/onLoad/onUnload` sunar); `handle-shadow-dom.md:34,41` (`shadowPierce`, `shadowExists` `BasePage`'de var); `custom-drivers.md` Appium import hataları (tüketici bağımlılığı); `from-restassured/selenide/serenity/webdrivermanager` "önce" kodları; `accessibility.md:63-65`, `clock-mocking.md:67-70`, `api-requests.md:10-14` açıklayıcı listeler; `README.md:548` `.body(...)` yer tutucusu.

---

## 7. Build & Test Doğrulama Sonuçları (gerçek komut çıktıları)

Ortam: Temurin `openjdk version "21.0.12" 2026-07-21 LTS`, `Apache Maven 3.9.16`, macOS. Komutlar depo kökünde çalıştırıldı; ham çıktılar `target/audit-scratch/` altında.

### 7.1 Derleme ve test (`build-test.md` içeriği, aynen)

```
# Build/Test
- mvn -q compile: exit 0
- mvn test: BUILD SUCCESS, Tests run 1361, F0 E0 S0 (test.log)
- classpath.txt, deptree.txt generated (exit 0)
```

### 7.2 `mvn test` çıktısının sonu (`test.log`, aynen)

```
[INFO] Results:
[INFO]
[INFO] Tests run: 1361, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] --- jacoco:0.8.12:report (report) @ testfly ---
[INFO] Skipping JaCoCo execution due to missing execution data file.
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  50.292 s
[INFO] Finished at: 2026-10-08T14:29:47+03:00
```

Ek `test.log` satırları: satır 13 `argLine set to -javaagent:…org.jacoco.agent-0.8.12-runtime.jar=destfile=…/target/jacoco.exec`; 47 × `A Java agent has been loaded dynamically`; 134 × `AnnotationTransformer already set`; `ls target/jacoco.exec` → `No such file or directory`.

### 7.3 Bu çıktılardan çıkan sonuçlar

- Derleme ve birim testleri geçiyor. 1399 `@Test` − 39 (entegrasyon, varsayılandan hariç) ≈ 1361 çalıştırılan: sessizce atlanan test yok.
- **Kapsama ölçülemiyor.** JaCoCo hiçbir yürütme verisi yazmadı (BLD-009/TST-001); bu nedenle bu rapor hiçbir satır/dal kapsama yüzdesi iddia etmez.
- Testlerin geçmesi aşağıdaki kusurların yokluğunu **göstermez**: ARCH-002/003 (repro ile kanıtlı), API-001…004 (repro ile kanıtlı), SEC-001/002 (PoC), BLD-019/020 (repro), DOC-003 (repro).
- `mvn verify`, `mvn deploy`, `-Pquality`, `-Preal-backends`, `docs-site` `npm run build` bu denetimde **çalıştırılmadı** (imza/yükleme tetiklerler veya depo içine `docs-site/build` yazarlar). Scratchpad notları EN/TR Docusaurus build'inin daha önce geçtiğini kaydediyor; bu, Java örneklerinin doğruluğunu göstermez.

### 7.4 Denetimde üretilen repro çıktıları (özet)

| Bulgu | Komut/dosya | Gerçek çıktı |
|---|---|---|
| ARCH-001 | `area-a-tmp` scratch programı | `loadtest-heuristic: true / true / pkg com.acme.uploadtests: true` |
| ARCH-002 | `Heal.java` | `lifecycle=per-suite providerCalls=1 getDriver()==deadFirstDriver:true` |
| ARCH-003 | `Leak.java` | `Timed out waiting for an available session slot after 30s…` / `waited ms=30001` |
| ARCH-005/006 | scratch TestNG | `EVT @BeforeMethod → onTestStart → @Test → onTestSuccess → @AfterMethod`; `onTestSkipped wasRetried=true` |
| API-001…004 | `LoadRepro.java` | `500-endpoint: total=4078 failed=0 errorRate=0.0 statusCodes={}` / `extract w/o feeder: total=7255 failed=7255` |
| API-012 | `XP.java` (JDK XPath) | `html, body, div, form, button` eşleşir |
| BLD-019 | `cfg/P.java` | `-Dtestfly.profile=stage -> FAIL: java.lang.IllegalStateException: Browser configuration must be specified` |
| BLD-020 | `cfg/O.java` | `browser.name after -Dbrowser.name=firefox: chrome` |
| BLD-021 | `cfg/T.java` | `nested={userName=${HOME}}`, `list=[${HOME}]`, `apiKey=${NOPE_UNSET_VAR}` |
| DOC-003 | `repro/Repro.java` | `gs.yml -> IllegalStateException: Execution mode must be specified` |
| SEC-001 | `secpoc/P.java` | `…</script><img src=x onerror=alert(1)>` ham çıkıyor |
| SEC-002 | `secpoc/P.java` | `expected {{PASSED}} items` → `expected 42 items` |
| BLD-001 | `curl` Maven Central | `<latest>1.0.4</latest>`, `lastUpdated 20260907122817`; `1.0.7` pom → 404 |
| BLD-005 | OSV.dev (`osv.json`) | `jackson-databind 2.21.6` → `GHSA-cxp5-3px4-pw24`, `GHSA-wv8q-qhhj-9h54` |

### 7.5 Rapor yazımı sırasında yeniden yapılan salt-okunur doğrulamalar

Bu rapor yazılırken aşağıdaki iddialar kaynakta/ağda yeniden doğrulandı (hiçbir dosya değiştirilmedi):

```
DriverManager.java:439:  || testClass.getName().toLowerCase().contains("loadtest")) {
TestExecutionListener.java:411:  return pkg.contains("loadtest") || simpleName.contains("loadtest");
HtmlReportGenerator.java:507:  .replace("{{TESTFLY_DATA_JSON}}", reportDataJson)
JdkLoadEngine.java:102: ConcurrentHashMap<Integer, AtomicLong> statusCounts = …  (yalnız :224'te okunuyor)
RetryListener.java:82:   if (attempt < maxAttempts) { attempt++; return true; }
pom.xml:338: <argLine>-Dnet.bytebuddy.experimental=true --add-opens java.base/java.lang=ALL-UNNAMED</argLine>
git tag --list → v1.0.2 v1.0.4 v1.0.6
Maven Central: latest/release 1.0.4, lastUpdated 20260907122817
```

---

## 8. Denetlenemeyen Alanlar / Kısıtlar

1. **Gerçek tarayıcı/ızgara yok.** Hiçbir Selenium oturumu başlatılmadı (repoda tarayıcı testi de yok). Locator/Wait bulguları (API-011/012/013/015/023) kod okuma + JDK düzeyi repro'ya dayanır (API-012 XPath semantiği JDK XPath ile üretildi). ARCH-013/017/026 çalıştırılmadı.
2. **Yayın hattı ve depo ayarları erişimi yok.** GitHub Actions çalışma geçmişi, Central Portal dağıtım günlükleri, depo sırları, dal/etiket koruması, `testfly-test` tüketici deposu (404; özel olabilir) ve Cloudflare Workers dağıtım tetikleyicisi doğrulanamadı. BLD-001'in **nedeni** bilinmiyor.
3. **JDK > 21 yok.** BLD-008 yalnız statik kanıta dayanıyor.
4. **Kapsama ölçülemedi** (JaCoCo ölü); `mvn verify`, `-Pquality`, `-Preal-backends`, `-Pexamples`, Javadoc üretimi, `npm run build`, Gradle tüketici derlemesi çalıştırılmadı. Tam test paketi yeniden çalıştırılmadı (tek koşu); flakiness bulguları gözlem değil statik şüphedir. Mutasyon testi (PIT) yok.
5. **Bağımlılık güvenliği.** OSV.dev sorgusu tek zaman noktasında yapıldı; uygulanabilirlik/erişilebilirlik analizi yok. OWASP Dependency-Check, `osv-scanner`, Trivy/Grype çalıştırılmadı; `docs-site/package-lock.json` (Docusaurus 3.5.2 zinciri) için `npm audit` yapılmadı. WireMock gölgeli kütüphaneleri ve `axe.min.js` sürüm/menşei denetlenmedi. Yerel build özel bir Nexus aynası kullandı (`~/.m2/settings.xml`, **bilinçli olarak açılmadı**); temiz CI koşucusundaki çözümleme bağımsız olarak üretilmedi.
6. **Gizli bilgi taraması sınırlı.** Geçmiş yalnız dosya adı ve bir `-S` sorgusuyla tarandı; tam içerik taraması (gitleaks/trufflehog) yapılmadı. `.env` yalnız anahtar adlarıyla listelendi, değerler okunmadı veya yazılmadı.
7. **Dokümanlar.** Kotlin/Groovy/XML/Gherkin blokları derlenmedi; harici `http(s)` bağlantıları yoklanmadı; Javadoc HTML üretilmedi (kaynak yorumları grep ile incelendi). Maven Central durumu CDN gecikmesi ihtimaline rağmen `repo1.maven.org` üzerinden okundu.
8. **Kısmen/hiç denetlenmeyen kod alanları.** `ApiClient`/`ApiExecution`/`ApiTransport` ayrıntısı (yalnız temizlik ve güvenlik yönü), Gatling motoru iç yapısı (`GatlingEngine`, `TestFlyGatlingSimulation`, alt süreç), `db`, `email` (güvenlik hariç), `network` (CDP), `visual`, `recording` (kare yakalama), `tracing`, `healing`, `quarantine`, `flakiness` puanlama, `sharding`, `testmanagement`, `ai/remediation`, `shadow`, `clock`, `accessibility`, `performance`; Cucumber köprüsü (adım günlükçüsü, karantina etiketleri), JUnit5 paralel mod. `src/test/java/io/testfly/examples/**` (33 dosya) ve `src/test/resources/features/*.feature` yalnız sınıflandırıldı.
9. **Ayrı depolar.** MCP köprüsü (`hakanngul/testfly-mcp`, `@testfly/mcp`), `testfly-test` tüketici deposu, `testfly/website` (LATEST_VERSION) bu depodan denetlenemez.
10. **Eşzamanlılık.** Bulgular kod okuma ve tek iş parçacıklı repro'lara dayanır; stres/paralel koşu yapılmadı.
11. **Sayım farkları.** Aynı olgu için alanlar farklı yöntemle sayım yaptı (`mockStatic` 22 kullanım/12 dosya ↔ 24 dosya; `${VAR}` kopyası 5 ↔ 8; `@TestFlyApi(since)` 189 içinde 146 ↔ 329 anotasyon+Javadoc). Bunlar kapsam/arama deseni farkıdır; sonuç değişmez.
12. **Aynı oturum güvenliği.** Bu denetim yalnız şu iki dosyayı yazdı: `audit/TESTFLY_AUDIT_REPORT.md` ve `audit/TESTFLY_REMEDIATION_PLAN.md`. Önceden var olan `M .agents/memories/log.md` ve `M .agents/memories/scratchpad.md` değişiklikleri denetimden önce de mevcuttu (`discovery.md` başlangıç durumu).

---

*Rapor sonu. Düzeltme planı için bkz. [`TESTFLY_REMEDIATION_PLAN.md`](./TESTFLY_REMEDIATION_PLAN.md).*

