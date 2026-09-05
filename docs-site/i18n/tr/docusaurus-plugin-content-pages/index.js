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
    title: 'Sıfır Boilerplate',
    description: 'BaseTest’i extend edin, @Test metotları yazın ve ilerleyin. Driver yaşam döngüsü, bekleme, retry, raporlar ve ekran görüntüleri hepsi halledilir — kurulum kodu gerekmez.',
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
    title: 'DOM Değişikliklerine Dirençli Lokasyonlar',
    description: 'Erişilebilirlik odaklı getByRole, getByText ve getByLabel ile erişilebilirlik ağacını hedefleyin — Playwright ergonomisinde, otomatik bekleyen ve CSS/DOM refactor’lerine dirençli testler.',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z" />
        <path d="m9 12 2 2 4-4" />
      </svg>
    ),
    title: 'Kendi Kendini Onaran Lokasyonlar (Self-Healing)',
    description: 'Bir lokatör kırıldığında akıllı onarım motoru text, name ve data-testid üzerinden elemanı bulup testi kurtarır; yapılan her iyileştirmeyi raporda açıkça belgeler.',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 3v18h18" />
        <path d="M18 17V9M13 17V5M8 17v-3" />
      </svg>
    ),
    title: 'Paydaşların Gerçekten Okuduğu Rapor',
    visual: 'report',
    description: 'Zaman çizelgesi (timeline), adım adım ekran görüntüleri, Flakiness Radarı, video kaydı, arama filtreleri ve karanlık mod ile tüm ekibin anlayacağı şeffaf test sonuçları.',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 2a7 7 0 0 1 7 7c0 2.38-1.19 4.47-3 5.74V17a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2v-2.26C6.19 13.47 5 11.38 5 9a7 7 0 0 1 7-7z" />
        <path d="M9 21h6" />
      </svg>
    ),
    title: 'Yapay Zeka ile Otomatik Arıza Analizi',
    description: 'Test çöktüğünde Claude veya Gemini DOM ağacını, logları ve adımları analiz eder; raporda doğrudan hatanın kök nedenini ve düzeltme önerisini sunar.',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="18" height="18" rx="2" />
        <path d="m9 9 6 6M15 9l-6 6" />
      </svg>
    ),
    title: 'Prompt\'tan Test Üretin — Yakında',
    description: 'TestFly MCP (yakında) üzerinden AI destekli test yazımı, Claude veya Copilot\'un gerçek bir tarayıcıyı sürmesine ve tek bir prompt\'tan çalışmaya hazır TestFly testleri üretmesine olanak tanıyacak.',
  },
];

const moreFeatures = [
  { icon: '📄', title: 'Sıfır Kod Değişikliğiyle Ortam Yönetimi', short: 'Tek bir testfly.yml ile tarayıcıları, paralel thread’leri, timeout’ları ve CI kapılarını yönetin.' },
  { icon: '🚀', title: 'Yerleşik REST API Test İstemcisi', short: 'Harici kütüphane olmadan fluent HTTP istemcisi (ApiClient), JSON Schema doğrulama ve auth token yönetimi.' },
  { icon: '🔁', title: 'Flaky Test Yönetimi ve Otomatik Retry', short: 'Kırılgan testleri otomatik yeniden deneyin; raporda HIGH / WATCH / STABLE olarak izleyin.' },
  { icon: '📋', title: 'Boilerplate Değil, Temiz Page Object', short: 'BasePage tıklamaları, akıllı beklemeleri, dropdown’ları, iframe’leri ve Shadow DOM’u tek satıra indirir.' },
  { icon: '🔗', title: 'Akıllı ve Otomatik Bekleyen Doğrulamalar', short: 'assertThat() ile otomatik bekleyen (auto-waiting), zincirlenebilir modern web assertion’ları.' },
  { icon: '🌐', title: 'Ağ ve API Mocking (Network DSL)', short: 'CDP üzerinden ağ isteklerini yakalayın, yanıtları mock’layın; çerezleri ve storage’ı kolayca yönetin.' },
  { icon: '📸', title: 'Görsel Regresyon ve Cihaz Emülasyonu', short: 'Piksel bazlı ekran görüntüsü karşılaştırması ve mobil cihaz profilleriyle piksel kusursuzluğu.' },
  { icon: '🪜', title: 'Spesifikasyon Kalitesinde Adım Loglama', short: 'Her adıma özel ekran görüntüleri, milisaniyelik zaman damgaları ve hata anı görsel izi.' },
  { icon: '🔐', title: '@PreCondition ile Akıllı Oturum Önbelleği', short: 'Login adımını bir kez çalıştırıp oturumu önbelleğe alın; tüm testlerde saniyeler kazanın.' },
  { icon: '📧', title: 'Yerleşik E-Posta Doğrulama (EmailSupport)', short: 'Mailhog, Mailtrap veya IMAP üzerinden aktivasyon ve şifre sıfırlama e-postalarını bekleyin ve doğrulayın.' },
  { icon: '🕐', title: 'Saat Manipülasyonu (Clock Mocking)', short: 'Geri sayımlar ve oturum süreleri için tarayıcı saatini dondurun veya ileri sarın.' },
  { icon: '🗄️', title: 'Veritabanı Doğrulama Desteği (DbSupport)', short: 'UI veya API testlerinizin arkasındaki veritabanı durumunu yerleşik sorgularla doğrudan doğrulayın.' },
];

const faqs = [
  {
    q: 'WebDriver binary’lerini indirmem gerekiyor mu?',
    a: 'Hayır. Selenium Manager (Selenium 4 ile birlikte gelir) doğru driver’ı otomatik olarak çözer ve indirir. Sadece Chrome veya Firefox’un kurulu olması yeterli.',
  },
  {
    q: 'JUnit 5 ve Cucumber ile çalışıyor mu, yoksa sadece TestNG mi?',
    a: 'Üçü de. TestNG varsayılan; JUnit 5 BaseJUnit5Test veya @ExtendWith(TestFlyExtension.class) ile tam parity sağlar; Cucumber BaseCucumberSteps + CucumberHooks üzerinden desteklenir.',
  },
  {
    q: 'testfly.yml zorunlu mu?',
    a: 'Hayır — isteğe bağlı. TestFlyDefaults her şey için makul varsayılanlar sağlar, böylece framework sıfır config ile çalışır. testfly.yml’i yalnızca bir varsayılanı override etmek istediğinizde ekleyin.',
  },
  {
    q: 'Hâlâ ham Selenium WebDriver seviyesine inebilir miyim?',
    a: 'Her zaman. getDriver() size canlı WebDriver’ı verir ve her fluent locator toBy() ile standart bir Selenium By döndürür. TestFly, Selenium’u sarar — asla gizlemez.',
  },
  {
    q: 'Bu Playwright’tan nasıl farklı?',
    a: 'TestFly, sizi Selenium ekosisteminde (Grid, bulut sağlayıcıları, tüm Java tooling dünyası) tutarken, insanların Playwright’ta sevdiği ergonomiyi sunar — fluent ve erişilebilirlik-öncelikli locator’lar, otomatik bekleme assertion’ları, tracing ve codegen.',
  },
  {
    q: 'Paralel çalıştırma kutudan çıkar mı?',
    a: 'Evet. Driver ThreadLocal içinde tutulur, bu nedenle paralel TestNG/JUnit çalıştırmaları varsayılan olarak izoledir. testfly.yml’de parallel ve threadCount ayarlayın ve başlayın.',
  },
  {
    q: 'Maliyeti nedir?',
    a: 'Apache 2.0 altında ücretsiz ve açık kaynak; Maven Central’da yayınlanmış. Tek bağımlılık ekleyin ve işiniz biter.',
  },
];

const stats = [
  { value: '1', label: 'Eklenecek bağımlılık' },
  { value: '30+', label: 'Dahili özellik' },
  { value: '7', label: 'Otomatik algılanan CI platformu' },
  { value: '0', label: 'Gereken boilerplate' },
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
          <span className={styles.chipPass}>48 başarılı</span>
          <span className={styles.chipFlaky}>2 flaky</span>
          <span className={styles.chipFail}>0 başarısız</span>
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
    <Layout title="Ana Sayfa" description={siteConfig.tagline}>
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
                  Test otomasyonu
                  <br />
                  <span className={styles.heroTitleAccent}>sıfır boilerplate ile</span>
                </h1>
                <p className={styles.heroSubtitle}>
                  Spring Boot ergonomisinde, Selenium tabanlı modern Java test platformu: Web, REST API, Veritabanı ve AI destekli arıza analizi — tek bağımlılık, sıfır karmaşa.
                </p>
                <div className={styles.heroActions}>
                  <Link className={styles.buttonPrimary} to="/docs/getting-started">
                    Başlayın
                  </Link>
                  <Link className={styles.buttonSecondary} to="https://github.com/hakanngul/testfly">
                    GitHub'da Görüntüle
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
              <span className={styles.sectionEyebrow}>Önce / Sonra</span>
              <h2 className={styles.sectionTitle}>Aynı test.<br />Sıfır altyapı hamallığı.</h2>
              <p className={styles.sectionSubtitle}>
                TestFly ekibinizin altyapı mühendisliğine değil, test senaryolarına odaklanmasını sağlar.
                Akıllı beklemeler, driver kurulumu ve konfigürasyon kalabalığı — hepsi arka planda sessizce çözülür.
              </p>
            </div>

            <div className={styles.compareGrid} data-reveal>
              <div className={styles.compareCol}>
                <span className={styles.compareLabel} data-kind="before">Düz Selenium</span>
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
              <span className={styles.sectionEyebrow}>Özellikler</span>
              <h2 className={styles.sectionTitle}>İhtiyacın olan her şey,<br />ihtiyacın olmayan hiçbir şey</h2>
              <p className={styles.sectionSubtitle}>
                Tek bağımlılık. Sıfır zorunlu yapılandırma. BaseTest’i extend ettiğiniz anda hazır, full-stack otomasyon gücü.
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
              <h3 className={styles.moreTitle}>Tam araç takımı</h3>
              <p className={styles.moreSubtitle}>
                On iki yetenek daha, hepsi dahil — eklenti yok, ekstra kurulum yok.
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
                <span className={styles.sectionEyebrow}>Hızlı Başlangıç</span>
                <h2 className={styles.quickTitle}>3 dakikada<br />çalışır durumda</h2>
                <p className={styles.quickSubtitle}>
                  Bağımlılığı ekleyin, bir YAML config oluşturun, BaseTest’i extend edin — ilk testiniz, tam raporlama, retry ve akıllı bekleme ile birlikte zaten yapılandırılmış olarak çalışır.
                </p>
                <Link className={styles.buttonPrimary} to="/docs/getting-started">
                  Kılavuzu Oku
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
              <span className={styles.sectionEyebrow}>SSS</span>
              <h2 className={styles.sectionTitle}>Sorular, cevaplandı</h2>
              <p className={styles.sectionSubtitle}>
                Takımlar TestFly'i benimsemeden önce sorduğu şeyler.
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
              <h2 className={styles.ctaTitle}>Boilerplate'lerinizi silmeye hazır mısınız?</h2>
              <p className={styles.ctaSubtitle}>
                Tek bağımlılık. Tek YAML dosyası. Niyet gibi okunan testler.
              </p>
              <div className={styles.ctaActions}>
                <Link className={styles.buttonPrimary} to="/docs/getting-started">
                  Başlayın
                </Link>
                <Link className={styles.buttonSecondary} to="https://github.com/hakanngul/testfly">
                  GitHub'da Görüntüle
                </Link>
              </div>
            </div>
          </div>
        </section>
      </main>
    </Layout>
  );
}
