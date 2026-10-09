# TestFly — İyileştirme Yol Haritası (Remediation Plan)

| Alan | Değer |
|---|---|
| Plan tarihi | 2026-10-08 |
| Kaynak rapor | [`TESTFLY_AUDIT_REPORT.md`](./TESTFLY_AUDIT_REPORT.md) (164 bulgu: 1 P0, 36 P1, 83 P2, 44 P3) |
| Kapsam | Denetim bulgularının önceliklendirilmiş, bağımlılık sıralı düzeltme planı. **Bu plan kod değiştirmez; uygulama kullanıcı onayından sonra yapılır.** |
| ID kuralı | Bulgu ID'leri (`ARCH-`, `API-`, `BLD-`, `TST-`, `DOC-`, `SEC-`, `PERF-`, `MAINT-`) rapordakiyle birebir aynıdır. `G-xx` rapor Bölüm 2.3'teki konsolidasyon gruplarıdır. `T<faz>.<n>` bu plandaki görev kimlikleridir. `D-xx` karar kapılarıdır. |
| Efor birimi | S ≈ 1 kişi-gün, M ≈ 3 kişi-gün, L ≈ 7+ kişi-gün (yaklaşık; kod incelemesi ve test yazımı dahil, yayın bekleme süreleri hariç) |

## 0. Çalışma kuralları (AGENTS.md ile uyum)

1. **Dal kuralı.** `main` dalına doğrudan commit/push yasaktır. Tüm geliştirme `development` dalında yürütülür; her git işleminden önce `git branch --show-current` kontrol edilir. Denetim sırasında çalışma dalı `chore/docs-cloudflare-workers` idi (PR #44 Anthropic başvuru sonucuna kadar birleştirilmeyecek); düzeltmeler için dal stratejisi başlamadan kullanıcıyla netleştirilir.
2. **Commit/push yalnızca kullanıcı onayıyla**; `git-release-workflow` kuralı izlenir. Her görev küçük, bağımsız commit'lerle, gerekirse ayrı PR ile ilerler.
3. **Docs-site değişiklikleri** her zaman `/docusaurus-config` skill'i ile yapılır, **EN ve TR birlikte** güncellenir, `npm run build` ile doğrulanır (`docusaurus-workflow` kuralı). Sürüm değişince AGENTS.md "version-bump checklist"i (bu planın T1.4 görevinde genişletilmiş haliyle) izlenir.
4. **Her kusur düzeltmesi, düzeltmeden önce başarısız olan bir regresyon testiyle gelir.** Bulguların çoğu 1361 yeşil testin kaçırdığı kusurlardır; test olmadan düzeltme kabul edilmez.
5. **Mevcut testler yanlış davranışı kodluyorsa** (`JdkLoadEngineTest.testErrorEndpoint`, `RetryListenerTest.retry_globalEnabled_singleAttempt_retriesOnce`) test, davranış kararıyla birlikte güncellenir ve PR açıklamasında belirtilir.
6. **Davranış değiştiren düzeltmeler** CHANGELOG girişi ve geçiş notu gerektirir (Bölüm 0.1 karar kapıları).
7. **Aşama kapıları:** her fazın sonunda `mvn clean verify -Dgpg.skip=true` (JaCoCo düzeltildikten sonra kapsama dahil) ve `cd docs-site && npm run build` yeşil olmalı. Oturum sonunda `.agents/memories/scratchpad.md` güncellenir ve `log.md`'ye giriş eklenir.
8. **Sırlar:** hiçbir görev sır değeri okumaz/yazmaz; `~/.m2/settings.xml` ve `.env` değerleri açılmaz.

### 0.1 Karar kapıları (ürün/sahip kararı gerektirir — kod yazmadan önce)

| Kapı | Karar | İlgili bulgular | Öneri (karar sahibine) | Karar gelmeden yapılabilecek |
|---|---|---|---|---|
| **D-01** | Yayın edilecek sürüm numarası ve koordinat; 1.0.5–1.0.7 neden Central'da yok | BLD-001, BLD-003, DOC-002 | `io.github.hakanngul:testfly` tek koordinat; önce kök neden, sonra 1.0.7'yi (veya sonraki) yayınla | Kök neden araştırması, docs'ta `io.testfly` temizliği |
| **D-02** | Retry sözleşmesi: `maxAttempts` toplam mı yeniden deneme sayısı mı; `retry.enabled` varsayılanı; `@Retryable` opt-in mi geçersiz kılma mı; sınıf düzeyi | API-020, ARCH-011, API-021, TST-004 | `maxAttempts` = toplam deneme; varsayılan `enabled=false`; `@Retryable` opt-in (metot+sınıf), geçişte uyarı logu | Ölü dalın temizliği, eksik testlerin yazımı |
| **D-03** | Konfigürasyon katmanlama: profil derin birleştirme ve `-D`/`TESTFLY_*` katmanını uygula mı, dokümanı mı düzelt | BLD-019, BLD-020, BLD-021, BLD-023 | Uygula (öncelik `-D` > env > profil > taban > varsayılan); `.env` önceliği için ayrı bayrak | Docs'a geçici uyarı |
| **D-04** | Yük testi: 4xx/5xx başarısız sayılsın mı; `load()` `BaseTest`/`BaseApiTest`'e de eklensin mi; `targetRps` uygulanacak mı | API-002, API-007, DOC-005, DOC-007 | 4xx/5xx = başarısız (yapılandırılabilir); `BaseApiTest` için `LoadTestSupport` varsayılan metotları; `targetRps` şimdilik dokümandan çıkar | Durum kodu kaydı (API-001), `extract` düzeltmesi |
| **D-05** | `@TestFlyApi(since)` gerçek sürüm geçmişine nasıl eşlenecek; `forRemoval` ne zaman | ARCH-019, API-032, DOC-026 | Sürüm eşleme tablosunu sahip verir; `forRemoval` takvimlenene dek kaldırılır | Betik ve doğrulama testi hazırlığı |
| **D-06** | TestNG'de `@BeforeMethod`/`@AfterMethod` içinde driver erişimi | ARCH-005 | Seçenek (a) tembel oluşturma + konfigürasyon bayrağı; en az (b) eyleme dönük hata mesajı + docs | (b) hemen yapılabilir |
| **D-07** | Güvenlik varsayılanları: IMAP kimlik doğrulaması, WireMock bind adresi, `--no-sandbox`, `.env` önceliği, `trustAll` CI politikası | SEC-003, SEC-007, SEC-012, SEC-010, SEC-021 | Güvenli varsayılan + açık opt-out anahtarları | Anahtarların eklenmesi |
| **D-08** | Pazarlama ifadeleri: "mobile", "first-class MCP server", Playwright/Appium | API-029 | "mobile via Appium custom provider (BYO)", "MCP bridge (ayrı paket)" | — |
| **D-09** | `BasePage` yardımcılarının (alert/frame/shadow) test sınıflarından erişimi | DOC-016 | `ActionSupport`/`BrowserSupport` üzerinden de sun (varsayılan metot) veya docs'ta sahip sınıfı göster | Docs'ta sahip sınıfı açıkça göster |

### 0.2 Faz özeti ve bağımlılık grafiği

| Faz | Odak | Bulgu sayısı | Tahmini efor | Giriş koşulu |
|---|---|---|---|---|
| **Faz 1** | P0/P1 kritik: yayın/kurulum, güvenlik, driver yaşam döngüsü, retry, yük motoru, Locator, konfig, kritik docs | 50 | ≈ 45–55 kişi-gün | D-01 başlangıçta; D-02/03/04/06 görev bazında |
| **Faz 2** | Public API ve mimari tutarlılık, güvenlik sertleştirme, rapor boru hattı | 51 | ≈ 55–70 kişi-gün | Faz 1 test iskelesi (T1.8, T1.20) tamam |
| **Faz 3** | Test güvenilirliği ve kalitesi | 14 | ≈ 25–35 kişi-gün | T1.20 (JaCoCo) ve T1.8 (DriverManager testleri) |
| **Faz 4** | Dokümantasyon ve DX | 20 | ≈ 25–35 kişi-gün | Faz 1 docs ve D-08/D-09 |
| **Faz 5** | CI/CD, önleyici kontroller, teknik borç | 29 | ≈ 35–50 kişi-gün | Faz 1–4 (kapılar yeşil olabilsin diye) |

Bağımlılık okları (özet): `D-01 → T1.3 → T1.4`; `T1.8 → {T1.9, T1.10, T1.13} → ARCH-021 (Faz 5)`; `T1.20 → T3.x → T5 kapsama kapısı`; `T1.19 (konfig) → T2.7 → T3.4`; `Faz 1 docs → Faz 4 → T5.2/T5.3 (docs kapıları bloklayıcı)`; `T1.2 (release güvenliği) → T1.3 (yayın)`.

Paralelleştirme: Faz 1 içinde dört bağımsız hat vardır: (A) yayın/docs-kurulum (T1.1–T1.5), (B) güvenlik/bağımlılık (T1.6–T1.7), (C) çekirdek kod: driver + retry + yük + Locator + konfig (T1.8–T1.20), (D) kritik docs (T1.21–T1.22). Hat C'nin içinde T1.8 (test harness) ilk yapılır.

---

## Faz 1 — P0/P1 Kritik Sorunlar

**Amaç:** Kurulumu çalışır kılmak, tek P1 güvenlik açığını ve HIGH advisory'yi kapatmak, kaynak sızıntısı/yanlış-olumlu üreten çekirdek kusurları düzeltmek, ana kullanıcı senaryolarının sözleşmesini (bekleme, retry, konfig, yük testi) güvenilir hale getirmek.
**Kapsanan bulgular (50):** BLD-001, 002, 003, 004, 005, 007, 009, 010, 011, 019, 020; ARCH-001…007, 011, 020; API-001…005, 008, 011, 012, 019, 020, 024; TST-001…005; DOC-001, 002, 003, 005, 006, 007, 008, 010, 013, 014; SEC-001, 002, 008, 016.

### Hat A — Yayın, koordinat ve kurulum

**T1.1 — Yayın durumunun kök neden analizi (D-01)** · BLD-001 · Efor S
- İş: GitHub Actions `release.yml` çalışma geçmişi, Central Portal dağıtım listesi ve etiket durumunu incele; 1.0.5–1.0.7'nin neden yayınlanmadığını belgele; yayın edilecek sürümü ve tag stratejisini (v1.0.5/v1.0.7 etiketleri eksik) sahipten al.
- Bağımlılık: yok (erişim: depo sahibi).
- Kabul: Yazılı kök neden + karar kaydı (yayın sürümü, tag planı). Çıktı `.agents/memories/log.md`'ye işlenir.
- Doğrulama: `curl -s https://repo1.maven.org/maven2/io/github/hakanngul/testfly/maven-metadata.xml` çıktısı ile kayıt karşılaştırması.

**T1.2 — Release hattının minimum sertleştirilmesi** · BLD-010, BLD-011, SEC-008 · Efor M
- İş: (1) `${{ inputs.version }}` → `env:` + regex doğrulaması `^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]+)?$`; (2) GitHub `environment: release` (zorunlu onaylayıcı) ve "etiket `main`'den erişilebilir olmalı" koşulu; (3) deploy öncesi `pom` sürümü == etiket kontrolü; (4) `versions-maven-plugin` ve `maven-gpg-plugin` sürümlerini sabitle; (5) çift test koşusunu kaldır (`verify` sonrası `deploy -DskipTests`); (6) GPG imzasını `release` profiline taşı, varsayılan `verify` yan etkisiz olsun (`-Prelease` yalnız `release.yml`'de); (7) etiket/GitHub Release'i workflow oluştursun.
- Bağımlılık: T1.1. Not: yayın geri alınamaz; değişiklikler fork/test namespace'inde kuru çalıştırma ile denenir.
- Kabul: Kötü niyetli `version` girdisi ile dispatch reddedilir; `main` dışı etiket yayını engellenir; imza anahtarı olmayan makinede `mvn clean verify` başarılı; `release.yml` kuru çalıştırma yeşil.
- Doğrulama: fork üzerinde `workflow_dispatch` ile negatif/pozitif senaryolar; `mvn -o -DskipTests verify` anahtarsız ortamda.
- Regresyon riski: orta (yayın hattı).

**T1.3 — 1.0.x yayınının yapılması / dokümanın yayınlanmış sürüme hizalanması** · BLD-001 · Efor S
- Bağımlılık: T1.1, T1.2, D-01.
- Kabul: Hedef sürüm Central'da çözümlenir (`curl …/<sürüm>/testfly-<sürüm>.pom` → 200) **veya** (yayın ertelenecekse) tüm dokümanlar son yayınlanmış sürümü (1.0.4) gösterir ve landing rozeti düzeltilmiştir (`docs-site/src/pages/index.js:459`).
- Doğrulama: Temiz bir Maven projesinde `mvn dependency:resolve` ile sürüm çözümlemesi.

**T1.4 — Koordinat ve sürüm tek-geçiş düzeltmesi (EN+TR)** · BLD-002, BLD-003, BLD-004, DOC-001, DOC-002 · Efor M
- İş: Rapor Bölüm 3.1'deki tabloya göre tüm `io.testfly:testfly` ve `<groupId>io.testfly</groupId>` örneklerini `io.github.hakanngul` yap; `intro.md:13` rozet URL'sini düzelt; tüm sürümleri T1.3 sonucuna eşitle (TR `gradle.md` 2.6.0, `.github/profile/README.md:89` 1.0.2, `junit5.md:51`, `cucumber.md:22`, `from-selenium-testng.md:27`, `README.md:797`, `AGENTS.md:44-45`, `ai/testfly-mcp.md:91`); AGENTS/docs bağımlılık sürümlerini (Selenium, Jackson, POI, Gatling, jakarta.mail, workflow adı, yayın süreci, `master`→`main`, `CONTRIBUTING` testfly.yml iddiası) pom'a göre güncelle ya da "bkz. pom.xml" ifadesine çevir; AGENTS "version-bump checklist"ine eksik dosyaları (`cucumber.md`, `gradle.md`, `loadtest/getting-started.md`, `migration/from-selenium-testng.md`, `cli.md`, `ai/testfly-mcp.md`, `.github/profile/README.md`, `docs-site/src/data/homeData.js`, tüm TR aynaları) ekle; CHANGELOG'a 1.0.1–1.0.3 kayıtlarını tamamla.
- Bağımlılık: T1.3.
- Kabul: `grep -rnE "io\.testfly:testfly|<groupId>io\.testfly</groupId>"` yalnızca "eski/tarihsel" işaretli changelog satırlarında sonuç verir; tüm TestFly sürüm dizgeleri `pom.xml` sürümüne eşit (betikle doğrulanır, bkz. Önleme Stratejisi P3); `npm run build` EN+TR yeşil.
- Doğrulama: `scripts/check-version.sh` (T5.3'te kalıcılaşır) çıktısı + `mvn help:evaluate -Dexpression=project.version`.

**T1.5 — Getting-started `testfly.yml` düzeltmesi** · DOC-003 · Efor S
- İş: `getting-started.md:98-109` (EN+TR) `execution.mode: local` ekle; zorunlu anahtarları (`execution.mode`, `browser.name`, `timeouts.explicit/pageLoad`) açıkça yaz; "mode varsayılan local olsun" kod değişikliği ayrı ürün kararıdır (bu görevde yapılmaz). `DOC-003` listesindeki diğer şüpheli bloklar (parça mı tam dosya mı) tek tek `ConfigurationLoader` ile doğrulanıp düzeltilir.
- Kabul: Dokümandaki `testfly.yml` ile `ConfigurationLoader.load()` hatasız yüklenir.
- Doğrulama: `target/audit-scratch/repro/Repro.java` benzeri çalıştırma (T5.2 kapısında otomatikleşir).

### Hat B — Güvenlik ve bağımlılık

**T1.6 — Bağımlılık advisory'leri: Jackson yükseltme + BOM + gerçek tarama** · BLD-005, BLD-007, SEC-016 · Efor S–M
- İş: (1) `osv-scanner` (pom + `docs-site/package-lock.json`) ve OWASP `dependency-check` ile gerçek tarama çalıştır; SEC-016'daki bellek-tabanlı ipuçlarını doğrula veya çürüt; (2) `jackson-databind` 2.21.7'ye (yama) yükselt; 2.22.3 Dependabot PR'ı ayrıca test edilir; (3) `dependencyManagement` içinde `jackson-bom` import et; (4) opsiyonel `dataformat-yaml/jsr310/jdk8` aynı sürüme hizalansın; (5) gerçek bir YAML şema/OpenAPI dosyası yükleyen birim test ekle (BLD-007).
- Bağımlılık: yok. (BLD-006 BOM ile ilişkili; Faz 3'te tamamlanır.)
- Kabul: OSV'de `jackson-databind` için HIGH advisory kalmaz; `mvn dependency:tree` Jackson modüllerini tek minor'da gösterir; tarama raporu `audit/` dışında (CI artifact) saklanır; yeni birim test geçer.
- Doğrulama: `curl` ile OSV sorgusu (yalnız koordinat) veya `osv-scanner`; `mvn test`; `mvn dependency:tree -Dincludes=com.fasterxml.jackson*`.
- Regresyon riski: düşük (yama) / orta (2.22 minor).

**T1.7 — HTML rapor veri bloğu kaçışı ve tek geçişli şablon** · SEC-001, SEC-002, API-024 · Efor S
- İş: `HtmlReportGenerator.buildHtml` içinde gömülü JSON'da `<` → `\u003c` (+ `\u2028`, `\u2029`), `RUN_HISTORY_JSON` dahil; `{{TESTFLY_DATA_JSON}}` en son (veya tek geçişli `appendReplacement`) yerleştirilir.
- Bağımlılık: yok.
- Kabul: Hata mesajı `x</script><img src=x onerror=alert(1)>` içeren rapor üretildiğinde `testfly-data` bloğu tek `</script>` ile biter; `expected {{PASSED}} items` aynen korunur; mevcut rapor testleri yeşil.
- Doğrulama: Yeni birim test (iki senaryo); `secpoc/P.java` çıktısı eşdeğeri; manuel olarak üretilen raporun tarayıcıda açılması (JS çalışmaz).
- Regresyon riski: düşük.

### Hat C — Çekirdek kod

**T1.8 — `DriverManager` test iskelesi** · ARCH-020, TST-002 · Efor M
- İş: Sahte `NamedDriverProvider` (Mockito `WebDriver`) ile `DriverManagerTest`; testler `maxActiveSessions=1/2`, `ExecutorService` ile eşzamanlılık, yük-test sezgisi, per-test/per-suite yaşam döngüsü. 30 sn'lik `tryAcquire` için test dikişi (yapılandırılabilir süre; T1.12 ile birleşir). Statik `SESSION_SEMAPHORE` için test reset kancası.
- Bağımlılık: yok (ilk yapılan görev).
- Kabul: `createDriver/quitDriver/recreateDriver/forceQuitDriver/quitAllSuiteDrivers/acquirePermit/releasePermit` gerçek metotlarla test edilir; sınıf `singleThreaded` işaretli; JaCoCo (T1.20 sonrası) `driver` paketinde ölçülebilir kapsama gösterir.
- Doğrulama: `mvn test -Dtest=DriverManagerTest`.

**T1.9 — Permit sızıntısı** · ARCH-003, TST-003 · Efor S
- İş: `quitDriver`/`forceQuitDriver`/`quitAllSuiteDrivers` içinde `release()` `finally` bloğunda ve driver başına bir kez; `DRIVER.remove()` her koşulda.
- Bağımlılık: T1.8.
- Kabul: `quit()` fırlatan mock ile `activeSessions()==0` ve `maxActiveSessions=1` iken ikinci `createDriver()` anında başarılı (30 sn beklemez). Önce başarısız olan regresyon testi.
- Doğrulama: `DriverManagerTest#quitFailureReleasesPermit`.

**T1.10 — per-suite self-healing** · ARCH-002 · Efor S
- İş: `recreateDriver()` `forceQuitDriver()` benzeri teardown ile; `SUITE_DRIVERS`'tan çıkarma; `createDriver()` yeni örnek üretsin.
- Bağımlılık: T1.8, T1.9.
- Kabul: `lifecycle=per-suite` repro'sunda `getDriver()==deadFirstDriver:false`, `providerCalls=2`.
- Doğrulama: `DriverManagerTest#recreateDriverPerSuite`.

**T1.11 — Yük-testi sezgisinin merkezileştirilmesi** · ARCH-001, API-008 · Efor S
- İş: İsim-tabanlı `contains("loadtest")` dört yerden (`DriverManager.java:439`, `TestExecutionListener.java:410-411`, `TestFlyExtension.java:518`, `CucumberHooks.java:432`) kaldırılır; tek `LoadTestDetector` yalnız `BaseLoadTest`, `LoadTestSupport`, `@LoadTest`, `@NoBrowser`, `LoadTestRunner.isExecuting()` sinyallerini kullanır.
- Bağımlılık: T1.8; D-04 (yük testi için isimle yazanlara geçiş uyarısı).
- Kabul: `FileUploadTest`, `DownloadTest` ve `com.acme.uploadtests` paketi driver alır; `BaseLoadTest` altsınıfları almaz; geçiş sürümünde "isim sezgisi kaldırıldı" uyarı logu ve CHANGELOG notu.
- Doğrulama: Her dört çağrı noktası için negatif birim testler.

**T1.12 — Slot bekleme süresi ve konfigürasyon çapraz doğrulaması** · ARCH-007 · Efor S–M
- İş: `execution.sessionWaitSeconds` (varsayılan geniş veya sınırsız), `threadCount <= maxActiveSessions` doğrulaması (veya otomatik yükseltme + uyarı), çoklu oturum için boyutlandırma kuralı belgelenir.
- Bağımlılık: T1.8, T1.19 (yeni anahtar doğrulama akışına girer).
- Kabul: `threadCount > maxActiveSessions` başlangıçta net hata/uyarı verir; 30 sn sabiti yok.
- Doğrulama: `ExecutionValidatorTest` + `DriverManagerTest#slotWaitConfigurable`.

**T1.13 — Global `quitAllSuiteDrivers()` kapsamının daraltılması** · ARCH-004 · Efor M
- İş: Global teardown'u launcher düzeyi hook'a (`TestFlyLauncherListener.testPlanExecutionFinished`) / root `ExtensionContext` `CloseableResource`'a taşı; `afterAll` ve Cucumber `@AfterAll` yalnız çağıran thread'in driver'ını yönetsin; diğer thread `DRIVER` ThreadLocal'ları yanıltmasın.
- Bağımlılık: T1.8, T1.10. Önkoşul: ARCH-004'ün uçtan uca doğrulaması (Rapor Bölüm 4) için JUnit paralel testi yazılır.
- Kabul: İki sınıf paralel koşarken biri bitince diğerinin driver'ı kapanmaz; per-suite modda sınıflar arası driver yeniden kullanılır.
- Doğrulama: JUnit Launcher ile iki sınıflı mini-suite (T3.1 çerçevesi).
- Regresyon riski: orta (per-suite kullanıcıları); bayrakla geçiş.

**T1.14 — TestNG config metotlarında driver erişimi (D-06)** · ARCH-005 · Efor S (b) / M (a)
- İş: (b) `getDriver()` hatasını eyleme dönük hale getir ("driver yalnızca `@Test` içinde mevcuttur; `@PreCondition` kullanın") ve `precondition.md:19-24` ile ilgili dokümanı düzelt; (a) seçilirse `IInvokedMethodListener.beforeInvocation/afterInvocation` ile tembel oluşturma + konfigürasyon bayrağı.
- Bağımlılık: D-06; T1.8.
- Kabul: (b) için net mesaj ve doküman; (a) için `@BeforeMethod` içinde `open()` çalışır ve metrik sırası bozulmaz.
- Doğrulama: Mini-suite testi (EVT sırası TestNG repro'sundaki gibi doğrulanır).

**T1.15 — Retry sözleşmesi ve metrikler (D-02)** · API-020, ARCH-011, ARCH-006, TST-004 · Efor M
- İş: D-02 sonucuna göre `RetryListener`'ı düzelt: `maxAttempts` anlamı, `enabled` varsayılanı, `@Retryable` opt-in/geçersiz kılma ve sınıf düzeyi lookup, ölü dalın silinmesi, JUnit5/Cucumber ile ortak `RetryPolicy`; `TestExecutionListener.onTestSkipped` içinde `result.wasRetried()` ise `ExecutionMetrics.recordRetry(testId)` çağır, hook/TestRail/Xray/SKIPPED durumu gönderme; docs (`configuration.md:182,554`, `intro.md:95`) ve test uyumu; CHANGELOG ve geçiş uyarısı.
- Bağımlılık: D-02. Eksik testler (TST-004 listesi) kararsız beklemeden yazılır.
- Kabul: Dokümandaki "1 = yeniden deneme yok" gerçekle uyumlu; `ci.maxFlakyTests` kapısı TestNG koşusunda yeniden denenen-sonra-geçen testi flaky sayar; retried deneme TestRail/Xray'e SKIPPED göndermez; `RetryListenerTest` yeni sözleşmeyi (enabled=false+`@Retryable`, `maxAttempts=0`, Cucumber dalı) kapsar.
- Doğrulama: Mini-suite TestNG koşusu (retry analyzer) ile `retryCount` ve durum doğrulaması.
- Regresyon riski: **yüksek** (tüm tüketiciler); sürüm notu zorunlu.

**T1.16 — JDK yük motoru düzeltmeleri** · API-001, API-002, API-003, API-004 · Efor M
- İş: `executeStep` durum kodu döndürsün, `statusCounts` artsın (taşıma hatası için sentetik kod); `status>=400` başarısız (D-04; açık `status()` kontrolü yoksa); `LoadScenario.assertStatus(n)` doğru semantik; iterasyon başına `new HashMap<>(feederRow)`; `JdkLoadEngineTest.testErrorEndpoint` güncellenir.
- Bağımlılık: D-04.
- Kabul: `LoadRepro` senaryolarında: 500-endpoint `errorRate==1.0`, `statusCodes` dolu; `assertStatus(404)` 500-endpoint'te başarısız; `extract()` besleyicisiz hatasız; sağlıklı 200 endpoint'te `assertStatus(200)` geçer.
- Doğrulama: `JdkLoadEngineTest` yeni testler (yerel `HttpServer`).

**T1.17 — `Locator` otomatik bekleme sözleşmesi** · API-011, TST-005 (çekirdek bölüm) · Efor M
- İş: `resolve()` içinde `WebDriverWait(timeouts.explicit)` yoklaması; her iterasyonda `resolveAll()` yeniden (staleness da çözülür); zaman aşımında self-heal; `count()/isVisible()` bekletmesiz kalır; `WaitEngineTest`'e zaman aşımı → `TimeoutException`, heal yolu, `waitForClickable/Invisible/Staleness`, `waitMillis` kesinti testleri.
- Bağımlılık: yok (T1.20 sonrası kapsama ölçülür).
- Kabul: Gerçek/headless tarayıcıda 2 sn gecikmeli eleman `Locator.click()` ile hatasız tıklanır; eleman hiç gelmezse `explicit` süre sonunda anlaşılır `LocatorException`/`TimeoutException`; `WaitEngine` birim testleri bu semantikleri kapsar.
- Doğrulama: Küçük headless-Chrome `file://` sayfası testi (T3.2 profiliyle) + mock tabanlı birim testler.
- Regresyon riski: orta (negatif kontrollerde gecikme).

**T1.18 — `getByText()` en içteki eşleşme** · API-012 · Efor S
- İş: XPath'i `text()` düğümüne veya `[not(.//*[contains(…)])]` koşuluna göre yeniden yaz; `exact()` aynı mantıkla.
- Bağımlılık: yok.
- Kabul: `<html><body><div id='app'><form><button>Sign In</button></form></div></body></html>` üzerinde yalnız `button` eşleşir (XP.java eşdeğeri); `getByText("Sign In").click()` düğmeyi tıklar.
- Doğrulama: JDK XPath birim testi (eşleşen düğüm listesi) + tarayıcı testi.

**T1.19 — Konfigürasyon yükleyici: profil birleştirme ve geçersiz kılma katmanı (D-03)** · BLD-019, BLD-020 · Efor M
- İş: Sıra: taban YAML → `testfly-<profile>.yml` derin birleştirme (Map üzerinde, liste/map birleştirme kuralı belgelenir) → `-Dtestfly.<yol>` ve `TESTFLY_<YOL>` katmanı (tür dönüşümü, bilinmeyen anahtar uyarısı) → defaults → doğrulama. `-Dtestfly.config` tam geçersiz kılma olarak kalır. Repo'daki kısmi `testfly-stage.yml` ile test.
- Bağımlılık: D-03. T2.7 (BLD-021/022/023) aynı refactor üzerine devam eder.
- Kabul: `-Dtestfly.profile=stage` hatasız yüklenir ve `execution.baseUrl/parallel/threadCount` profilden, geri kalanı tabandan gelir; `-Dtestfly.browser.name=firefox` değeri etkiler; `ci/github-actions.md` tarayıcı matrisi gerçekten farklı tarayıcı çalıştırır.
- Doğrulama: `ConfigurationLoaderTest` yeni testler (temp YAML + sistem özelliği, `singleThreaded`, try/finally).
- Regresyon riski: orta; mevcut tam profil dosyaları çalışmaya devam etmeli.

**T1.20 — JaCoCo'nun etkinleştirilmesi** · BLD-009, TST-001 · Efor S
- İş: Surefire `<argLine>@{argLine} -Dnet.bytebuddy.experimental=true --add-opens …</argLine>` ve boş varsayılan `argLine` özelliği; `mvn verify -Dgpg.skip=true` ile `target/jacoco.exec` ve `target/site/jacoco/` oluşumu; ilk gerçek kapsama sayıları kaydedilir ve %40/%30 eşikleri **ölçüme göre** yeniden belirlenir (hemen ratchet yok).
- Bağımlılık: yok (JaCoCo 0.8.12 ↔ ByteBuddy `experimental` bayrağı; sorun çıkarsa BLD-008 çalışmasıyla birlikte ele alınır).
- Kabul: `ls target/jacoco.exec` var; `mvn verify` raporu üretir; eşik uygulaması gerçekten çalışır (bilinçli düşük eşikle kanıtlanır).
- Doğrulama: `mvn -B verify -Dgpg.skip=true` çıktısında "Skipping JaCoCo execution" yok.

### Hat D — Kritik dokümanlar

**T1.21 — Yük testi dokümanlarının yeniden yazımı (EN+TR)** · API-005, DOC-005, DOC-006, DOC-007, DOC-008 · Efor M
- İş: `loadtest/*` sayfaları rapor Bölüm 3.3 "gerçek `@LoadTest` sözleşmesi" ve `BaseLoadTest` Javadoc'una göre; taban sınıf `BaseLoadTest` (veya D-04 sonucu); `@LoadEngine` kaldır; `LoadTestFeeder.csv/json/random/uuid/sequence/constant`, `feedCsv/feedJson`; `targetRps` kaldır (D-04); `docs/changelog.md:92-94` düzelt. TR aynı içerikle birlikte güncellenir; eksik 4 TR sayfa Faz 4'te.
- Bağımlılık: D-04; T1.16 (yeniden yazılan örneklerin gerçek davranışla uyumu).
- Kabul: Tüm yük testi Java blokları javac ile derlenir (örnek `extends BaseLoadTest` ile); `npm run build` EN+TR yeşil.
- Doğrulama: Rapor yöntemindeki `extract.py` + `gen.py` + javac hattı (kalıcı hâli T5.2).

**T1.22 — Derlenmeyen ana docs sayfaları** · DOC-010, DOC-013, DOC-014, API-019 · Efor M
- İş: `ai/prompt-recipes.md`, `guides/video-recording.md` (import'lar `io.testfly.test.*`, `io.testfly.locator.Role`, `find("#x").type(...)`, `open()` → `openLogin()`); `getWait().waitFor*`/`.wait(...)` → `WaitEngine.waitForX(...)`, `getWait().until(...)` (EN: `why-waitengine`, `oauth-sso`, `infinite-scroll`, `from-selenium-testng`; TR: `wait-engine.md`, `base-page.md` EN'i yansıtsın); `cucumber.md`/`junit5.md` örnekleri: `ctx().put/get`, `apiPost(path).body(json).send().assertStatus(201).json("$.id")`, olmayan `db().table(...)` kaldırılır (gerçek `DbClient` API'si).
- Bağımlılık: yok.
- Kabul: İlgili bloklar javac ile derlenir (`-Xlint:removal` uyarısız); EN/TR kod blokları token düzeyinde aynı.
- Doğrulama: Snippet derleme betiği + `npm run build`.

### Faz 1 — kabul, doğrulama, efor

- **Çıkış kriterleri:** (1) Temiz projede dokümandaki bağımlılık çözülür; (2) rapor XSS testi ve Jackson advisory kapalı; (3) `DriverManagerTest` yeşil ve P1 yaşam döngüsü regresyon testleri eklenmiş; (4) retry/yük/Locator/konfig sözleşme testleri yeşil; (5) JaCoCo gerçek kapsama üretiyor; (6) P1 docs sayfaları derleniyor; (7) `mvn clean verify -Dgpg.skip=true` ve EN/TR `npm run build` yeşil.
- **Genel doğrulama:** Tüm Faz 1 regresyon testlerinin "düzeltmeden önce kırmızı, sonra yeşil" olduğu PR açıklamasında gösterilir; denetimdeki repro'lar (`target/audit-scratch/...`) düzeltmeden sonra yeniden koşulur ve beklenen sonuç raporlanır.
- **Efor:** ≈ 45–55 kişi-gün (Hat A ≈ 9, B ≈ 4, C ≈ 30–36, D ≈ 7).
- **Riskler / geri alma:** T1.2 ve T1.3 geri alınamaz yayın içerir → onay kapısı; T1.15 ve T1.16 davranış değiştirir → geçiş bayrağı ve CHANGELOG; T1.13 yaşam döngüsünü değiştirir → bayrak.

---

## Faz 2 — Public API ve Mimari Tutarlılık

**Amaç:** Runner'lar (TestNG/JUnit5/Cucumber) arasında davranış tutarlılığı, iç yardımcıların public sözleşmeden ayrılması, güvenlik sertleştirmeleri ve rapor boru hattı sağlamlaştırması.
**Kapsanan bulgular (51):** ARCH-008, 009, 010, 012…019, 023, 024, 026, 027; API-006, 007, 009, 010, 013…016, 021…023, 025, 027, 028, 030, 032; BLD-021, 022, 023; DOC-026; SEC-003…007, 010, 011, 015, 018; PERF-001, 002, 003, 007; MAINT-001, 002, 007.

| Görev | Bulgular | İş tanımı | Bağımlılık | Kabul kriterleri | Doğrulama | Efor |
|---|---|---|---|---|---|---|
| **T2.1** Listener temizlik güvenliği | ARCH-008, ARCH-012, ARCH-024, API-022 | `onTestSuccess/Failure/Skipped` üç kopya temizliği tek `cleanup()` içinde `try/finally`; `jsErrorsLogged` `onTestStart` ve `finally`'de sıfırlansın; tüm yollarda `clearCurrentTest()`; soft-assert hükmü `IInvokedMethodListener.afterInvocation`'a taşınsın | T1.8, T1.15 | Hook zinciri fırlatsa bile `quitDriver()` çağrılır ve `activeSessions()==0`; soft-assert başarısızlığı `TestListenerAdapter` ve `TestNG.getStatus()` ile tutarlı raporlanır; ardışık testte konsol hatası toplama atlanmaz | Mini-suite testleri; Surefire altında `mvn test` çıkış kodu/XML (API-022 şüphesini doğrular/çürütür) | M |
| **T2.2** Driver ek sağlamlaştırma | ARCH-009, ARCH-015, ARCH-017, ARCH-023, ARCH-026 | `2×CPU` sınırı yalnız `local`; `BrowserContext` çözülmüş tarayıcı Remote/BrowserStack/SauceLabs sağlayıcılarına geçsin; `getDriver()` saf erişimci, sağlık kontrolü sınır noktalarında / `NoSuchSessionException`'da, `UnhandledAlertException` canlı sayılsın; sağlayıcı iç hatasında `quit` ve `DRIVER.set` son adım; tüm driver'ları JVM-genel kümede tut ve shutdown hook'ta boşalt | T1.8–T1.10 | Rapor Bölüm 3.2'deki ilgili repro'lar düzeltmeden sonra beklenen sonucu verir; açık alert varken `getDriver()` tarayıcıyı değiştirmez; matris tarayıcısı uzak oturumda gerçekten kullanılır | Birim testler + (mümkünse) headless tarayıcı ile alert testi; SIGTERM davranışı için mock'lu uzak oturum | M |
| **T2.3** Metrik kimliği ve suite yaşam döngüsü | ARCH-016, ARCH-018 | Metrik kimliğine parametre özeti/çağrı sayısı ekle (eski anahtarı da oku); `FrameworkBootstrap` bağlamı **son** yayınla; `onFinish` adımları bağımsız korumalı; plugin/adapter ömrü JVM düzeyine ya da suite başı yeniden yükleme | T1.15 | DataProvider satırları ayrı kayıtlı; çoklu `<suite>` koşusunda eklentiler çalışır; kayıt yüklemesi başarısızsa `initialize()` yeniden denenebilir | İki suite'li mini-suite, DataProvider testi, flakiness geçmişi geri uyumluluk testi | M |
| **T2.4** Köprü tutarlılığı | ARCH-010, ARCH-013, ARCH-014, API-021, API-023, API-030 | SPI dosyasından Allure/ReportPortal satırlarını kaldır (veya adaptörleri self-gating yap, `register` `getName()`'e göre tekilleştirsin); suite teardown idempotent; `PreConditionResolver` ortak (sınıf düzeyi TestNG'de de); `PreconditionSessionCache.restore` kaynak URL'ye gidip geri yükle ve hata logla/yeniden çalıştır; JUnit5 retry `TestAbortedException`'ı yeniden denemesin; Cucumber `@After` yakalama (yüksek order) ve quit (düşük order) olarak ayrılsın; `PluginRegistry.loadAll` eklenti başına korumalı, `FrameworkVersion` doğru grup/Manifest | T1.15 | Allure yalnız etkinse ve bir kez çalışır; TestNG ve JUnit5 aynı sınıf-düzey `@PreCondition` davranışı; Cucumber kullanıcı `@After` hook'larında `getDriver()` çalışır; IDE'de `FrameworkVersion` `0.0.0` dönmez | Birim + gerçek tarayıcıda restore testi (T3.2); Cucumber mini-koşusu | M |
| **T2.5** Locator ve assertion tutarlılığı | API-013, API-014, API-015, API-016, ARCH-027 | `robustClear` platforma göre tek akor, gerekirse `el.clear()`, hata İngilizce SLF4J ve değer boş değilse fırlat; `findElements(context)` bağlamı kullansın; `LocatorException extends NoSuchElementException`; `SeleniumAssert.LocatorBy` görünürlük süzmesi kaldırılsın; `LocatorAssert` zaman aşımı null korumalı; `SmartLocator` bekleme + SLF4J | T1.17 | Rapor Bölüm 3.3'teki davranışlar test edilir; `element.findElement(locator)` verilen bağlamda arar; `assertThat(locator).count(n)` `Locator.count()` ile tutarlı | Birim + tarayıcı testleri (gizli checkbox, iç içe bağlam) | M |
| **T2.6** Yük testi API tutarlılığı | API-006, API-007, API-009, API-010 | Javadoc zincirleri düzeltilir veya `LoadStep.step(String)` eklenir (additive); `load()` D-04'e göre `BaseApiTest`/`BaseJUnit5ApiTest`; `loadtest.enabled` uygulanır veya silinir; baseUrl fallback JDK motorunda; `engine` `equalsIgnoreCase`; ms hassasiyeti; Jackson ile JSON/JSONPath, URL kodlama, ilk N hata logu, 0 istekli koşu varsayılan başarısız | T1.16 | Belgelenen akıcı zincirler derlenir; `engine: JDK` kabul edilir; 0 istekli koşu `assert*` ile vakumla geçmez | `JdkLoadEngineTest` ve Javadoc örnek derlemesi | M |
| **T2.7** Konfigürasyon yükleyici ikinci aşama | BLD-021, BLD-022, BLD-023, SEC-010, MAINT-001 | Tüm `Map/List`'te `${VAR}` çözümü; çözülmemiş belirteç toplu hata (`${VAR:-}` ile muaf; bir minor boyunca yalnız uyarı); `TestFlyDefaults` doğrulamadan **önce**; tek `Secrets.resolve()` ile beş/sekiz kopyayı değiştir; `.env` değerleri sistem özelliği olarak yayınlanmasın; `.env` önceliği yapılandırılabilir (D-07/D-03); `config.strict`; `applyCiOverrides` "açıkça set edildi" ayrımı | T1.19, D-03, D-07 | BrowserStack `bstack:options.userName` çözülür; typo'lu env değişkeni başlangıçta hata verir; `TestFlyDefaults.set("browser.name",…)` eksik zorunlu anahtarı karşılar; `System.getProperties()` içinde `.env` anahtarı yok | `ConfigurationLoaderTest`, `DotEnvLoaderTest`, repro `T.java` eşdeğeri | L |
| **T2.8** `@TestFlyApi` metaverisi | ARCH-019, API-032, DOC-026 | D-05 eşleme tablosuna göre betikle tek geçişte `since` yeniden yazımı; `forRemoval=true` indirilir; `DriverManager`/`MultiSessionManager` iç yardımcıları `internal`'a taşınır (önce deprecate + yönlendirme); `popSessionOverride()` ayrık Javadoc düzeltilir; build-time test: `since <= project.version` | D-05 | `@TestFlyApi(since=…)` değerlerinin tekil listesi (`grep -rhoE` çıktısı sıralanıp tekilleştirilince) yalnız gerçek sürümleri verir; test sürümü aşan `since`'te kırılır | Yeni birim/arşiv testi + `japicmp` ön denemesi (T5.6) | M |
| **T2.9a** Redaksiyon katmanı | SEC-005, SEC-006, SEC-011, SEC-015, API-028 | Merkezi `Redactor`/`UrlRedactor` (query parametre adları, başlık listesi genişletilmiş, JSON alanları, URL userinfo) → `ApiClient` log/curl/exception, AI prompt, TestRail/Xray yorumları, CI/grid URL'leri; `DomPruner` gizli alan/`value`/`data-*token*` temizliği; `ai.allowInsecureHttp` (varsayılan kapalı, loopback hariç); Claude tek auth başlığı; `ai.sendSourceCode` açık opt-in | D-07 | `apiKeyQuery` ile yapılan çağrının hiçbir log/rapor/exception satırında anahtar görünmez; AI isteğinde `input[type=hidden]` ve `value` yok; `http://` AI baseUrl reddedilir | Birim testler (yerel echo sunucu ile gönderilen gövde incelenir) | M |
| **T2.9b** Diğer güvenlik sertleştirmeleri | SEC-003, SEC-004, SEC-007, SEC-018, API-027, MAINT-002, PERF-003 | IMAP `ssl.checkserveridentity=true` + STARTTLS + `email.imap.trust`; `ApiClient` çerezleri JDK `CookieManager` ile test kapsamlı; `ApiMockServer.bindAddress("127.0.0.1")` + opt-in; AI/TestRail/Xray JSON'u Jackson `ObjectNode`; paylaşılan `HttpClient` + connect/request zaman aşımı; Gemini'deki model regex'i tüm sağlayıcılarda | D-07 | İki WireMock host'u testinde A'nın çerezi B'ye gitmez; kontrol karakterli stack trace AI isteğinde geçerli JSON; mailhog/TestRail zaman aşımı gerçekleşir; sahte kimlikli IMAP sunucusuna bağlanılmaz | Birim testler; `javap` yerine yapılandırma özelliği assert'i | M |
| **T2.10** Rapor boru hattı | PERF-001, PERF-002, API-025, PERF-007, MAINT-007 | Ekran görüntüleri dosyaya, JSON'da yalnız bağıl yol; geçmiş kopyalarından medya çıkar; geçmişi akış ayrıştırıcıyla oku; video `<video src>` bağıl yol veya boyut sınırı; `reports/` arşiv rotasyonu (`flakiness.historyRuns`) ve `reporting.archive:false`; tüm yollar `ReportPaths` üzerinden; `ScreenshotManager` `OutputType.BYTES` + benzersiz ad; `JUnitXmlReporter` ve Allure geçersiz XML karakterlerini ayıkla | T1.7 | 200 test × 10 ekran görüntüsü senaryosunda `-Xmx256m` ile rapor üretimi başarılı; metrik JSON'da `screenshotBase64` yok; Gradle (`build/`) düzeninde tüm çıktılar tek kökte; paralel satırlarda ekran görüntüsü çakışması yok | Golden dosya testleri (metrik JSON/HTML şeması), yük repro'su, `SmartTestSharder` etkisi kontrolü | L |

### Faz 2 — kabul, doğrulama, efor

- **Çıkış kriterleri:** Runner'lar arası davranış tablosu (retry, sınıf-düzey `@PreCondition`, temizlik, driver kapama) testlerle eşit; iç yardımcılar `internal`'da; redaksiyon ve güvenlik sertleştirmeleri testli; rapor boru hattı golden testleriyle korunuyor; `mvn clean verify -Dgpg.skip=true` yeşil, kapsama eşiği T1.20'deki ölçüme göre sağlanıyor.
- **Genel doğrulama:** Her görev için Rapor Bölüm 4'teki "nasıl doğrulanır" adımları uygulanır; çözülemeyen şüpheler (ARCH-013/017/018/023/026, API-021/022/023) doğrulama sonucuyla "doğrulandı" veya "çürütüldü" olarak rapora not düşülür.
- **Efor:** ≈ 55–70 kişi-gün. **Risk:** ARCH-016/PERF-001 rapor/metrik şema değişikliği; geçişte eski şemayı okuyabilme zorunlu. T2.8 `internal` taşıması için deprecate + bir minor kuralı.

---

## Faz 3 — Test Güvenilirliği ve Kalitesi

**Amaç:** "Yeşil" sonucun anlam kazanması: gerçek yaşam döngüsü ve gerçek tarayıcı kapsamı, yanlış-olumlu testlerin düzeltilmesi, paralel izolasyon ve test altyapısı sağlamlığı.
**Kapsanan bulgular (14):** TST-006…011, 013…016; API-033; BLD-006, 008, 016.

| Görev | Bulgular | İş tanımı | Bağımlılık | Kabul kriterleri | Doğrulama | Efor |
|---|---|---|---|---|---|---|
| **T3.1** Mini-suite entegrasyon çerçevesi | TST-007 | `new TestNG()` programatik koşu: `RetryAnnotationTransformer`, `TestExecutionListener`, `SuiteExecutionListener`, sahte driver sağlayıcı, `parallel=methods threadCount=2`; JUnit Launcher karşılığı; 3–5 senaryo (retry→metrik, `@BeforeMethod` sırası, soft-assert, iki suite, DataProvider) | T1.8, T1.15, T2.1 | Sonuç sayıları, `retryCount`, suite sonunda `activeSessions()==0` assert'li; ayrı fork'ta çalışır | `mvn test -Dtest=MiniSuite*` | M |
| **T3.2** Gerçek tarayıcı smoke profili | API-033 (+ TST-012 ile Faz 5 CI bağı) | `-Pbrowser` profili; yerel `HttpServer` ile statik sayfalar: gecikmeli eleman, stale yeniden render, iç içe metin, gizli checkbox, kapsamlı `By.findElement`, alert, soft-assert; `PreCondition` restore | T1.17, T1.18, T2.5 | Headless Chrome ile `Locator`/`WaitEngine` P1 senaryoları (API-011/012) otomatik kanıtlı | `mvn -Pbrowser verify` (Chrome önceden kurulu) | L |
| **T3.3** Assertion'sız / yanlış-olumlu testlerin düzeltilmesi | TST-008, TST-009, TST-016 | 69 assertion'sız testten davranış iddia edenleri gözlemlenebilir duruma bağla (`ExecutionMetrics.getTiming(id)` okuyucu, interceptor etkisi yerel `HttpServer` ile); `ReportPortalReportAdapterTest` JSON'u düzelt ve çağrıyı doğrula; ters `assertEquals`, `assertNotNull(fluent)`, yutulan `catch` temizliği; "no-throw" sözleşmeli testler adlandırılarak korunur | — | Assertion'sız test sayısı ≥%80 azalır; `ReportPortal*Test` loglarında "failed: null" kalmaz; PMD `EmptyCatchBlock` ihlali yok | PIT (nightly) ile mutasyon skoru kıyası | M |
| **T3.4** Konfigürasyon test derinliği | TST-010 | Temp-dizin YAML fixture'ları + `-Dtestfly.config`; tablo-güdümlü negatif `validate()` testleri; `${VAR}`/`${VAR:-default}` ağaç çözümü; profil öncelik sırası; `TestFlyDefaults`'un zorunlu anahtarı karşılaması | T1.19, T2.7 | `ConfigurationLoader` tüm dalları kapsanır (JaCoCo ile) | `mvn test -Dtest=ConfigurationLoader*` | M |
| **T3.5** Test referansı olmayan sınıflar | TST-011 | Saf mantık sınıfları önce (`FrameworkVersion`, `CapabilityValidator`, `SchemaValidator`, `OpenApiValidator`, `BrowserMatrixListener`, `ExcelDataReader`, `SmartLocator`, `RetryAnnotationTransformer`); sonra yerel `HttpServer` ile sağlayıcı istemcileri (`XrayClient`, `OutlookProvider`, `ImapProvider`, `ApiMockServer`, `PerformanceCollector`) | T1.20 | İlk 20 sınıf için dal/satır kapsama hedefi T1.20 ölçümünden türetilir | JaCoCo raporu | L |
| **T3.6** Paralel izolasyon ve rapor dizini yarışı | TST-006, TST-013, TST-015 | Rapor dosyalarına dokunan her test `testfly.reports.dir`'i per-test temp dizine alsın (veya Surefire `-Dtestfly.reports.dir=${project.build.directory}/surefire-${surefire.forkNumber}`); `singleThreaded`/`try-finally`; `elapsed < 1000` yerine probe sayacı; assertion'sız `Thread.sleep` testleri kaldırılsın | T1.20 | Rapor 50 tekrarda (TST-006 döngüsü) 0 hata; CI'a yüklenen rapor "son koşan test" değil gerçek rapor | `for i in $(seq 50); do …` döngüsü | S–M |
| **T3.7** Surefire yapılandırması | TST-014, BLD-016 | `forkedProcessTimeoutInSeconds`, `perCoreThreadCount` kaldırılır, `parallel=methods` ↔ `forkCount` kararı; `reuseForks=true` gece koşusu (sızıntı kanıtı); `AnnotationTransformer already set` çift kayıt; `integration` profil dışlamaları; `testfly.profile` `systemPropertyVariables`; görsel baseline dizini `target/` | T1.20 | Test süresi ve sapma ölçülür; `reuseForks=true` gece koşusu yeşil veya sızıntılar bug olarak kayıtlı | `mvn -Dsurefire.reuseForks=true test` | S–M |
| **T3.8** Test sınıf yolu ve JDK hazırlığı | BLD-006, BLD-008 | `guice` → `test` kapsamı; `dependencyManagement` ile `guava` (33.x) ve `byte-buddy`/`byte-buddy-agent` aynı sürüm; Mockito `-javaagent` + `@{argLine}`; `experimental` bayrağının kaldırılması denemesi; JDK 25'te deneme koşusu (izinli hata) | T1.6, T1.20 | `dependency:tree` Guava 33.x ve ByteBuddy tek sürüm; JDK 21 yeşil; JDK 25 durum raporu | `mvn -o dependency:tree -Dverbose -Dincludes=…`; JDK 25 CI deneme işi | M |

### Faz 3 — kabul, doğrulama, efor

- **Çıkış kriterleri:** Kapsama raporu CI'da üretiliyor ve ölçülen taban çizgisinin altına düşmüyor (ratchet); `driver`, `listeners`, `config`, `wait`, `locator` paketlerinde kritik yolların testleri var; gerçek tarayıcı smoke profili en az bir kez yeşil; assertion'sız test azalmış; paralel izolasyon döngüsü 0 hata.
- **Genel doğrulama:** "Düzeltmeden önce kırmızı" kanıtı için denetimdeki hatalı davranışlar (ör. `maxAttempts` ya da permit sızıntısı) testin geçici olarak geri alınmış düzeltmeyle kırıldığı gösterilir (mutation sanity).
- **Efor:** ≈ 25–35 kişi-gün. **Risk:** Gerçek tarayıcı testlerinin CI'da yeni flaky yüzeyi (tek smoke test ile başla).

---

## Faz 4 — Dokümantasyon ve Geliştirici Deneyimi

**Amaç:** Tüm Java ve YAML örneklerinin gerçek API/konfigürasyonla uyumu, framework-managed driver ve semantik `Locator` yaklaşımının varsayılan öğretilmesi, EN/TR eşdeğerliği, geçerli bağlantılar.
**Kapsanan bulgular (20):** DOC-009, 011, 012, 015, 016, 017, 018, 019, 020, 021, 022, 023, 024, 025, 028, 029; API-017, 018, 029, 031.
**Ön koşul:** Faz 1 docs görevleri (T1.4, T1.5, T1.21, T1.22) tamam; D-08, D-09 kararları.

| Görev | Bulgular | İş tanımı | Bağımlılık | Kabul kriterleri | Doğrulama | Efor |
|---|---|---|---|---|---|---|
| **T4.1** Kalan API sapması düzeltmeleri | DOC-011, DOC-012, DOC-015, DOC-017, DOC-020 | `plugins.md` (`TestFlyContext` yerine `onLoad`'a geçen `TestFlyConfig`, `getExecution().getBaseUrl()`); TR `oauth-sso.md` `io.testfly.client.ApiResponse`; `RoleOptions` → `getByRole(Role.TEXTBOX, "Username")` (MCP codegen için ayrı depoya bildirim notu); `first()` → `.element()`; `README` `hasUrlContains` → `urlContains`, `softAssertPage()`, `assertSchema`, `$$()` kaldır; `homeData.js` `hasText`/`Role.STATUS` → gerçek API (veya `Role.STATUS` ekleme, eklemeli enum) | T1.22 | İlgili blokların javac derlemesi temiz; landing sayfası kodu derlenir | Snippet derleme betiği + `npm run build` | M |
| **T4.2** Semantik `Locator` + `BasePage()` standardı | DOC-016, DOC-018, DOC-019, API-017, API-018 | `guides/base-page.md`, README Adım 4, `intro.md`, `parallel.md`, `browser-lifecycle.md`, `external-test-data.md`, `precondition.md`, `ai/agentic-testing.md`, `ai/recorder.md`: argümansız `BasePage()` + `Locator` alanları (`getByRole/getByLabel/getByTestId` → `find(String)`), `By` yalnız "interop" bölümünde (`WaitEngine`, frame/shadow/upload, `SmartLocator`, ham Selenium); `$()` örneklerden kaldır (Selenide "önce" sütunu hariç); Javadoc'taki 11 `$()` satırı ve `BasePage` kanonik POM örneği; `Role` Javadoc'u "CSS/ARIA-öznitelik yaklaşımı"; D-09: protected yardımcıların sahip sınıfı gösterilir veya varsayılan metot eklenir | D-09, T2.5 | EN Java bloklarında `By.*` yalnız interop bölümlerinde; `-Xlint:removal` uyarısı yok; Javadoc'ta `$(` yok (`grep -rnE '^\s*\*.*[^a-zA-Z]\$\(' src/main/java` boş) | Snippet derleme betiği; `mvn javadoc:javadoc` | L |
| **T4.3** Konfigürasyon ve yük dağıtım dokümanları | DOC-024, DOC-009 | Üst düzey `browserstack:` → `execution.browserstack`; `browser.binaryPath`/`browser.type`/`reporting.allure.resultsDir` kaldır/düzelt; `cloud-execution.md:13` bayat uyarı; `distributed-docker-k8s.md`: `${LOAD_USERS:-10}` yer tutucu örnekleri, `BuildThresholdEnforcer` ve canlı akış iddiaları düzeltilir/kaldırılır, "100.000+ RPS" kaynaklandırılır veya silinir | T1.19 | Dokümandaki tüm `testfly.yml` blokları `ConfigurationLoader` ile "Unknown config key" uyarısı olmadan yüklenir | YAML doğrulayıcı (T5.2) | S |
| **T4.4** EN/TR parite ve bağlantılar | DOC-021, DOC-022, DOC-023 | EN kaynak kabul edilir; TR kod blokları EN'den üretilir, yalnız metin çevrilir; 4 eksik TR sayfa (`ci/bitbucket-pipelines`, `loadtest/distributed-docker-k8s`, `migration/from-restassured`, `reporting/allure`); `guides/assertions.md` eksik TR blokları; TR `ci/*` `browser.type` düzeltmesi; TR changelog'u EN/`CHANGELOG.md` ile hizala; `file:///` bağlantıları `https://github.com/hakanngul/testfly/blob/main/<yol>`; README `docs/features/*` bağlantıları kaldırılır veya geri getirilir; `AGENTS.md:277,377-378` düzeltilir; `onBrokenMarkdownLinks:'throw'` | T1.4, T1.21, T1.22, T4.3 | EN/TR dosya listesi ve fence sayısı eşit; token düzeyinde Java farkı yalnız çevrilmiş test adları; 0 `file:` bağlantısı | Parite betiği (T5.3) + `npm run build` | M–L |
| **T4.5** Bayat olgular ve getting-started bütünlüğü | DOC-025, DOC-028 | `jenkins.md` JDK 21 ve `Jenkinsfile` ile aynı araç adları; Gatling 3.13.x ve 4 artifact; `getting-started.md` Surefire/`suiteXmlFiles`, Gradle `suites`, `release 21`, `junit-platform-launcher`; tek kanonik minimal proje (pom + testfly.yml + tek test) temiz dizinde uçtan uca doğrulanır | T1.4, T1.5 | Temiz dizinde adımlar izlenince ilk test geçer | Kanonik proje CI işi (T5.2) | S–M |
| **T4.6** Kendi içinde tam örnekler | DOC-029 | `src/test/` veya `examples/` altında docs'un kullandığı `LoginPage`, `User`, `DashboardPage` gibi yardımcı sınıflar (companion samples) | T4.2 | Parça örneklerin `cannot find symbol` sayısı ≥%90 azalır | Snippet derleme istatistiği | M |
| **T4.7** Pazarlama/iddia hizası ve özel driver dokümanı | API-029, API-031 | D-08'e göre README/pom/intro ifadeleri; `custom-drivers.md`: `execution.mode`, remote/cloud modunda özel sağlayıcı sınırı, yanlış YAML ve checked exception örneği; gerekirse `DriverProvider.createDriver() throws Exception` (kaynak uyumlu) veya sarma notu | D-08 | İddialar kodla örtüşür | Docs incelemesi + snippet derleme | S–M |

### Faz 4 — kabul, doğrulama, efor

- **Çıkış kriterleri:** (1) Java bloklarının "tam derlenebilir" işaretli alt kümesi %100 derlenir ve `-Werror=removal` ile yeni kod örneklerinde `$()` yok; (2) tüm `testfly.yml` blokları geçerli; (3) EN/TR parite betiği 0 fark; (4) `file:` ve ölü iç bağlantı yok; (5) `npm run build` (EN+TR) yeşil; (6) kanonik minimal proje temiz ortamda geçer.
- **Genel doğrulama:** Rapor Bölüm 3.7'deki ham sayılar (597 Java bloğu / 315 derleme hatası) yeniden ölçülür; kalan hatalar yalnızca "parça/üçüncü taraf" izin listesinde (gerekçeli) kalmalı.
- **Efor:** ≈ 25–35 kişi-gün. **Risk:** Büyük metin değişimi; çift dil kuralı gereği EN ve TR aynı PR'da.

---

## Faz 5 — CI/CD, Önleyici Kontroller ve Teknik Borç

**Amaç:** Bu denetimin bulduğu sorun sınıflarının tekrarını makineyle engellemek; kalan P2/P3 güvenlik, performans ve bakım borcunu kapatmak.
**Kapsanan bulgular (29):** ARCH-021, 022, 025; API-026; BLD-012…015, 017, 018; TST-012, 017; DOC-027; SEC-009, 012, 013, 014, 017, 019, 020, 021; PERF-004, 005, 006, 008; MAINT-003…006.

| Görev | Bulgular | İş tanımı | Bağımlılık | Kabul kriterleri | Doğrulama | Efor |
|---|---|---|---|---|---|---|
| **T5.1** CI iş akışı sertleştirme | BLD-012, BLD-013, BLD-014, TST-012, TST-017 | İş düzeyi `if:` ile atlama görünür ("skipped"); entegrasyon işi hizmet-tabanlı testleri (Mailhog/DB/Gatling) koşulsuz çalıştırır, yalnız AI testleri sır ile kapılı; consumer repo yoksa `main`/`development`'ta başarısız (veya zorunlu-opsiyonel işaret); `fail-on-empty: true`; docs build işi (`npm ci && npm run build`, Node 18/20); `-Pquality` (önce non-blocking); JDK matrisi `[21, 25]`; `timeout-minutes`, `concurrency: cancel-in-progress`, aynı-depo PR tekrar tetikleme; action'lar SHA ile sabit; Dependabot `github-actions`; `Jenkinsfile`: `jdk 'JDK21'`, `main`, aşağı akış tetikleyicisi yalnız `main` | T1.2, T3.2, T3.8 | Gerekli kontroller listesi branch protection'a işlenir; atlanan iş "skipped" görünür; `npm run build` PR'da koşar | Deneme PR'ı ile her iş | M |
| **T5.2** Docs snippet / YAML / API kontrat kapıları | DOC-027 | Önleme Stratejisi P1/P2 başlıklarındaki uygulama (rapor yöntemi `extract.py`+`gen.py`+javac'ın kalıcı hâli, `ConfigurationLoader` ile YAML doğrulama, yasak-desen listesi) | Faz 4 (veya rapor-only başlayıp Faz 4 sonunda blokla) | CI'da bloklayıcı; kasıtlı hatalı örnek (`io.testfly.core.BasePage`) PR'ı kırar | Negatif test PR'ı | M–L |
| **T5.3** Docs tutarlılık kapıları | DOC-027 (devam) | EN/TR parite betiği, tek-kaynak sürüm (`pom.xml` → docs sabiti), `io.testfly:testfly` yasağı, link kontrolü | T1.4, T4.4 | Sürüm uyuşmazlığı PR'ı kırar | Negatif test PR'ı | M |
| **T5.4** Tedarik zinciri ve güvenlik tarama | SEC-009 (+SEC-016 sürekliliği), BLD-018 | OSV-Scanner + OWASP dependency-check (haftalık + PR), CodeQL (java, javascript), `npm audit --omit=dev`, CycloneDX SBOM, gitleaks (tam geçmiş taraması bir kez), `maven-enforcer` (`dependencyConvergence`, `requireUpperBoundDeps`, `requireJavaVersion [21,)`, `requireMavenVersion [3.8,)`); Gatling/Netty sürümleri için `netty-bom` yönetimi ve docs sürüm hizası | T1.6 | Advisory'siz taban çizgisi veya bilinçli `suppression.xml` kayıtları | İlk tarama raporu + sürekli koşu | M |
| **T5.5** Build/POM hijyeni ve `quality` profili | BLD-015, BLD-017 | `quality` profilini ya onar (Checkstyle ≥3.5 + 10.x, PMD 7.x, SpotBugs öncelikli, `google_checks` yerine proje kuralları) ya da kaldır ve `CONTRIBUTING.md:110` düzelt; `outputTimestamp`, Maven wrapper, gereksiz `java.version`; `jcodec` opsiyonel (lazy-load + net hata); `selenium-java` şemsiyesinin ileride bölünmesi için karar kaydı | T5.1 | `mvn -Pquality verify` Java 21'de çalışır veya profil yok | Yerel + CI | M |
| **T5.6** API uyumluluk kapısı | ARCH-019 (devam), API-032 | `japicmp-maven-plugin` ile `@TestFlyApi` işaretli türlerin son Central sürümüyle (örn. 1.0.4) ikili/kaynak uyumluluğu; `since`/`forRemoval` kural testi | T2.8 | Uyumsuz değişiklik PR'ı kırar | Negatif test | M |
| **T5.7** Mimari borç | ARCH-021, ARCH-025, MAINT-004, PERF-006, PERF-008 | `TestLifecycle` çıkarımı ve üç adaptörün ona geçişi (Faz 3 testleri güvencesinde); `DriverGuard` ile katman tersine çevirme; auth çözümünün `client`'a taşınması; registry'lerde `ConcurrentHashMap/CopyOnWriteArrayList` ve özel kilit nesnesi; `HookRegistry.reset()`; `ApiClient` bölünmesi; `TestFlyConfig` iç sınıflarının ayrı dosyalara alınması; tek sıralı shutdown hook; `assertBodyMatches` → `Pattern.find`; 8 sınıfta `ThreadLocal.remove()` doğrulaması | Faz 3, T1.13 | Davranış değişmez (mevcut + yeni testler yeşil); `TestExecutionListener` < 300 satır hedefi | Tüm test paketi + mini-suite | L |
| **T5.8** Loglama ve ölü kod | ARCH-022, API-026, MAINT-003, MAINT-005, MAINT-006 | SLF4J cephesi (`TestFlyLog`) ve yutulan istisnalar için `debug` log; tanıtım afişi `-Dtestfly.banner=false`; `System.out/err` mekanik dönüşüm; kullanılmayan 3 istisna sınıfı (public API değilse), `writeToFile`, deprecated `SessionCache` temizliği; `.testfly/healed-locators.json` `.gitignore` + `git rm --cached` (normal commit, geçmiş yeniden yazılmaz) | T5.7 | Konsol çıktısı yapılandırılabilir; `grep -c "System\.\(out\|err\)\." src/main/java` hedefi <20 | Birim + grep | M |
| **T5.9** Kalan güvenlik ve performans maddeleri | SEC-012, SEC-013, SEC-014, SEC-017, SEC-019, SEC-020, SEC-021, PERF-004, PERF-005 | `--no-sandbox` yalnız root/açık opt-in; `DbClient.assertRowCount(table, where, Object... params)` + ham sürümün deprecate'i, `db:` test verisi salt-okunur; `email.allowClear` veya Trash'e taşıma; `OAuth2TokenCache` anahtarına sır özeti, `digest()` kaldır/uygula; `writeToFile` sil ya da `rw-------`; multipart RFC 7578 kaçışı + UUID boundary; `trustAll` her profilde uyarı ve CI politikası; `JdkLoadEngine` ham sayaç/HDR histogram ve `GatlingEngine` zaman aşımı; `DomPruner` klon üzerinde çalışsın | T2.9a/b, D-07 | İlgili birim testler yeşil | Birim testler | M–L |

### Faz 5 — kabul, doğrulama, efor

- **Çıkış kriterleri:** Bloklayıcı CI kapıları: `mvn -B verify -Dgpg.skip=true` (JaCoCo eşiği), docs build, docs snippet/YAML/parite/sürüm kontrolleri, bağımlılık ve kod taraması, JDK matrisi; release hattı korumalı; Dependabot üç ekosistemde; teknik borç maddeleri kapalı veya gerekçeli ertelendi.
- **Genel doğrulama:** Her yeni kapı için negatif test PR'ı (kasıtlı hata → kapı kırmızı).
- **Efor:** ≈ 35–50 kişi-gün. **Risk:** İlk tarama/kalite profili çıktısında "backlog dalgası"; kapıları önce rapor-only başlat, düzeltmeden sonra bloklayıcı yap.

---

## Önleme Stratejisi (Prevention Strategy)

Amaç: Bu denetimde bulunan hata sınıflarının (docs-API sapması, sürüm/koordinat kayması, sözleşme–kod ayrışması, yeşil ama anlamsız testler, bakımsız bağımlılık) yeniden oluşmasını, insan dikkatine değil CI kapılarına bağlamak. Her kapı önce **rapor-only**, ilgili faz bitince **bloklayıcı** olur.

### P1. CI'da docs Java snippet derlemesi (DOC-027, DOC-029, DOC-001…017)

- **Mekanizma:** Denetimde kullanılan hat kalıcılaştırılır: (1) `docs-site/docs`, TR `i18n/.../current` ve `README.md` içinden fence'li blokları çıkar (`scripts/snippets/extract.*`); (2) derlenebilir olması iddia edilen bloklar fence meta etiketiyle işaretlenir: ` ```java compile ` (tam sınıf/üye listesi/ifade-gövde sarmalayıcısı otomatik); (3) `mvn -q compile` çıktısı + `classpath` ile `javac -Xlint:removal,deprecation` (yeni kod örneklerinde `-Werror=removal` ile `$()` yasaklanır); (4) üçüncü taraf "önce" kodları ve parçalar `compile-skip` etiketi veya gerekçeli izin listesi (`docs-site/scripts/snippets/allowlist.yml`) ile ayrılır; (5) bağımlı yardımcı sınıflar için "companion samples" (T4.6).
- **YAML blokları:** ` ```yaml testfly-yml ` etiketli blokları gerçek `ConfigurationLoader.load()` ile yükle (denetimdeki `repro/Y.java` yaklaşımı); "Unknown config key ... ignored" çıktısı hata sayılır. `testfly-quarantine.yml` gibi farklı şemalar ayrı etiket (`quarantine-yml`).
- **Çalıştırma:** PR'da `docs-site/**`, `src/main/**` veya `README.md` değişince; yerelde `npm run check:snippets`. Çıktıda bulgu kimliği (`DOC-xxx`) yerine dosya:satır ve javac mesajı.
- **Etap:** Faz 1'de kritik sayfalar için rapor-only betik; Faz 4 sonunda bloklayıcı.
- **Kabul/doğrulama:** Kasıtlı `import io.testfly.core.BasePage;` içeren PR kırmızı; tüm `compile` etiketli bloklar yeşil.

### P2. API kontrat doğrulaması (docs ↔ `@TestFlyApi`/kaynak) (API-017/019/031/032, ARCH-019, DOC-026)

- **Sembol indeksi:** Derlenmiş sınıflardan (`target/classes`) public tür/metot/alan listesi üret (`api-index.json`: sınıf, imza, `@TestFlyApi(since)`, `@Deprecated(forRemoval)`); docs ve Javadoc örneklerindeki her `io.testfly.*` import ve (derleme kapısının yakalamadığı) kod-dışı API adları bu indekse karşı doğrulanır.
- **Yasak-desen listesi (lint):** `io.testfly.core.`, `io.testfly.locators.`, `RoleOptions`, `.fill(`, `.val(`, `getWait().waitFor`, `getWait().wait(`, `@LoadEngine`, `targetRps`, `fromCsv`, `io.testfly:testfly`, `<groupId>io.testfly</groupId>`, `file:///`, `$$(`, `$(` (Selenide "önce" bölümü hariç). Yeni sahte API'ler bulundukça liste büyür.
- **`@TestFlyApi` kuralları (birim test olarak):** (a) `since` ≤ `project.version`; (b) `forRemoval=true` varsa kaldırma sürümü takvimlenmiş ve en az bir minor sonra; (c) `io.testfly.*` public türleri `@TestFlyApi` taşıyor ya da `internal` paketinde; (d) iç yardımcı metotlar tür düzeyinde "stable" ile dondurulmaz.
- **İkili/kaynak uyumluluk:** `japicmp-maven-plugin` (veya revapi) son Central sürümüne karşı yalnız `@TestFlyApi` işaretli türlerde uyumsuz değişikliği PR'da kırmızıya çevirir (T5.6).
- **Javadoc:** `mvn javadoc:javadoc -Xdoclint:all` uyarıları hata; `<pre>` örnekleri için P1'deki çıkarım hattı genişletilir (API-006, API-017).

### P3. Docs tutarlılık kontrolleri (DOC-021/022/023, BLD-003/004)

- **EN/TR parite:** (1) dosya listesi eşitliği (EN−TR ve TR−EN boş); (2) fence sayısı eşitliği; (3) Java token farkı (import / metot çağrısı / `new` çoklu kümesi; yorum ve string hariç; çevrilmiş test adları izinli); (4) YAML anahtar kümesi eşitliği; (5) `sidebars.js` kimlik tutarlılığı. Fark varsa PR kırmızı; "TR çevirisi bekleniyor" yalnızca izin listesindeki açık kayıtla geçici olabilir (son tarihli).
- **Tek-kaynak sürüm:** `pom.xml` → `docs-site/src/data/version.js` (prebuild betiği); docs/README sürüm dizgeleri sabitten üretilir ya da CI betiği tüm `io.github.hakanngul:testfly` / `<version>` / `testImplementation` eşleşmelerini `mvn help:evaluate -Dexpression=project.version` ile karşılaştırır; ayrıca **release işinde** "etiket == pom == CHANGELOG üst girişi == (yayın sonrası) Central latest" kontrolü; AGENTS "version-bump checklist" listesi betiğin taradığı dosya kümesiyle eşleştirilir.
- **Bağlantı kontrolü:** `onBrokenLinks: 'throw'` ve `onBrokenMarkdownLinks: 'throw'`; `file:` şeması ve depo içi mutlak yol yasak; harici bağlantılar haftalık `lychee` ile (PR'ı değil sorun açmayı tetikler); README/AGENTS görece bağlantıları dosya varlığı ile doğrulanır.
- **Konfigürasyon dokümanı:** P1'deki YAML doğrulayıcı, `-D`/`TESTFLY_*` geçersiz kılma örneklerini de gerçek `ConfigurationLoader` ile (sistem özelliği verilerek) çalıştırır (BLD-020 tekrarını engeller).

### P4. Bağımlılık ve güvenlik taraması (BLD-005/007/018, SEC-009/016)

- **Dependabot:** `maven`, `npm` + **`github-actions`** (BLD-013/SEC-009); gruplama (Jackson BOM tek PR), haftalık.
- **Tarama:** OSV-Scanner (pom + `docs-site/package-lock.json`) her PR ve haftalık; OWASP `dependency-check-maven` (`failBuildOnCVSS=7`, NVD API anahtarı CI sırrı, `suppression.xml` yalnız gerekçeli); `npm audit --omit=dev` (docs-site); CodeQL (java, javascript); gitleaks (PR + bir kez tam geçmiş); CycloneDX SBOM release artifact'ı.
- **Pom kapıları:** `maven-enforcer-plugin`: `dependencyConvergence`, `requireUpperBoundDeps`, `banDuplicatePomDependencyVersions`, `requireJavaVersion [21,)`, `requireMavenVersion [3.8,)`; `jackson-bom` ve Guava/ByteBuddy için `dependencyManagement`.
- **Sırlar/akış:** Action'lar SHA ile sabit, `permissions` en az ayrıcalık, sırlı işler fork PR'larına kapalı, yayın işi `environment: release` onaylı.
- **Süreç:** OSV/CVE bulgusu → 7 gün içinde yama PR'ı (HIGH) / 30 gün (MEDIUM); atlamalar `suppression` kaydında gerekçe + bitiş tarihiyle.

### P5. Regresyon ve kalite kapıları (TST-001…017, BLD-009/013)

- **Zorunlu PR kontrolleri (branch protection):** `mvn -B verify -Dgpg.skip=true` (testler + JaCoCo `check`), docs build (EN+TR), docs snippet/YAML/parite/sürüm kapıları, OSV/CodeQL, `-Pquality` (onarıldıktan sonra).
- **Kapsama:** JaCoCo eşiği T1.20'de ölçülen taban çizgisinden başlar ve **ratchet** olur (düşmez); kritik paketler (`driver`, `listeners`, `config`, `wait`, `locator`) için paket başına alt sınır; yeni/değişen satırlar için fark-kapsama raporu.
- **Test kalitesi:** PIT mutasyon testi gece koşusu (kritik paketler); assertion'sız test sayısı için üst sınır (betik); `rerunFailingTestsCount` yalnız tanı amaçlı rapor-only; gece `reuseForks=true` koşusu (statik sızıntı kanıtı); iş "atlandı" durumları açık görünür, entegrasyon işi sırsız çalışabilen testleri koşar.
- **Regresyon disiplini:** PR şablonu onay kutuları: "düzeltmeden önce kırmızı olan regresyon testi eklendi", "davranış değişikliği CHANGELOG'da", "EN+TR docs birlikte", "`@TestFlyApi` etkisi kontrol edildi". Testi olmayan hata düzeltmesi birleştirilmez.
- **Gerçek tarayıcı smoke:** Headless Chrome ile bir `-Pbrowser` işi (T3.2) ve consumer (`testfly-test`) deposunun gerçek tarayıcı kapsayan en az bir işi; consumer depo yoksa/özelse bu açıkça belgelenir.
- **Release kapısı:** Etiket `main`'den; pom == etiket; CHANGELOG girişi; testler yeşil (aynı commit için CI başarılı olmadan release başlamaz); `environment: release` onayı; yayın sonrası `maven-metadata.xml` latest == yayın sürümü doğrulaması ve docs sürüm sabiti otomatik PR'ı.

---

## Ek A. Bulgu → Faz eşlemesi (164 bulgu, her ID tam bir kez)

| Faz | Bulgu ID'leri | Adet |
|---|---|---|
| **Faz 1** | BLD-001, BLD-002, BLD-003, BLD-004, BLD-005, BLD-007, BLD-009, BLD-010, BLD-011, BLD-019, BLD-020; ARCH-001, ARCH-002, ARCH-003, ARCH-004, ARCH-005, ARCH-006, ARCH-007, ARCH-011, ARCH-020; API-001, API-002, API-003, API-004, API-005, API-008, API-011, API-012, API-019, API-020, API-024; TST-001, TST-002, TST-003, TST-004, TST-005; DOC-001, DOC-002, DOC-003, DOC-005, DOC-006, DOC-007, DOC-008, DOC-010, DOC-013, DOC-014; SEC-001, SEC-002, SEC-008, SEC-016 | 50 |
| **Faz 2** | ARCH-008, ARCH-009, ARCH-010, ARCH-012, ARCH-013, ARCH-014, ARCH-015, ARCH-016, ARCH-017, ARCH-018, ARCH-019, ARCH-023, ARCH-024, ARCH-026, ARCH-027; API-006, API-007, API-009, API-010, API-013, API-014, API-015, API-016, API-021, API-022, API-023, API-025, API-027, API-028, API-030, API-032; BLD-021, BLD-022, BLD-023; DOC-026; SEC-003, SEC-004, SEC-005, SEC-006, SEC-007, SEC-010, SEC-011, SEC-015, SEC-018; PERF-001, PERF-002, PERF-003, PERF-007; MAINT-001, MAINT-002, MAINT-007 | 51 |
| **Faz 3** | TST-006, TST-007, TST-008, TST-009, TST-010, TST-011, TST-013, TST-014, TST-015, TST-016; API-033; BLD-006, BLD-008, BLD-016 | 14 |
| **Faz 4** | DOC-009, DOC-011, DOC-012, DOC-015, DOC-016, DOC-017, DOC-018, DOC-019, DOC-020, DOC-021, DOC-022, DOC-023, DOC-024, DOC-025, DOC-028, DOC-029; API-017, API-018, API-029, API-031 | 20 |
| **Faz 5** | ARCH-021, ARCH-022, ARCH-025; API-026; BLD-012, BLD-013, BLD-014, BLD-015, BLD-017, BLD-018; TST-012, TST-017; DOC-027; SEC-009, SEC-012, SEC-013, SEC-014, SEC-017, SEC-019, SEC-020, SEC-021; PERF-004, PERF-005, PERF-006, PERF-008; MAINT-003, MAINT-004, MAINT-005, MAINT-006 | 29 |
| **Toplam** | | **164** |

Notlar:
- P1 bulguların tamamı Faz 1'dedir; tek istisna yoktur. `TST-005` (P1) kritik kısmı T1.17 içinde, genişletilmiş kısmı T3.5/T3.2 ile devam eder.
- P2/P3 olup Faz 1'e alınanlar bilinçli bağımlılıklardır: `BLD-004` (T1.4 ile aynı docs geçişi), `BLD-007` (Jackson BOM), `BLD-010`/`BLD-011`/`SEC-008` (yayın öncesi zorunlu), `ARCH-011` (D-02 ile tek karar), `ARCH-020` (test iskelesi), `API-008` (ARCH-001 ile aynı kök neden), `API-024` (SEC-001 ile aynı), `DOC-008` (yük docs yeniden yazımı), `SEC-002` (SEC-001 ile aynı kod), `SEC-016` (gerçek tarama).
- Aynı kök nedeni paylaşan bulgular (rapor Bölüm 2.3) tek görevde kapanır; her ID ilgili görevin "Bulgular" sütununda anılır: `TST-001`↔`BLD-009` (T1.20), `TST-003`↔`ARCH-003` (T1.9), `API-024`↔`SEC-001` (T1.7), `PERF-007`↔`API-025` (T2.10), `TST-012`↔`BLD-012` (T5.1).

## Ek B. Karar ve izleme listesi (özet)

| Madde | Sahip | Gerekli olduğu görev |
|---|---|---|
| D-01 yayın sürümü / kök neden | Depo sahibi | T1.1, T1.3, T1.4 |
| D-02 retry sözleşmesi | Ürün sahibi | T1.15 |
| D-03 konfigürasyon katmanlama | Ürün sahibi | T1.19, T2.7 |
| D-04 yük testi davranışları | Ürün sahibi | T1.11, T1.16, T1.21, T2.6 |
| D-05 `since` eşleme tablosu | Depo sahibi | T2.8 |
| D-06 TestNG config metotlarında driver | Ürün sahibi | T1.14 |
| D-07 güvenlik varsayılanları | Ürün/güvenlik sahibi | T2.7, T2.9a/b, T5.9 |
| D-08 pazarlama ifadeleri | Ürün sahibi | T4.7 |
| D-09 `BasePage` yardımcı erişimi | Ürün sahibi | T4.2 |

*Plan sonu. Bulgu kanıtları ve repro adımları için bkz. [`TESTFLY_AUDIT_REPORT.md`](./TESTFLY_AUDIT_REPORT.md).*
