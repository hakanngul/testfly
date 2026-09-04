# TestFly — Kapsamlı Action Plan

> Tarih: 2026-08-29
> Amaç: TestFly'ı "market-ready"den "battle-tested"a taşımak.
> Kaynak: QA-TASKS.md, feature.md, ROADMAP.md, tr-i18n-gaps.md ve repo analizi

---

## Mevcut Durum Özeti

| Metrik | Değer |
|--------|-------|
| Kaynak dosya (main) | 180 |
| Unit test dosyası | 49 |
| Demo / example test | 18 |
| Integration test | 1 |
| Sıfır testli paket | 13 (aşağıda detay) |
| JaCoCo | `-Pquality` profilinde — default build'de çalışmaz |
| Türkçe doküman | 4/63 çevrilmiş |
| Pre-publish checklist | 9/9 ✅ |
| MCP | Coming soon olarak işaretlendi ✅ |

### Test Kapsamı Haritası

**İyi test edilmiş (unit test mevcut):**
`ai`, `api`, `ci`, `clock`, `config`, `context`, `driver`, `extension`, `flakiness`, `healing`, `hooks`, `locator`, `metrics`, `network`, `performance`, `quarantine`, `reporting`, `shadow`, `steps`, `testdata`, `testmanagement`, `tracing`, `visual`, `wait`

**Kısmi test (ciddi boşluklar):**
| Paket | Src | Test | Eksik |
|-------|-----|------|-------|
| `junit5` | 5 | 1 | TestFlyExtension, TestFlyLauncherListener, EnableTestFly, ReportPortalJUnit5Bridge |
| `assertion` | 4 | 1 | LocatorAssert, SoftAssertions, SoftAssertionCollector |
| `browser` | 11 | 2 | ConsoleErrorCollector, DownloadManager, DeviceEmulator, SessionCache |
| `db` | 4 | 1 | DbClient, DbConnectionFactory, query builder |
| `email` | 9 | 1 | ImapProvider, MailhogProvider, MailtrapProvider, OutlookProvider (HTTP stub testleri) |
| `driver` | 14 | 2 | LocalEdgeDriverProvider, LocalSafariDriverProvider, driver health check |
| `listeners` | 5 | 1 | TestExecutionListener, SuiteListener callback'leri |

**Sıfır test:**
| Paket | Src | Risk Seviyesi |
|-------|-----|---------------|
| `cucumber` | 6 | 🔴 HIGH — bridge katmanı, her Cucumber consumer etkilenir |
| `precondition` | 8 | 🔴 HIGH — session cache, koşul runner, registry |
| `lifecycle` | 1 | 🔴 HIGH — FrameworkBootstrap init sırası kritik |
| `recording` | 2 | 🔴 HIGH — kayıt failure = kayıp delil |
| `execution` | 1 | 🟡 MEDIUM |
| `exceptions` | 3 | 🟡 MEDIUM |
| `internal` | 1 | 🟢 LOW |
| `session` | 1 | 🟢 LOW |

---

## Faz 1 — Temel Sağlamlık (Kritik)

> Hedef: Framework'ün omurgasını test altına almak, coverage görünürlüğü sağlamak.
> Tahmini süre: ~2 hafta (tek geliştirici)

### 1.1 — JaCoCo'yu Default Build'e Taşı

**Neden:** Coverage verisi olmadan "nerede eksiğim" sorusunun cevabı tahmin.

**Yapılacaklar:**
- [ ] `jacoco-maven-plugin`'ı `<profiles>` içinden çıkar, `<build><plugins>` altına taşı
- [ ] `mvn test` çalışınca `target/site/jacoco/index.html` otomatik oluşsun
- [ ] `.gitignore`'a `target/site/jacoco/` ekle (report artifact)
- [ ] `mvn test` çalıştır, ilk coverage tablosunu çıkar

**Acceptance:** `mvn clean test` → JaCoCo HTML raporu oluşur, per-package coverage görünür.

---

### 1.2 — JUnit 5 Wiring Testleri (T-01)

**Neden:** `io.testfly.junit5` bridge'i kullanan her consumer'ın giriş noktası. Regresyon burada felaket.

**Yapılacaklar:**
- [ ] `src/test/java/io/testfly/unit/junit5/` dizini oluştur
- [ ] `TestFlyExtension` testleri:
  - [ ] `beforeAll` → driver init çağrılır
  - [ ] `afterEach` → step logger reset
  - [ ] `afterAll` → driver teardown
  - [ ] Driver init failure → test fail, sonraki testler etkilenmez
- [ ] `TestFlyLauncherListener` testleri:
  - [ ] Suite start → report adapter'lara bildirim ulaşır (fake adapter)
  - [ ] Suite finish → metrics flush
  - [ ] Adapter exception → framework survive eder, log yazar
- [ ] `EnableTestFly` annotation testleri:
  - [ ] Annotation var → extension register edilir
  - [ ] Annotation yok → extension register edilmez
- [ ] `ReportPortalJUnit5Bridge` testleri:
  - [ ] RP config yoksa → no-op (exception yok)
  - [ ] RP config varsa → reflection ile listener register

**Acceptance:** `mvn test -Dtest='io.testfly.unit.junit5.*'` green.

---

### 1.3 — Framework Bootstrap Testleri (T-02)

**Neden:** `FrameworkBootstrap` init sırası bozulursa runtime'da patlar — ve şu an sıfır test.

**Yapılacaklar:**
- [ ] `src/test/java/io/testfly/unit/lifecycle/` dizini oluştur
- [ ] Happy path: config → SPI registries → hooks → ready
- [ ] Idempotency: ikinci `bootstrap()` çağrısı yeniden init yapmaz
- [ ] Missing `testfly.yml` → `ConfigurationException` (NPE değil)
- [ ] Invalid YAML → `ConfigurationException` with message
- [ ] Temp dir kullan, filesystem state leak yok

**Acceptance:** `mvn test -Dtest='io.testfly.unit.lifecycle.*'` green.

---

### 1.4 — Recording Testleri (T-03)

**Neden:** Recording failure = failure anında kayıp delil. Raporun güvenilirliği buna bağlı.

**Yapılacaklar:**
- [ ] `src/test/java/io/testfly/unit/recording/` dizini oluştur
- [ ] `RecordingManager`:
  - [ ] start/stop lifecycle
  - [ ] Output file naming (ReportPaths integration)
  - [ ] Recording disabled config → sıfır side effect
  - [ ] Disk write failure → graceful degradation (test fail, framework crash değil)
- [ ] `@TempDir` kullan, gerçek browser yok

**Acceptance:** `mvn test -Dtest='io.testfly.unit.recording.*'` green.

---

### 1.5 — Duplicate SessionCache Çözümü (T-07)

**Neden:** `browser.SessionCache` ve `precondition.SessionCache` — aynı isim, farklı semantics.

**Yapılacaklar:**
- [ ] Her iki sınıfı oku, semantics karşılaştır
- [ ] Farklı ise: `BrowserSessionCache` / `PreconditionSessionCache` olarak rename
- [ ] Public API'de ise: bir release `@Deprecated` alias tut
- [ ] Tüm usage'ları güncelle
- [ ] `CHANGELOG.md`'ye entry ekle

**Acceptance:** `grep -r "class SessionCache" src/` → tam 1 sonuç (veya 2 ama farklı isimlerle).

---

## Faz 2 — Test Piramidini Doldur (Önemli)

> Hedef: Kritik paketlerdeki test boşluklarını kapatmak, integration iskeleti kurmak.
> Tahmini süre: ~2-3 hafta
> Bağımlılık: Faz 1.1 (JaCoCo verisi ile önceliklendirme)

### 2.1 — Assertion Testleri

**Kapsam:** `LocatorAssert`, `SoftAssertions`, `SoftAssertionCollector`

- [ ] `LocatorAssert`: isVisible/isHidden/hasText/containsText/hasAttribute/count — her assertion için positive + negative case
- [ ] `SoftAssertions`:
  - [ ] Birden fazla failure → hepsi toplanır, tek error throw
  - [ ] Failure sırası korunur
  - [ ] Empty assertion list → no-op
- [ ] `SoftAssertionCollector`: thread-safety (parallel test senaryosu)

---

### 2.2 — Browser Paket Boşlukları

**Kapsam:** `ConsoleErrorCollector`, `DownloadManager`, `DeviceEmulator`

- [ ] `ConsoleErrorCollector`: fake CDP events → log entries toplanır, failOnConsoleErrors modu
- [ ] `DownloadManager`: `@TempDir` ile dosya poll, timeout path, empty dir case
- [ ] `DeviceEmulator`: profile application (viewport, user agent, device scale)

---

### 2.3 — Cucumber Bridge Testleri

**Kapsam:** `CucumberRetryContext`, `CucumberStepLogger`, `CucumberContext`

- [ ] Glue code'u doğrudan instantiate et (feature file çalıştırmaya gerek yok)
- [ ] Retry context: scenario retry → state doğru resetlenir
- [ ] Step logger: step start/end → StepLogger entegrasyonu
- [ ] Context: scenario-scoped state isolation

---

### 2.4 — Precondition Testleri

**Kapsam:** `ApiHealthChecker`, `PreConditionRunner`, `PreConditionRegistry`, `SessionCache`

- [ ] `PreConditionRunner`: failing precondition → dependent test skip edilir
- [ ] `PreConditionRegistry`: unknown condition → error (silent skip değil)
- [ ] `SessionCache`: cache hit/miss/expiry
- [ ] `ApiHealthChecker`: endpoint reachable/unreachable senaryoları

---

### 2.5 — Listeners Testleri

**Kapsam:** `TestExecutionListener`, `SuiteListener`

- [ ] `TestExecutionListener`:
  - [ ] onTestSuccess → metrics güncellenir
  - [ ] onTestFailure → screenshot tetiklenir, metrics güncellenir
  - [ ] onTestSkipped → skip count artar
- [ ] `SuiteListener`: onStart/onFinish → hook chain çağrılır

---

### 2.6 — Integration Test İskeleti (T-04, T-05)

**Neden:** Email, DB, TestRail/Xray client'ları network/IO — unit mock'lar yetmez.

**Yapılacaklar:**
- [ ] `src/test/java/io/testfly/integration/` altında yapı kur
- [ ] JUnit `@Tag("integration")` ile tag'le, surefire'dan exclude et
- [ ] `-Pintegration` profile ile çalıştırılabilir yap
- [ ] **Email** (T-04):
  - [ ] Mailhog/Mailtrap request construction (auth headers, query params) → HTTP stub
  - [ ] ImapProvider: basic connection + search (embedded server varsa)
- [ ] **DB** (T-05):
  - [ ] H2 in-memory ile DbClient end-to-end
  - [ ] Connection factory config parsing
  - [ ] DbAssertException on mismatch
- [ ] **TestManagement** (T-05):
  - [ ] TestRail/Xray REST stub → payload mapping, 4xx/5xx handling

**Acceptance:**
- `mvn test` → integration testler çalışmaz (exclude)
- `mvn verify -Pintegration` → integration testler çalışır, green

---

## Faz 3 — Code Hygiene & DevX (Orta)

> Hedef: Teknik borç temizliği, developer experience iyileştirmesi.
> Tahmini süre: ~1 hafta

### 3.1 — Demo Test İzolasyonu (T-08)

- [ ] `examples/` ve `tdd/` testlerini `@Tag("demo")` ile tag'le
- [ ] Surefire config'de `demo` tag'ini exclude et
- [ ] `-Pdemo` profile ekle → demo testleri çalıştırır
- [ ] `docs/getting-started.md`'ye "demo testleri çalıştırma" bölümü ekle

**Acceptance:** `mvn test` → network/browser bağımlılığı yok, tamamen deterministik.

---

### 3.2 — Dosya İsmi Düzeltmesi (T-10)

- [x] `implementation-status.md` rename (done)
- [ ] Tüm referansları güncelle (`feature.md`, `README.md`, `AGENTS.md`, vs.)
- [ ] Root `CONTRIBUTING.md` vs `docs/CONTRIBUTING.md`: tek kaynak, diğeri link

---

### 3.3 — Visual Baseline Politikası (T-11)

- [ ] `src/test/resources/baselines/` için policy dokümanı yaz
- [ ] `VisualAssert`'a `-Dvisual.updateBaselines=true` flag ekle (yoksa)
- [ ] Unit test: flag aktif → baseline güncellenir

---

### 3.4 — Coverage Threshold (T-13)

**Bağımlılık:** Faz 1.1 (JaCoCo) + Faz 2 tamamlanması

- [ ] `BuildThresholdEnforcer`'ı JaCoCo output'una bağla
- [ ] Kritik paketler için minimum floor belirle:
  - `junit5`: ≥%70
  - `lifecycle`: ≥%80
  - `recording`: ≥%70
  - `healing`: ≥%60
- [ ] "Ratchet up, never down" politikası

---

## Faz 4 — Dokümantasyon (Devam Eden)

> Hedef: Türkçe doküman açığını kapatmak, EN dokümanı güncel tutmak.
> Tahmini süre: ~2-3 hafta (paralel yürüyebilir)

### 4.1 — Türkçe Doküman Çevirisi

**Kaynak:** `docs-site/tr-i18n-gaps.md`

| Öncelik | Kapsam | Belge Sayısı | Tahmini Satır |
|---------|--------|-------------|---------------|
| P0 | Ana sayfa artıkları (index.js) | 1 dosya | ~5 satır |
| P1 | `why/` kalan belgeler | 4 | ~600 |
| P1 | `guides/` core rehberler | 18 | ~4,500 |
| P2 | `recipes/` | 9 | ~1,800 |
| P2 | `migration/` + `extensibility/` + `reporting/` | 12 | ~2,000 |
| P3 | Kök seviye kavramsal belgeler | 12 | ~2,800 |

**Kurallar:**
- Frontmatter `id`/`slug`/`sidebar_position` korunur, sadece `title`/`description` çevrilir
- Kod blokları, path'ler, inline code çevrilmez
- `tr-i18n-gaps.md` §3'teki terim sözlüğü takip edilir

### 4.2 — Ana Sayfa İngilizce Artıkları

- [ ] `index.js` (TR): "Get Started" → "Başlayın"
- [ ] `index.js` (TR): "View on GitHub" → "GitHub'da Görüntüle"
- [ ] `index.js` (TR): "Read the guide" → "Rehberi Oku"
- [ ] `index.js` (TR): satır 319 İngilizce paragraf → Türkçe çeviri

---

## Backlog — MCP (Beklemede)

> TestFly MCP server şu an "coming soon" olarak işaretlendi. Release zamanı geldiğinde:
>
> - Docs-site'a MCP sayfası ekle (sidebar'a da)
> - `docs/mcp-codegen-contract.md`'yi "active" olarak güncelle
> - README ve marketing materyallerini "available" olarak güncelle
> - PyPI badge'i geri ekle
> - `codegen_tools.py` ile `@TestFlyApi` surface'ını senkron kontrol et

**Status:** Backlog — aktif faz değil, MCP release kararı beklenecek.

---

## Özet: Faz Sıralaması ve Bağımlılıklar

```
FAZ 1 — Temel Sağlamlık (Kritik)          ~2 hafta
├── 1.1 JaCoCo default build              [30 dk]
├── 1.2 JUnit 5 wiring tests              [3 gün]
├── 1.3 Bootstrap tests                   [2 gün]
├── 1.4 Recording tests                   [2 gün]
└── 1.5 SessionCache cleanup              [1 gün]

FAZ 2 — Test Piramidi (Önemli)            ~2-3 hafta
├── 2.1 Assertion tests                   [2 gün]
├── 2.2 Browser tests                     [2 gün]
├── 2.3 Cucumber bridge tests             [2 gün]
├── 2.4 Precondition tests                [2 gün]
├── 2.5 Listener tests                    [1 gün]
└── 2.6 Integration test skeleton         [3 gün]

FAZ 3 — Hygiene & DevX (Orta)             ~1 hafta
├── 3.1 Demo test isolation               [1 gün]
├── 3.2 File rename + doc alignment       [2 saat]
├── 3.3 Visual baseline policy            [1 gün]
└── 3.4 Coverage threshold                [1 gün]

FAZ 4 — Dokümantasyon (Paralel)           ~2-3 hafta
├── 4.1 Türkçe çeviriler (59 belge)       [devam eden]
└── 4.2 Ana sayfa artıkları               [1 saat]

BACKLOG — MCP                              [Beklemede]
```

---

## Başarı Kriterleri

| Kriter | Mevcut | Hedef |
|--------|--------|-------|
| Unit test dosyası | 49 | ~80+ |
| Sıfır testli paket | 13 | 0 |
| JaCoCo default | ❌ | ✅ |
| Integration skeleton | 1 test | Tag-based, profile-gated |
| `mvn test` determinism | ⚠️ Demo'lar network'e bağımlı | ✅ Tamamen isolated |
| Duplicate SessionCache | 2 adet | 0 (rename ile) |
| TR doküman | 4/63 | 63/63 |
| Coverage threshold | Yok | Enforcer ile ratchet |

---

## Notlar

- **CHANGELOG.md** ve tarihi kayıtlara dokunulmaz.
- Her faz bağımsız merge edilebilir — PR'lar faz başına açılabilir.
- Faz 4 (TR çeviri) diğer fazlarla paralel yürüyebilir, blocking dependency yok.
- MCP backlog'ta — release kararı geldiğinde aktif faza alınır.
