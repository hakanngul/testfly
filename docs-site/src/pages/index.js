import React, { useEffect, useState } from 'react';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { useColorMode } from '@docusaurus/theme-common';
import { Highlight } from 'prism-react-renderer';
import Layout from '@theme/Layout';
import styles from './index.module.css';

// ─── Feature data ─────────────────────────────────────────────────────────────

const flagshipFeatures = [
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
      </svg>
    ),
    title: 'Zero Boilerplate',
    description: 'Extend BaseTest, write @Test methods, and go. Driver lifecycle, waits, retries, reports, and screenshots are all handled — no setup code required.',
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
        <path d="M12 16v-4M12 8h.01" />
      </svg>
    ),
    title: 'Tests Survive CSS Refactors',
    description: 'Accessibility-first getByRole / getByText / getByLabel target the accessibility tree — Playwright-style, auto-waiting, and resilient to CSS or DOM refactors.',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z" />
        <path d="m9 12 2 2 4-4" />
      </svg>
    ),
    title: 'Locators That Repair Themselves',
    description: 'When a locator breaks, self-healing falls back through id, name, text, and data-testid automatically — and flags every heal in the report.',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 3v18h18" />
        <path d="M18 17V9M13 17V5M8 17v-3" />
      </svg>
    ),
    title: 'A Report Stakeholders Actually Read',
    visual: 'report',
    description: 'A tabbed HTML dashboard with a pass-rate gauge, retry badges, expandable errors, a Flakiness Radar, trace links, search, and dark mode.',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 2a7 7 0 0 1 7 7c0 2.38-1.19 4.47-3 5.74V17a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2v-2.26C6.19 13.47 5 11.38 5 9a7 7 0 0 1 7-7z" />
        <path d="M9 21h6" />
      </svg>
    ),
    title: 'Know Why a Test Failed',
    description: 'On every failure, AI failure analysis has Claude read the error, steps, and URL, then embeds a plain-English root cause and suggested fix in the report.',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="18" height="18" rx="2" />
        <path d="m9 9 6 6M15 9l-6 6" />
      </svg>
    ),
    title: 'Generate Tests From a Prompt — Coming Soon',
    description: 'AI test authoring via TestFly MCP (coming soon) will let Claude or Copilot drive a real browser and generate ready-to-run TestFly tests from a single prompt.',
  },
];

const moreFeatures = [
  { icon: '📄', title: 'Switch Environments Without Code Changes', short: 'One testfly.yml controls browsers, threads, timeouts, retry, and CI gates.' },
  { icon: '🔁', title: 'Flaky Tests Stop Failing Your Build', short: 'Auto-retry flaky tests and rank them HIGH / WATCH / STABLE in the report.' },
  { icon: '📋', title: 'Write Pages, Not Plumbing', short: 'BasePage wraps clicks, waits, dropdowns, iframes, Shadow DOM, and uploads.' },
  { icon: '🔗', title: 'Pin Down Any Element', short: 'Fluent, Playwright-style chainable locators with auto-retrying assertThat().' },
  { icon: '🌐', title: 'Test Without a Real Backend', short: 'Mock API responses over CDP; read and write storage, cookies, and geo.' },
  { icon: '📸', title: 'Catch Visual Regressions', short: 'Pixel-diff screenshots and one-line device emulation for 6 mobile profiles.' },
  { icon: '🪜', title: 'Read the Test Like a Spec', short: 'Step logging — named steps with screenshots and a self-contained failure trace.' },
  { icon: '🔐', title: 'Log In Once, Reuse the Session', short: '@PreCondition runs login once, caches the session, and restores it for every test.' },
  { icon: '📧', title: 'Assert on the Email Your App Sent', short: 'Email verification waits for real emails via Mailhog, Mailtrap, Graph API, or IMAP.' },
  { icon: '🕐', title: 'Test Time Without Waiting for It', short: 'Clock mocking freezes the browser clock to test expiry, trials, and countdowns.' },
  { icon: '☁️', title: 'Run on Real Cloud Browsers', short: 'BrowserStack or Sauce Labs by changing one config line.' },
  { icon: '🔌', title: 'Extend It Without Forking It', short: 'Register custom drivers, report adapters, and hooks via Java SPI / ServiceLoader.' },
];

const faqs = [
  {
    q: 'Do I need to download WebDriver binaries?',
    a: 'No. Selenium Manager (built into Selenium 4) resolves and downloads the right driver automatically. You just need Chrome or Firefox installed.',
  },
  {
    q: 'Does it work with JUnit 5 and Cucumber, or only TestNG?',
    a: 'All three. TestNG is the default, JUnit 5 has full parity via BaseJUnit5Test or @ExtendWith(TestFlyExtension.class), and Cucumber is supported through BaseCucumberSteps + CucumberHooks.',
  },
  {
    q: 'Is testfly.yml required?',
    a: 'No — it is optional. TestFlyDefaults supplies sensible defaults for everything, so the framework runs with zero config. Add testfly.yml only when you want to override a default.',
  },
  {
    q: 'Can I still drop down to the raw Selenium WebDriver?',
    a: 'Always. getDriver() gives you the live WebDriver, and every fluent locator exposes toBy() to hand back a standard Selenium By. TestFly wraps Selenium — it never hides it.',
  },
  {
    q: 'How is this different from Playwright?',
    a: 'TestFly keeps you on the Selenium ecosystem (Grid, cloud vendors, the whole Java tooling world) while giving you the ergonomics people love about Playwright — fluent and accessibility-first locators, auto-waiting assertions, tracing, and codegen.',
  },
  {
    q: 'Does parallel execution work out of the box?',
    a: 'Yes. The driver is held in a ThreadLocal, so parallel TestNG/JUnit runs are isolated by default. Set parallel and threadCount in testfly.yml and go.',
  },
  {
    q: 'What does it cost?',
    a: 'It is free and open source under Apache 2.0, published to Maven Central. Add one dependency and you are done.',
  },
];

const stats = [
  { value: '1', label: 'Dependency to add' },
  { value: '30+', label: 'Built-in features' },
  { value: '7', label: 'CI platforms auto-detected' },
  { value: '0', label: 'Boilerplate required' },
];

// ─── Components ───────────────────────────────────────────────────────────────

function CodeWindow({ filename, code, className, language = 'java' }) {
  const { colorMode } = useColorMode();

  // Custom themes with better contrast for readability
  const lightTheme = {
    plain: { color: '#1d1d1f', backgroundColor: '#f5f5f7' },
    styles: [
      { types: ['comment', 'prolog', 'doctype', 'cdata'], style: { color: '#6e6e73' } },
      { types: ['punctuation'], style: { color: '#1d1d1f' } },
      { types: ['property', 'tag', 'boolean', 'number', 'constant', 'symbol'], style: { color: '#0071e3' } },
      { types: ['selector', 'attr-name', 'string', 'char', 'builtin'], style: { color: '#34c759' } },
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
        <span className={styles.dot} style={{ background: '#ff5f57' }} />
        <span className={styles.dot} style={{ background: '#febc2e' }} />
        <span className={styles.dot} style={{ background: '#28c840' }} />
        <span className={styles.codeWindowFilename}>{filename}</span>
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

function ReportPreview() {
  return (
    <div className={styles.reportPreview} aria-hidden>
      <div className={styles.reportRing}>
        <span className={styles.reportRingValue}>96%</span>
      </div>
      <div className={styles.reportMeta}>
        <div className={styles.reportChips}>
          <span className={styles.chipPass}>48 passed</span>
          <span className={styles.chipFlaky}>2 flaky</span>
          <span className={styles.chipFail}>0 failed</span>
        </div>
        <div className={styles.reportBar}>
          <span className={styles.reportBarFill} />
        </div>
        <span className={styles.reportCaption}>LoginSuite · 3.2s · chrome 126</span>
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

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function Home() {
  const { siteConfig } = useDocusaurusContext();
  const [openFaq, setOpenFaq] = useState(0);

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
    <Layout title="Home" description={siteConfig.tagline}>
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
          <div className="container">
            <div className={styles.heroInner}>
              <div className={styles.heroText}>
                <span className={styles.heroEyebrow}>Java · Selenium · TestNG · JUnit 5 · Cucumber</span>
                <h1 className={styles.heroTitle}>
                  Test automation
                  <br />
                  <span className={styles.heroTitleAccent}>without the noise</span>
                </h1>
                <p className={styles.heroSubtitle}>{siteConfig.tagline}</p>
                <div className={styles.heroActions}>
                  <Link className={styles.buttonPrimary} to="/docs/getting-started">
                    Get Started
                  </Link>
                  <Link className={styles.buttonSecondary} to="https://github.com/hakanngul/testfly">
                    View on GitHub
                  </Link>
                </div>
                <CodeWindow
                  filename="pom.xml"
                  language='xml'
                  className={styles.heroSnippet}
                  code={`<dependency>
  <groupId>io.testfly</groupId>
  <artifactId>testfly</artifactId>
  <version>1.0.0</version>
</dependency>`}
                />
              </div>

              <div className={styles.heroVisual}>
                <CodeWindow
                  filename="LoginTest.java"
                  code={`public class LoginTest extends BaseTest {

  @Test
  public void userCanSignIn() {
    StepLogger.step("Open app");
    open();

    StepLogger.step("Enter credentials");
    find("#email").type("admin@testfly.io");
    find("#password").type("secret");
    find("[data-testid='sign-in']").click();

    StepLogger.step("Verify dashboard");
    assertThat(find("h1")).hasText("Dashboard");
  }
}`}
                />
              </div>
            </div>
          </div>
        </section>

        {/* ── Stats strip ──────────────────────────────────────────────────── */}
        <section className={styles.statsSection}>
          <div className="container">
            <div className={styles.statsGrid}>
              {stats.map((s, i) => (
                <div key={i} className={styles.statItem} data-reveal style={{ '--i': i }}>
                  <span className={styles.statValue}>{s.value}</span>
                  <span className={styles.statLabel}>{s.label}</span>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* ── Before / After ───────────────────────────────────────────────── */}
        <section className={styles.compareSection}>
          <div className="container">
            <div className={styles.sectionHeader} data-reveal>
              <span className={styles.sectionEyebrow}>Before / After</span>
              <h2 className={styles.sectionTitle}>Same test.<br />None of the plumbing.</h2>
              <p className={styles.sectionSubtitle}>
                TestFly lets your team focus on testing, not framework engineering.
                The waits, the driver setup, the boilerplate — handled.
              </p>
            </div>

            <div className={styles.compareGrid} data-reveal>
              <div className={styles.compareCol}>
                <span className={styles.compareLabel} data-kind="before">Plain Selenium</span>
                <CodeWindow
                  filename="LoginTest.java"
                  className={styles.compareWindow}
                  code={`WebDriverWait wait = new WebDriverWait(
    driver, Duration.ofSeconds(10));

wait.until(ExpectedConditions
    .elementToBeClickable(By.id("login")))
    .click();

wait.until(ExpectedConditions.textToBe(
    By.cssSelector("h1"), "Dashboard"));`}
                />
              </div>

              <div className={styles.compareArrow} aria-hidden>
                <span className={styles.compareArrowIcon}>→</span>
                <span className={styles.compareArrowLabel}>TestFly</span>
              </div>

              <div className={styles.compareCol}>
                <span className={styles.compareLabel} data-kind="after">TestFly</span>
                <CodeWindow
                  filename="LoginTest.java"
                  className={styles.compareWindow}
                  code={`find("#login").click();   // auto-waits

assertThat(find("h1"))
    .hasText("Dashboard");`}
                />
              </div>
            </div>
          </div>
        </section>

        {/* ── Features ─────────────────────────────────────────────────────── */}
        <section className={styles.featuresSection}>
          <div className="container">
            <div className={styles.sectionHeader} data-reveal>
              <span className={styles.sectionEyebrow}>Features</span>
              <h2 className={styles.sectionTitle}>Everything you need,<br />nothing you don't</h2>
              <p className={styles.sectionSubtitle}>
                One dependency. Zero required config. Full-stack automation power, ready the moment you extend BaseTest.
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

            <div className={styles.moreHeader} data-reveal>
              <h3 className={styles.moreTitle}>The complete toolkit</h3>
              <p className={styles.moreSubtitle}>
                Twelve more capabilities, all built in — no plugins, no extra setup.
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

        {/* ── Quick start ───────────────────────────────────────────────────── */}
        <section className={styles.quickSection}>
          <div className="container">
            <div className={styles.quickInner}>
              <div className={styles.quickText} data-reveal>
                <span className={styles.sectionEyebrow}>Quick Start</span>
                <h2 className={styles.quickTitle}>Up and running<br />in 3 minutes</h2>
                <p className={styles.quickSubtitle}>
                  Add the dependency, create a YAML config, extend BaseTest — your first test runs with full reporting, retry, and smart waits already configured.
                </p>
                <Link className={styles.buttonPrimary} to="/docs/getting-started">
                  Read the guide
                </Link>
              </div>

              <div className={styles.quickCode} data-reveal style={{ '--i': 1 }}>
                <CodeWindow
                  filename="testfly.yml"
                  language='yaml'
                  code={`browser:
  name: chrome
  headless: false

execution:
  baseUrl: https://your-app.com

retry:
  enabled: true
  maxAttempts: 2

email:
  provider: mailhog

clock:
  injectHeader: false`}
                />
              </div>
            </div>
          </div>
        </section>

        {/* ── FAQ ──────────────────────────────────────────────────────────── */}
        <section className={styles.faqSection}>
          <div className="container">
            <div className={styles.sectionHeader} data-reveal>
              <span className={styles.sectionEyebrow}>FAQ</span>
              <h2 className={styles.sectionTitle}>Questions, answered</h2>
              <p className={styles.sectionSubtitle}>
                The things teams ask before adopting TestFly.
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
              <h2 className={styles.ctaTitle}>Ready to delete your boilerplate?</h2>
              <p className={styles.ctaSubtitle}>
                One dependency. One YAML file. Tests that read like intent.
              </p>
              <div className={styles.ctaActions}>
                <Link className={styles.buttonPrimary} to="/docs/getting-started">
                  Get Started
                </Link>
                <Link className={styles.buttonSecondary} to="https://github.com/hakanngul/testfly">
                  View on GitHub
                </Link>
              </div>
            </div>
          </div>
        </section>
      </main>
    </Layout>
  );
}
