import React from 'react';

// ─── Prism Themes ─────────────────────────────────────────────────────────────

export const prismLightTheme = {
  plain: { color: '#1d1d1f', backgroundColor: '#f5f5f7' },
  styles: [
    { types: ['comment', 'prolog', 'doctype', 'cdata'], style: { color: '#6e6e73' } },
    { types: ['punctuation'], style: { color: '#1d1d1f' } },
    { types: ['property', 'tag', 'boolean', 'number', 'constant', 'symbol'], style: { color: '#0071e3' } },
    { types: ['selector', 'attr-name', 'string', 'char', 'builtin'], style: { color: '#248a3d' } },
    { types: ['operator', 'entity', 'url'], style: { color: '#ff9500' } },
    { types: ['atrule', 'attr-value', 'keyword'], style: { color: '#af52de' } },
    { types: ['function', 'class-name'], style: { color: '#ff3b30' } },
    { types: ['regexp', 'important', 'variable'], style: { color: '#ff9500' } },
  ],
};

export const prismDarkTheme = {
  plain: { color: '#f5f5f7', backgroundColor: '#1c1c1e' },
  styles: [
    { types: ['comment', 'prolog', 'doctype', 'cdata'], style: { color: '#8e8e93' } },
    { types: ['punctuation'], style: { color: '#f5f5f7' } },
    { types: ['property', 'tag', 'boolean', 'number', 'constant', 'symbol'], style: { color: '#0a84ff' } },
    { types: ['selector', 'attr-name', 'string', 'char', 'builtin'], style: { color: '#30d158' } },
    { types: ['operator', 'entity', 'url'], style: { color: '#ff9f0a' } },
    { types: ['atrule', 'attr-value', 'keyword'], style: { color: '#bf5af2' } },
    { types: ['function', 'class-name'], style: { color: '#ff453a' } },
    { types: ['regexp', 'important', 'variable'], style: { color: '#ff9f0a' } },
  ],
};

// ─── Hero Code Showcase Tabs ──────────────────────────────────────────────────

export const heroTabs = [
  {
    id: 'agentic',
    label: '🤖 Agentic AI',
    filename: 'AgenticCheckoutTest.java',
    language: 'java',
    code: `public class AgenticCheckoutTest extends BaseTest {

  @Test
  public void autonomousCheckoutFlow() {
    open();

    // 1. Goal-oriented action compiled & frozen to .testfly/action-cache.json
    act("Log in as 'standard_user', add Backpack to cart, proceed to checkout");

    // 2. Zero-shot semantic assertion on live DOM (anti-throttle protected)
    assertThatPage().satisfiesAi("Checkout overview displays 1 item with valid tax");
    assertThatPage().violatesAi("Error banner, stock shortage, or checkout failure");
  }
}`,
  },
  {
    id: 'web',
    label: '⚡ Self-Healing',
    filename: 'InventoryPage.java',
    language: 'java',
    code: `public class InventoryPage extends BasePage {

  public InventoryPage addToCart(String itemName) {
    // Playwright-style accessibility locator with auto-waiting
    getByRole(Role.BUTTON, "Add to cart")
        .filter(hasText(itemName))
        .click(); // Level-1 (static) & Level-2 (LLM) self-healing if markup drifted
    return this;
  }

  public void verifyInStock() {
    assertThat(find(".inventory_item_price"))
        .isVisible()
        .hasText("$29.99");
  }
}`,
  },
  {
    id: 'api',
    label: '🌐 API & CDP',
    filename: 'PaymentApiTest.java',
    language: 'java',
    code: `public class PaymentApiTest extends BaseApiTest {

  @Test
  public void checkoutWithMockedCdpPayment() {
    // Intercept payment gateway over Chrome DevTools Protocol
    network().route("**/api/payment", r -> r.fulfill(200, "{\\"status\\":\\"PAID\\"}"));

    // Fluent REST assertions
    api().auth(bearer("\${AUTH_TOKEN}"))
         .post("/orders")
         .body(new OrderRequest("item-42", 1))
         .send()
         .assertThat()
         .status(201)
         .jsonPath("$.orderId").exists()
         .durationLessThan(500);
  }
}`,
  },
  {
    id: 'bdd',
    label: '🥒 Cucumber BDD',
    filename: 'agentic_saucedemo.feature',
    language: 'gherkin',
    code: `Feature: Autonomous E-Commerce Journey
  Background:
    Given the user is on the Sauce Demo login page

  @Agentic
  Scenario: Autonomous login and cart flow with Compile & Freeze
    When the agent executes goal "Enter username 'standard_user' and password 'secret_sauce', then click Login"
    Then the page satisfies AI condition "The user is logged in and products catalog is displayed"
    And the page violates AI condition "Error banner or locked out message"
    When the agent executes goal "Add backpack to cart and navigate to checkout"
    Then the page satisfies AI condition "Shopping cart contains Sauce Labs Backpack"`,
  },
  {
    id: 'recorder',
    label: '🎥 Live Recorder',
    filename: 'testfly record https://app.com',
    language: 'bash',
    code: `# 1. Launch interactive Chrome companion & web studio (:8765)
$ testfly record https://www.saucedemo.com

# 2. Injected companion captures clicks, debounced typing & visual assertions
# 3. TestFly synthesizes & saves multi-file Java tests with compiler guards:
#    ✔ src/test/java/com/example/pages/InventoryPage.java  (BasePage)
#    ✔ src/test/java/com/example/tests/CheckoutTest.java   (BaseTest)
#    ✔ src/test/resources/features/checkout.feature       (Cucumber BDD)

# 4. Connect AI coding assistants (Claude, Cursor, Copilot) to 88 tools
$ testfly mcp`,
  },
];

// ─── Feature Data ─────────────────────────────────────────────────────────────

export function getFlagshipFeatures(isTr) {
  return [
    {
      icon: (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
        </svg>
      ),
      title: isTr ? 'Sıfır Konfigürasyon & Boilerplate' : 'Zero Boilerplate Architecture',
      description: isTr
        ? "BaseTest'i extend edin, @Test metodunuzu yazın. Driver yaşam döngüsü, ThreadLocal izolasyonu, akıllı beklemeler, otomatik retry ve raporlama tamamen yönetilir."
        : 'Extend BaseTest, write @Test methods, and go. ThreadLocal driver lifecycle, waits, retries, reports, and screenshots are all managed out of the box.',
      code: `class CheckoutTest extends BaseTest {

  @Test
  void completeOrder() {
    open();
    find("#checkout").click();
    assertThat(find("[role='alert']"))
        .isVisible();
  }
}`,
    },
    {
      icon: (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="10" />
          <path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20" />
          <path d="M2 12h20" />
        </svg>
      ),
      title: isTr ? 'Agentic Testing & Compile & Freeze' : 'Agentic Testing & Compile & Freeze',
      description: isTr
        ? 'act("...") ile doğal dil hedeflerini çalıştırın. İlk koşuda somut Selenium adımlarına derlenir, .testfly/action-cache.json dosyasına dondurulur ve sonraki tüm koşularda <50ms deterministik hızla çalışır.'
        : 'Execute high-level natural language goals via act("..."). Compiles into concrete Selenium steps on run 1, freezes to .testfly/action-cache.json, and replays under 50ms with zero AI latency.',
      code: `// First run compiles; subsequent runs replay frozen cache
act("Delete the first item in the cart and checkout");

// Dynamic semantic intent locator
byIntent("Proceed to payment").click();`,
    },
    {
      icon: (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z" />
          <path d="m9 12 2 2 4-4" />
        </svg>
      ),
      title: isTr ? 'AI Self-Healing & Otomatik Git Yaması (Auto-PR)' : 'AI Self-Healing & Auto-PR Patches',
      description: isTr
        ? 'Seçiciler kırıldığında DomPruner DOM ağacını 8K token altına budar, LLM semantik yedeği bulup testi kurtarır. Kalıcı hatalarda ise target/remediations/*.patch Unified Git Diff dosyası üretir.'
        : 'When locators break, DomPruner compresses the DOM to <8K tokens and synthesizes a healed selector. On permanent failure, it generates target/remediations/*.patch ready for git apply.',
    },
    {
      icon: (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 2a7 7 0 0 1 7 7c0 2.38-1.19 4.47-3 5.74V17a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2v-2.26C6.19 13.47 5 11.38 5 9a7 7 0 0 1 7-7z" />
          <path d="M9 21h6" />
        </svg>
      ),
      title: isTr ? 'Semantik Doğrulamalar (satisfiesAi & violatesAi)' : 'Semantic AI Assertions',
      description: isTr
        ? 'Kırılgan metin eşleşmeleri yerine LLM muhakemesiyle sayfa veya element durumunu doğrulayın. 500ms polling gecikmesi olmadan anti-throttle korumalı tek seferlik akıllı kontrol.'
        : 'Verify complex visual or logical state using zero-shot LLM reasoning against the live DOM. Single-shot anti-throttle protection ensures zero rate-limit waste.',
      code: `assertThatPage()
    .satisfiesAi("Order confirmation summary shows valid total");
assertThatPage()
    .violatesAi("500 server error or session expired");`,
    },
    {
      icon: (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <path d="M3 3v18h18" />
          <path d="M18 17V9M13 17V5M8 17v-3" />
        </svg>
      ),
      title: isTr ? 'Paydaşların Gerçekten Okuduğu HTML Rapor' : 'Reports Stakeholders Actually Read',
      visual: 'report',
      description: isTr
        ? 'Başarı oranı göstergesi, Flakiness Radar, adım adım ekran görüntüleri, video kayıtları, filtrelenebilir hatalar ve karanlık mod desteğiyle tam teşekküllü HTML paneli.'
        : 'Tabbed HTML dashboard with pass-rate gauge, Flakiness Radar, retry badges, expandable error stacks, timeline screenshots, video recordings, and dark mode.',
    },
    {
      icon: (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="2" />
          <path d="M9 9h6v6H9z" />
          <path d="m15 9-6 6" />
        </svg>
      ),
      title: isTr ? 'Model Context Protocol & Canlı Web Kaydedici' : 'Model Context Protocol & Live Recorder',
      description: isTr
        ? 'Claude Code, Cursor, Copilot ve JetBrains AI için 88 tarayıcı otomasyon aracı sunan yerleşik MCP sunucusu. testfly record komutuyla canlı Chrome oturumunu dinleyip derleme korumalı Page Object ve BDD testleri üretir.'
        : 'Native MCP server exposing 88 browser automation tools to Claude Code, Cursor, Copilot, and JetBrains AI. Includes an interactive Chrome companion (testfly record) for zero-boilerplate Page Object and BDD codegen.',
      code: `# 1. Live web recording with Chrome companion
testfly record https://www.saucedemo.com

# 2. Run MCP server for IDE coding assistants
testfly mcp`,
    },
  ];
}

export function getMoreFeatures(isTr) {
  return [
    {
      icon: '🎥',
      title: isTr ? 'Native CDP Video Kaydı (MP4 / GIF)' : 'Native CDP Video Recording',
      short: isTr
        ? 'Chromium CDP screencast ile harici yazılımsız ekran kaydı; retain-on-failure ile yalnızca hatalı testleri MP4/H.264 olarak saklar.'
        : 'Chromium CDP screencasting without external binaries; retain-on-failure saves H.264 MP4 videos only when tests fail.',
    },
    {
      icon: '📊',
      title: isTr ? 'ReportPortal & Allure Entegrasyonu' : 'ReportPortal & Allure Dashboards',
      short: isTr
        ? 'Canlı lansman akışıyla ReportPortal ve Allure panellerine anında sonuç, log ve ekleri gönderin.'
        : 'Sync execution status, attachments, and traces live to ReportPortal and Allure dashboards.',
    },
    {
      icon: '🗄️',
      title: isTr ? 'Veritabanı Doğrulama (DbClient)' : 'Database Testing (DbClient)',
      short: isTr
        ? 'PostgreSQL, MySQL, Oracle veya MSSQL için akıcı SQL sorguları ve otomatik kayıt doğrulamaları.'
        : 'Execute fluent SQL queries and assert records across PostgreSQL, MySQL, Oracle, and MSSQL.',
    },
    {
      icon: '🎯',
      title: isTr ? 'TestRail & Jira Xray Senkronizasyonu' : 'TestRail & Jira Xray Sync',
      short: isTr
        ? 'Test sonuçlarını, logları ve ekran görüntülerini TestRail veya Jira Xray test planlarına otomatik aktarır.'
        : 'Automatically push run status, error logs, and screenshots directly into TestRail and Jira Xray.',
    },
    {
      icon: '♿',
      title: isTr ? 'axe-core ile Erişilebilirlik (a11y)' : 'Accessibility Auditing (axe-core)',
      short: isTr
        ? 'Sayfalar arası geçişlerde WCAG 2.1 AA uyumluluk denetimleri ve otomatik ihlal raporlaması.'
        : 'Automated WCAG 2.1 AA audits on every navigation with zero-boilerplate violation reports.',
    },
    {
      icon: '📈',
      title: isTr ? 'Core Web Vitals & Performans' : 'Core Web Vitals Performance',
      short: isTr
        ? 'LCP, CLS ve FID değerlerini canlı tarayıcıdan toplayıp SLA eşik değer kontrolleri uygulayın.'
        : 'Capture real-user LCP, CLS, and FID metrics directly from Chromium and assert performance SLAs.',
    },
    {
      icon: '📋',
      title: isTr ? '@TestData Veri Sürücüsü (Excel/CSV)' : 'Data-Driven Testing (@TestData)',
      short: isTr
        ? 'Excel (.xlsx), CSV ve JSON dosyalarını otomatik TestNG DataProvider parametrelerine dönüştürün.'
        : 'Load Excel (.xlsx), CSV, and JSON data sources directly into strongly typed test method parameters.',
    },
    {
      icon: '🌐',
      title: isTr ? 'CDP Ağ & API Taklidi' : 'CDP Network Interception',
      short: isTr
        ? 'Chrome DevTools Protocol ile ağ isteklerini durdurun, mock yanıtlar dönün ve coğrafi konum taklit edin.'
        : 'Mock API responses, stub network routes, and simulate geolocation via Chrome DevTools Protocol.',
    },
    {
      icon: '🔁',
      title: isTr ? 'Flakiness Radar & Karantina' : 'Flakiness Radar & Quarantine',
      short: isTr
        ? 'Kararsız testleri puanlar ve testfly-quarantine.yml ile koda dokunmadan CI hattından izole eder.'
        : 'Score stability across runs and isolate unstable tests via testfly-quarantine.yml without code edits.',
    },
    {
      icon: '🔐',
      title: isTr ? '@PreCondition Oturum Önbelleği' : '@PreCondition Session Cache',
      short: isTr
        ? 'Giriş işlemini bir kez yapın, çerez ve oturumu tüm testlerde anında yeniden kullanın.'
        : 'Run login once, cache browser cookies/storage, and restore authenticated state instantly for tests.',
    },
    {
      icon: '📧',
      title: isTr ? 'E-Posta & OTP Doğrulama' : 'Email & OTP Verification',
      short: isTr
        ? 'Mailhog, Mailtrap, Graph API veya IMAP üzerinden gelen doğrulama kodlarını ve sihirli linkleri yakalayın.'
        : 'Poll transactional mailboxes and extract OTPs/magic links via Mailhog, Mailtrap, or IMAP.',
    },
    {
      icon: '📸',
      title: isTr ? 'Görsel Regresyon Testleri' : 'Visual Regression Testing',
      short: isTr
        ? 'Piksel bazlı ekran farkı doğrulaması, tolerans kontrolü ve 6 farklı mobil cihaz emülasyonu.'
        : 'Pixel-diff screenshot comparison with tolerance thresholds and 6 mobile device emulator profiles.',
    },
    {
      icon: '🕐',
      title: isTr ? 'Zaman Taklidi (TestClock)' : 'Browser Clock Mocking',
      short: isTr
        ? 'Tarayıcı saatini dondurarak token süresi, deneme periyodu ve geri sayım testlerini saniyeler içinde yapın.'
        : 'Freeze or warp the browser clock to test token expiries, trial countdowns, and time-gated features.',
    },
    {
      icon: '🪜',
      title: isTr ? 'Adım Günlüğü (StepLogger)' : 'StepLogger Timeline',
      short: isTr
        ? 'Ekran görüntülü isimlendirilmiş test adımları ve yürütme izleri doğrudan HTML rapora basılır.'
        : 'Named execution steps with inline screenshots and self-contained timeline traces in the HTML report.',
    },
    {
      icon: '☁️',
      title: isTr ? 'Bulut & Selenium Grid Desteği' : 'Cloud & Grid Execution',
      short: isTr
        ? 'BrowserStack, Sauce Labs veya uzaktaki Selenium Grid tek satır config ile bağlanır.'
        : 'Run seamlessly on BrowserStack, Sauce Labs, or remote Selenium Grid in one configuration line.',
    },
    {
      icon: '🔌',
      title: isTr ? 'SPI Eklenti Mimarisi' : 'Extensible SPI Architecture',
      short: isTr
        ? 'Java ServiceLoader ile özel driver sağlayıcıları, yaşam döngüsü kancaları ve rapor adaptörleri ekleyin.'
        : 'Plug in custom driver providers, lifecycle hooks, and report adapters via standard Java SPI.',
    },
    {
      icon: '🎙️',
      title: isTr ? 'İnteraktif Web Kaydedici (testfly record)' : 'Interactive Web Recorder (testfly record)',
      short: isTr
        ? 'Canlı Chrome oturumunda gezinirken tıklama, yazım ve doğrulamaları yakalar; Page Object ve BDD üretir.'
        : 'Stream live Chrome clicks, debounced typing, and toolbar assertions to generate Page Objects and BDD.',
    },
    {
      icon: '🤖',
      title: isTr ? 'TestFly MCP Sunucusu (88 Araç)' : 'TestFly MCP Server (88 Tools)',
      short: isTr
        ? 'Claude Code, Cursor ve Copilot için 88 tarayıcı otomasyon aracıyla canlı DOM denetimi ve test üretimi.'
        : 'Protocol-native MCP server giving Claude, Cursor, and Copilot 88 live tools for verified test generation.',
    },
    {
      icon: '💻',
      title: isTr ? 'TestFly CLI Geliştirici Deneyimi' : 'TestFly CLI Toolkit',
      short: isTr
        ? 'testfly record, testfly mcp, testfly ui ve testfly doctor komutlarıyla eksiksiz terminal araç kiti.'
        : 'Unified terminal suite featuring testfly record, testfly mcp, testfly ui, and testfly doctor.',
    },
    {
      icon: '🧩',
      title: isTr ? 'IDE Eklentileri (IntelliJ & VS Code)' : 'IDE Plugins (IntelliJ & VS Code)',
      short: isTr
        ? 'JetBrains AI Assistant ve VS Code için tek tıkla yapılandırılan sıfır-konfigürasyon eklentileri.'
        : 'Zero-config plugins for JetBrains AI Assistant and VS Code with status bar actions and diagnostics.',
    },
  ];
}

// ─── Before / After Comparisons ───────────────────────────────────────────────

export const compareScenarios = [
  {
    id: 'dynamic',
    labelEn: '⚡ Dynamic Wait & Actionability',
    labelTr: '⚡ Dinamik Bekleme & Aksiyon',
    taglineEn: 'Explicit waits, stale elements, and JS scroll hacks vs. instant auto-waiting & actionability checks',
    taglineTr: 'Explicit wait karmaşası, stale element hataları ve JS executor yerine akıllı bekleme ve aksiyon kontrolü',
    filename: 'CheckoutTest.java',
    beforeEn: `// Plain Selenium: Flaky explicit wait & JS scroll hack
WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

WebElement btn = wait.until(
    ExpectedConditions.elementToBeClickable(By.id("checkout")));
((JavascriptExecutor) driver).executeScript(
    "arguments[0].scrollIntoView(true);", btn);
btn.click();

// Brittle XPath text match with manual visibility check
WebElement status = wait.until(
    ExpectedConditions.visibilityOfElementLocated(
        By.xpath("//span[contains(@class,'order-badge')]")));
Assert.assertEquals(status.getText().trim(), "Confirmed");`,
    beforeTr: `// Geleneksel Selenium: Kırılgan explicit wait ve JS scroll hilesi
WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

WebElement btn = wait.until(
    ExpectedConditions.elementToBeClickable(By.id("checkout")));
((JavascriptExecutor) driver).executeScript(
    "arguments[0].scrollIntoView(true);", btn);
btn.click();

// Kırılgan XPath metin eşleşmesi ve manuel görünürlük kontrolü
WebElement status = wait.until(
    ExpectedConditions.visibilityOfElementLocated(
        By.xpath("//span[contains(@class,'order-badge')]")));
Assert.assertEquals(status.getText().trim(), "Confirmed");`,
    afterEn: `// TestFly: Auto-scroll, actionability check & self-healing
find("#checkout").click();

// Web-first semantic assertion with built-in auto-retry
assertThat(getByRole(Role.STATUS))
    .isVisible()
    .hasText("Confirmed");`,
    afterTr: `// TestFly: Otomatik kaydırma, aksiyon kontrolü ve akıllı bekleme
find("#checkout").click();

// Otomatik bekleyen web öncelikli semantik doğrulama
assertThat(getByRole(Role.STATUS))
    .isVisible()
    .hasText("Confirmed");`,
    badgeBeforeEn: '14 lines · 2 explicit waits · StaleElement prone',
    badgeBeforeTr: '14 satır · 2 explicit wait · StaleElement riski',
    badgeAfterEn: '2 lines · Zero-flakiness auto-waiting',
    badgeAfterTr: '2 satır · Sıfır kırılganlıkta akıllı bekleme',
  },
  {
    id: 'healing',
    labelEn: '🪄 Self-Healing Locators',
    labelTr: '🪄 Kendi Kendini Onaran Seçiciler',
    taglineEn: 'Broken locators that crash CI builds vs. automated runtime semantic healing',
    taglineTr: 'CI/CD pipeline’ını çökerten kırık seçiciler yerine çalışma anında otonom onarım',
    filename: 'PaymentTest.java',
    beforeEn: `// Frontend changed button ID: '#pay-now' -> '#submit-order-v2'
// ❌ FAILS with NoSuchElementException:
WebElement pay = driver.findElement(By.id("pay-now"));
pay.click();

// CI build fails, pull request blocked, engineer must fix manually...`,
    beforeTr: `// Arayüz ekibi buton ID'sini değiştirdi: '#pay-now' -> '#submit-order-v2'
// ❌ NoSuchElementException ile ÇÖKER:
WebElement pay = driver.findElement(By.id("pay-now"));
pay.click();

// CI hattı kırılır, PR engellenir, mühendis manuel kod düzeltmek zorunda kalır...`,
    afterEn: `// Button ID changed? TestFly recovers it dynamically:
// 🪄 [HEALED] '#pay-now' -> 'button[name="submit-order-v2"]' (confidence: 96%)
find("#pay-now").click();

// Test passes smoothly. Remediation patch exported in HTML report!`,
    afterTr: `// Buton ID'si mi değişti? TestFly çalışma anında dinamik onarır:
// 🪄 [ONARILDI] '#pay-now' -> 'button[name="submit-order-v2"]' (güven: %96)
find("#pay-now").click();

// Test kesintisiz geçer. target/remediations/*.patch dosyası hazır üretilir!`,
    badgeBeforeEn: 'Pipeline broken · Manual PR needed',
    badgeBeforeTr: 'Pipeline çöker · Manuel kod düzeltmesi şart',
    badgeAfterEn: 'Zero downtime · Auto-PR patch created',
    badgeAfterTr: 'Sıfır duruş · Otomatik PR yaması üretilir',
  },
  {
    id: 'auth',
    labelEn: '🔐 Reusable Session Cache',
    labelTr: '🔐 Yeniden Kullanılabilir Oturum',
    taglineEn: 'Repeating slow UI logins for every test vs. instant single-login cookie & storage caching',
    taglineTr: 'Her testte yavaş UI login tekrarlamak yerine tek oturum açıp çerezleri ve depolamayı önbelleğe alma',
    filename: 'UserDashboardTest.java',
    beforeEn: `// Plain Selenium: 100 tests re-logging in over slow UI
driver.get("https://app.com/login");
driver.findElement(By.id("username")).sendKeys("admin");
driver.findElement(By.id("password")).sendKeys("secret");
driver.findElement(By.id("login-btn")).click();
wait.until(ExpectedConditions.urlContains("/dashboard"));

// 100 tests x 5s login = 8+ minutes wasted on repeated UI logins`,
    beforeTr: `// Geleneksel Selenium: 100 testin her biri yavaş UI'dan tekrar giriş yapar
driver.get("https://app.com/login");
driver.findElement(By.id("username")).sendKeys("admin");
driver.findElement(By.id("password")).sendKeys("secret");
driver.findElement(By.id("login-btn")).click();
wait.until(ExpectedConditions.urlContains("/dashboard"));

// 100 test x 5sn giriş = Tekrarlanan UI girişleriyle boşa giden 8+ dakika`,
    afterEn: `// TestFly: Login once, cache session state for entire suite
@PreCondition("admin-auth")
public class UserDashboardTest extends BaseTest {

    @Test
    void testProfile() {
        open("/dashboard"); // Already authenticated! 0s login overhead
        assertThat(find(".avatar")).isVisible();
    }
}`,
    afterTr: `// TestFly: Bir kez giriş yapın, oturumu tüm test paketine önbelleğe alın
@PreCondition("admin-auth")
public class UserDashboardTest extends BaseTest {

    @Test
    void testProfile() {
        open("/dashboard"); // Oturum hazır! 0 sn ek giriş maliyeti
        assertThat(find(".avatar")).isVisible();
    }
}`,
    badgeBeforeEn: '100 logins · 8m wasted on auth UI',
    badgeBeforeTr: '100 giriş · UI girişine harcanan 8dk kayıp',
    badgeAfterEn: '1 login · Instant test execution',
    badgeAfterTr: '1 giriş · Anında başlayan test koşusu',
  },
  {
    id: 'recorder',
    labelEn: '🎥 Live Studio & Codegen',
    labelTr: '🎥 Canlı Stüdyo & Kod Üretimi',
    taglineEn: 'Tedious Chrome DevTools element inspection vs. interactive web companion with instant Java codegen',
    taglineTr: 'Saatler süren manuel DevTools element incelemesi ve şablon sınıflar yerine tarayıcıda gezinerek anında Java testi üretimi',
    filename: 'CartPage.java',
    beforeEn: `// Plain Selenium: Inspecting elements one-by-one in Chrome DevTools
// Manually typing 50+ lines of brittle XPath and Page Object boilerplate:
public class CartPage {
    private By checkoutBtn = By.xpath("//*[@id='checkout']");
    private By firstName = By.xpath("//input[@name='firstName']");
    private By postalCode = By.cssSelector(".postal-code-input");

    public void fillFormAndSubmit(String fName, String zip) {
        driver.findElement(firstName).sendKeys(fName);
        driver.findElement(postalCode).sendKeys(zip);
        driver.findElement(checkoutBtn).click();
    }
} // Fragile XPaths break on minor frontend changes...`,
    beforeTr: `// Geleneksel Selenium: Chrome DevTools ile elementleri tek tek inceleme
// Elle 50+ satır kırılgan XPath ve Page Object şablon kodu yazma zahmeti:
public class CartPage {
    private By checkoutBtn = By.xpath("//*[@id='checkout']");
    private By firstName = By.xpath("//input[@name='firstName']");
    private By postalCode = By.cssSelector(".postal-code-input");

    public void fillFormAndSubmit(String fName, String zip) {
        driver.findElement(firstName).sendKeys(fName);
        driver.findElement(postalCode).sendKeys(zip);
        driver.findElement(checkoutBtn).click();
    }
} // En ufak arayüz güncellemesinde kırılgan XPath'ler çöker...`,
    afterEn: `// TestFly Interactive Recorder ($ testfly record https://app.com):
// Injected Chrome companion streams clicks, typing & toolbar assertions live.
// Real-time Page Object Model synthesis with compiler safeguards:
public class CartPage extends BasePage {
    private final Locator checkoutBtn = getByTestId("checkout");
    private final Locator firstName = getByTestId("firstName");

    public CartPage proceedToCheckout(String name) {
        firstName.type(name);
        checkoutBtn.click();
        return this;
    }
}
// Saved directly to src/test/java/.../CartPage.java with 1-click!`,
    afterTr: `// TestFly Interactive Recorder ($ testfly record https://app.com):
// Chrome eşlikçisi tıklamaları, yazımları ve doğrulamaları anında dinler.
// Java derleyici güvenceleriyle anlık Page Object Model sentezi:
public class CartPage extends BasePage {
    private final Locator checkoutBtn = getByTestId("checkout");
    private final Locator firstName = getByTestId("firstName");

    public CartPage proceedToCheckout(String name) {
        firstName.type(name);
        checkoutBtn.click();
        return this;
    }
}
// Tek tıkla doğrudan src/test/java/.../CartPage.java dizinine kaydedilir!`,
    badgeBeforeEn: 'DevTools inspection · 45m per page',
    badgeBeforeTr: 'Manuel DevTools · Sayfa başına 45dk',
    badgeAfterEn: '1 command · Live POM & BDD codegen',
    badgeAfterTr: 'Tek komut · Canlı POM ve BDD üretimi',
  },
];

// ─── FAQ Items ────────────────────────────────────────────────────────────────

export function getFaqs(isTr) {
  if (isTr) {
    return [
      {
        q: 'Compile & Freeze mimarisi CI ortamında determinizmi nasıl garanti eder?',
        a: "act(...) metodunu ilk kez çalıştırdığınızda TestFly doğal dil hedefini somut Selenium adımlarına derler ve .testfly/action-cache.json dosyasına dondurur. Sonraki CI koşularında LLM'e hiç gitmeden standart Selenium WaitEngine ile 50 ms'nin altında ve sıfır AI gecikmesiyle çalışır. Arayüz değişip bir adım aksarsa önbellek otomatik düşürülür ve plan yeniden derlenerek test kurtarılır.",
      },
      {
        q: 'Seviye 2 AI Self-Healing klasik iyileştirme araçlarından nasıl ayrışır?',
        a: "Geliştiriciler ID veya class adlarını değiştirdiğinde, DomPruner 8K token bütçesinde canlı DOM'u analiz eder ve doğru elementi semantik olarak bulur. Onarılan seçici .testfly/healed-locators.json dosyasına kaydedilerek sonraki koşularda 0 ms sürede işletilir ve HTML raporda ⚠ healed etiketiyle işaretlenir.",
      },
      {
        q: 'Yapay zekanın ürettiği hata düzeltmelerini doğrudan koduma uygulayabilir miyim?',
        a: "Evet! ai.generatePatch: true ayarlandığında, başarısız olan locator veya assertion için target/remediations/TestClass.patch Unified Git Diff dosyası üretilir. Geliştiriciler veya CI botları 'git apply target/remediations/...' ile tek komutta kaynak kodu güncelleyebilir.",
      },
      {
        q: 'TestFly bizi belirli bir test aracına veya bulut sağlayıcısına mahkum eder mi?',
        a: "Asla. TestNG, JUnit 5 ve Cucumber BDD ile %100 özellik denkliğine sahiptir. Yerel Chrome/Firefox/Edge'den Selenium Grid'e, BrowserStack'ten Sauce Labs'e kadar tek bir config satırıyla her yerde çalışır.",
      },
      {
        q: 'TestFly MCP sunucusu IDE asistanlarıyla (Claude Code, IntelliJ, Copilot) nasıl entegre olur?',
        a: "TestFly, 88 adet tarayıcı otomasyon aracı sunan yerleşik bir Model Context Protocol (MCP) sunucusuna sahiptir. AI asistanınız kör kod yazmak yerine canlı tarayıcıyı inceler, erişilebilirlik ağacından doğru elementleri seçer ve hatasız TestFly Java kodları üretir.",
      },
      {
        q: 'testfly record komutu ve Interactive Recorder stüdyosu nasıl çalışır?',
        a: "testfly record <url> komutunu çalıştırdığınızda TestFly izole bir Google Chrome penceresi açar ve CDP aracılığıyla injected_recorder.js betiğini otomatik enjekte eder. Tarayıcıdaki tıklamalar, tuş vuruşları (debounced typing) ve görsel doğrulamalar (isVisible, hasText) SSE üzerinden yerel stüdyoya (:8765) iletilir. Stüdyo anlık olarak Page Object Model, TestNG, JUnit 5 veya Cucumber BDD kodları derler. 'Save to Project' butonuna bastığınızda dosyalar Java anahtar kelime güvenceleriyle (örn. continueElement) doğrudan src/test/java/ projenize yazılır.",
      },
      {
        q: 'Ham Selenium WebDriver ve CDP API\'larına doğrudan erişebilir miyim?',
        a: "Her zaman. getDriver() ile canlı WebDriver daima elinizin altındadır. Tüm Playwright-tarzı Locator nesneleri .toBy() ile standart Selenium By verir. CDP üzerinden ağ trafiği durdurma, API taklit etme, konum taklidi ve çerez yönetimi yerel olarak desteklenir.",
      },
    ];
  }

  return [
    {
      q: 'How does Agentic Testing with Compile & Freeze guarantee zero flakiness in CI?',
      a: 'When you run act(...), TestFly compiles the high-level intent into concrete Selenium steps and freezes them into .testfly/action-cache.json. In subsequent CI runs, the cached plan replays directly via Selenium WaitEngine with zero AI latency (under 50ms) and 100% deterministic repeatability. If the UI changes and a step fails, TestFly automatically invalidates the cache, recompiles against the fresh DOM, and self-heals.',
    },
    {
      q: 'How does Level-2 AI Self-Healing prevent false build failures?',
      a: 'When selectors break due to front-end refactoring (renamed IDs, altered classes, or DOM restructuring), TestFly prunes the live DOM to under 8,000 tokens and prompts the configured LLM to synthesize a replacement selector. The healed selector is saved to .testfly/healed-locators.json and reused in future runs at 0 ms latency.',
    },
    {
      q: 'Can I apply AI-generated fixes directly to my source code?',
      a: 'Yes! With ai.generatePatch: true, whenever an assertion or locator fails permanently, TestFly generates a standard Unified Git Diff (target/remediations/TestClass.patch). Developers or CI bots can review and apply the fix in one command with git apply target/remediations/...',
    },
    {
      q: 'Does TestFly lock my team into a specific test runner or vendor cloud?',
      a: 'Never. TestFly provides 100% feature parity across TestNG, JUnit 5, and Cucumber BDD. It executes locally on Chrome, Firefox, Edge, and Safari, or remotely on Selenium Grid, BrowserStack, and Sauce Labs with a single config line.',
    },
    {
      q: 'How does the TestFly MCP server integrate with AI coding tools?',
      a: 'TestFly provides a built-in Model Context Protocol (MCP) server exposing 88 browser automation tools. AI assistants like Claude Code, JetBrains AI Assistant, GitHub Copilot, and Google Antigravity can inspect live browsers, query the accessibility tree, and generate reliable, production-grade TestFly Java code rather than hallucinating selectors.',
    },
    {
      q: 'How does testfly record and the Interactive Recorder studio work?',
      a: "Running testfly record <url> launches an isolated Google Chrome instance with automated CDP script injection. User clicks, coalesced keystrokes, and toolbar assertions (isVisible, hasText) are streamed via SSE to the local web studio (:8765). The studio synthesizes production-ready Page Object Model, TestNG, JUnit 5, or Cucumber BDD code in real time. Clicking 'Save to Project' writes clean, compiler-safe classes directly into your project's src/test/java/ directory.",
    },
    {
      q: 'Can I still drop down to the raw Selenium WebDriver and CDP APIs?',
      a: 'Always. getDriver() returns the live WebDriver instance, and every fluent locator exposes .toBy(). Furthermore, native CDP integration lets you intercept network traffic, mock REST responses, manipulate browser cookies, and emulate geo-locations without third-party proxies.',
    },
  ];
}

export const stats = [
  { value: '1', label: 'Single Maven Dependency' },
  { value: '1.0.5', label: 'Latest Stable Release' },
  { value: '<50ms', label: 'Frozen AI Action Replay' },
  { value: '88', label: 'Built-in MCP Tools' },
];

export const recorderTabs = [
  {
    id: 'pom',
    label: '📄 Page Object (BasePage)',
    filename: 'com/example/pages/InventoryPage.java',
    language: 'java',
    code: `package com.example.pages;

import io.testfly.test.BasePage;
import io.testfly.locator.Locator;
import org.openqa.selenium.WebDriver;

public class InventoryPage extends BasePage {

    // Accessibility-first locators captured from live Chrome DOM
    private final Locator backpackBtn = getByTestId("add-to-cart-sauce-labs-backpack");
    private final Locator cartBadge = getByTestId("shopping-cart-badge");
    private final Locator checkoutBtn = getByTestId("checkout");

    public InventoryPage(WebDriver driver) {
        super(driver);
    }

    public InventoryPage addBackpackToCart() {
        backpackBtn.click();
        return this;
    }

    public InventoryPage proceedToCheckout() {
        checkoutBtn.click();
        return this;
    }
}`,
  },
  {
    id: 'test',
    label: '🧪 POM Test (BaseTest)',
    filename: 'com/example/tests/InventoryTest.java',
    language: 'java',
    code: `package com.example.tests;

import com.example.pages.InventoryPage;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class InventoryTest extends BaseTest {

    @Test
    public void testRecordedCheckoutFlow() {
        open("https://www.saucedemo.com/inventory.html");

        InventoryPage page = new InventoryPage(getDriver());
        page.addBackpackToCart()
            .proceedToCheckout();

        // 👁️ Visual assertion captured live from Studio toolbar
        assertThat(getByTestId("title"))
            .isVisible()
            .hasText("Checkout: Your Information");
    }
}`,
  },
  {
    id: 'bdd',
    label: '🥒 Cucumber BDD',
    filename: 'src/test/resources/features/inventory.feature',
    language: 'gherkin',
    code: `Feature: Live Recorded E-Commerce Journey
  Background:
    Given the user opens "https://www.saucedemo.com/inventory.html"

  Scenario: Add backpack to cart and proceed to checkout
    When the user clicks element "add-to-cart-sauce-labs-backpack"
    Then the element "shopping-cart-badge" is visible
    And the element "shopping-cart-badge" has text "1"
    When the user clicks element "checkout"
    Then the page title equals "Swag Labs"`,
  },
  {
    id: 'terminal',
    label: '💻 CLI & MCP ($ testfly record)',
    filename: 'Terminal ($ testfly record)',
    language: 'bash',
    code: `# 1. Start zero-setup Chrome companion and Web Studio (:8765)
$ testfly record https://www.saucedemo.com

# 2. Injected Chrome companion streams clicks, debounced typing & assertions
# 3. Live Smart Locator Tester reports: "getByTestId('checkout') -> 1 match"
# 4. Click 'Save to Project' (saved with Java reserved keyword safeguards):
#    ✔ src/test/java/com/example/pages/InventoryPage.java
#    ✔ src/test/java/com/example/tests/InventoryTest.java
# 5. Connect AI coding assistants (Claude, Cursor, Copilot) to 88 live tools
$ testfly mcp`,
  },
];

export const mavenDependencySnippet = `<dependency>
  <groupId>io.github.hakanngul</groupId>
  <artifactId>testfly</artifactId>
  <version>1.0.5</version>
</dependency>`;

export function getQuickConfig(isTr) {
  return `browser:
  name: chrome
  headless: false
  arguments:
    - --start-maximized
    - --disable-notifications
    - --remote-allow-origins=*
  capabilities:
    acceptInsecureCerts: true
    pageLoadStrategy: normal

execution:
  mode: local
  baseUrl: https://www.saucedemo.com/
  gridUrl: http://localhost:4444/wd/hub
  parallel: methods
  threadCount: 4
  maxActiveSessions: 4

locators:
  selfHealing: true

ai:
  failureAnalysis: false
  provider: openai-compatible     # openai-compatible | claude | gemini | deepseek
  baseUrl: https://api.deepseek.com
  apiKey: "\${AI_API_KEY}"
  model: deepseek-v4-flash
  language: ${isTr ? 'tr' : 'en'}
  timeoutSeconds: 20

recording:
  enabled: true                    ${isTr ? '# Video kaydını aktif eder (varsayılan: false)' : '# Enables test video recording'}
  mode: retain-on-failure          # 'retain-on-failure' | 'on' | 'off'
  format: mp4                      # 'mp4' (H.264 video) | 'gif'
  fps: 5                           ${isTr ? '# Saniyedeki kare sayısı (2-10)' : '# Frames per second (2-10)'}
  maxDurationSeconds: 60           ${isTr ? '# Bellek güvenliği için maksimum kayıt süresi' : '# Max duration safety limit'}
  cdp: true                        ${isTr ? '# Chromium native CDP screencast kullanımı' : '# Native Chromium CDP screencast'}

reporting:
  allureEnabled: true
  reportPortal:
    enabled: false
    endpoint: "\${REPORTPORTAL_ENDPOINT:-https://reportportal.example.com}"
    apiKey: "\${REPORTPORTAL_API_KEY}"
    project: demo-web
    launch: "Demo Web - Dev"
    description: "Automated test execution powered by TestFly"
    attributes: "env:dev"
    type: auto
    mode: default

api:
  baseUrl: https://fakeapi.net
  timeoutSeconds: 30
  logBody: false

retry:
  enabled: false
  maxAttempts: 2

timeouts:
  explicit: 10
  pageLoad: 30`;
}
