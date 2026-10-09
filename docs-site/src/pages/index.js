import React, { useEffect, useState } from 'react';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { useColorMode } from '@docusaurus/theme-common';
import { Highlight } from 'prism-react-renderer';
import Layout from '@theme/Layout';
import styles from './index.module.css';
import {
  heroTabs,
  getFlagshipFeatures,
  getMoreFeatures,
  compareScenarios,
  getFaqs,
  stats,
  recorderTabs,
  mavenDependencySnippet,
  getQuickConfig,
  prismLightTheme,
  prismDarkTheme,
} from '../data/homeData';

// ─── Code Window Component ────────────────────────────────────────────────────

function CodeWindow({ filename, code, className, language = 'java' }) {
  const { colorMode } = useColorMode();
  const prismTheme = colorMode === 'dark' ? prismDarkTheme : prismLightTheme;

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
  const prismTheme = colorMode === 'dark' ? prismDarkTheme : prismLightTheme;

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

function RecorderCodeShowcase() {
  const [activeTab, setActiveTab] = useState(0);
  const current = recorderTabs[activeTab];
  const { colorMode } = useColorMode();
  const prismTheme = colorMode === 'dark' ? prismDarkTheme : prismLightTheme;

  return (
    <div className={styles.codeWindow}>
      <div className={styles.codeWindowBar}>
        <div className={styles.codeWindowDots}>
          <span className={styles.dot} style={{ background: '#ff5f57' }} />
          <span className={styles.dot} style={{ background: '#febc2e' }} />
          <span className={styles.dot} style={{ background: '#28c840' }} />
        </div>
        <div className={styles.codeTabList} role="tablist">
          {recorderTabs.map((tab, idx) => (
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
                  Java 21 · Selenium 4 · TestNG · JUnit 5 · Cucumber · AI/MCP
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
                  ? "Modern mühendislik ekipleri için Java test otomasyon SDK'sı. TestFly tarayıcı oturumları, API testleri, raporlama ve isteğe bağlı AI araçlarını birleştirir. Ayrı Node.js MCP köprüsü kod üretimi ve proje oluşturma araçları sunar."
                  : 'A Java test automation SDK for modern teams. TestFly combines browser sessions, API testing, reporting, and optional AI tools. A separate Node.js MCP bridge offers code generation and project scaffolding.'}
              </p>

              <div className={styles.heroBottom}>
                <div className={styles.heroActions}>
                  <Link className={styles.buttonPrimary} to="/docs/getting-started">
                    {isTr ? 'Hemen Başlayın' : 'Get Started'}
                  </Link>
                  <Link className={styles.buttonSecondary} to="/docs/guides/video-recording">
                    {isTr ? '🎥 Test Video Kaydı' : '🎥 Test Video Recording'}
                  </Link>
                  <Link className={styles.buttonSecondary} to="/docs/ai/overview">
                    {isTr ? '🤖 AI & MCP Rehberi' : '🤖 AI & MCP Guide'}
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
                    {isTr ? (s.labelTr || s.label) : s.label}
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

            {/* ── SDK Dependency Banner ────────────────────────────────────────── */}
            <div className={styles.installBanner} data-reveal>
              <div className={styles.installText}>
                <span className={styles.sectionEyebrow}>
                  {isTr ? 'SDK Kaynak Sürümü · 1.0.7' : 'SDK Source Version · 1.0.7'}
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
                    Java 21+
                  </span>
                  <span className={styles.installBadge}>
                    <span className={styles.installBadgeDot} />
                    {isTr ? 'Yayın doğrulaması bekleniyor' : 'Release verification pending'}
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
                  code={mavenDependencySnippet}
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

        {/* ── Separate MCP bridge & SDK video recording ───────────────────── */}
        <section className={styles.recorderSection}>
          <div className="container">
            <div className={styles.recorderInner}>
              <div className={styles.recorderText} data-reveal>
                <span className={styles.sectionEyebrow}>
                  {isTr ? 'Java SDK & Ayrı MCP Köprüsü' : 'Java SDK & Separate MCP Bridge'}
                </span>
                <h2 className={styles.recorderTitle}>
                  {isTr ? (
                    <>
                      Testleri videoya kaydedin,
                      <br />
                      <span className={styles.recorderTitleAccent}>gözlemlenen adımlardan Java üretin</span>
                    </>
                  ) : (
                    <>
                      Record test execution,
                      <br />
                      <span className={styles.recorderTitleAccent}>generate Java from observed actions</span>
                    </>
                  )}
                </h2>
                <p className={styles.recorderSubtitle}>
                  {isTr
                    ? 'Java SDK, test çalışırken isteğe bağlı video kaydı alır. Ayrı Node.js MCP köprüsü, sağladığınız tarayıcı adımlarını Java test koduna çevirebilir; tarayıcı incelemesi için Playwright MCP kullanılır. Canlı etkileşim kaydedicisi henüz sunulmaz.'
                    : 'The Java SDK optionally captures execution video. The separate Node.js MCP bridge can turn supplied browser actions into Java test code; browser inspection uses Playwright MCP. A live interaction recorder is not currently shipped.'}
                </p>

                <div className={styles.recorderCode}>
                  <RecorderCodeShowcase />
                </div>

                <div className={styles.recorderPills}>
                  <div className={styles.recorderPill}>
                    <span className={styles.recorderPillIcon}>🎥</span>
                    <div>
                      <strong>
                        {isTr ? 'Test Çalışması Video Kaydı' : 'Test Execution Video'}
                      </strong>
                      <span>
                        {isTr
                          ? 'Chromium için CDP, diğer sürücüler için ekran görüntüsü yedeği; retain-on-failure modu'
                          : 'CDP on Chromium, screenshot fallback for other drivers; retain-on-failure mode'}
                      </span>
                    </div>
                  </div>

                  <div className={styles.recorderPill}>
                    <span className={styles.recorderPillIcon}>👁️</span>
                    <div>
                      <strong>
                        {isTr ? 'Tarayıcı Denetimi' : 'Browser Inspection'}
                      </strong>
                      <span>
                        {isTr
                          ? 'Ayrı Playwright MCP ile canlı sayfayı inceleyin ve gerçek seçicileri doğrulayın'
                          : 'Inspect the live page with separate Playwright MCP and verify real selectors'}
                      </span>
                    </div>
                  </div>

                  <div className={styles.recorderPill}>
                    <span className={styles.recorderPillIcon}>🏗️</span>
                    <div>
                      <strong>
                        {isTr ? 'Sağlanan Adımlardan Kod Üretimi' : 'Codegen From Supplied Actions'}
                      </strong>
                      <span>
                        {isTr
                          ? 'Köprü TestNG Java kodu üretir; çıktıyı projeye eklemeden önce inceleyip derleyin'
                          : 'The bridge emits TestNG Java code; review and compile it before adding it to your project'}
                      </span>
                    </div>
                  </div>

                  <div className={styles.recorderPill}>
                    <span className={styles.recorderPillIcon}>🤖</span>
                    <div>
                      <strong>
                        {isTr ? 'Altı MCP Aracı' : 'Six MCP Tools'}
                      </strong>
                      <span>
                        {isTr
                          ? 'Ayrı köprü: proje oluşturma, kod üretimi, eylem önbelleği ve onarım araçları'
                          : 'Separate bridge: scaffolding, code generation, action cache, and remediation tools'}
                      </span>
                    </div>
                  </div>
                </div>

                <div className={styles.recorderActions}>
                  <Link className={styles.buttonPrimary} to="/docs/guides/video-recording">
                    {isTr ? '🎥 Video Kaydı Rehberi' : '🎥 Video Recording Guide'}
                  </Link>
                  <Link className={styles.buttonSecondary} to="/docs/ai/overview">
                    {isTr ? '🤖 AI & MCP Mimarisi' : '🤖 AI & MCP Architecture'}
                  </Link>
                  <Link className={styles.buttonSecondary} to="/docs/cli">
                    {isTr ? '💻 CLI Kılavuzu' : '💻 CLI Reference'}
                  </Link>
                </div>
              </div>
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
                    ? 'Yüzlerce satırlık driver fabrikaları ve dağınık setup script’leri yazmaya son. Tek YAML ile paralel testleri, tarayıcı oturum sınırını, CDP tercihli MP4 kaydını ve seçici onarımını yapılandırın. Allure açıktır; DeepSeek/OpenAI analizi ve ReportPortal için gerçek erişim bilgilerinizi ekleyip ilgili anahtarları açabilirsiniz.'
                    : 'Stop writing fragile framework plumbing. Configure parallel tests, browser session limits, CDP-preferred MP4 recording, and locator recovery in one YAML file. Allure is enabled; add real credentials and enable the switches for DeepSeek/OpenAI analysis and ReportPortal.'}
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
                          ? 'Test boyunca kaydedilir, yalnızca hata anında saklanır'
                          : 'Captured during tests, retained only on failure'}
                      </span>
                    </div>
                  </div>
                  <div className={styles.quickPill}>
                    <span className={styles.quickPillIcon}>🧠</span>
                    <div>
                      <strong>{isTr ? 'DeepSeek & OpenAI AI Analiz Motoru' : 'DeepSeek & OpenAI AI Engine'}</strong>
                      <span>
                        {isTr
                          ? 'Erişim anahtarıyla açılabilen hata analizi ve patch üretimi'
                          : 'Optional failure analysis and patch generation with an API key'}
                      </span>
                    </div>
                  </div>
                  <div className={styles.quickPill}>
                    <span className={styles.quickPillIcon}>📊</span>
                    <div>
                      <strong>{isTr ? 'Allure & ReportPortal Entegrasyonu' : 'Unified Enterprise Reporting'}</strong>
                      <span>
                        {isTr
                          ? 'Yerel HTML ve Allure açık; ReportPortal erişim bilgisiyle açılır'
                          : 'Local HTML and Allure on; ReportPortal needs credentials to enable'}
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
                  code={getQuickConfig(isTr)}
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
                  ? 'İlk kurulumdan test çalıştırmaya, isteğe bağlı AI ve entegrasyonlara kadar kısa yanıtlar.'
                  : 'Straight answers on setup, running tests, optional AI features, and integrations.'}
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
