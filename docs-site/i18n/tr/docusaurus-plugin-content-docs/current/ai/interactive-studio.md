---
id: interactive-studio
title: İnteraktif Web Stüdyosu
sidebar_label: İnteraktif Web Stüdyosu
sidebar_position: 4
description: Görsel test üretimi, tarayıcı deneme alanı, canlı refakatçi kaydı ve TestFly Web Stüdyosu ile yapılandırma yönetimi.
---

# İnteraktif Web Stüdyosu

**TestFly Web Stüdyosu**, QA ekiplerinin ve geliştiricilerin tarayıcıları görsel olarak kontrol etmesini, MCP araçlarını denemesini, üretime hazır Java kodları üretmesini ve proje ayarlarını yönetmesini sağlayan yerel ve sıfır bağımlılıklı bir web arayüzüdür.

```bash
# Stüdyoyu http://127.0.0.1:8765 adresinde başlatır
testfly studio
```

*(Ayrıca `testfly ui` takma adını da kullanabilirsiniz).*

---

## Stüdyo Modları

Web Stüdyosu birbirini tamamlayan iki ana modda çalışır:

### 1. Bağımsız Deneme Alanı Modu (`testfly studio`)
Manuel tarayıcı keşfi, araç incelemesi ve yapılandırma düzenlemeleri için tasarlanmıştır:
- **Tarayıcı Yürütme Modları:** Pop-up pencereler olmadan ekran görüntüsü akışı sunan **🤖 Headless (Arka Plan)** ve görsel inceleme sunan **🖥️ Görünür Pencere** arasında seçim yapabilirsiniz.
- **Araçlar Dizini (88 Araç):** Tüm MCP araçlarını arayabilir, parametre şemalarını inceleyebilir ve özel JSON girdileriyle doğrudan çalıştırabilirsiniz.
- **Görsel `testfly.yml` Editörü:** Senkronize form kontrolleri ve YAML önizlemesi ile tek tıkla proje köküne kaydetme imkanı sunar.
- **Ortam Tanılaması:** Python, Selenium, Chrome ve IDE entegrasyonlarının sağlık durumunu görsel olarak gösterir.

### 2. Canlı Refakatçi Kayıt Modu (`testfly record <url>`)
Gerçek kullanıcı akışlarının Google Chrome üzerinde canlı kaydedilmesi için tasarlanmıştır:
- **Chrome Refakatçisi:** DOM olay yakalayıcı betikler ve gevşetilmiş güvenlik parametreleriyle Google Chrome'u otomatik başlatır.
- **Canlı Olay Akışı (SSE):** Tıklamalar, birleştirilmiş metin girişleri ve özel assertion adımlarının anlık akışı.
- **Doğrulama (Assertion) Araç Çubuğu:** Öğe görünürlüğü, etkinlik durumu ve tam/içerik metin doğrulamalarını tek tıkla kaydetme.
- **Akıllı Seçici Test Edici:** Canlı sayfa üzerinde anlık seçici doğrulama.
- **Çoklu Mimari Java Kod Üretimi:** Page Object Model (`BasePage` + `BaseTest`), TestNG, JUnit 5 ve Cucumber BDD için eşzamanlı renklendirilmiş kod çıktısı.
- **Projeye Kaydetme:** Page Object sınıflarını `pages/`, Test sınıflarını `tests/` ve Gherkin dosyalarını `resources/features/` klasörlerine temiz şekilde ayrıştırarak kaydetme.

> [!TIP]
> Canlı refakatçi kaydedicinin adım adım kullanım rehberi için [İnteraktif Kaydedici Kılavuzu](./recorder.md) sayfasına göz atın.

---

## Görsel `testfly.yml` Editörü

Test yürütme parametrelerini yönetmek yerleşik düzenleyici ile çok kolaydır:

1. **Yürütme Ayarları:** Yerel yürütme, Selenium Grid adresleri, paralel yürütme modu (`methods` vs `classes`), iş parçacığı (thread) sayısı ve maksimum oturum limitlerini ayarlayın.
2. **Tarayıcı Profilleri:** Varsayılan tarayıcı (`chrome`, `firefox`, `edge`), pencere boyutları, headless tercihi ve özel başlatma argümanlarını seçin.
3. **Zaman Aşımları & Yeniden Deneme:** Açık bekleme eşikleri, sayfa yükleme süreleri ve flaky test yeniden deneme ilkelerini (`enabled`, `maxAttempts`) yapılandırın.
4. **Raporlama Entegrasyonları:** HTML raporları, Allure 2 ve ReportPortal çıktılarını etkinleştirin.
5. **Doğrudan Projeye Eşitleme:** Manuel kopyalama gerektirmeden **"Save testfly.yml to Project Root"** butonuyla ayarları doğrudan proje kökünüze kaydedin.

---

## Port Yönetimi ve Çakışma Çözümü

Web Stüdyosu varsayılan olarak `8765` portunu kullanır. Bu port başka bir uygulama veya açık bir oturum tarafından kullanılıyorsa:
- Sunucu sonraki portları (`8766`, `8767`, ...) 20 denemeye kadar otomatik olarak tarar.
- Aktif port konsola yazdırılır ve varsayılan masaüstü tarayıcınızda otomatik olarak açılır:
  ```text
  ✓ TestFly Web Studio running at: http://127.0.0.1:8766
  ```
