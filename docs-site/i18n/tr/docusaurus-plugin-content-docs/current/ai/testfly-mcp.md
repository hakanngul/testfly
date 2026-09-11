---
id: testfly-mcp
title: TestFly MCP Sunucusu & CLI
sidebar_label: MCP Sunucusu & CLI
sidebar_position: 2
description: TestFly MCP sunucusu kurulumu, CLI komut paketi, IDE entegrasyonu, ortam tanılaması ve araç kataloğu rehberi.
---

# TestFly MCP Sunucusu & CLI

**TestFly MCP Sunucusu**, yapay zeka kodlama asistanlarını ve yazılım mühendislerini gerçek tarayıcı oturumlarına, erişilebilirlik denetim motorlarına ve Java kod üreticilerine bağlayan kurumsal düzeyde bir otomasyon köprüsüdür.

Standart [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) JSON-RPC şartnamesi üzerinden yapay zeka ajanlarına **88 özelleşmiş otomasyon aracı** sunarken, geliştiricilere birleşik bir komut satırı aracı (`testfly`) sağlar.

---

## Kurulum

### Önerilen: `uv` ile Global Araç Kurulumu

CLI aracını sistem genelinde en hızlı ve izole şekilde kurmak için [`uv`](https://github.com/astral-sh/uv) kullanılması önerilir:

```bash
# Yerel depodan veya kaynak koddan global kurulum
cd testfly-mcp
uv tool install --editable . --force
```

CLI yürütülebilir dosyasının terminalinizde çalıştığını doğrulayın:
```bash
testfly --version
# Çıktı: testfly-mcp 1.0.0
```

### Alternatif: Pip / Sanal Ortam
```bash
pip install -e .
```

---

## Komut Satırı Arayüzü (CLI)

`testfly` ikili dosyası test kaydı, proje iskeleti oluşturma, ortam doğrulama ve IDE bağlantısı için eksiksiz bir komut seti sunar:

```bash
testfly [KOMUT] [SEÇENEKLER]
```

### Komut Matrisi

| Komut | Açıklama | Örnek |
| :--- | :--- | :--- |
| `testfly record <url>` | Chrome DevTools Protocol enjeksiyonu ve interaktif Stüdyo ile **Canlı Refakatçi Kaydediciyi** başlatır. | `testfly record https://saucedemo.com` |
| `testfly studio` *(veya `ui`)* | Tarayıcı denemeleri, araç yürütme ve görsel YAML düzenleme için bağımsız **TestFly Web Stüdyosunu** açar. | `testfly studio --port 8765` |
| `testfly doctor` | Python, Selenium, Chrome ve IDE yapılandırmalarını kapsayan kapsamlı ortam tanılaması yapar. | `testfly doctor` |
| `testfly init [isim]` | İnteraktif veya parametrelerle yeni bir TestFly projesi iskeleti oluşturur (TestNG, JUnit 5, Cucumber BDD; Web, API, Hybrid). | `testfly init my-tests --framework testng` |
| `testfly tools` | Mevcut 88 MCP aracını parametre adetleri ve açıklamalarıyla listeler. | `testfly tools --search locator` |
| `testfly mcp` *(veya `stdio`)* | MCP sunucusunu standart girdi/çıktı üzerinden çalıştırır (Claude Code, Cursor vb. IDE ajanları tarafından kullanılır). | `testfly mcp` |
| `testfly init-config` | Geçerli dizine standart, önceden yapılandırılmış bir `testfly.yml` dosyası çıkarır. | `testfly init-config` |
| `testfly` *(argümansız)* | Adım adım terminal sihirbazı sunan interaktif TTY modu. | `testfly` |

---

## Ortam Tanılaması (`testfly doctor`)

Testleri çalıştırmadan veya bir yapay zeka ajanını bağlamadan önce, tüm gereksinimlerin karşılandığını doğrulamak için `testfly doctor` komutunu çalıştırın:

```bash
testfly doctor
```

```text
========================================================
✈  TestFly MCP Environment Doctor — Status: HEALTHY
========================================================

✓ [PASS]  [Runtime] Python Version
          Details: Python 3.13.15 (/Users/.../.local/share/uv/tools/testfly-mcp/bin/python3)

✓ [PASS]  [Dependencies] Selenium Package
          Details: Version 4.49.0

✓ [PASS]  [Dependencies] Model Context Protocol SDK
          Details: Installed (mcp >= 1.2.0)

✓ [PASS]  [Browser] Google Chrome
          Details: Detected at /Applications/Google Chrome.app/Contents/MacOS/Google Chrome

✓ [PASS]  [IDE / AI Assistant] Claude Code Registration
          Details: Registered in ~/.claude/settings.json

✓ [PASS]  [Project] testfly.yml in Working Directory
          Details: Found at /workspace/testfly.yml

========================================================
```

---

## Yapay Zeka Ajanları ve IDE Bağlantısı

TestFly MCP sunucusu `stdio` üzerinden sorunsuz çalışır ve MCP uyumlu tüm istemcilere bağlanabilir.

### 1. Cursor IDE (`.cursor/mcp.json`)
Çalışma alanınızdaki veya global `.cursor/mcp.json` dosyasına sunucuyu ekleyin:

```json
{
  "mcpServers": {
    "testfly": {
      "command": "testfly",
      "args": ["mcp"]
    }
  }
}
```

### 2. Claude Code CLI (`~/.claude/settings.json`)
Claude CLI kullanarak TestFly'ı doğrudan kaydedin:

```bash
claude mcp add testfly testfly mcp
```

Veya `~/.claude/settings.json` dosyasını düzenleyin:
```json
{
  "mcpServers": {
    "testfly": {
      "command": "testfly",
      "args": ["mcp"]
    }
  }
}
```

### 3. VS Code Eklentisi
TestFly, etkinlik çubuğuna özel bir **TestFly QA Hub** paneli ekleyen resmi bir VS Code eklentisi (`vscode-extension`) sunar:
- **İnteraktif Araç Gezgini:** 88 aracın tümünü tek tıkla form arayüzüyle çalıştırma.
- **Tek Tıkla Kaydedici:** Editörden çıkmadan canlı kaydı başlatma.
- **Ortam Doktoru:** Gerçek zamanlı sistem sağlık rozeti.

---

## Araç Kataloğu Özeti (88 Araç)

Araçlar 5 ana işlevsel alanda düzenlenmiştir:

### 1. Tarayıcı Yaşam Döngüsü & Gezinme (12 Araç)
- `start_browser`: Chrome/Firefox/Edge oturumunu başlatır; arka plan (headless) ve görünür modları destekler.
- `navigate`: Belirtilen URL'ye gider (`navigate_to` takma adı desteklenir).
- `take_screenshot`: Çok modlu yapay zeka incelemesi için Base64 PNG ekran görüntüsü alır.
- `get_page_source`: Canlı DOM HTML kodunu çeker.
- `check_accessibility`: Sayfayı WCAG 2.1 kontrast ve etiket kurallarına göre denetler.
- `inspect_page`: Etkileşimli butonları, alanları ve formları özetler.
- `close_browser`: Aktif tarayıcı oturumunu kapatır.

### 2. Öğe Etkileşimi & Hareketler (20 Araç)
- `find_element` / `find_elements`: CSS, XPath, ID, isim, testid veya rol ile öğe bulur.
- `click`: Görünüme otomatik kaydırma yaparak tıklama gerçekleştirir.
- `type_text`: Giriş alanını temizleyip yapılandırılabilir gecikmeyle metin girer.
- `select_option`: Açılır menülerden değer, görünen metin veya indeks ile seçim yapar.
- `hover`, `double_click`, `right_click`, `drag_and_drop`: Fare hareketlerini yönetir.
- `upload_file`: İşletim sistemi dosya pencerelerini atlayarak dosya yükler.

### 3. Web-First Doğrulamalar & Durum Kontrolleri (18 Araç)
- `assert_element_visible` / `assert_element_hidden`: Otomatik bekleyen görünürlük kontrolleri.
- `assert_element_enabled` / `assert_element_disabled`: Form kontrollerinin etkinlik durumu.
- `assert_text_contains` / `assert_text_equals`: İçerik kontrolleri.
- `assert_title` / `assert_url`: Sayfa başlığı ve URL doğrulamaları.
- `assert_attribute`: Belirli HTML özniteliklerini doğrular (örn. `aria-expanded="true"`).

### 4. TestFly Java Kod Üretim Motoru (24 Araç)
- `detect_testfly`: `pom.xml` veya `build.gradle` içindeki TestFly bağımlılıklarını tespit eder.
- `generate_java_page_object`: `BasePage` genişleten Page Object sınıfları üretir.
- `generate_java_testng`: `BaseTest` genişleten TestNG sınıfları üretir.
- `generate_java_junit5`: `BaseJUnit5Test` genişleten JUnit 5 sınıfları üretir.
- `generate_gherkin`: Cucumber Feature, Step ve Runner sınıfları üretir.
- `generate_testfly_config`: Standart `testfly.yml` üretir.

### 5. Akıllı Test Bölücü & CI Kalite Kapıları (14 Araç)
- `shard_test_suite`: Test paketlerini geçmiş süre ağırlıklarına göre paralel CI düğümlerine dengeli dağıtır.
- `analyze_failure`: Hata yığın izleri üzerinde yapay zeka kök neden analizi yapar.

---

## İlgili Kılavuzlar
- [İnteraktif Kaydedici & Chrome Companion](./recorder.md)
- [İnteraktif Web Stüdyosu](./interactive-studio.md)
- [ADR-001: Kaydedici Mimarisi](./adr-001-mcp-recorder-architecture.md)
- [Yapay Zeka Destekli Test Otomasyonu](./agentic-testing.md)
