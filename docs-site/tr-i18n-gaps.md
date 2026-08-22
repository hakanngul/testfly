# TestFly Docs — Türkçe (tr) i18n Eksik Denetimi

> Tarih: 2026-08-22 · Kapsam: `docs-site/` Docusaurus sitesinin `tr` dil desteği · Yöntem: mevcut `i18n/tr/` ağacı ile kaynak `docs/` ve `src/` ağacının birebir karşılaştırması.

## 1. Genel Durum Özeti

| Alan | Durum |
|------|-------|
| Yerel ayar yapılandırması (`docusaurus.config.js` → `i18n`) | ✅ `locales: ['en','tr']`, `tr-TR` htmlLang tanımlı, locale'e göre başlık/tagline dinamik |
| Tema çevirileri (`code.json`) | ✅ Tamamlandı (tema arayüz metinleri) |
| Navigasyon çubuğu (`navbar.json`) | ✅ Tamamlandı |
| Footer (`footer.json`) | ✅ Tamamlandı |
| Kenar çubuğu kategorileri (`current.json`) | ✅ Tamamlandı (9 kategori) |
| Ana sayfa (`index.js`) | ⚠️ Çevrildi ama içinde İngilizce artıklar var (bkz. §4) |
| **Doküman çevirileri** | ❌ **63 belgeden yalnızca 4'ü çevrildi — 59 eksik** |

**Çevrilmiş belgeler (örnek alınabilecekler):**
- `configuration.md` ✅
- `getting-started.md` ✅
- `intro.md` ✅
- `why/why-testfly.md` ✅

**Sonuç:** i18n *altyapısı* ve *tema çevirileri* bitmiş; asıl iş — **doküman içeriklerinin Türkçeleştirilmesi** — 59/63 belgeyle yarım kalmış durumda.

---

## 2. Eksik Doküman Çevirileri (59 adet)

Aşağıdaki kaynak belgelerin `i18n/tr/docusaurus-plugin-content-docs/current/` altında karşılığı **yok**; Docusaurus İngilizce'ye düşüyor.

### Kök seviye (12)
| Dosya | Satır |
|-------|-------|
| `accessibility.md` | 155 |
| `changelog.md` | 514 |
| `clock-mocking.md` | 170 |
| `cloud-execution.md` | 299 |
| `cucumber.md` | 276 |
| `email-verification.md` | 239 |
| `external-test-data.md` | 192 |
| `gradle.md` | 307 |
| `junit5.md` | 227 |
| `performance.md` | 192 |
| `quarantine.md` | 249 |
| `test-management.md` | 246 |

### `ci/` (4)
| Dosya |
|-------|
| `ci/ci-metadata.md` (213) |
| `ci/github-actions.md` (146) |
| `ci/jenkins.md` (135) |
| `ci/quality-gates.md` (109) |

### `extensibility/` (4)
| Dosya |
|-------|
| `extensibility/custom-drivers.md` (137) |
| `extensibility/hooks.md` (110) |
| `extensibility/plugins.md` (133) |
| `extensibility/report-adapters.md` (133) |

### `guides/` (18) — en büyük blok
`api-auth.md` · `api-testing.md` · `base-page.md` · `base-test.md` · `browser-lifecycle.md` · `console-errors.md` · `download-manager.md` · `parallel.md` · `precondition.md` · `retry.md` · `scenario-context.md` · `screenshots.md` · `self-healing.md` · `semantic-locators.md` · `smart-locator.md` · `step-logging.md` · `testfly-yml-guide.md` · `wait-engine.md`

### `migration/` (5)
`coming-from-playwright.md` · `from-selenide.md` · `from-selenium-testng.md` · `from-serenity.md` · `from-webdrivermanager.md`

### `recipes/` (9)
`alerts.md` · `download-and-verify-a-pdf.md` · `drag-and-drop.md` · `handle-iframes.md` · `handle-shadow-dom.md` · `infinite-scroll.md` · `oauth-sso.md` · `tables.md` · `upload-a-file.md`

### `reporting/` (3)
`html-report.md` · `junit-xml.md` · `report-portal.md`

### `why/` (4)
`why-accessibility-first.md` · `why-not-plain-selenium.md` · `why-not-playwright.md` · `why-waitengine.md`

---

## 3. Çevirilerde İzlenecek Kurallar (mevcut kaliteyi korumak için)

1. Türkçe çeviriler **aynı dizin yapısını** korumalı: `i18n/tr/docusaurus-plugin-content-docs/current/<aynı yol>.md`.
2. **Frontmatter'ı koru**: `id`, `slug`, `sidebar_position` aynen kalmalı; yalnızca `title` ve `description` Türkçeleştirilmeli. (Mevcut `intro.md` bu düzensizliği örnekliyor.)
3. Kod blokları, path'ler, `` `inline` `` kodlar, YAML anahtarları, sınıf/metot adları **çevrilmez** — olduğu gibi bırakılır.
4. `#` başlıkları ve bağlantı metinleri Türkçe olabilir; ama `[...](/)` hedef linkleri İngilizce kalmalı (doküman yolları değişmez).
5. Terimlerde tutarlılık için sözlük (aşağıda) izlenmeli.

### Önerilen terim sözlüğü
| İngilizce | Türkçe |
|-----------|--------|
| Getting Started | Hızlı Başlangıç |
| Configuration | Yapılandırma |
| Reporting | Raporlama |
| Extensibility | Genişletilebilirlik |
| Recipes | Tarifler |
| Migration | Geçiş |
| Step Logging | Adım Loglama |
| Self-Healing | Kendini Onarma |
| Waits/Explicit Wait | Bekleme / Açık Bekleme |
| Locator | Konumlandırıcı (veya Locator — bağlama göre) |
| Flaky | Flaky (teknik terim) |
| Retry | Yeniden Deneme (veya Retry) |
| Driver | Driver (sürücü) |
| Screenshot | Ekran Görüntüsü |
| Assertion | Doğrulama / Assertion |

> Not: `navbar.json` / `footer.json` / `code.json` zaten çeviri tutarlılığında; bu sözlük dokümanlarda aynı dili korumak içindir.

---

## 4. Ana Sayfa (`index.js`) İngilizce Artıkları

`i18n/tr/docusaurus-plugin-content-pages/index.js` **büyük ölçüde çevrildi**, ancak şu İngilizce parçalar kaldı:

| Satır | Mevcut (İngilizce) | Durum |
|-------|--------------------|-------|
| 256, 497 | `Get Started` | ❌ çevrilmemiş (buton) |
| 259, 500 | `View on GitHub` | ❌ çevrilmemiş (buton) |
| 428 | `Read the guide` | ❌ çevrilmemiş (buton) |
| **319** | **`TestFly lets your team focus on testing, not framework engineering. The waits, the driver setup, the boilerplate — handled.`** | ❌ **tamamen İngilizce paragraf** — büyük ihmal |

Önerilen çeviriler:
- 319 → *"TestFly, ekibinizin çerçeve mühendisliğine değil, teste odaklanmasını sağlar. Beklemeler, driver kurulumu, boilerplate — hepsi halledilir."*
- 256/497 → `Başlayın` veya `Hızlı Başlangıç`
- 259/500 → `GitHub'da Görüntüle`
- 428 → `Rehberi Oku` / `Kılavuzu Oku`

---

## 5. Yapı / Altyapı Gözlemleri

- ✅ **Deploy config i18n dostu**: `.github/workflows/docs.yml` `npm run build` çalıştırıyor; Docusaurus `build` komutu tüm locale'leri (en+tr) varsayılan olarak derlediğinden ayrıca bir değişiklik **gerekmez**.
- ✅ `onBrokenLinks: 'throw'` — eksik çeviri **build'i kırmaz** (Docusaurus İngilizce'ye fallback yapar). Güvenli.
- ✅ `build/` ve `.docusaurus/` klasörleri gitignore'da; `build/` sürüm takibinde değil. İlgili değil ama teyit edildi.
- ⚠️ Çevrilen ilk 4 belge tam/sağlam (ör. `intro.md` 145/146 satır); örnek kalite iyi. Kalan 59 için aynı standardı tutturmak gerek.

---

## 6. Önceliklendirme Önerisi

Tamamlanması gereken iş ~59 belge (~9.700 satır). Kaynağa göre öncelik:

1. **P0 — Kritik görünürlük** (ana sayfadan ulaşılanlar): `getting-started` ✅, `configuration` ✅, `intro` ✅ → sırada `gradle`, `guides/step-logging` (footer linki), `changelog` (footer linki), `ci/github-actions` (footer linki).
2. **P1 — Core Guides** (18): framework kullanımının %80'i burada.
3. **P2 — Recipes / Migration / Reporting / Extensibility / CI** (~21).
4. **P3 — why/ + kök seviye kavramsal belgeler** (marketing tarzı).
5. **P4 — Tema + Ana Sayfa artıklarının temizlenmesi** (küçük, hemen yapılabilir — bkz. §4).

---

## 7. Tanımlanan İş Kalemleri (Yapılacaklar)

- [ ] 1. Ana sayfa `index.js` İngilizce artıklarını temizle (§4)
- [ ] 2. `why/` kalan 4 belgeyi çevir (kısa — hızlı kazanım)
- [ ] 3. `guides/` 18 belgeyi çevir
- [ ] 4. `recipes/` 9 belgeyi çevir
- [ ] 5. `migration/` 5 + `extensibility/` 4 + `reporting/` 3 belgeyi çevir
- [ ] 6. Kök seviye 12 belgeyi çevir
- [ ] 7. TR build ile doğrula ve `tr` locale çıktısını kontrol et
- [ ] 8. Terim sözlüğünü doküman ekibine duyur (veya bu dosyada tut)