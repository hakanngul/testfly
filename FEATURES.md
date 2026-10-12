# TestFly — Kapsamlı Özellik Tablosu

> Kaynak: `README.md > What You Get Out of the Box`, `src/main/java/io/testfly/*`, `docs-site/docs/*`, `docs-site/src/data/homeData.js (getFlagshipFeatures + getMoreFeatures)`. Sürüm: `1.0.7`.
> Bu dosya, SmartLocator / SelfHealing dahil SDK'nın kutudan çıkan tüm yeteneklerinin tek dosyalık envanteridir.

## 1. Locator & Self-Healing

| # | Özellik | Ne Yapar | API / Config |
|---|---|---|---|
| 1 | **Semantic / Accessibility-First Locators** | `getByRole(36 ARIA rolü)`, `getByLabel`, `getByText`, `getByPlaceholder`, `getByTestId`, `getByAltText`, `getByTitle`. Case-insensitive substring default, `.exact()` opt-in. `toBy()` ile Selenium `By`'ye dönüşür. | `BasePage#getByRole()`, `Locator`, `Role` |
| 2 | **SmartLocator** | Çoklu stratejiyi sırayla dener, ilk bulunan + görünür elementi döndürür. CSS/DOM refactor'larına dayanıklı. | `io.testfly.test.SmartLocator` |
| 3 | **Self-Healing Locator (Statik/Fuzzy)** | Kırık seçiciden `id/class/name/text` fragmanı çıkarıp retry yapar. `FuzzyHealingEngine` + kural tabanlı fallback. | `SelfHealingLocator`, `locators.selfHealing: true` |
| 4 | **AI Self-Healing** | Statik onarım yetmezse LLM ile yeni locator sentezler. `DomPruner` DOM'u `<8K token`'a budar. | `AiHealingEngine`, `DomPruner`, `locators.aiHealing + maxDomTokens: 8000` |
| 5 | **Healing Kalıcılığı & Raporu** | Onarım loglanır, raporlanır. | `HealingCache`, `HealLog`, `HealEvent`, `target/healed-locators.json` |
| 6 | **byIntent (Dinamik Semantik Niyet)** | `byIntent("Proceed to payment").click()` — metin/niyetten locator. | `byIntent()` |

## 2. Wait / Assertion / Retry / Kararlılık

| # | Özellik | Ne Yapar | API / Config |
|---|---|---|---|
| 7 | **WaitEngine (Auto-Wait)** | `Thread.sleep()`'i bitirir. 10+ built-in condition, tüm `Locator` akışı otomatik bekler. | `WaitEngine`, `timeouts.explicit/pageLoad` |
| 8 | **Web-First Assertions** | `assertThat(el).isVisible()`, `assertThatPage().hasTitle/hasUrl` vb. | `SeleniumAssert`, `LocatorAssert`, `PageAssert`, `ElementAssert` |
| 9 | **Semantic AI Assertions** | `satisfiesAi("...")` / `violatesAi("...")` — LLM muhakemesi, DOM başına tek değerlendirme (anti-throttle). Soft assert desteği. | `assertThatPage().satisfiesAi()` |
| 10 | **Soft Assertions** | Toplu doğrulama, sonunda raporla. | `SoftAssertions`, `SoftAssertionCollector` |
| 11 | **Retry** | Flaky testi otomatik yeniden dener. | `@Retryable`, `retry.enabled/maxAttempts` |
| 12 | **Flakiness Radar + Smart Triage** | Son `N` koşudan `STABLE/WATCH/HIGH` sınıflandırma. HTML'de Radar kartı + `flakiness-report.json`. | `SmartTriageEngine`, `flakiness.*`, `target/metrics-history/` |
| 13 | **Quarantine** | Seçili testleri atla. | `testfly-quarantine.yml`, `QuarantineLoader` |
| 14 | **Smart Test Sharder** | Geçmiş metriklere göre testleri dengeli dağıtır. | `SmartTestSharder`, `@TestWeight` |

## 3. Web UI Çekirdeği

| # | Özellik | Ne Yapar | API / Config |
|---|---|---|---|
| 15 | **Zero-Boilerplate Lifecycle** | Driver setup/teardown yok. `BaseTest` yönetir. | `BaseTest`, `BasePage`, `@NoBrowser`, `FrameworkBootstrap` |
| 16 | **Wait-Backed Eylemler** | `click/type/getText/isDisplayed`, iFrame helper, file upload. | `BasePage` |
| 17 | **Zor Senaryo Tarifleri** | iFrame, Shadow DOM, Table, Alert, Drag&Drop, Infinite Scroll, OAuth/SSO, Download PDF, Upload File. | `ShadowDom`, `DownloadManager`, `recipes/*` |
| 18 | **DownloadManager** | Download klasörünü poll eder, `.part/.crdownload`'ı yönetir. | `DownloadManager` |
| 19 | **ConsoleErrorCollector** | Kullanıcının göreceği JS hatalarını yakalar (Chrome logs, Firefox shim). | `browser.captureConsoleErrors: true` |
| 20 | **Storage / Session / Cihaz** | Cookie/storage inject, clipboard, geolocation, mobil emülasyon. | `StorageHelper`, `SessionCache`, `ClipboardHelper`, `DeviceEmulator/DeviceProfiles`, `GeoLocation`, `BrowserContext` |
| 21 | **Screenshot + StepLogger** | Failure'da otomatik screenshot; isimli adımlar + timestamp + per-step screenshot timeline. | `ScreenshotManager`, `StepLogger` |
| 22 | **Video Recording** | Chromium'da CDP, diğerlerinde screenshot fallback. Fail'leri MP4/GIF sakla. | `RecordingManager`, `Mp4Encoder/GifEncoder`, `recording.*` |
| 23 | **TraceRecorder + Metrics** | Adım izi + süre metrikleri, rapor timeline. | `TraceRecorder`, `ExecutionMetrics`, `TestTiming` |

## 4. API Testing

| # | Özellik | Ne Yapar | API / Config |
|---|---|---|---|
| 24 | **Fluent ApiClient** | `BaseApiTest` + zincirlenebilir HTTP client, JSONPath extract, polling, async-batch. | `ApiClient`, `ApiRequest/ApiResponse`, `ApiBatchRunner` |
| 25 | **Auth & Interceptor & SSL** | Bearer/Basic/OAuth, request/response interceptor, SSL güvensiz sertifika. | `ApiAuth`, `ApiInterceptor`, `api.ssl.*` |
| 26 | **Schema + Performans Doğrulama** | JSON Schema validation, SLA/response-time assertion. | `api-schema-validation`, `api-performance` |
| 27 | **Mocking** | `ApiMockServer` (WireMock opsiyonel) + CDP network intercept/mock. | `ApiMockRule`, `NetworkMock/StubBuilder/Route`, `network.interceptEnabled` |
| 28 | **Hybrid UI+API + Context** | Aynı suitte UI+API, thread-safe veri paylaşımı. | `ScenarioContext/SuiteContext`, `PreConditionRunner` |
| 29 | **@PreCondition Session Cache** | Login'i bir kez yap, cookie+localStorage'ı thread-local'da sakla/geri yükle. | `@PreCondition`, `PreconditionSessionCache` |

## 5. Load & Performance

| # | Özellik | Ne Yapar | API / Config |
|---|---|---|---|
| 30 | **Declarative Load Test** | UI/API testlerinin yanında yük testi. Gatling veya JDK virtual-thread motoru, feeder, SLA `assertErrorRateBelow` vb., HTML/Allure/ReportPortal raporu. | `loadtest.*`, `LoadStep`, `loadtest.enabled/engine` |

## 6. AI / Agentic Runtime (Built-in)

| # | Özellik | Ne Yapar | API / Config |
|---|---|---|---|
| 31 | **act(goal)** | `act("Delete first item in cart")` → doğal dili deterministik Selenium adımına derler. | `agent.ActionCompiler/ActionExecutor/ActionPlan` |
| 32 | **Compile & Freeze Cache** | Planı `.testfly/action-cache.json`'a dondurur, sonraki koşuda **0ms replay**, LLM çağrısı yok. | `ActionCache` |
| 33 | **AI Failure Analysis** | Hata + stack + step log + URL/title ile kök neden + fix önerisi, HTML rapora gömülür. Non-blocking, timeout korumalı. | `AiFailureAnalyzer`, `ai.failureAnalysis/model/timeoutSeconds/apiKey` |
| 34 | **Auto-PR Patch** | Kalıcı failure'da Unified Git Diff üretir, `git apply` ile uygulanır. | `RemediationPatchGenerator`, `SourceCodeLocator`, `target/remediations/*.patch` |
| 35 | **Çoklu AI Provider** | `claude/anthropic/gemini/openai/openai-compatible` (DeepSeek, Qwen, Groq, Ollama `baseUrl` ile). | `AiProviderRegistry`, `ai.provider/baseUrl` + `ai.enabled` kill-switch + `features.ai` |

## 7. Veri / DB / E-posta / Görsel / Zaman / Ağ

| # | Özellik | Ne Yapar | API / Config |
|---|---|---|---|
| 36 | **External Test Data** | Excel/CSV/JSON data-driven, `TestDataStore`. | `TestData/TestDataLoader/ExcelDataReader`, `@TestData` |
| 37 | **DbClient** | JDBC ile parametreli SQL + satır doğrulama. | `DbClient`, `DbAssertException` |
| 38 | **Email & OTP** | MailHog/Mailtrap/Outlook Graph/IMAP'tan OTP/link okuma + bekleme. | `MailboxClient`, `Email/EmailCriteria` |
| 39 | **Accessibility (axe-core)** | Tek satır: `accessibility().withTags("wcag2a").run()`. axe JAR içinde, ek bağımlılık yok. | `AccessibilityChecker/AccessibilityAssert` |
| 40 | **Visual Regression** | Onaylı baseline ile piksel farkı + tolerans. | `VisualAssert`, `VisualTolerance`, `visual.*` |
| 41 | **TestClock** | Client `Date` saatini mocklar (CDP'ye bağlı kalıcılık). | `TestClock`, `clock-mocking` |
| 42 | **Network Assert** | Yakalanan istekleri doğrulama. | `NetworkAssert`, `RecordedRequest` |

## 8. Execution / Paralel / CI / Rapor / Genişletilebilirlik

| # | Özellik | Ne Yapar | API / Config |
|---|---|---|---|
| 43 | **Thread-Safe Parallel** | Thread-isolated driver + semaphore slot (`maxActiveSessions`, `sessionWaitSeconds`), `parallel: methods/classes` tek satır. `per-test/per-suite` lifecycle. | `DriverManager`, `execution.parallel/threadCount` |
| 44 | **Remote & Cloud** | Selenium Grid + BrowserStack + Sauce Labs provider. | `execution.mode: remote/gridUrl`, `DriverProviderFactory/Registry` |
| 45 | **Multi-Framework Adapter** | TestNG + JUnit5 + Cucumber BDD aynı lifecycle. | `BaseTest`, `junit5.*`, `cucumber.*`, `MultiSessionManager` |
| 46 | **Self-Configuring CI** | GitHub/Jenkins/CircleCI/Bitbucket auto-detect, headless zorlar, CPU'ya göre thread, Docker flag, JUnit XML. Quality gate: `failOnPassRateBelow/maxFlakyTests/failOnHighFlakiness`. CI metadata rapora işlenir. | `CiEnvironmentDetector`, `CiMetadata`, `BuildThresholdEnforcer`, `ci.*` |
| 47 | **Raporlama** | Paydaş dostu HTML dashboard (gauge, donut, slowest, timeline, dark mode) + JUnit XML + Allure + ReportPortal + Slack/e-posta notification + TestRail/Xray senkronu. | `HtmlReportGenerator`, `JUnitXmlReporter`, `AllureReportAdapter`, `ReportPortal*`, `NotificationAdapter`, `TestRailClient/XrayClient` |
| 48 | **SPI Plugin Sistemi** | Fork'suz genişletme: custom driver, report adapter, lifecycle hook, tek deploy birimi + `minFrameworkVersion()` guard. | `TestFlyPlugin/PluginRegistry`, `ReportAdapterRegistry`, `TestFlyHook` |
| 49 | **Config Sistemi** | Tek `testfly.yml` + env profil (`-Dtestfly.profile`), `${VAR}` + `.env`, `features:` master on/off paneli, fail-fast validasyon. | `ConfigurationLoader/TestFlyConfig/TestFlyDefaults/FeatureGate` |

## 9. Companion (SDK Dışı — Ayrı Projeler)

| # | Özellik | Durum |
|---|---|---|
| 50 | **TestFly MCP Bridge (Node)** | Ayrı repo `hakanngul/testfly-mcp`. 6 araçla scaffold + gözlemlenen adımdan Java üretimi. Canlı browser için Playwright MCP gerekir. `testfly record/studio` yok. |
| 51 | **TestFly CLI** | Ayrı Node köprüsünde `init/--version/--help` |
| 52 | **IDE Pluginleri + Recorder/Studio/Auto-POM** | Ayrı IntelliJ/VS Code eklentileri. Dökümanda `recorder/interactive-studio/auto-pom/prompt-recipes` anlatılır. |

> Örnek istediğin ikili: `SmartLocator (#2)` = deneme-yanılma çoklu strateji, `SelfHealing (#3-5)` = kırık locator'ı kural + fuzzy + gerekirse LLM (`#4`) ile kurtarıp loglama.


