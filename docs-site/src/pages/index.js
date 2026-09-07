import React, { useEffect, useState } from 'react';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { useColorMode } from '@docusaurus/theme-common';
import { Highlight } from 'prism-react-renderer';
import Layout from '@theme/Layout';
import styles from './index.module.css';

// ─── Hero Code Showcase Tabs ──────────────────────────────────────────────────

const heroTabs = [
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
];

// ─── Feature Data ─────────────────────────────────────────────────────────────

function getFlagshipFeatures(isTr) {
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
      title: isTr ? 'Model Context Protocol (88 Yerleşik MCP Aracı)' : 'Model Context Protocol (88 MCP Tools)',
      description: isTr
        ? 'Claude Code, JetBrains AI Assistant, GitHub Copilot ve Google Antigravity için 88 tarayıcı otomasyon aracı. Hayali seçiciler yerine canlı tarayıcıdan doğrulanmış kod üretimi.'
        : 'Native MCP server exposing 88 browser automation tools to Claude Code, JetBrains AI, Copilot, and Google Antigravity. Inspects live accessibility trees for 100% verified test generation.',
    },
  ];
}

function getMoreFeatures(isTr) {
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
  ];
}

const compareScenarios = [
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
    badgeAfterEn: 'Healed in 42ms · Auto-PR patch ready',
    badgeAfterTr: '42ms’de onarıldı · Otomatik PR yaması hazır',
  },
  {
    id: 'session',
    labelEn: '🔐 Session Caching (@PreCondition)',
    labelTr: '🔐 Oturum Önbelleği (@PreCondition)',
    taglineEn: 'Re-logging in on every single test vs. instant cookie & storage hydration',
    taglineTr: 'Her testte tekrar tekrar login olmak yerine anında oturum enjeksiyonu',
    filename: 'BillingTest.java',
    beforeEn: `// Plain Selenium: 100 tests re-logging in over slow UI
driver.get("https://app.com/login");
driver.findElement(By.id("user")).sendKeys("admin");
driver.findElement(By.id("pass")).sendKeys("secret");
driver.findElement(By.id("submit")).click();
wait.until(ExpectedConditions.urlContains("/dashboard"));

// 8-15 seconds wasted per test · Flaky login rate-limits`,
    beforeTr: `// Geleneksel Selenium: 100 testin her biri yavaş UI'dan tekrar tekrar giriş yapar
driver.get("https://app.com/login");
driver.findElement(By.id("user")).sendKeys("admin");
driver.findElement(By.id("pass")).sendKeys("secret");
driver.findElement(By.id("submit")).click();
wait.until(ExpectedConditions.urlContains("/dashboard"));

// Her testte 8-15 saniye kayıp · Kırılgan login rate-limit hataları`,
    afterEn: `// TestFly: Login once, cache session state for entire suite
@PreCondition(AdminSession.class)
@Test
void verifyInvoiceSummary() {
    open("/dashboard/billing"); // Instant authenticated landing!
    assertThat(find("#invoice-table")).hasRowCount(5);
}`,
    afterTr: `// TestFly: Bir kez giriş yapın, oturumu tüm test paketine önbellekleyin
@PreCondition(AdminSession.class)
@Test
void verifyInvoiceSummary() {
    open("/dashboard/billing"); // Oturumu anında açık başlar!
    assertThat(find("#invoice-table")).hasRowCount(5);
}`,
    badgeBeforeEn: '12s wasted per test · Fragile UI logins',
    badgeBeforeTr: 'Her testte 12s kayıp · Kırılgan UI login',
    badgeAfterEn: '0.1s instant restore · 80% faster suites',
    badgeAfterTr: '0.1s anında oturum · %80 daha hızlı koşan suite',
  },
];

function getFaqs(isTr) {
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
      q: 'Can I still drop down to the raw Selenium WebDriver and CDP APIs?',
      a: 'Always. getDriver() returns the live WebDriver instance, and every fluent locator exposes .toBy(). Furthermore, native CDP integration lets you intercept network traffic, mock REST responses, manipulate browser cookies, and emulate geo-locations without third-party proxies.',
    },
  ];
}

const stats = [
  { value: '1', label: 'Single Maven Dependency' },
  { value: '1.0.2', label: 'Latest Stable Release' },
  { value: '<50ms', label: 'Frozen AI Action Replay' },
  { value: '88', label: 'Built-in MCP Tools' },
];

// ─── Code Window Component ────────────────────────────────────────────────────

function CodeWindow({ filename, code, className, language = 'java' }) {
  const { colorMode } = useColorMode();

  const lightTheme = {
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

  const darkTheme = {
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

  const prismTheme = colorMode === 'dark' ? darkTheme : lightTheme;

  return (
    <div className={`${styles.codeWindow} ${className || ''}`}>
      <div className={styles.codeWindowBar}>
        <div className={styles.codeWindowDots}>
          <span className={styles.dot} style={{ background: '#ff5f57' }} />
          <span className={styles.dot} style={{ background: '#febc2e' }} />
          <span className={styles.dot} style={{ background: '#28c840' }} />
        </div>
        {filename && <span className={styles.codeWindowFilename}>{filename}</span>}
      </div>
      <Highlight theme={prismTheme} code={code.trim()} language={language}>
        {({ className: hlClass, style, tokens, getLineProps, getTokenProps }) => (
          <pre className={`${styles.codeWindowBody} ${hlClass}`} style={{ ...style, background: 'transparent' }}>
            {tokens.map((line, i) => (
              <div key={i} {...getLineProps({ line })}>
                {line.map((token, key) => (
                  <span key={key} {...getTokenProps({ token })} />
                ))}
              </div>
            ))}
          </pre>
        )}
      </Highlight>
    </div>
  );
}

function HeroCodeShowcase() {
  const [activeTab, setActiveTab] = useState(0);
  const current = heroTabs[activeTab];
  const { colorMode } = useColorMode();

  const lightTheme = {
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

  const darkTheme = {
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

  const prismTheme = colorMode === 'dark' ? darkTheme : lightTheme;

  return (
    <div className={styles.codeWindow}>
      <div className={styles.codeWindowBar}>
        <div className={styles.codeWindowDots}>
          <span className={styles.dot} style={{ background: '#ff5f57' }} />
          <span className={styles.dot} style={{ background: '#febc2e' }} />
          <span className={styles.dot} style={{ background: '#28c840' }} />
        </div>
        <div className={styles.codeTabList} role="tablist">
          {heroTabs.map((tab, idx) => (
            <button
              key={tab.id}
              role="tab"
              aria-selected={activeTab === idx}
              className={`${styles.codeTab} ${activeTab === idx ? styles.codeTabActive : ''}`}
              onClick={() => setActiveTab(idx)}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <span className={styles.codeWindowFilename}>{current.filename}</span>
      </div>
      <Highlight theme={prismTheme} code={current.code.trim()} language={current.language}>
        {({ className: hlClass, style, tokens, getLineProps, getTokenProps }) => (
          <pre className={`${styles.codeWindowBody} ${hlClass}`} style={{ ...style, background: 'transparent' }}>
            {tokens.map((line, i) => (
              <div key={i} {...getLineProps({ line })}>
                {line.map((token, key) => (
                  <span key={key} {...getTokenProps({ token })} />
                ))}
              </div>
            ))}
          </pre>
        )}
      </Highlight>
    </div>
  );
}

function ReportPreview() {
  return (
    <div className={styles.reportPreview} aria-hidden>
      <div className={styles.reportRing}>
        <span className={styles.reportRingValue}>98%</span>
      </div>
      <div className={styles.reportMeta}>
        <div className={styles.reportChips}>
          <span className={styles.chipPass}>54 passed</span>
          <span className={styles.chipFlaky}>1 healed</span>
          <span className={styles.chipFail}>0 failed</span>
        </div>
        <div className={styles.reportBar}>
          <span className={styles.reportBarFill} />
        </div>
        <span className={styles.reportCaption}>AgenticSuite · 1.8s · chrome 126 · Thread-isolated</span>
      </div>
    </div>
  );
}

function FaqItem({ item, isOpen, onToggle }) {
  return (
    <div className={styles.faqItem} data-open={isOpen ? '' : undefined}>
      <button className={styles.faqQuestion} onClick={onToggle} aria-expanded={isOpen}>
        <span>{item.q}</span>
        <span className={styles.faqChevron} aria-hidden>›</span>
      </button>
      <div className={styles.faqAnswerWrap}>
        <div className={styles.faqAnswer}>
          <p>{item.a}</p>
        </div>
      </div>
    </div>
  );
}

// ─── Main Page ────────────────────────────────────────────────────────────────

export default function Home() {
  const { siteConfig, i18n } = useDocusaurusContext();
  const isTr = i18n.currentLocale === 'tr';
  const [openFaq, setOpenFaq] = useState(0);
  const [activeCompareTab, setActiveCompareTab] = useState('dynamic');

  const flagshipFeatures = getFlagshipFeatures(isTr);
  const moreFeatures = getMoreFeatures(isTr);
  const faqs = getFaqs(isTr);
  const currentScenario =
    compareScenarios.find((s) => s.id === activeCompareTab) || compareScenarios[0];

  useEffect(() => {
    const els = document.querySelectorAll('[data-reveal]');
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (e.isIntersecting) {
            e.target.setAttribute('data-visible', '');
            observer.unobserve(e.target);
          }
        });
      },
      { threshold: 0.1, rootMargin: '0px 0px -40px 0px' }
    );
    els.forEach((el) => observer.observe(el));
    return () => observer.disconnect();
  }, []);

  return (
    <Layout title={isTr ? 'Ana Sayfa' : 'Home'} description={siteConfig.tagline}>
      <main>
        {/* ── Hero ─────────────────────────────────────────────────────────── */}
        <section className={styles.hero}>
          <div className={styles.heroBackground} aria-hidden />
          <img
            src={useBaseUrl('/img/logo2.svg')}
            alt=""
            className={styles.heroLogoWatermark}
            aria-hidden
          />
          <div className={`container ${styles.heroContainer}`}>
            <div className={styles.heroContent}>
              <div className={styles.heroHeader}>
                <span className={styles.heroEyebrow}>
                  Java 17 · Selenium 4 · TestNG · JUnit 5 · Cucumber · AI/MCP
                </span>
                <h1 className={styles.heroTitle}>
                  {isTr ? (
                    <>
                      Gürültüden uzak{' '}
                      <span className={styles.heroTitleAccent}>test otomasyonu</span>
                    </>
                  ) : (
                    <>
                      Test automation{' '}
                      <span className={styles.heroTitleAccent}>without the noise</span>
                    </>
                  )}
                </h1>
              </div>

              <div className={styles.heroShowcaseWrap}>
                <HeroCodeShowcase />
              </div>

              <p className={styles.heroSubtitle}>
                {isTr
                  ? "Modern mühendislik ekipleri için geliştirilmiş, sıfır ek yüklü Java test otomasyon SDK'sı. TestFly; Selenium 4 altyapısını otonom Agentic AI, kendi kendini onaran seçiciler ve 88 yerleşik MCP aracıyla tek bir konfigürasyonsuz mimaride birleştirir."
                  : 'An opinionated, zero-overhead Java test automation SDK engineered for modern teams. TestFly unifies convention-over-configuration Selenium 4 with autonomous Agentic AI, self-healing locators, and 88 protocol-native MCP tools.'}
              </p>

              <div className={styles.heroBottom}>
                <div className={styles.heroActions}>
                  <Link className={styles.buttonPrimary} to="/docs/getting-started">
                    {isTr ? 'Hemen Başlayın' : 'Get Started'}
                  </Link>
                  <Link className={styles.buttonSecondary} to="/docs/ai/agentic-testing">
                    {isTr ? '🤖 Otonom AI Rehberi' : '🤖 Agentic AI Guide'}
                  </Link>
                  <Link className={styles.buttonSecondary} to="https://github.com/hakanngul/testfly">
                    GitHub
                  </Link>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* ── Stats Strip ──────────────────────────────────────────────────── */}
        <section className={styles.statsSection}>
          <div className="container">
            <div className={styles.statsGrid}>
              {stats.map((s, i) => (
                <div key={i} className={styles.statItem} data-reveal style={{ '--i': i }}>
                  <span className={styles.statValue}>{s.value}</span>
                  <span className={styles.statLabel}>
                    {isTr
                      ? i === 0
                        ? 'Tek Maven Bağımlılığı'
                        : i === 1
                        ? 'Güncel Sürüm'
                        : i === 2
                        ? 'Dondurulmuş AI Oynatma Hızı'
                        : 'Yerleşik MCP Aracı'
                      : s.label}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* ── Before / After ───────────────────────────────────────────────── */}
        <section className={styles.compareSection}>
          <div className="container">
            <div className={styles.sectionHeader} data-reveal>
              <span className={styles.sectionEyebrow}>{isTr ? 'Öncesi / Sonrası' : 'Before / After'}</span>
              <h2 className={styles.sectionTitle}>
                {isTr ? (
                  <>
                    Tesisat kodları değil,
                    <br />
                    <span className={styles.sectionTitleAccent}>gerçek testler yazın</span>
                  </>
                ) : (
                  <>
                    Write tests,
                    <br />
                    <span className={styles.sectionTitleAccent}>not framework plumbing</span>
                  </>
                )}
              </h2>
              <p className={styles.sectionSubtitle}>
                {isTr
                  ? "TestFly'ın kırılgan explicit wait'leri, çöken seçici yamalarını ve saatler alan oturum kurulumlarını nasıl tek satırlık temiz ve dirençli otomasyona dönüştürdüğünü görün."
                  : 'See how TestFly replaces hundreds of lines of fragile waits, broken locator workarounds, and repetitive setup with clean, self-healing automation.'}
              </p>
            </div>

            {/* Scenario Tabs */}
            <div className={styles.compareTabsWrap} data-reveal>
              <div className={styles.compareTabs} role="tablist">
                {compareScenarios.map((sc) => {
                  const isActive = sc.id === currentScenario.id;
                  return (
                    <button
                      key={sc.id}
                      type="button"
                      role="tab"
                      aria-selected={isActive}
                      className={`${styles.compareTab} ${isActive ? styles.compareTabActive : ''}`}
                      onClick={() => setActiveCompareTab(sc.id)}
                    >
                      {isTr ? sc.labelTr : sc.labelEn}
                    </button>
                  );
                })}
              </div>
              <p className={styles.compareTagline}>
                {isTr ? currentScenario.taglineTr : currentScenario.taglineEn}
              </p>
            </div>

            <div className={styles.compareGrid} data-reveal>
              <div className={styles.compareCol}>
                <div className={styles.compareColHeader}>
                  <span className={styles.compareLabel} data-kind="before">
                    {isTr ? 'Geleneksel Selenium' : 'Plain Selenium'}
                  </span>
                  <span className={styles.compareBadgeBefore}>
                    {isTr ? currentScenario.badgeBeforeTr : currentScenario.badgeBeforeEn}
                  </span>
                </div>
                <CodeWindow
                  filename={currentScenario.filename}
                  className={styles.compareWindow}
                  code={isTr ? currentScenario.beforeTr : currentScenario.beforeEn}
                />
              </div>

              <div className={styles.compareArrow} aria-hidden>
                <span className={styles.compareArrowIcon}>→</span>
                <span className={styles.compareArrowLabel}>TestFly</span>
              </div>

              <div className={styles.compareCol}>
                <div className={styles.compareColHeader}>
                  <span className={styles.compareLabel} data-kind="after">
                    TestFly
                  </span>
                  <span className={styles.compareBadgeAfter}>
                    {isTr ? currentScenario.badgeAfterTr : currentScenario.badgeAfterEn}
                  </span>
                </div>
                <CodeWindow
                  filename={currentScenario.filename}
                  className={styles.compareWindow}
                  code={isTr ? currentScenario.afterTr : currentScenario.afterEn}
                />
              </div>
            </div>
          </div>
        </section>

        {/* ── Features ─────────────────────────────────────────────────────── */}
        <section className={styles.featuresSection}>
          <div className="container">
            <div className={styles.sectionHeader} data-reveal>
              <span className={styles.sectionEyebrow}>{isTr ? 'Öne Çıkan Özellikler' : 'Features'}</span>
              <h2 className={styles.sectionTitle}>
                {isTr ? (
                  <>
                    İhtiyacınız olan her şey,
                    <br />
                    gürültüden uzak
                  </>
                ) : (
                  <>
                    Everything you need,
                    <br />
                    nothing you don't
                  </>
                )}
              </h2>
              <p className={styles.sectionSubtitle}>
                {isTr
                  ? "Tek bağımlılık. Sıfır zorunlu ayar. BaseTest'i extend ettiğiniz anda kurumsal güçte test altyapısı hazır."
                  : 'One dependency. Zero required config. Full-stack automation power ready the moment you extend BaseTest.'}
              </p>
            </div>

            <div className={styles.bentoGrid}>
              {flagshipFeatures.map((f, i) => (
                <div
                  key={f.title}
                  className={styles.bentoCard}
                  data-reveal
                  style={{ '--i': i % 3 }}
                >
                  <div className={styles.featureIconWrap}>{f.icon}</div>
                  <h3 className={styles.bentoTitle}>{f.title}</h3>
                  <p className={styles.bentoDesc}>{f.description}</p>
                  {f.code && (
                    <CodeWindow
                      filename=""
                      language="java"
                      code={f.code}
                      className={styles.bentoCode}
                    />
                  )}
                  {f.visual === 'report' && <ReportPreview />}
                </div>
              ))}
            </div>

            {/* ── Maven Central Dependency Banner ──────────────────────────────── */}
            <div className={styles.installBanner} data-reveal>
              <div className={styles.installText}>
                <span className={styles.sectionEyebrow}>
                  {isTr ? 'Maven Central · Sıfır Konfigürasyon' : 'Maven Central · Zero Boilerplate'}
                </span>
                <h3 className={styles.installTitle}>
                  {isTr ? 'Tek Bir Bağımlılıkla Tüm Gücü Açın' : 'One Dependency to Power Your Entire Stack'}
                </h3>
                <p className={styles.installSubtitle}>
                  {isTr
                    ? 'Bağımlılık karmaşasına ve versiyon çakışmalarına son: Selenium 4, akıllı beklemeler, native CDP video kaydı, AI analiz motoru ve test çalıştırıcı köprüleri tek bir standart JAR altında çakışmasız gelir.'
                    : 'No dependency conflicts or classpath bloat. TestFly bundles Selenium 4, smart waits, native CDP screencast, AI engine, and test runner bridges in a single verified artifact.'}
                </p>
                <div className={styles.installBadges}>
                  <span className={styles.installBadge}>
                    <span className={styles.installBadgeDot} />
                    Java 17+
                  </span>
                  <span className={styles.installBadge}>
                    <span className={styles.installBadgeDot} />
                    Maven Central v1.0.2
                  </span>
                  <span className={styles.installBadge}>
                    <span className={styles.installBadgeDot} />
                    {isTr ? 'Sıfır Çakışma' : 'Zero Conflicts'}
                  </span>
                  <span className={styles.installBadge}>
                    <span className={styles.installBadgeDot} />
                    Apache 2.0
                  </span>
                </div>
              </div>
              <div className={styles.installCodeWrap}>
                <CodeWindow
                  filename="pom.xml"
                  language="xml"
                  className={styles.installCode}
                  code={`<dependency>
  <groupId>io.github.hakanngul</groupId>
  <artifactId>testfly</artifactId>
  <version>1.0.2</version>
</dependency>`}
                />
              </div>
            </div>

            <div className={styles.moreHeader} data-reveal>
              <h3 className={styles.moreTitle}>
                {isTr ? 'Eksiksiz Test Araç Seti' : 'The Complete Toolkit'}
              </h3>
              <p className={styles.moreSubtitle}>
                {isTr
                  ? 'Ekstra eklenti veya konfigürasyon gerektirmeyen 16 yerleşik kurumsal yetenek.'
                  : 'Sixteen enterprise capabilities, all built in — no plugins, no extra setup.'}
              </p>
            </div>

            <div className={styles.miniGrid}>
              {moreFeatures.map((f, i) => (
                <div
                  key={f.title}
                  className={styles.miniCard}
                  data-reveal
                  style={{ '--i': i % 4 }}
                >
                  <span className={styles.miniIcon}>{f.icon}</span>
                  <div className={styles.miniText}>
                    <h4 className={styles.miniTitle}>{f.title}</h4>
                    <p className={styles.miniDesc}>{f.short}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* ── Quick Start ───────────────────────────────────────────────────── */}
        <section className={styles.quickSection}>
          <div className="container">
            <div className={styles.quickInner}>
              <div className={styles.quickText} data-reveal>
                <span className={styles.sectionEyebrow}>
                  {isTr ? 'Deklaratif Test Orkestrasyonu' : 'Declarative Orchestration'}
                </span>
                <h2 className={styles.quickTitle}>
                  {isTr ? (
                    <>
                      Tek bir YAML dosyası,
                      <br />
                      <span className={styles.quickTitleAccent}>tüm kurumsal test gücü</span>
                    </>
                  ) : (
                    <>
                      One unified config,
                      <br />
                      <span className={styles.quickTitleAccent}>complete test orchestration</span>
                    </>
                  )}
                </h2>
                <p className={styles.quickSubtitle}>
                  {isTr
                    ? 'Yüzlerce satırlık kırılgan driver fabrikaları ve dağınık setup script’leri yazmaya son. ThreadLocal paralel thread yönetimi, native Chromium CDP MP4 video kaydı, Allure ve ReportPortal senkronizasyonu, DeepSeek/OpenAI kök neden tespiti ve kendi kendini onaran seçicileri tek bir standart YAML ile canlı yönetin.'
                    : 'Stop writing fragile framework plumbing. Configure ThreadLocal driver isolation, native Chromium CDP MP4 recordings, live Allure & ReportPortal sync, DeepSeek/OpenAI failure diagnosis, and self-healing locators—all driven by a single, type-safe configuration.'}
                </p>

                <div className={styles.quickPills}>
                  <div className={styles.quickPill}>
                    <span className={styles.quickPillIcon}>⚡</span>
                    <div>
                      <strong>{isTr ? 'ThreadLocal Paralel İzolasyon' : 'ThreadLocal Parallel Engine'}</strong>
                      <span>
                        {isTr
                          ? 'Methods veya classes düzeyinde güvenli paralelleştirme'
                          : 'Safe concurrent test execution at method/class level'}
                      </span>
                    </div>
                  </div>
                  <div className={styles.quickPill}>
                    <span className={styles.quickPillIcon}>🎥</span>
                    <div>
                      <strong>{isTr ? 'Native Chromium CDP MP4 Screencast' : 'Native CDP MP4 Screencast'}</strong>
                      <span>
                        {isTr
                          ? 'Yalnızca hata anında saklanan sıfır ek yükte video kaydı'
                          : 'Zero-overhead video, retained only on test failure'}
                      </span>
                    </div>
                  </div>
                  <div className={styles.quickPill}>
                    <span className={styles.quickPillIcon}>🧠</span>
                    <div>
                      <strong>{isTr ? 'DeepSeek & OpenAI AI Analiz Motoru' : 'DeepSeek & OpenAI AI Engine'}</strong>
                      <span>
                        {isTr
                          ? 'Hata kök-neden tespiti ve otomatik git patch üretimi'
                          : 'Plain-English root-cause triage and auto-PR patches'}
                      </span>
                    </div>
                  </div>
                  <div className={styles.quickPill}>
                    <span className={styles.quickPillIcon}>📊</span>
                    <div>
                      <strong>{isTr ? 'Allure & ReportPortal Entegrasyonu' : 'Unified Enterprise Reporting'}</strong>
                      <span>
                        {isTr
                          ? 'HTML zaman çizelgesi, JUnit XML ve canlı dashboard akışı'
                          : 'Interactive HTML timeline, JUnit XML, and live dashboard sync'}
                      </span>
                    </div>
                  </div>
                </div>

                <div className={styles.quickActions}>
                  <Link className={styles.buttonPrimary} to="/docs/configuration">
                    {isTr ? 'Konfigürasyon Rehberini İncele' : 'Explore Config Reference'}
                  </Link>
                  <Link className={styles.buttonSecondary} to="/docs/getting-started">
                    {isTr ? 'Hızlı Başlangıç Rehberi' : 'Quickstart Guide'}
                  </Link>
                </div>
              </div>

              <div className={styles.quickCode} data-reveal style={{ '--i': 1 }}>
                <CodeWindow
                  filename="testfly.yml"
                  language="yaml"
                  code={`browser:
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
  enabled: true                    ${isTr ? "# Video kaydını aktif eder (varsayılan: false)" : "# Enables test video recording"}
  mode: retain-on-failure          # 'retain-on-failure' | 'on' | 'off'
  format: mp4                      # 'mp4' (H.264 video) | 'gif'
  fps: 5                           ${isTr ? "# Saniyedeki kare sayısı (2-10)" : "# Frames per second (2-10)"}
  maxDurationSeconds: 60           ${isTr ? "# Bellek güvenliği için maksimum kayıt süresi" : "# Max duration safety limit"}
  cdp: true                        ${isTr ? "# Chromium native CDP screencast kullanımı" : "# Native Chromium CDP screencast"}

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
  pageLoad: 30`}
                />
              </div>
            </div>
          </div>
        </section>

        {/* ── FAQ ──────────────────────────────────────────────────────────── */}
        <section className={styles.faqSection}>
          <div className="container">
            <div className={styles.sectionHeader} data-reveal>
              <span className={styles.sectionEyebrow}>{isTr ? 'Sıkça Sorulan Sorular' : 'FAQ'}</span>
              <h2 className={styles.sectionTitle}>
                {isTr ? 'Kritik Sorular & Cevaplar' : 'Questions, Answered'}
              </h2>
              <p className={styles.sectionSubtitle}>
                {isTr
                  ? 'Mühendislik ekiplerinin ve QA liderlerinin TestFly hakkında en çok merak ettikleri.'
                  : 'The key architectural questions engineering teams ask before adopting TestFly.'}
              </p>
            </div>

            <div className={styles.faqList} data-reveal>
              {faqs.map((item, i) => (
                <FaqItem
                  key={i}
                  item={item}
                  isOpen={openFaq === i}
                  onToggle={() => setOpenFaq(openFaq === i ? null : i)}
                />
              ))}
            </div>
          </div>
        </section>

        {/* ── Closing CTA ──────────────────────────────────────────────────── */}
        <section className={styles.ctaSection}>
          <div className="container">
            <div className={styles.ctaCard} data-reveal>
              <img
                src={useBaseUrl('/img/logo3.svg')}
                alt=""
                className={styles.ctaLogo}
                aria-hidden
              />
              <h2 className={styles.ctaTitle}>
                {isTr
                  ? 'Kırılgan testlerinize ve altyapı yükünüze veda edin'
                  : 'Ready to delete your boilerplate?'}
              </h2>
              <p className={styles.ctaSubtitle}>
                {isTr
                  ? 'Tek bağımlılık. Tek YAML dosyası. Niyetinizi doğrudan koda döken otonom testler.'
                  : 'One dependency. One YAML file. Tests that read like intent.'}
              </p>
              <div className={styles.ctaActions}>
                <Link className={styles.buttonPrimary} to="/docs/getting-started">
                  {isTr ? 'Hemen Başlayın' : 'Get Started'}
                </Link>
                <Link className={styles.buttonSecondary} to="https://github.com/hakanngul/testfly">
                  {isTr ? "GitHub'da İnceleyin" : 'View on GitHub'}
                </Link>
              </div>
            </div>
          </div>
        </section>
      </main>
    </Layout>
  );
}
