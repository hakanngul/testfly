import React, { useEffect, useState } from 'react';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { useColorMode } from '@docusaurus/theme-common';
import { Highlight } from 'prism-react-renderer';
import { themes } from 'prism-react-renderer';
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
    span: 2,
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
    title: 'CSS Refactor’lerinden Sağ Çıkan Testler',
    description: 'Erişilebilirlik-öncelikli getByRole / getByText / getByLabel, erişilebilirlik ağacını hedefler — Playwright tarzı, otomatik bekleme ve CSS ya da DOM refactor’lerine dayanıklı.',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z" />
        <path d="m9 12 2 2 4-4" />
      </svg>
    ),
    title: 'Kendini Onaran Locator’lar',
    description: 'Bir locator kırıldığında, self-healing otomatik olarak id, name, text ve data-testid üzerinden geri döner — ve her iyileştirmeyi raporda işaretler.',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 3v18h18" />
        <path d="M18 17V9M13 17V5M8 17v-3" />
      </svg>
    ),
    title: 'Paydaşların Gerçekten Okuduğu Rapor',
    span: 2,
    visual: 'report',
    description: 'Sekmeli HTML dashboard, geçiş oranı göstergesi, retry rozetleri, genişletilebilir hatalar, Flakiness Radar, trace linkleri, arama ve karanlık mod.',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 2a7 7 0 0 1 7 7c0 2.38-1.19 4.47-3 5.74V17a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2v-2.26C6.19 13.47 5 11.38 5 9a7 7 0 0 1 7-7z" />
        <path d="M9 21h6" />
      </svg>
    ),
    title: 'Bir Testin Neden Başarısız Olduğunu Bilin',
    description: 'Her başarısızlıkta, AI failure analysis Claude ile hatayı, adımları ve URL’yi okur; ardından rapora düz İngilizce bir kök neden ve önerilen düzeltme yerleştirir.',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="18" height="18" rx="2" />
        <path d="m9 9 6 6M15 9l-6 6" />
      </svg>
    ),
    title: 'Prompt’tan Test Üretin',
    description: 'testfly-mcp üzerinden AI test yazımı, Claude veya Copilot’un gerçek bir tarayıcıyı sürmesine ve tek bir prompt’tan çalışmaya hazır TestFly testleri üretmesine olanak tanır.',
  },
];

const moreFeatures = [
  { icon: '📄', title: 'Kod Değiştirmeden Ortam Değiştirin', short: 'Tek bir testfly.yml, tarayıcıları, thread’leri, timeout’ları, retry ve CI kapılarını kontrol eder.' },
  { icon: '🔁', title: 'Flaky Test’ler Build’inizi Başarısız Etmeyi Bıraksın', short: 'Flaky test’leri otomatik retry yapın ve raporda HIGH / WATCH / STABLE olarak sıralayın.' },
  { icon: '📋', title: 'Tesisat Değil, Page Yazın', short: 'BasePage, tıklamaları, bekleme işlemlerini, dropdown’ları, iframe’leri, Shadow DOM’u ve upload’ları sarar.' },
  { icon: '🔗', title: 'Herhangi Bir Elemanı Sabitleyin', short: 'Otomatik retry yapan assertThat() ile fluent, Playwright tarzı zincirlenebilir locator’lar.' },
  { icon: '🌐', title: 'Gerçek Bir Backend Olmadan Test Edin', short: 'CDP üzerinden API yanıtlarını mock’layın; storage, cookie ve geo verilerini okuyun ve yazın.' },
  { icon: '📸', title: 'Görsel Regresyonları Yakalayın', short: 'Piksel farklı ekran görüntüleri ve 6 mobil profil için tek satırlık cihaz emülasyonu.' },
  { icon: '🪜', title: 'Testi Bir Spec Gibi Okuyun', short: 'Adım loglama — ekran görüntülü, isimlendirilmiş adımlar ve kendi içinde tutarlı hata izi.' },
  { icon: '🔐', title: 'Bir Kez Giriş Yapın, Oturumu Yeniden Kullanın', short: '@PreCondition login işlemini bir kez çalıştırır, oturumu önbelleğe alır ve her test için geri yükler.' },
  { icon: '📧', title: 'Uygulamanızın Gönderdiği E-postayı Doğrulayın', short: 'E-posta doğrulama, Mailhog, Mailtrap, Graph API veya IMAP üzerinden gerçek e-postaları bekler.' },
  { icon: '🕐', title: 'Zamanı Beklemeden Test Edin', short: 'Clock mocking, sona erme, trial ve geri sayımları test etmek için tarayıcı saatini dondurur.' },
  { icon: '☁️', title: 'Gerçek Bulut Tarayıcılarında Çalıştırın', short: 'Tek bir config satırını değiştirerek BrowserStack veya Sauce Labs.' },
  { icon: '🔌', title: 'Forklamadan Genişletin', short: 'Özel driver’lar, rapor adaptörleri ve hook’ları Java SPI / ServiceLoader ile kaydedin.' },
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
  { value: '20+', label: 'Dahili özellik' },
  { value: '4', label: 'Otomatik algılanan CI platformu' },
  { value: '0', label: 'Gereken boilerplate' },
];

// ─── Components ───────────────────────────────────────────────────────────────

function CodeWindow({ filename, code, className, language = 'java' }) {
  const { colorMode } = useColorMode();
  const prismTheme = colorMode === 'dark' ? themes.dracula : themes.oneLight;

  return (
    <div className={`${styles.codeWindow} ${className || ''}`}>
      <div className={styles.codeWindowBar}>
        <span className={styles.dot} />
        <span className={styles.dot} />
        <span className={styles.dot} />
        <span className={styles.codeWindowFilename}>{filename}</span>
      </div>
      <Highlight theme={prismTheme} code={code.trim()} language={language}>
        {({ className: hlClass, style, tokens, getLineProps, getTokenProps }) => (
          <pre className={`${styles.codeWindowBody} ${hlClass}`} style={style}>
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
                <span className={styles.heroEyebrow}>Java · Selenium · TestNG · JUnit 5</span>
                <h1 className={styles.heroTitle}>
                  Test otomasyonu
                  <br />
                  <span className={styles.heroTitleAccent}>gereksiz detay olmadan</span>
                </h1>
                <p className={styles.heroSubtitle}>{siteConfig.tagline}</p>
                <div className={styles.heroActions}>
                  <Link className={styles.buttonPrimary} to="/docs/getting-started">
                    Başlayın
                  </Link>
                  <Link className={styles.buttonSecondary} to="https://github.com/testfly/testfly">
                    GitHub'da Görüntüle
                  </Link>
                </div>
                <CodeWindow
                  filename="pom.xml"
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
              <h2 className={styles.sectionTitle}>Aynı test.<br />Hiçbir tesisat yok.</h2>
              <p className={styles.sectionSubtitle}>
                TestFly, ekibinizin çerçeve mühendisliğine değil, teste odaklanmasını sağlar.
                Beklemeler, driver kurulumu, boilerplate — hepsi halledilir.
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
                  data-span={f.span === 2 ? '2' : undefined}
                  data-reveal
                  style={{ '--i': i % 3 }}
                >
                  <div className={styles.featureIconWrap}>{f.icon}</div>
                  <h3 className={styles.bentoTitle}>{f.title}</h3>
                  <p className={styles.bentoDesc}>{f.description}</p>
                  {f.code && <pre className={styles.bentoCode}>{f.code}</pre>}
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
                <Link className={styles.buttonSecondary} to="https://github.com/testfly/testfly">
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
