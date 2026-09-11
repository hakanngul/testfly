---
id: adr-001-mcp-recorder-architecture
title: "ADR-001: MCP Sunucusu ve İnteraktif Kaydedici Mimarisi"
sidebar_label: "ADR-001: Kaydedici Mimarisi"
sidebar_position: 8
description: TestFly MCP sunucusu, Chrome refakatçi kaydedicisi ve çoklu dosya Java kod üretim motoru için Mimari Karar Kaydı.
---

# ADR-001: MCP Sunucusu ve İnteraktif Kaydedici Mimarisi

## Durum
**Kabul Edildi (Accepted)**

## Tarih
2026-09-11

## Bağlam (Context)

Kurumsal Java test otomasyon ekipleri günümüzde iki önemli zorlukla karşılaşmaktadır:
1. **Yapay Zeka Ajanlarının Yetkilendirilmesi:** Antigravity, Claude Code ve Cursor gibi otonom kodlama ajanları; canlı tarayıcı oturumlarına, DOM incelemesine, erişilebilirlik denetimlerine ve test üretimine standartlaştırılmış protokoller üzerinden doğrudan erişmeye ihtiyaç duyar.
2. **Kırılgan Olmayan Test Üretimi:** Klasik tarayıcı kaydedicileri (eski Selenium IDE veya standart Playwright codegen gibi); mutlak XPath'ler (`/html/body/div[2]/div/button`), rastgele `Thread.sleep()` beklemeleri ve kurumsal Java desenlerine (örn. `BasePage` ile Page Object Model, `BaseTest` ile TestNG veya Cucumber BDD) uymayan monolitik tek dosya çıktıları üretir.

Aşağıdaki niteliklere sahip bir mimariye ihtiyaç duyulmuştur:
- Yapay zeka ajanlarını resmi **Model Context Protocol (MCP)** standardı üzerinden tarayıcılara bağlamak.
- Manuel QA mühendislerine görsel ve düşük gecikmeli bir **Canlı Refakatçi Kaydedici (Live Companion Recorder)** sunmak.
- Otomatik bekleme mekanizmalarına ve erişilebilirlik öncelikli seçicilere sahip, standart **TestFly Java 17+** kodu üretmek.
- Projeye kaydederken modüler, derlenebilir ve standart dosya yapısı (`pages/`, `tests/` ve `resources/features/`) oluşturmak.

---

## Karar (Decision)

İki yönlü bir otomasyon köprüsü tasarlandı ve uygulandı:

### 1. Birleşik Python MCP Yürütme Motoru (`testfly-mcp`)
- `stdio` ve `sse` üzerinden **Model Context Protocol (MCP)** JSON-RPC standardını uygular.
- Tarayıcı yaşam döngüsü, öğe etkileşimi, erişilebilirlik denetimi ve kod üretimini kapsayan **88 atomik otomasyon aracını** dışa sunar.
- Yerel Selenium WebDriver oturumlarına doğrudan bağlanır; hem arka plan (headless) hem de görünür masaüstü tarayıcısı modlarını destekler.

### 2. Canlı Refakatçi Stüdyosu ve CDP Betik Enjeksiyonu
- Tarayıcı eklentilerinin getirdiği izin ve güncelleme kısıtlamaları yerine, Chrome DevTools Protocol (`Page.addScriptToEvaluateOnNewDocument`) aracılığıyla aktif bir DOM olay yakalayıcı (`injected_recorder.js`) doğrudan enjekte edilir.
- Google Chrome, özel gevşetilmiş güvenlik bayraklarıyla (`--disable-web-security`, `--allow-running-insecure-content`) çalıştırılır; böylece `127.0.0.1` üzerindeki yerel sunucuya yapılan çağrılarda CORS engeli yaşanmaz.
- Arka plandaki port yöneticisi `8765`'ten başlayarak ilk boş portu bulup bağlanır, böylece port çakışmaları tamamen engellenir.

### 3. Erişilebilirlik Öncelikli Seçici Hiyerarşisi
Öğe seçicileri sentezlenirken kararlılık hiyerarşisi uygulanır:
1. **Semantik Test ID'leri:** `getByTestId("ad")` (`data-testid`, `data-test`)
2. **Erişilebilir Roller ve İsimler:** `getByRole(Role.BUTTON, "Gönder")`, `getByLabel("E-posta")`
3. **Benzersiz ID'ler:** `$("#login-btn")`
4. **SmartLocator Çözücüsü:** Kırılgan yapısal seçiciler için birden fazla adayı sırayla deneyen akıllı fallback çözücüsü üretilir.

### 4. Ayrıştırılmış Çoklu Dosya Yönlendirmesi
Üretilen kod projeye kaydedilirken:
- Sunucu `File: <yol>` sınırlarını ayrıştırır ve her bir sınıfı ait olduğu dizine yazar:
  - **Page Object sınıfları:** `src/test/java/.../pages/`
  - **Test sınıfları:** `src/test/java/.../tests/`
  - **Cucumber Feature dosyaları:** `src/test/resources/features/`
- Monolitik birleşik dosyalar kesinlikle engellenir; birden fazla `package` veya dosya adıyla uyuşmayan birden fazla `public class` içeren hatalı Java derleme birimlerinin oluşması önlenir.

### 5. Derleyici Seviyesinde Anahtar Kelime & İsim Temizleme
- Öğe isimleri ve üretilen metotlar Java dili rezerve kelimelerine karşı (`continue`, `break`, `return`, `class`, `default`, `goto` vb.) taranır.
- Rezerve kelimeler otomatik olarak `Element` son eki alır (örn. `continue` kelimesi `continueElement` ve `clickContinueElement()` olur).
- Dosya başlıkları ve ayraçları yorum satırı (`//` veya `#`) ile korunur.

---

## Değerlendirilen Alternatifler

### 1. Chrome Eklentisi Tabanlı Kaydedici
- **Artıları:** Ayrı bir süreç başlatmadan standart tarayıcı penceresinde çalışır.
- **Eksileri:** Manifest V3 kısıtlamaları nedeniyle yapay zeka ajanlarıyla yerel HTTP iletişimi zordur. MCP üzerinden CLI veya AI ajanları tarafından arka planda otomatikleştirilemez.
- **Reddedildi:** Bağımsız CDP enjeksiyonu çok daha esnektir ve yapay zeka ajanlarının kaydediciyi programatik olarak tetiklemesine olanak tanır.

### 2. Standart / Ham Selenium Kodu Üretmek
- **Artıları:** `public static void main` içeren basit bağımsız betikler üretmek kolaydır.
- **Eksileri:** Kurumsal standartlara uymaz, `new ChromeDriver()` gibi gereksiz kod kalabalığı getirir, otomatik bekleme desteği yoktur ve TestFly'ın ThreadLocal paralel test yürütme avantajlarından yararlanamaz.
- **Reddedildi:** TestFly yerel kodları (`BaseTest`, `BasePage`, `assertThat(...)`) üretmek anında üretime hazır ve stabil bir test altyapısı sağlar.

### 3. Page Object Modeli İçin Tek Birleşik Dosya Üretmek
- **Artıları:** Her şeyi tek bir `.java` dosyasına yazan basit bir kaydetme mekanizması.
- **Eksileri:** Java dili farklı paketlerdeki public sınıfların tek dosyada birleşmesine izin vermez. `HomePage` ve `HomeTest` sınıflarının tek dosyaya dökülmesi `Syntax error on token(s), misplaced construct(s)` hatası verir.
- **Reddedildi:** Çoklu dosya ayrıştırması ve ilgili klasörlere yazım Java standartları için zorunludur.

---

## Sonuçlar (Consequences)

### Olumlu Sonuçlar
- **Anında Verimlilik:** QA ekipleri tüm regresyon akışını kaydedip manuel refactoring yapmadan temiz, modüler Java kodlarını projelerine ekleyebilir.
- **Sıfır Derleme Hatası:** Anahtar kelime temizliği ve çoklu dosya yönlendirmesi `mvn test-compile` sırasında sıfır hata garantisi verir.
- **Ajan ve İnsan Uyumu:** Hem insan test uzmanları (Web Studio ile) hem de yapay zeka ajanları (MCP araçları ile) aynı otomasyon motorunu paylaşır.

### Değiş-Tokuşlar (Trade-offs)
- **Chrome Bağımlılığı:** Canlı refakatçi kaydı yerel ortamda Google Chrome'un kurulu olmasını gerektirir (ajanlar arka planda diğer tarayıcıları da çalıştırabilir).
- **Yerel Port Yönetimi:** Web stüdyosu ve SSE akışı için yerel bir HTTP portunun açılmasını gerektirir.
