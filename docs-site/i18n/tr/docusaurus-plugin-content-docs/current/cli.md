---
id: cli
title: TestFly CLI & Scaffolder
sidebar_label: TestFly CLI
sidebar_position: 3
description: "Resmi TestFly CLI: proje iskeleti oluşturma (TestNG, JUnit 5, Cucumber BDD), ortam teşhisleri, interaktif stüdyo ve MCP sunucusu."
---

# TestFly CLI & Scaffolder

**TestFly CLI** (`testfly`), TestFly framework'ünün resmi komut satırı aracıdır. Hızlı proje iskeleti oluşturma, ortam teşhisleri, interaktif görsel stüdyo ve yapay zeka destekli test otomasyonu için entegre bir Model Context Protocol (MCP) sunucusu sunar.

---

## Kurulum

TestFly CLI'ı `pip` veya `uv` ile kurabilirsiniz:

```bash
# pip ile
pip install testfly-mcp

# Veya testfly-mcp deposundan yerel geliştirme için
cd testfly-mcp
pip install -e .
```

Kurulumunuzu doğrulayın:

```bash
testfly --version
# Çıktı: testfly 1.0.0
```

:::info Geriye Dönük Uyumluluk
CLI aracına `testfly`, `testfly-cli` veya `testfly-mcp` isimleriyle erişilebilir. `testfly-mcp` referansı veren mevcut IDE ve MCP istemci ayarlarınız kesintisiz çalışmaya devam eder.
:::

---

## Temel Komutlara Genel Bakış

```bash
testfly --help
```

| Komut | Açıklama |
| :--- | :--- |
| `testfly init [name]` | Yeni, çalışmaya hazır bir TestFly projesi oluşturur (TestNG, JUnit 5, Cucumber). |
| `testfly doctor` | Ortam ve bağımlılık tanı kontrollerini çalıştırır (Python, Selenium, Chrome, IDE yapılandırmaları). |
| `testfly studio` *(veya `ui`)* | `http://127.0.0.1:8765` adresinde interaktif görsel Web Stüdyosunu başlatır. |
| `testfly mcp` *(veya `stdio`)* | Yapay zeka asistanları için standart I/O üzerinden MCP sunucusunu başlatır. |
| `testfly tools` | Mevcut 88 MCP otomasyon ve kod üretim aracını listeler ve arar. |
| `testfly init-config` | Bulunulan dizinde standart `testfly.yml` şablonu oluşturur. |
| `testfly` *(terminalde)* | Parametresiz çalıştırıldığında interaktif terminal menüsünü açar. |

---

## 1. Proje Oluşturma (`testfly init`)

Tek bir komutla üretime hazır bir TestFly otomasyon projesi başlatın:

```bash
testfly init my-test-suite
```

Bu komut; `pom.xml`, `testfly.yml`, `.gitignore`, `README.md` ve `BaseTest` ya da `BaseApiTest` extend eden örnek test sınıflarını içeren eksiksiz bir proje yapısı üretir.

### Komut Seçenekleri

```bash
testfly init [name] [seçenekler]
```

- `--framework`, `-f`: Test çalıştırıcı framework (`testng` [varsayılan], `junit5`, `cucumber`).
- `--type`, `-t`: Test paketi türü (`web` [varsayılan], `api`, `hybrid`).
- `--base-url`, `-u`: Hedef uygulama adresi (varsayılan: `https://example.com`).
- `--group-id`, `-g`: Maven groupId (varsayılan: `com.example`).
- `--artifact-id`, `-a`: Maven artifactId (varsayılan: proje adından türetilir).
- `--dir`, `-d`: Özel hedef dizin yolu.

### Örnekler

#### TestNG Web UI Otomasyon Paketi
```bash
testfly init web-regression --framework testng --type web --base-url https://my-app.com
```

#### JUnit 5 API Test Paketi
```bash
testfly init payment-api-tests --framework junit5 --type api --base-url https://api.my-app.com
```

#### Cucumber BDD Hibrit Test Paketi (Web + API)
```bash
testfly init e2e-bdd --framework cucumber --type hybrid
```

---

## 2. Ortam Teşhisi (`testfly doctor`)

Ortamınızın test koşumu, tarayıcı otomasyonu ve yapay zeka araçları için gerekli tüm önkoşulları karşılayıp karşılamadığını denetleyin:

```bash
testfly doctor
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

## 3. İnteraktif Web Stüdyosu (`testfly studio`)

Canlı sayfadaki öğeleri incelemenizi, TestFly Page Object sınıfları üretmenizi ve doğrulamaları gerçek zamanlı test etmenizi sağlayan görsel web arayüzünü başlatın:

```bash
testfly studio
```

Seçenekler:
- `--port`, `-p`: Web stüdyosu portu (varsayılan: `8765`).
- `--no-browser`: Başlangıçta tarayıcıyı otomatik açmaz.

---

## 4. Yapay Zeka Asistanları İçin MCP Sunucusu (`testfly mcp`)

JSON-RPC Model Context Protocol sunucusunu başlatın:

```bash
testfly mcp
```

Bu uç nokta; Cursor, Claude Code, Windsurf veya JetBrains AI Assistant gibi yapay zeka ajanlarına 88 adet araç sunarak gerçek tarayıcı kontrolü, DOM hiyerarşi doğrulaması ve idiomatik TestFly Java test kodlarının otomatik üretilmesini sağlar.

---

## 5. Araç Kataloğu Arama (`testfly tools`)

Kullanılabilir otomasyon araçlarını listeleyin ve arayın:

```bash
# 88 aracın tamamını listele
testfly tools

# Anahtar kelimeye göre filtrele
testfly tools --search locator
testfly tools --search assertion
```

---

## Sonraki Adımlar

- [Hızlı Başlangıç](/docs/getting-started) — İlk testinizi 5 dakikada çalıştırın.
- [Yapılandırma Referansı](/docs/configuration) — Tüm `testfly.yml` seçenekleri.
- [Yapay Zeka & MCP Genel Bakış](/docs/ai/overview) — TestFly ile ajan tabanlı test otomasyonu.
