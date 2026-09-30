---
id: cli
title: TestFly MCP Bridge & CLI
sidebar_label: MCP Bridge & CLI
sidebar_position: 3
description: "Resmi TestFly MCP Bridge & CLI: NPX üzerinden sıfır bağımlılıkla Java 21 proje iskeleti oluşturma, yapay zeka asistanı entegrasyonu (Cursor, Claude, Copilot) ve Playwright MCP eşleşmesi."
---

# TestFly MCP Bridge & CLI

**TestFly MCP Bridge** (`@testfly/mcp`), TestFly framework'ünün resmi Model Context Protocol (MCP) köprüsü ve komut satırı araç setidir. Node.js üzerinde inşa edilmiş olup **sıfır Python ve sıfır pip bağımlılığı** ile çalışır. Canlı tarayıcı yönetimi için **Playwright MCP** ile eşleşirken, otonom **TestFly Java 21 test üretimi**, proje iskeleti oluşturma, eylem önbelleği (action-cache) yönetimi ve yapay zeka self-healing yama onarımı sağlar.

---

## ⚡ NPX ile Anında Proje İskeleti Oluşturma

Önceden herhangi bir kurulum yapmaya gerek kalmadan, tek bir komutla üretime hazır tam bir TestFly Java 21 test projesi oluşturabilirsiniz:

```bash
npx @testfly/mcp init my-test-suite
```

Bu komut saniyeler içinde şunları üretir:
- `testfly.yml` — Önceden yapılandırılmış yürütme modu, zaman aşımları ve raporlama ayarları.
- `pom.xml` — Java 21 (`<maven.compiler.release>21</maven.compiler.release>`) ve en güncel `io.github.hakanngul:testfly:1.0.7` bağımlılığı ile hazırlanmış Maven yapılandırması.
- `src/test/java/com/example/tests/SampleWebTest.java` — `BaseTest` extend eden, web-first assertion'lara sahip çalışan örnek test sınıfı.

Yeni test takımınızı hemen çalıştırın:

```bash
cd my-test-suite
mvn test
```

---

## 🤖 Yapay Zeka Asistanları (MCP) Entegrasyonu

TestFly MCP; **Cursor**, **Claude Desktop**, **GitHub Copilot** ve **Claude Code** araçlarına standart I/O (JSON-RPC) üzerinden doğrudan bağlanır.

### Seçenek 1: VS Code Eklentisi ile Tek Tıkla Kurulum (Önerilen)

1. **TestFly Studio** VS Code eklentisini (`testfly-vscode-1.1.0.vsix`) yükleyin.
2. Komut Paletini açın (`Cmd+Shift+P` / `Ctrl+Shift+P`).
3. **`TestFly: 1-Click Multi-Assistant MCP Setup`** komutunu çalıştırın.
4. Cursor, Claude Desktop ve VS Code; hem **Playwright MCP** hem de **TestFly Bridge** ile otomatik olarak yapılandırılır.

---

### Seçenek 2: NPX ile Manuel MCP Yapılandırması

`claude_desktop_config.json` veya `.cursor/mcp.json` dosyanıza aşağıdaki sunucuları ekleyin:

```json
{
  "mcpServers": {
    "playwright": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-playwright"]
    },
    "testfly": {
      "command": "npx",
      "args": ["-y", "@testfly/mcp"]
    }
  }
}
```

:::info Kendi Tarayıcını Getir (BYOB) Mimarisi
- **Tarayıcı Yürütme:** Resmi ve yüksek performanslı **Playwright MCP** (`@modelcontextprotocol/server-playwright`) tarafından yönetilir.
- **Test Üretimi ve Zeka:** Tarayıcı etkileşimlerini TestFly Java 21 kodlarına derleyen **TestFly Bridge** (`@testfly/mcp`) tarafından sağlanır.
:::

---

## 🛠️ Kullanılabilir MCP Araçları

Yapay zeka asistanınıza kaydedildiğinde, `@testfly/mcp` aşağıdaki araçları sunar:

| Araç | Açıklama |
| :--- | :--- |
| `generate_testfly_code` | Kaydedilen tarayıcı eylemlerini veya DOM anlık görüntülerini standart TestFly Java 21 test sınıflarına dönüştürür (`BaseTest`, `BasePage`, `getByRole`, `assertThat`). |
| `init_testfly_project` | Eksiksiz bir TestFly Java 21 Maven test projesi (`pom.xml`, `testfly.yml`, örnek test) oluşturur. |
| `inspect_action_cache` | Otonom `act("Hedef")` planlarını 0 ms yeniden oynatma için `.testfly/action-cache.json` üzerinden inceler. |
| `manage_action_cache` | Önbellekteki eylem planlarını geçersiz kılar veya temizler, böylece yapay zeka bunları bir sonraki çalıştırmada yeniden derler. |
| `list_remediations` | `target/remediations/` dizininde üretilen bekleyen AI self-healing git diff `.patch` dosyalarını listeler. |
| `apply_remediation_patch` | Kendi kendini onaran bir `.patch` dosyasını doğrudan Java test kaynak koduna uygular. |

---

## CLI Komutları Referansı

Terminalden doğrudan çalıştırıldığında `@testfly/mcp` şu komutları destekler:

```bash
# Yeni bir test projesi iskeleti oluşturun
npx @testfly/mcp init [dizin-adi]

# Köprü sunucu versiyonunu kontrol edin
npx @testfly/mcp --version

# Yardım çıktısını görüntüleyin
npx @testfly/mcp --help
```

---

## Sonraki Adımlar

- [Hızlı Başlangıç](/docs/getting-started) — İlk testinizi 5 dakikada çalıştırın.
- [Yapılandırma Referansı](/docs/configuration) — Tüm `testfly.yml` seçenekleri.
- [MCP Bridge & Playwright Mimarisi](/docs/ai/testfly-mcp) — Kod üretim motoru ve eylem önbelleğinin detayları.
- [Ajan Tabanlı Test & Otonom AI](/docs/ai/agentic-testing) — `act()` ile hedef odaklı yürütme ve kendi kendini onarma.
