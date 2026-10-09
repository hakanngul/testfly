import React from 'react';

// ─── Prism Themes ─────────────────────────────────────────────────────────────

export const prismLightTheme = {
  plain: { color: '#1d1d1f', backgroundColor: '#f5f5f7' },
  styles: [
    { types: ['comment', 'prolog', 'doctype', 'cdata'], style: { color: '#6e6e73' } },
    { types: ['punctuation'], style: { color: '#1d1d1f' } },
    { types: ['namespace', 'package'], style: { color: '#3a3a3c' } },
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
    { types: ['namespace', 'package'], style: { color: '#e5e5ea' } },
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
    code: `import io.testfly.test.BasePage;
import java.util.Map;

public class InventoryPage extends BasePage {

  // DOM contract: each button has data-testid="add-to-cart-{product-id}".
  private static final Map<String, String> PRODUCT_IDS = Map.of(
      "Sauce Labs Backpack", "sauce-labs-backpack",
      "Sauce Labs Bike Light", "sauce-labs-bike-light");

  public InventoryPage addToCart(String itemName) {
    String productId = PRODUCT_IDS.get(itemName);
    if (productId == null) {
      throw new IllegalArgumentException("Unknown inventory item: " + itemName);
    }

    // Plain, unfiltered CSS locators are eligible for configured self-healing.
    find("[data-testid='add-to-cart-" + productId + "']").click();
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
    label: '🌐 API & Mocking',
    filename: 'PaymentApiTest.java',
    language: 'java',
    code: `import io.testfly.client.ApiAuth;
import io.testfly.client.ApiMockRule;
import io.testfly.client.ApiResponse;
import io.testfly.test.BaseApiTest;
import org.testng.annotations.Test;

public class PaymentApiTest extends BaseApiTest {

  @Test
  public void checkoutWithClientSideMock() {
    ApiMockRule orderMock = ApiMockRule.builder()
        .match(request -> request.method().equals("POST")
            && request.uri().getPath().equals("/orders"))
        .respond(request -> ApiResponse.builder()
            .request(request)
            .status(201)
            .header("Content-Type", "application/json")
            .body("{\\"orderId\\":\\"order-123\\"}")
            .durationMs(12)
            .build())
        .build();

    apiPost("https://api.example.test/orders")
         .auth(ApiAuth.bearerToken(System.getenv("AUTH_TOKEN")))
         .mockRule(orderMock)
         .body(java.util.Map.of("itemId", "item-42", "quantity", 1))
         .send()
         .assertStatus(201)
         .assertJsonExists("$.orderId")
         .assertDurationLessThan(500);
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
    label: '🔌 MCP Bridge',
    filename: 'node bin/testfly-mcp.js --help',
    language: 'bash',
    code: `# Install the separate Node MCP bridge from its source checkout
$ node bin/testfly-mcp.js --help

# Start it as an MCP server from a compatible assistant:
# node /absolute/path/testfly-mcp/bin/testfly-mcp.js
# Pair with Playwright MCP for browser inspection.
# Supply observed actions to generate_testfly_code and review the Java output.
# Live recording and 'testfly record' are not part of the current bridge.`,
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
        ? 'act("...") ile doğal dil hedeflerini çalıştırın. İlk koşuda somut Selenium adımlarına derlenir, .testfly/action-cache.json dosyasına dondurulur ve önbellek isabetlerinde yeni bir LLM isteği olmadan yeniden oynatılır.'
        : 'Execute high-level natural language goals via act("..."). The first run compiles concrete Selenium steps into .testfly/action-cache.json; cache hits replay without a new LLM request.',
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
        ? 'Kırılgan metin eşleşmeleri yerine LLM muhakemesiyle sayfa veya element durumunu doğrulayın. Her doğrulama DOM üzerinde tek bir AI değerlendirmesi yapar; sağlayıcı kota sınırları yine geçerlidir.'
        : 'Verify complex visual or logical state using LLM reasoning against the live DOM. Each assertion performs one AI evaluation; provider rate limits still apply.',
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
      title: isTr ? 'Ayrı MCP Köprüsü & Kod Üretimi' : 'Separate MCP Bridge & Codegen',
      description: isTr
        ? 'Java SDK’dan ayrı Node.js köprüsü altı MCP aracı sunar. Canlı tarayıcı denetimi için Playwright MCP kullanın; gözlemlenen adımları Java test koduna dönüştürüp çıktıyı doğrulayın. Canlı kayıt henüz yok.'
        : 'The separate Node.js bridge exposes six MCP tools. Use Playwright MCP for live browser inspection, then generate and review Java from observed actions. Live recording is not shipped.',
      code: `# From the separate testfly-mcp checkout
node bin/testfly-mcp.js --help
# Configure the script as an MCP server; see the CLI guide.`,
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
        ? 'Akışın gerekli noktalarında accessibility().run() ile axe-core WCAG taramaları ve ayrıntılı ihlal raporları çalıştırın.'
        : 'Run explicit axe-core WCAG scans with accessibility().run() at the points your flow requires.',
    },
    {
      icon: '📈',
      title: isTr ? 'Core Web Vitals & Performans' : 'Core Web Vitals Performance',
      short: isTr
        ? 'Desteklendiği tarayıcılarda LCP, FCP, TTFB ve CLS metriklerini toplayıp performans eşiklerini doğrulayın.'
        : 'Collect LCP, FCP, TTFB, and CLS where the browser exposes them, then assert performance thresholds.',
    },
    {
      icon: '📋',
      title: isTr ? '@TestData Veri Sürücüsü (Excel/CSV)' : 'Data-Driven Testing (@TestData)',
      short: isTr
        ? 'Excel (.xlsx), CSV, JSON veya veritabanı satırını yükleyin; getTestData() ya da tipli anahtar erişimiyle okuyun.'
        : 'Load an Excel, CSV, JSON, or database row and read it through getTestData() or typed key access.',
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
      title: isTr ? 'MCP ile Java Kod Üretimi' : 'MCP Java Code Generation',
      short: isTr
        ? 'Ayrı Node köprüsü, sağladığınız tarayıcı adımlarını Java test koduna çevirir; canlı kaydedici içermez.'
        : 'The separate Node bridge converts supplied browser actions into Java test code; it does not record live sessions.',
    },
    {
      icon: '🤖',
      title: isTr ? 'TestFly MCP Köprüsü (6 Araç)' : 'TestFly MCP Bridge (6 Tools)',
      short: isTr
        ? 'Proje oluşturma, kod üretimi, önbellek ve onarım yönetimi; canlı DOM incelemesi için ayrı Playwright MCP gerekir.'
        : 'Scaffolding, codegen, cache and remediation tools; live DOM inspection requires separate Playwright MCP.',
    },
    {
      icon: '💻',
      title: isTr ? 'TestFly CLI Geliştirici Deneyimi' : 'TestFly CLI Toolkit',
      short: isTr
        ? 'Ayrı Node köprüsünde init, --version ve --help komutları bulunur; record ve studio henüz yoktur.'
        : 'The separate Node bridge supports init, --version and --help; record and studio are not available.',
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
    badgeAfterEn: '2 lines · Built-in auto-waiting',
    badgeAfterTr: '2 satır · Yerleşik akıllı bekleme',
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
    labelEn: '🔌 Browser MCP & Codegen',
    labelTr: '🔌 Tarayıcı MCP & Kod Üretimi',
    taglineEn: 'Inspect with Playwright MCP; hand-write Page Objects or review generated TestNG code',
    taglineTr: 'Playwright MCP ile inceleyin; Page Object yazın veya üretilen TestNG kodunu doğrulayın',
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
    afterEn: `// Illustrative hand-written Page Object; not emitted by the current bridge.
// Playwright MCP inspects the browser; TestFly MCP accepts supplied actions.
public class CartPage extends BasePage {
    private final Locator checkoutBtn = getByTestId("checkout");
    private final Locator firstName = getByTestId("firstName");

    public CartPage proceedToCheckout(String name) {
        firstName.type(name);
        checkoutBtn.click();
        return this;
    }
}
// Save reviewed code in your project; no live recorder is shipped.`,
    afterTr: `// Örnek elle yazılmış Page Object; güncel köprü tarafından üretilmez.
// Playwright MCP tarayıcıyı inceler; TestFly MCP verilen adımları işler.
public class CartPage extends BasePage {
    private final Locator checkoutBtn = getByTestId("checkout");
    private final Locator firstName = getByTestId("firstName");

    public CartPage proceedToCheckout(String name) {
        firstName.type(name);
        checkoutBtn.click();
        return this;
    }
}
// İncelenen kodu projeye kaydedin; canlı kaydedici henüz yok.`,
    badgeBeforeEn: 'Manual locator inspection',
    badgeBeforeTr: 'Elle seçici incelemesi',
    badgeAfterEn: 'Observed actions → reviewed Java',
    badgeAfterTr: 'Gözlemlenen adımlar → incelenen Java',
  },
];

// ─── FAQ Items ────────────────────────────────────────────────────────────────

export function getFaqs(isTr) {
  if (isTr) {
    return [
      {
        q: 'TestFly nedir; yalnızca tarayıcı testleri için mi kullanılır?',
        a: 'TestFly, Java 21 için çok alanlı bir test otomasyon SDK’sıdır. Selenium tabanlı web testlerinin yanında API, yük ve diğer test alanları için de araçlar sunar; tarayıcı sürücüsü framework’ün tamamı değil, bir adaptörüdür.',
      },
      {
        q: 'İlk testime nasıl başlarım?',
        a: 'Java 21 ve Maven ile TestFly bağımlılığını ekleyin, src/test/resources/testfly.yml içinde tarayıcı, çalışma modu ve zaman aşımı ayarlarını tanımlayın. Web testi için BaseTest, API testi için BaseApiTest kullanabilirsiniz. İlk örnek için Başlangıç rehberini izleyin.',
      },
      {
        q: 'testfly.yml zorunlu mu ve nasıl bulunur?',
        a: 'Standart başlatma akışı yapılandırma dosyası arar. Önce -Dtestfly.config ile verilen dosyaya, ardından classpath üzerindeki testfly.yml dosyasına, son olarak çalışma dizinine bakar; testfly.profile verilirse profil dosyasını arar. Hiçbiri bulunmazsa hata verir. Her özelliği YAML’a yazmanız gerekmez.',
      },
      {
        q: 'Hangi test çalıştırıcıları ve tarayıcı ortamları desteklenir?',
        a: 'TestNG, JUnit 5 ve Cucumber BDD adaptörleri bulunur; yaşam döngüsü ve bazı özellikler adaptöre göre değişebilir. Yerel tarayıcılar ve uzak sağlayıcılar, gereken sürücü, yapılandırma ve erişim bilgileri sağlandığında kullanılabilir.',
      },
      {
        q: 'Paralel çalışma ve raporlama nasıl ayarlanır?',
        a: 'TestNG için execution.parallel ve execution.threadCount paralelliği belirler; execution.maxActiveSessions eşzamanlı tarayıcı oturumlarını sınırlar. Yerel HTML raporu ayrı bir ayardır; Allure ve ReportPortal isteğe bağlı entegrasyonlardır. ReportPortal için ayrıca geçerli sunucu ve erişim bilgileri gerekir.',
      },
      {
        q: 'Seçici onarımı ve AI hata analizi varsayılan olarak çalışır mı?',
        a: 'Hayır. locators.selfHealing yerel onarma stratejilerini, locators.aiHealing AI destekli seçici onarımını açar. Hata analizi için ai.failureAnalysis ve uygun sağlayıcı/anahtar gerekir. Başarılı onarma veya analiz garanti edilmez.',
      },
      {
        q: 'AI ile patch üretimi kodumu otomatik değiştirir mi?',
        a: 'Hayır. ai.generatePatch etkinse, AI erişimi varsa, hataya ait kaynak kod bulunursa ve geçerli bir diff üretilebilirse target/remediations/ altında incelenebilecek bir .patch dosyası yazılır. Uygulama kararı size aittir.',
      },
      {
        q: 'Compile & Freeze tekrar eden AI çağrılarını nasıl azaltır?',
        a: 'act(...) ile oluşturulan eylem planı .testfly/action-cache.json içinde saklanabilir. Aynı hedefte önbellek isabeti yeni bir LLM isteğini önler; tarayıcı eylemleri ve beklemeler yine çalışır. Önbellek özelliği ve AI sağlayıcısı kendi ayarlarına bağlıdır.',
      },
      {
        q: 'MCP köprüsü ve tarayıcı kaydedici aynı ürün mü?',
        a: 'Hayır. Java SDK’dan ayrı Node.js MCP köprüsü proje oluşturma ve kod üretme araçları sunar; canlı tarayıcı incelemesi için Playwright MCP kullanılır. Mevcut Node köprüsünde testfly record komutu bulunmaz; bu komut tarihsel Python kaydedicisine aittir. Ayrıntılar için CLI rehberine bakın.',
      },
      {
        q: 'Selenium WebDriver’a doğrudan erişebilir miyim?',
        a: 'Evet. Web testlerinde getDriver() canlı WebDriver oturumunu verir; TestFly Locator nesneleri toBy() ile Selenium By değerine dönüştürülebilir. CDP özellikleri kullanılan tarayıcı ve sürücünün desteğine bağlıdır.',
      },
    ];
  }

  return [
    {
      q: 'What is TestFly? Is it only for browser testing?',
      a: 'TestFly is a multi-domain test automation SDK for Java 21. Alongside Selenium-based web tests, it offers tools for API, load, and other test domains. The browser driver is an adapter, not the entire framework.',
    },
    {
      q: 'How do I get started with my first test?',
      a: 'With Java 21 and Maven, add the TestFly dependency and define browser, execution mode, and timeouts in src/test/resources/testfly.yml. Use BaseTest for a web test or BaseApiTest for an API test. Follow the Getting Started guide for a complete example.',
    },
    {
      q: 'Is testfly.yml required, and where does TestFly look for it?',
      a: 'The standard bootstrap looks for a configuration file: first the path set with -Dtestfly.config, then testfly.yml on the classpath, then in the working directory. Set testfly.profile to select a profile-specific filename. Missing files cause an error; you do not need to list every optional feature in YAML.',
    },
    {
      q: 'Which test runners and browser environments are supported?',
      a: 'TestFly has adapters for TestNG, JUnit 5, and Cucumber BDD; lifecycle and some features can differ by adapter. Local browsers and remote providers work when their required drivers, configuration, and credentials are available.',
    },
    {
      q: 'How do parallel execution and reporting work?',
      a: 'For TestNG, execution.parallel and execution.threadCount control parallelism; execution.maxActiveSessions limits concurrent browser sessions. Local HTML reporting is a separate setting, while Allure and ReportPortal are optional integrations. ReportPortal also requires a valid endpoint and credentials.',
    },
    {
      q: 'Are locator recovery and AI failure analysis enabled by default?',
      a: 'No. locators.selfHealing enables local locator recovery; locators.aiHealing enables AI-assisted recovery. Failure analysis requires ai.failureAnalysis and a configured provider and API key. Neither recovery nor analysis guarantees a successful result.',
    },
    {
      q: 'Does AI patch generation automatically change my code?',
      a: 'No. When ai.generatePatch is enabled, AI access is configured, a source snippet can be found, and a valid diff is returned, TestFly can write a reviewable .patch file under target/remediations/. You decide whether to apply it.',
    },
    {
      q: 'How does Compile & Freeze reduce repeated AI calls?',
      a: 'An action plan created with act(...) can be stored in .testfly/action-cache.json. A cache hit for the same goal avoids a new LLM request; browser actions and waits still run. Caching and AI-provider use depend on their configuration.',
    },
    {
      q: 'Are the MCP bridge and browser recorder the same product?',
      a: 'No. The Node.js MCP bridge is separate from the Java SDK and provides project scaffolding and code-generation tools; live browser inspection uses Playwright MCP. The current Node bridge has no testfly record command: that command belongs to the historical Python recorder. See the CLI guide for details.',
    },
    {
      q: 'Can I access Selenium WebDriver directly?',
      a: 'Yes. In web tests, getDriver() returns the live WebDriver session, and TestFly Locator objects can be converted to Selenium By with toBy(). CDP features depend on browser and driver support.',
    },
  ];
}

export const stats = [
  { value: '1', label: 'Single Maven Dependency', labelTr: 'Tek Maven Bağımlılığı' },
  { value: '1.0.7', label: 'SDK Source Version', labelTr: 'SDK Kaynak Sürümü' },
  { value: '0', label: 'LLM Calls on Cache Hit', labelTr: 'Önbellek İsabetinde LLM Çağrısı' },
  { value: '6', label: 'Separate Bridge Tools', labelTr: 'Ayrı Köprü Aracı' },
];

export const recorderTabs = [
  {
    id: 'pom',
    label: '📄 Page Object (illustrative)',
    filename: 'com/example/pages/InventoryPage.java',
    language: 'java',
    code: `package com.example.pages;

import io.testfly.test.BasePage;
import io.testfly.locator.Locator;
import org.openqa.selenium.WebDriver;

public class InventoryPage extends BasePage {

    // Illustrative locators: verify these against the actual page before use
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
    label: '🧪 POM Test (illustrative)',
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

        // Add assertions after inspecting the actual page
        assertThat(getByTestId("title"))
            .isVisible()
            .hasText("Checkout: Your Information");
    }
}`,
  },
  {
    id: 'bdd',
    label: '🥒 Hand-written Cucumber BDD',
    filename: 'src/test/resources/features/inventory.feature',
    language: 'gherkin',
    code: `# Illustrative feature file; the current bridge does not generate BDD
Feature: E-Commerce Journey
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
    label: '💻 CLI & MCP bridge',
    filename: 'Terminal (testfly-mcp source checkout)',
    language: 'bash',
    code: `# Separate Node bridge; run from its source checkout
$ node bin/testfly-mcp.js --help
$ node bin/testfly-mcp.js init ../my-test-suite

# Configure node /absolute/path/testfly-mcp/bin/testfly-mcp.js
# as an MCP server, plus Playwright MCP for browser inspection.
# Supply observed actions to generate_testfly_code; review and compile Java.
# Live recording ('testfly record') is not available.`,
  },
];

export const mavenDependencySnippet = `<dependency>
  <groupId>io.github.hakanngul</groupId>
  <artifactId>testfly</artifactId>
  <version>1.0.7</version>
</dependency>`;

export function getQuickConfig(isTr) {
  return `browser:
  name: chrome                     # ${isTr ? 'Yerel Chrome sürücüsünü seçer' : 'Selects the local Chrome driver'}
  headless: false                  # ${isTr ? 'Görünür pencere açar' : 'Opens a visible browser window'}
  lifecycle: per-test              # ${isTr ? 'Her testten sonra tarayıcıyı kapatır' : 'Closes the browser after each test'}
  arguments:
    - --start-maximized            # ${isTr ? 'Pencereyi büyütür (headless modda boyut belirler)' : 'Maximizes the window (sets size in headless mode)'}
    - --disable-notifications      # ${isTr ? 'Chrome bildirimlerini kapatır' : 'Disables Chrome notifications'}
    - --remote-allow-origins=*     # ${isTr ? 'Chrome başlatma argümanı olarak iletilir' : 'Passed through as a Chrome launch argument'}
  capabilities:
    acceptInsecureCerts: true      # ${isTr ? 'Geçersiz TLS sertifikalarını kabul eder' : 'Accepts invalid TLS certificates'}
    pageLoadStrategy: normal       # ${isTr ? 'Sayfa yüklenmesinin tamamlanmasını bekler' : 'Waits for full page load'}

execution:
  mode: local                      # ${isTr ? 'Yerel tarayıcı sürücüsünü kullanır' : 'Uses a local browser driver'}
  baseUrl: https://www.saucedemo.com/ # ${isTr ? 'Göreli web adresleri için temel URL' : 'Base URL for relative web navigation'}
  gridUrl: http://localhost:4444/wd/hub # ${isTr ? 'Yalnızca mode: remote iken kullanılır' : 'Used only when mode: remote'}
  parallel: methods                # ${isTr ? 'TestNG metotlarını paralel çalıştırır' : 'Runs TestNG methods in parallel'}
  threadCount: 4                   # ${isTr ? 'TestNG paralel thread sayısı' : 'Number of parallel TestNG threads'}
  maxActiveSessions: 4             # ${isTr ? 'Eşzamanlı tarayıcı oturumu sınırı' : 'Limit on concurrent browser sessions'}

locators:
  selfHealing: true                # ${isTr ? 'Başarısız seçiciler için onarım dener' : 'Attempts recovery for failed locators'}
  aiHealing: false                 # ${isTr ? 'AI seçici onarımı kapalı; anahtarla açılabilir' : 'AI locator healing off; enable with an API key'}

ai:
  failureAnalysis: false          # ${isTr ? 'Hata analizini kapatır' : 'Disables failure analysis'}
  generatePatch: false            # ${isTr ? 'AI patch üretimini kapatır' : 'Disables AI patch generation'}
  provider: openai-compatible     # ${isTr ? 'DeepSeek için OpenAI uyumlu sağlayıcı' : 'OpenAI-compatible provider for DeepSeek'}
  baseUrl: https://api.deepseek.com # ${isTr ? 'AI istekleri için sağlayıcı adresi' : 'Provider URL for AI requests'}
  apiKey: "\${AI_API_KEY}"           # ${isTr ? 'Anahtarı ortam değişkeninden çözer' : 'Resolves the key from an environment variable'}
  model: deepseek-v4-flash         # ${isTr ? 'İsteklerde iletilecek model adı' : 'Model name sent with requests'}
  language: ${isTr ? 'tr' : 'en'}                      # ${isTr ? 'Hata analizi yanıt dili' : 'Failure analysis response language'}
  timeoutSeconds: 20              # ${isTr ? 'AI isteği için zaman aşımı (saniye)' : 'AI request timeout in seconds'}

recording:
  enabled: true                   # ${isTr ? 'Tarayıcı testlerinde video kaydını açar' : 'Enables recording for browser tests'}
  mode: retain-on-failure         # ${isTr ? 'Videoyu yalnızca hatada saklar' : 'Keeps video only on failure'}
  format: mp4                     # ${isTr ? 'Videoyu MP4 olarak kaydeder (hata halinde GIF)' : 'Saves MP4 video (GIF fallback on error)'}
  fps: 5                          # ${isTr ? 'Saniyede hedeflenen kare sayısı' : 'Target frames captured per second'}
  maxDurationSeconds: 60          # ${isTr ? 'Saklanan kareleri fps × süre ile sınırlar' : 'Caps stored frames at fps × duration'}
  cdp: true                       # ${isTr ? 'CDP tercih eder; JUnit 5 bu alanı okumaz' : 'Prefers CDP; JUnit 5 ignores this field'}

reporting:
  allureEnabled: true             # ${isTr ? 'Allure rapor entegrasyonunu açar' : 'Enables Allure reporting integration'}
  htmlReport: true                # ${isTr ? 'Yerel HTML test raporunu üretir' : 'Generates the local HTML test report'}
  reportPortal:
    enabled: false                # ${isTr ? 'ReportPortal aktarımını kapatır' : 'Disables ReportPortal publishing'}
    endpoint: "\${REPORTPORTAL_ENDPOINT:-https://reportportal.example.com}" # ${isTr ? 'Sunucu adresi; değişken yoksa örnek adres' : 'Server URL; example fallback if unset'}
    apiKey: "\${REPORTPORTAL_API_KEY}" # ${isTr ? 'Erişim anahtarını ortamdan çözer' : 'Resolves the access key from the environment'}
    project: demo-web             # ${isTr ? 'ReportPortal proje adı' : 'ReportPortal project name'}
    launch: "Demo Web - Dev"      # ${isTr ? 'Rapor çalıştırması adı' : 'Report launch name'}
    description: "Automated test execution powered by TestFly" # ${isTr ? 'Çalıştırma açıklaması' : 'Launch description'}
    attributes: "env:dev"         # ${isTr ? 'Çalıştırma etiketleri' : 'Launch attributes'}
    type: auto                    # ${isTr ? 'API veya Web çalıştırma türünü belirler' : 'Detects API or Web run type'}
    mode: default                 # ${isTr ? 'Kabul edilir; çalışma akışında kullanımı yok' : 'Accepted; not applied by the runtime'}

api:
  baseUrl: https://fakeapi.net    # ${isTr ? 'API istemcisinin varsayılan temel adresi' : 'Default base URL for the API client'}
  timeoutSeconds: 30              # ${isTr ? 'API istekleri için zaman aşımı (saniye)' : 'API request timeout in seconds'}
  logBody: false                  # ${isTr ? 'Yanıt gövdesini loglarda göstermez' : 'Omits response bodies from logs'}

retry:
  enabled: false                  # ${isTr ? 'Başarısız testlerin yeniden denenmesini kapatır' : 'Disables retries for failed tests'}
  maxAttempts: 2                  # ${isTr ? 'Yalnızca retry açıkken kullanılır' : 'Only used when retries are enabled'}

timeouts:
  explicit: 10                     # ${isTr ? 'Öğe bekleme süresi (saniye)' : 'Element wait timeout in seconds'}
  pageLoad: 30                    # ${isTr ? 'Sayfa yükleme süresi (saniye)' : 'Page load timeout in seconds'}`;
}
