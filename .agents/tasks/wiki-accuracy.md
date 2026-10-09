# Wiki Doğruluk Ölçümü — `.agents/wiki/` (18 sayfa)

Ölçüm tarihi: dal `development`, HEAD `4fef6d0` (brief `e47e75a` demişti, worktree o sırada ilerlemiş). `git status` temiz, hiçbir dosyaya dokunulmadı. pom.xml sürümü **1.0.7**.

---

## A. SONUÇ (tek paragraf)

Kontrol edebildiğim **178 iddianın %80,9'u doğru (144), %11,8'i yanlış (21), %2,2'si eskimiş (4), %5,1'i doğrulanamadı (9)**; kod tabanının **dosya sayısına göre ~%19'unu (48/247 dosya; sayfalarda yazıldığı hâliyle anlatılan 5 sınıf daha eklenirse ~%21), satır sayısına göre ~%30'unu** kapsıyor. Yani wiki "söylediği şeylerin çoğunda haklı ama az şey söylüyor": doğru kısımlar çoğunlukla "bu sınıf var" türünden basit iddialar; hatalar ise ajanın **gerçekten yanlış iş yapmasına** yol açacak yerlerde (karantina YAML şeması, olmayan `SchemaAssert`/`ResponseAssert`, olmayan "88 MCP aracı" ve `testfly-mcp doctor/ui`, olmayan K6 motoru, test yönetimine "ekran görüntüsü" gönderildiği iddiası). **Tavsiye: wiki'yi olduğu gibi tutma. `docs-site/docs` (EN/TR) ve `.agents/skills/testfly/SKILL.md` aynı konuları kodla daha uyumlu anlatıyor.** Önce 3 sayfayı sil/yönlendir (`quarantine-engine`, `ai-mcp-automation`, `roadmap`), 6 sayfayı küçük düzeltmeyle tut (`api-testing`, `load-testing`, `test-management`, `ci-quality-gates`, `webui-testing`, `index`), kalan 9 sayfa zaten doğru (ama kısa; istersen `docs-site` ile birleştir). Tümünü silip sıfırdan yazmaya gerek yok; ama hiçbir sayfa "otomatik güncel" değil: 11 sayfa git'te 2026-09-10'dan beri hiç değişmedi, kodda ise wiki'de adı bile geçmeyen 18 paket var (bkz. E).

---

## B. Toplam tablo

| Karar | Adet | % |
|---|---:|---:|
| DOĞRU | 144 | 80,9 |
| YANLIŞ (kodla çelişiyor / hiç var olmamış) | 21 | 11,8 |
| ESKİMİŞ (eskiden doğruydu) | 4 | 2,2 |
| DOĞRULANAMADI | 9 | 5,1 |
| **Toplam** | **178** | 100 |

Not: Bazı hatalar sayfalar arasında **tekrar ediyor** (K6: `load-testing`, `index`, `MAP.md`, `api-webui-testing:39`; GraphQL: `api-testing`, `index`). Her sayfa kendi iddiasıyla sayıldı; tekrarlar toplamı yaklaşık 3 iddia şişiriyor.

Kuralım: bir özelliği "devrede" diye anlatıp varsayılanı kapalıysa (ör. self-healing, cURL log) YANLIŞ saydım; sınıfın var olup ayrıntısı eksikse DOĞRU saydım, eksikliği notladım.

---

## C. Sayfa tablosu

| Sayfa | İddia | Doğru | Yanlış | Eskimiş | Doğrulanamadı | Güvenilirlik | Yargı |
|---|---:|---:|---:|---:|---:|---:|---|
| architecture | 17 | 16 | 1 | 0 | 0 | %94 | DÜZELT (tek cümle: "Asla Thread.sleep") |
| configuration | 4 | 4 | 0 | 0 | 0 | %100 | KORU — ama 23 config bloğundan sadece 3'ünü anlatıyor |
| webdriver-lifecycle | 7 | 7 | 0 | 0 | 0 | %100 | KORU |
| webui-testing | 14 | 12 | 1 | 0 | 1 | %86 | DÜZELT (self-healing varsayılan kapalı) |
| assertion-system | 7 | 7 | 0 | 0 | 0 | %100 | KORU |
| ci-quality-gates | 8 | 6 | 1 | 0 | 1 | %75 | DÜZELT (`GITHUB_REF`, Allure) |
| api-testing | 26 | 18 | 4 | 0 | 4 | %69 | DÜZELT (SchemaAssert, ResponseAssert, GraphQL, cURL) |
| api-webui-testing | 7 | 7 | 0 | 0 | 0 | %100 | KORU |
| load-testing | 9 | 8 | 1 | 0 | 0 | %89 | DÜZELT (K6 yok) |
| ai-mcp-automation | 6 | 2 | 0 | 3 | 1 | %33 | **SİL** → `docs-site/docs/cli.md` + `ai/testfly-mcp.md` |
| accessibility-visual-testing | 11 | 11 | 0 | 0 | 0 | %100 | KORU |
| cucumber-bdd | 6 | 6 | 0 | 0 | 0 | %100 | KORU |
| quarantine-engine | 10 | 3 | 6 | 0 | 1 | **%30** | **SİL** → `docs-site/docs/quarantine.md` |
| spi-extensions | 10 | 10 | 0 | 0 | 0 | %100 | KORU |
| test-management | 7 | 6 | 1 | 0 | 0 | %86 | DÜZELT (ekran görüntüsü yok) |
| memory-system (meta) | 7 | 5 | 1 | 0 | 1 | %71 | DÜZELT (küçük) |
| index (meta) | 9 | 5 | 3 | 1 | 0 | %56 | DÜZELT (K6, GraphQL, roadmap eksik) |
| roadmap (meta) | 13 | 11 | 2 | 0 | 0 | %85 | SİL → kök `ROADMAP.md` zaten tek kaynak |
| **Toplam** | **178** | **144** | **21** | **4** | **9** | **%80,9** | |

Hiçbir sayfada 3'ten az iddia çıkmadı, "yetersiz veri" yazmam gerekmedi. Ancak `configuration` (4 iddia) ve `webdriver-lifecycle` (7) %100 çıkmasına aldanma: sayfalar kısa olduğu için hata yapacak yer az; kapsam sorunu başka (bkz. E).

---

## D. En kritik YANLIŞ / ESKİMİŞ iddialar (sıralı, 22 madde + 3 ek not)

Satır numaraları wiki dosyalarındandır.

| # | Sayfa:satır | Wiki ne diyor | Kod / kanıt ne diyor | Ajan güvenirse ne ters gider |
|---|---|---|---|---|
| 1 | quarantine-engine:22-31 | `testfly-quarantine.yml` içinde `quarantine.enabled`, `cucumberTag: "@quarantine"`, `tests:[{className, methodName, reason, owner}]` | `QuarantineLoader.java:240-255` yalnızca `quarantine:` altındaki **liste**yi okur: düz string (`pkg.Class#metot`) ya da `{test:, reason:}`. `className/methodName/owner` anahtarı kodda yok. `enabled` ve `cucumberTag` `testfly.yml`'de (`TestFlyConfig.java:1646-1653`), etiket varsayılanı `"quarantine"` (başında `@` yok). Doğru örnek: `docs-site/docs/quarantine.md:20-27` | Ajan wiki şemasını yazar, hiçbir test karantinaya alınmaz, hata sessiz kalır. |
| 2 | quarantine-engine:35-36 | Karantinadaki testler CI'da **çalışmaya devam eder**, rapor `QUARANTINED` der | `TestExecutionListener.java:332-343` → `throw new SkipException("[Quarantined] …")`: test **hiç çalışmaz, atlanır**. `grep QUARANTINED src/main` → 0 sonuç. SKILL.md:394 de "SkipException" diyor | Ajan "düzelince fark ederiz, koşuyor" diye düşünür; test aslında hiç koşmuyor, düzelme izlenemez. |
| 3 | quarantine-engine:58 | Geçmiş `target/flakiness-history.json`'dan okunur | `FlakinessAnalyzer.java:20-26`: `target/metrics-history/` altındaki son N JSON; çıktı `target/flakiness-report.json`. `flakiness-history` kodda yok | Ajan olmayan dosyayı arar/oluşturur, analiz boş çalışır. |
| 4 | quarantine-engine:59-61 | Risk 0.0-0.2 / 0.2-0.5 / 0.5+ skor | `FlakinessScore.java:10-21`: yüzde bazlı; `STABLE <10`, `WATCH 10..33`, `HIGH ≥ highRiskThreshold` (varsayılan **33.0**, `TestFlyConfig.java:1224`) | Yanlış eşik yazılır/yorumlanır. |
| 5 | api-testing:26 | `SchemaAssert` ile JSON Schema doğrulanır | Böyle bir sınıf **hiç yok** (`git log -S"class SchemaAssert"` boş). Gerçek: `ApiResponse.assertSchema(String)` (`ApiResponse.java:261`), `ApiResponseSpec.schema(...)`, paket-içi `SchemaValidator`, `assertOpenApi` (`:272`) | Ajan `import …SchemaAssert` yazar, derleme hatası. |
| 6 | api-testing:27 | `ResponseAssert` ile durum kodu/JsonPath/SLA | Sınıf **hiç yok** (tek iz: `ApiResponseAssertionsTest`, commit `cfa3851`). Gerçek: `ApiResponse.assertStatus/assertJson/assertDurationLessThan/assertHeader…` (`ApiResponse.java:220-367`) | Aynı: derlenmeyen kod üretilir. |
| 7 | api-testing:13, index:25 | "REST & **GraphQL**" | `grep -i graphql` → `src/main`, `docs-site/docs`, `README.md`'de **0 sonuç** | Ajan GraphQL için hazır yardımcı sanır, uydurma API çağırır. |
| 8 | api-testing:28 | `StepLogger` cURL + istek gövdesi + yanıt detayını rapora işler | cURL ve body **varsayılan kapalı**: `api.logCurl=false`, `api.logBody=false` (`TestFlyConfig.java:~1331-1336`); `ApiClient.java:818-851` yalnızca bayrak açıksa yazar | Ajan raporda cURL bekler, bulamaz; config'i açması gerektiğini bilmez. |
| 9 | ai-mcp-automation:21 | "88 standart MCP aracı" | `testfly-mcp/bin/testfly-mcp.js` **6 araç** tanımlar (`generate_testfly_code`, `inspect_action_cache`, `manage_action_cache`, `list_remediations`, `apply_remediation_patch`, `init_testfly_project`). `docs-site/docs/cli.md:58` açıkça "88 araç yok" diyor. 88 sayısı eski Python tasarımından (`ai/interactive-studio.md:10` bunu "tarihsel" diye işaretliyor) | Ajan olmayan araçları çağırmaya çalışır. **ESKİMİŞ** |
| 10 | ai-mcp-automation:31 | `testfly-mcp doctor` | Bridge'de `doctor` yok; `cli.md`: yalnızca `init`, `--version`, `--help` | Komut hata verir. **ESKİMİŞ** |
| 11 | ai-mcp-automation:34 | `testfly-mcp ui` | Yok (`interactive-studio.md:10`: "`testfly ui` … ship etmiyor") | Aynı. **ESKİMİŞ** |
| 12 | load-testing:13 (+ index:28, MAP:54, api-webui:39, diyagram adı) | "K6 ve Gatling" yük testi | `src/main` ve `docs-site/docs/loadtest/*` içinde k6 **yok**. Motorlar: `JdkLoadEngine` (varsayılan) + `GatlingEngine` (`loadtest/internal/`) | Ajan K6 motoru seçmeye/kurmaya çalışır. |
| 13 | test-management:58 | Durum, hata mesajı **ve ekran görüntüsü eki** "anında" iletilir | `testmanagement/` altında attachment/screenshot kodu yok. `TestManagementReporter.java:112-124,132`: TestRail anında, **Xray suite sonunda toplu** (`xrayBuffer`). `docs-site/docs/test-management.md` ekran görüntüsünden bahsetmiyor | Ajan "kanıt otomatik ekleniyor" diye çalışır, Jira/TestRail'de ek yok. |
| 14 | webui-testing:26 | Eleman bulunamazsa `FuzzyHealingEngine` devreye girer | `locators.selfHealing` varsayılan **false** (`TestFlyConfig.java:1024`); `FuzzyHealingEngine` yalnızca `SelfHealingLocator` içinden, o da `WaitEngine`'den ve bayrak açıksa çağrılır. SKILL.md:129-135 4 katmanı ve `aiHealing: true` şartını doğru anlatıyor | Ajan healing'in hep açık olduğunu varsayıp kırılgan seçici bırakır. |
| 15 | architecture:26 | "Asla `Thread.sleep()` kullanılmaz" | `WaitEngine.java:68` kendisi kullanıyor; ayrıca `Route.java:141`, `NetworkMock.java:627`, `JdkLoadEngine.java:147,422,425`, `DownloadManager.java:116`, `MailboxClient.java:94`. SKILL.md:31 doğru: "tek istisna framework-internal polling" | Düşük risk; ajan "kod tabanında hiç yok" diye grep'le doğrulamaya çalışırsa şaşırır. |
| 16 | ci-quality-gates:34 | GitHub için `GITHUB_REF` | `CiEnvironmentDetector.java:123-126` `GITHUB_HEAD_REF` / `GITHUB_REF_NAME` kullanıyor, `GITHUB_REF` yok | Düşük; yanlış değişkenle test yazılır. |
| 17 | roadmap:18 | "Tamamlanan fazlar (v0.1 – **v1.1**)" | Yayınlanan sürüm 1.0.7 (`pom.xml:9`); 1.1.0 yayınlanmadı (yalnız `@TestFlyApi(since="1.1.0")`, 50 satır/21 dosya) | Ajan 1.1.0'ı çıkmış sanır. |
| 18 | roadmap:31 | `testfly init` CLI ✅ | `testfly` diye komut yok; paket `bin`'leri `testfly-mcp`/`testfly-bridge` (`testfly-mcp/package.json`), komut `testfly-mcp init`. Kök `ROADMAP.md`'de bu satır yok | Ajan `testfly init` çalıştırır, "command not found". |
| 19 | memory-system:34 | "Her referans `[[wikilink]]`" | 5 yerde `file:///Users/hagul/...` mutlak yol, 2 yerde `../../ROADMAP.md`; `[[AGENTS]]`, `[[ROADMAP]]` vault (`.agents/.obsidian`) dışında | Obsidian'da kırık link; başka makinede/worktree'de açılmaz. |
| 20 | index:(tüm sayfa) | Wiki dizini | 18 sayfadan `roadmap.md` listede **yok** (16 sayfa + kendisi). `MAP.md` listeliyor | Roadmap'e giden tek yol MAP olur, tutarsız. |
| 21 | index:28 | `load-testing` = "K6 ve Gatling" | Bkz. #12 | — |
| 22 | index:25 | `api-testing` = "REST & GraphQL" | Bkz. #7 | — |

Ek notlar (sayıma girmeyenler):
- **Mutlak yol bağları**: `architecture:34`, `webui-testing:36`, `api-testing:~27`, `api-webui-testing:27`, `load-testing:29` hepsi `file:///Users/hagul/Projects/TestFramework/testfly/docs-site/...` (ana checkout). Dosyalar var (`ls` ile doğruladım) ama başka makine, CI ve worktree'de çalışmaz.
- **`AuthStrategy`** (`api-testing:22`) gerçek: `TestFlyConfig.Api.AuthStrategy` (`TestFlyConfig.java:1995`), yani `testfly.yml` içindeki `api.auth.<ad>` POJO'su. Ajanın yazacağı akıcı API `ApiAuth` (`ApiAuth.bearerToken/oauth2…`) ve `@UseAuth`. Wiki "sınıf" gibi anlatıyor; DOĞRU saydım ama yanıltıcı.
- **`SchemaAssert`/`ResponseAssert`'in gerçeği**: yeniden adlandırılmadı, silinmedi; hiç var olmadılar. En yakın tarihî iz: commit `cfa3851` mesajındaki "ApiResponseAssertions" ve `ApiResponseAssertionsTest`.
- **Sürüm**: `AGENTS.md`, `README.md`, `CHANGELOG.md` artık hepsi 1.0.7 (brief'teki "1.1.0" tutarsızlığı bu HEAD'de yok). Kalan tek 1.1.0 izi: kodda 50 `@TestFlyApi(since = "1.1.0")` ve `api-testing.md` §3 başlığı "(1.1.0 API sözleşmesi)".

---

## E. Paket kapsam tablosu

Yöntem: paketteki her `.java` dosyasının sınıf adı wiki'de (herhangi bir sayfada) kelime olarak geçiyor mu. **EVET** ≥%50 dosya adı geçiyor, **KISMEN** <%50 ama ≥1, **HAYIR** 0 (ve özellik de anlatılmıyor). Toplam 247 dosya, 38.484 satır (`src/main/java/io/testfly/`).

| Paket | .java | Adı geçen | Kapsam | Açıklama |
|---|---:|---:|---|---|
| test | 24 | 5 | KISMEN | BaseTest/BasePage/BaseApiTest/SmartLocator; `support/*` arayüzleri yok |
| loadtest | 21 | 4 | KISMEN | LoadScenario/LoadTest/Feeder/Assert; Gatling/JDK motorlar, Runner yok; K6 yanlış |
| driver | 14 | 2 | KISMEN | DriverManager, NamedDriverProvider; Edge/Safari/Remote/ BrowserStack sınıfları adla yok |
| reporting | 13 | 3 | KISMEN | ReportAdapter(+Registry), ScreenshotManager |
| client | 13 | 5 | EVET | ApiClient/Response/Interceptor/Request/MockRule; `ApiAuth`, `ApiBatchRunner`, `OpenApiValidator` yok |
| browser | 12 | 1 | KISMEN | yalnız StorageHelper (console, download, geolocation, clipboard, device yok) |
| agent | 11 | 0 | **HAYIR** | |
| ai | 10 | 1 | KISMEN | yalnız AiHealingEngine |
| network | 9 | 0 | **HAYIR** | CDP stub/mock |
| email | 9 | 0 | **HAYIR** | |
| precondition | 8 | 0 | **HAYIR** | |
| testmanagement | 6 | 2 | KISMEN | TestRailCase, XrayTest (Reporter/Client adla yok) |
| junit5 | 6 | 0 | **HAYIR** | `architecture`, `webdriver-lifecycle` JUnit5'ten genel söz eder ama köprü anlatılmaz |
| cucumber | 6 | 3 | EVET | |
| assertion | 6 | 4 | EVET | |
| config | 5 | 1 | KISMEN | TestFlyConfig'in 23 bloğundan ~7'si anılıyor (execution, browser.lifecycle, timeouts, ci, api, testmanagement, quarantine[yanlış]) |
| healing | 5 | 1 | KISMEN | yalnız FuzzyHealingEngine |
| accessibility | 5 | 1 | EVET* | AccessibilityAssert adla geçmiyor ama `accessibility()` akışı örnekle anlatılıyor |
| listeners | 5 | 1 | KISMEN | yalnız `@Retryable`; Suite/TestExecutionListener, RetryListener anlatılmıyor |
| testdata | 4 | 0 | **HAYIR** | |
| extension | 4 | 1 | KISMEN | TestFlyPlugin |
| ci | 4 | 2 | EVET | |
| exceptions | 4 | 0 | **HAYIR** | |
| db | 4 | 0 | **HAYIR** | |
| steps | 3 | 1 | KISMEN | StepLogger |
| sharding | 3 | 0 | KISMEN* | yalnız roadmap.md'de "Smart Test Sharder" satırı; mimari anlatılmıyor |
| recording | 3 | 0 | **HAYIR** | |
| performance | 3 | 0 | **HAYIR** | |
| locator | 3 | 1 | KISMEN | Locator var; `Role` enum, semantik sentez yok |
| internal | 3 | 2 | EVET | ApiExecution, ApiTransport |
| flakiness | 3 | 1 | KISMEN | SmartTriageEngine; FlakinessAnalyzer/Score yanlış anlatılıyor (bkz. D) |
| visual | 2 | 2 | EVET | |
| metrics | 2 | 0 | **HAYIR** | |
| hooks | 2 | 2 | EVET | |
| context | 2 | 1 | KISMEN | ScenarioContext |
| api | 2 | 0 | **HAYIR** | (`TestFlyApi` anotasyonu; AGENTS.md'de var, wiki'de yok) |
| wait | 1 | 1 | EVET | |
| quarantine | 1 | 0 | KISMEN* | QuarantineLoader adla yok; özellik anlatılıyor ama **yanlış** |
| tracing | 1 | 0 | **HAYIR** | |
| shadow | 1 | 0 | **HAYIR** | |
| session | 1 | 0 | **HAYIR** | |
| lifecycle | 1 | 0 | **HAYIR** | FrameworkBootstrap |
| execution | 1 | 0 | **HAYIR** | |
| clock | 1 | 0 | **HAYIR** | |

Özet:
- **Dosya ağırlıklı kapsam:** 48 / 247 = **%19,4** (adı geçen sınıf). Adı geçmese de özelliği anlatılan 5 sınıfı (AccessibilityAssert, QuarantineLoader[yanlış anlatım], FlakinessAnalyzer/Score[yanlış anlatım], SmartTestSharder) eklersem 53/247 ≈ **%21**; yanlış anlatılanları saymazsam ≈ %19-20.
- **Satır ağırlıklı:** 11.578 / 38.484 = **%30,1** (`TestFlyConfig.java` 2.486 satırla bunu şişiriyor).
- **Hiç anlatılmayan paket sayısı:** 44 paketten **18'i HAYIR** (agent, network, email, precondition, junit5, testdata, exceptions, db, recording, performance, metrics, api, tracing, shadow, session, lifecycle, execution, clock). Brief'in listesi (db email exceptions listeners metrics network precondition recording session shadow sharding tracing): `listeners` ve `sharding` aslında çok kısmen anılıyor (`@Retryable`, roadmap satırı); diğer 10'u (db email exceptions metrics network precondition recording session shadow tracing) gerçekten hiç yok.
- **Config kapsamı:** `testfly.yml` için 23 blok var (`Network, Browser, Execution, Retry, Timeouts, Ci, Notifications, Reporting, Recording, Locators, Ai, Flakiness, Tracing, Visual, Email, Sessions, Performance, Quarantine, Clock, Database, Api, TestManagement, LoadTest`); wiki bunların ~7'sini anıyor, birinde (quarantine) yanlış.

---

## F. Çakışan / tekrar eden kopyalar (hangisi kodla uyumlu?)

Karşılaştırdığım kaynaklar: `.agents/skills/testfly/SKILL.md` (485 satır; **yol `.agents/skills/testfly/SKILL.md`**, kökte `skills/` yok), `docs/*.md` (brief 11 dedi; gerçekte **8 .md** + `diagrams/`), `docs-site/docs/**` (EN/TR), kök `ROADMAP.md`.

| Konu | Wiki sayfası | Diğer kopyalar | Kodla uyumlu olan | Not |
|---|---|---|---|---|
| Karantina | quarantine-engine | `docs-site/docs/quarantine.md`, SKILL.md:394 | **docs-site + SKILL** | Wiki yanlış (D#1-4) |
| MCP / CLI | ai-mcp-automation | `docs-site/docs/cli.md`, `ai/testfly-mcp.md`, `docs/mcp-codegen-contract.md` | **cli.md + ai/testfly-mcp.md** (6 araç) | `ai/adr-001…` ve `ai/interactive-studio.md` hâlâ 88 araç/`ui` diyor ama `interactive-studio.md:10` bunu "tarihsel" diye işaretliyor; ADR işaretsiz → docs-site içinde de iç tutarsızlık var |
| Yük testi | load-testing | `docs-site/docs/loadtest/*` (10 dosya), SKILL.md §4 | **docs-site + SKILL** (K6 yok) | |
| Self-healing | webui-testing, architecture | SKILL.md §2.5 (4 katman), `docs-site/docs/guides/self-healing.md` | **SKILL** (HealingCache→SelfHealingLocator→Fuzzy→AI, `aiHealing` şartı) | Wiki varsayılan-kapalıyı atlıyor |
| Assertion sınırı | assertion-system | `docs-site/docs/guides/assertions.md:203`, SKILL §2.6 | Üçü de uyumlu | ADR iki yerde birebir tekrar |
| API (auth, response, schema) | api-testing | SKILL §3, `docs-site/docs/guides/api-*.md` (14 dosya) | **SKILL + docs-site** | SKILL'de `SchemaAssert/ResponseAssert` geçmiyor (grep 0) → wiki'ye özgü hata |
| API interceptor/mock/SSL | api-testing §3-4 (14 madde) | `docs-site/docs/guides/api-interceptors/mocking/ssl/performance.md` | docs-site | Wiki'deki §3-4 özünde docs-site'ın özeti; sürdürülmesi iki kat iş |
| Test yönetimi | test-management | `docs-site/docs/test-management.md` | **docs-site** (ekran görüntüsü yok) | |
| CI metadata/eşik | ci-quality-gates | `docs-site/docs/ci/{ci-metadata,quality-gates,*}.md` | docs-site | |
| Yaşam döngüsü | webdriver-lifecycle | `docs/internals.md`, `docs-site/docs/guides/browser-lifecycle.md` | Hepsi uyumlu | |
| SPI | spi-extensions | SKILL §7, `docs-site/docs/extensibility/*` | Hepsi uyumlu | |
| Roadmap | roadmap | kök `ROADMAP.md` | **ROADMAP.md** | wiki'nin kendisi "tek kaynak kök ROADMAP.md" diyor; yani zaten kopya |
| Erişilebilirlik/görsel | accessibility-visual-testing | `docs-site/docs/accessibility.md` | uyumlu | |
| Config | configuration | `docs-site/docs/configuration.md`, `guides/testfly-yml-guide.md` | docs-site çok daha kapsamlı | |

Sonuç: Wiki'nin içeriği tamamen `docs-site` + SKILL.md içinde var; wiki'ye **özgü** (başka yerde bulunmayan) değer yalnızca ajan/Obsidian meta katmanı (`memory-system`, `index` + MAP) ve Archify diyagram bağlantıları. Hatalı olanlar da (karantina, MCP, SchemaAssert) tam bu özgün olmayan, el ile tekrar edilmiş sayfalar.

---

## G. Yöntem notu

Komutlar: `find/wc` (dosya ve satır sayıları), `grep -rn`/`grep_search` (sınıf, config anahtarı, string ara), `sed -n` ile ilgili sınıfları okuma (`TestFlyConfig`, `ConfigurationLoader`, `DriverManager`, `Locator`, `LocatorAssert/PageAssert/SeleniumAssert`, `AssertionSupport`, `QuarantineLoader`, `TestExecutionListener`, `FlakinessAnalyzer/Score`, `CiEnvironmentDetector`, `ApiClient/ApiResponse/ApiResponseSpec/ApiAuth`, `TestManagementReporter`, `LoadTest*`, `VisualAssert`, `Cucumber*`, `HookRegistry` vb.), `git log -S` (sınıf hiç var oldu mu), `ls`, ve küçük bir `python3` betiği (a) wikilink çözümü: 111 link, 108'i çözülüyor, 3'ü açıklama amaçlı yer tutucu (`...`, `wiki/...`, `wikilink`), (b) sınıf adı kapsam sayımı, (c) dosya/satır toplamları. Diğer repo `testfly-mcp`'yi salt okunur inceledim (`bin/testfly-mcp.js`, `package.json`).

Sayım: Her sayfadaki **doğrulanabilir tüm iddiaları** tek tek çıkarıp saydım (rastgele örnekleme yok). Sayfa başına 4–26 iddia çıktı; çıktığı kadarını kontrol ettim. Toplam 178.

Güvenilirliğin sınırları:
- `api-testing.md` §3-4'teki **davranış** iddialarını (interceptor sırası, mock sırası, `trustAll`+truststore geçersizliği, fail-closed hostname, retry/hook semantiği) kod akışını izleyerek doğrulamadım; yalnızca yapı/ad/varsayılan/`pom`-seviyesi olanları doğruladım → 4 iddia DOĞRULANAMADI. Bunlar büyük olasılıkla `ApiInterceptorExecutionTest` vb. testlerden türetilmiş ama "büyük olasılık" kanıt değil.
- `webui-testing` "otomatik **tam sayfa** ekran görüntüsü": `ScreenshotManager` `TakesScreenshot.getScreenshotAs` kullanıyor, tam-sayfa mantığı görmedim → DOĞRULANAMADI.
- `ci-quality-gates:38`: CI metadata JUnit XML ve ReportPortal özelliklerine yazılıyor (kanıtlı), **Allure** için kanıt bulamadım → DOĞRULANAMADI.
- `ai-mcp` "Claude, Cursor, Copilot yönetebilir": `cli.md` yalnız Claude Desktop/Cursor şeklini söylüyor → DOĞRULANAMADI.
- `memory-system` "Obsidian Graph'ta yıldız şekli": ölçülemez.
- `architecture` "pattern matching/record/`.toList()`" için sadece kullanım var mı baktım (109 `case ->`, 19 dosyada `record`, 7 `.toList()`); "üzerine inşa" derecesini ölçemem.
- Ölçemediklerim: frontmatter `date:` alanlarının doğruluğu (5 sayfa git'te frontmatter tarihinden **sonra** değişmiş: architecture, index, load-testing, roadmap, webui-testing), çalışma zamanı davranışları (hiçbir şey koşturmadım, derleme/test yapmadım), `target/` altındaki eski çıktılar, `docs-site` TR kopyaları.
- Yüzdeler 178 iddialık tam sayım üzerinden; "ağırlıklı" bir önem puanı değil. Önem için D tablosuna bak: 21 yanlışın büyük kısmı "ajanı yanlış koda/komuta iter" (SchemaAssert, ResponseAssert, K6, doctor/ui, 88 araç, karantina şeması/davranışı, ekran görüntüsü), geri kalanı ayrıntı/tutarlılık.
- Dosya sayısı kapsamı "adı geçen sınıf" metriğidir; özelliği farklı kelimelerle anlatıp sınıf adı vermeyen sayfaları eksik sayar (bunları yukarıda elle +5 düzelttim).

---

## Sonuç ve öneriler (hiçbirini uygulamadım)

1. **Hemen**: `quarantine-engine.md`, `ai-mcp-automation.md`, `roadmap.md`'yi sil veya 3 satırlık "doğru kaynak şurada" yönlendirmesine çevir (`docs-site/docs/quarantine.md`, `docs-site/docs/cli.md` + `ai/testfly-mcp.md`, kök `ROADMAP.md`). `MAP.md` ve `index.md` linklerini buna göre güncelle.
2. **Küçük düzeltmeler** (≈10 satır toplam): `api-testing` (SchemaAssert/ResponseAssert → `ApiResponse.assert*`, GraphQL ifadesini sil, cURL/body "opsiyonel"), `load-testing` + `index` + `MAP` + `api-webui:39` (K6 → "JDK motoru + Gatling"), `test-management` (ekran görüntüsü sil, Xray toplu), `webui-testing` (healing opsiyonel), `ci-quality-gates` (`GITHUB_REF_NAME`, Allure), `architecture` (sleep istisnası), `memory-system:34`, `index` (roadmap ekle/sil).
3. **Uzun vadede**: wiki'nin teknik sayfaları `docs-site` + SKILL.md ile çakışıyor; bir kopyayı seç. Öneri: teknik sayfaları **sil**, wiki'de yalnız `index`/`memory-system`/ajan meta ve mimari ADR'leri (assertion boundary) bırak; ajanlara "kodu oku, gerekirse `docs-site/docs`'a bak" de. `file:///Users/hagul/...` bağlarını göreli yollara çevir.
4. Silme/birleştirme yaparsan `wiki-lint` skill'i ve `memory-protocol.md`'deki "kalıcı kararları wiki'ye taşı" kuralını da güncelle, yoksa ajan olmayan sayfaları yeniden üretir.
5. Wiki'yi tutacaksan her sayfa için kodla otomatik doğrulanan küçük bir kontrol (sınıf adı `grep`'i, config anahtarı) ekle; yoksa 11 sayfanın 1 ayda eskimesi tekrar olur.
