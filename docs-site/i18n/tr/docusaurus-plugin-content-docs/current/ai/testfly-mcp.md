---
id: testfly-mcp
title: TestFly MCP Sunucusu ve Komut Satırı (CLI)
sidebar_label: MCP Sunucusu & CLI
sidebar_position: 2
description: TestFly MCP sunucusunun kurulumu, komut satırı arayüzü, ortam teşhisi ve 88 araçlık kataloğu.
---

# TestFly MCP Sunucusu ve Komut Satırı (CLI)

**TestFly MCP Sunucusu**, yapay zeka destekli tarayıcı otomasyonu ve kod üretimini yürüten ana motordur. Model Context Protocol (MCP) JSON-RPC standardı üzerinden AI asistanlarına 88 adet gelişmiş araç sunar.

---

## Kurulum

### Kaynak Koddan / Yerel Geliştirme Kurulumu
`testfly-mcp` dizinine gidin ve geliştirici (editable) modunda kurun:

```bash
cd testfly-mcp
pip install -e .
```

Kurulumun başarılı olduğunu ve komutun ortamınızda çalıştığını doğrulayın:
```bash
testfly-mcp --version
# Çıktı: testfly-mcp 1.0.0
```

---

## Komut Satırı Arayüzü (CLI)

`testfly-mcp` zengin bir komut setiyle gelir:

```bash
testfly-mcp --help
```

### Kullanılabilir Komutlar

| Komut | Açıklama |
| :--- | :--- |
| `testfly-mcp --help` | Kullanım seçeneklerini, komutları ve örnekleri gösterir. |
| `testfly-mcp --version` | Yüklü sürüm numarasını (`1.0.0`) basar. |
| `testfly-mcp doctor` | Python, Selenium, Chrome ve IDE ayarlarını tarayıp sistem teşhis raporu verir. |
| `testfly-mcp tools` | Kullanılabilir 88 MCP aracını parametreleriyle listeler. |
| `testfly-mcp tools --search <kelime>` | Araçlar içinde ada veya açıklamaya göre arama yapar. |
| `testfly-mcp ui` | Varsayılan tarayıcınızda **Etkileşimli Web Stüdyosu**'nu açar (`http://127.0.0.1:8765`). |
| `testfly-mcp init-config` | Bulunduğunuz dizine standart `testfly.yml` şablonu oluşturur. |
| `testfly-mcp stdio` | MCP stdio sunucusunu başlatır (IDE veya Claude bağlandığında otomatik çalışır). |
| `testfly-mcp` (terminalde doğrudan) | Terminali algılar ve etkileşimli seçim menüsü sunar. |

---

## Sistem Teşhisi (`testfly-mcp doctor`)

Test otomasyonu önkoşullarının eksiksiz olduğunu doğrulamak için `testfly-mcp doctor` komutunu çalıştırın:

```bash
testfly-mcp doctor
```

Örnek Çıktı:
```text
========================================================
✈  TestFly MCP Environment Doctor — Status: HEALTHY
========================================================

✓ [PASS]  [Runtime] Python Version
          Details: Python 3.12.14 (/usr/local/bin/python3)

✓ [PASS]  [Dependencies] Selenium Package
          Details: Version 4.48.0

✓ [PASS]  [Dependencies] Model Context Protocol SDK
          Details: Installed (>=2.0.0)

✓ [PASS]  [Browser] Google Chrome
          Details: Detected at /Applications/Google Chrome.app/Contents/MacOS/Google Chrome

✓ [PASS]  [IDE / AI Assistant] Claude Code Registration
          Details: Registered in ~/.claude/settings.json

✓ [PASS]  [Project] testfly.yml in Working Directory
          Details: Found at /workspace/testfly.yml

========================================================
```

---

## 88 Araçlık Katalog Özeti

Araçlar 4 ana işlevsel kategoriye ayrılmıştır:

### 1. Tarayıcı Yönetimi ve Gezinme
- `start_browser`: Gerçek tarayıcıyı (Chrome/Firefox) başlatır, headless modunu ve özel pencere boyutunu destekler.
- `navigate`: Hedef URL'ye gider (otomatik `navigate_to` takma adıyla).
- `take_screenshot`: Canlı sayfa ekran görüntüsünü base64 PNG olarak alır.
- `get_page_source`: Canlı DOM HTML kaynağını döndürür.
- `check_accessibility`: Eksik etiketler ve renk kontrastı gibi a11y hatalarını denetler.
- `inspect_page`: Sayfadaki form alanlarını, butonları ve bağlantıları özetler.
- `close_browser`: Aktif oturumu kapatır.

### 2. Öğe Etkileşimi ve İnceleme
- `find_element` / `find_elements`: CSS, XPath, ID veya a11y seçicileriyle öğe bulur.
- `click`: Öğeyi görünür alana kaydırarak tıklar.
- `type_text`: Input alanını temizleyip metin yazar.
- `select_option`: Açılır listeden seçim yapar.
- `hover`, `double_click`, `right_click`, `drag_and_drop`: Fare hareketleri.
- `upload_file`: Dosya yükleme iletişim kutularını yönetir.

### 3. Doğrulamalar (Assertions)
- `assert_element_visible` / `assert_element_hidden`: Otomatik beklemeli görünürlük kontrolleri.
- `assert_text_contains` / `assert_text_equals`: Tam veya kısmi metin doğrulaması.
- `assert_title` / `assert_url`: Sayfa başlığı ve URL kontrolleri.
- `assert_element_enabled` / `assert_element_disabled`: Durum kontrolleri.

### 4. Yerel TestFly Kod Üretimi (Codegen)
- `detect_testfly`: Proje kökünde TestFly bağımlılığını tespit eder.
- `generate_java_page_object`: `BasePage` extend eden Page Object ve test sınıfları üretir.
- `generate_java_testng`: `BaseTest` extend eden TestNG test sınıfları üretir.
- `generate_java_junit5`: `BaseJUnit5Test` extend eden JUnit 5 test sınıfları üretir.
- `generate_gherkin`: Cucumber feature dosyası, `BaseCucumberSteps` ve `BaseCucumberTest` runner'ı üretir.
- `generate_testfly_config`: Standart `testfly.yml` üretir.
- `generate_testfly_pom`: TestFly bağımlılıklarını içeren Maven `pom.xml` üretir.
