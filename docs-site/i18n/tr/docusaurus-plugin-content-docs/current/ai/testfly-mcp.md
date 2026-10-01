---
id: testfly-mcp
title: TestFly MCP Köprüsü
sidebar_label: MCP Köprüsü & Playwright
sidebar_position: 2
description: Playwright MCP ile eşleşen TestFly MCP Köprüsü mimarisi, araç kataloğu ve tek tıkla kurulum rehberi.
---

# TestFly MCP Köprüsü & Playwright Entegrasyonu

**TestFly MCP Köprüsü**, yapay zeka kodlama asistanlarını (**Cursor**, **Claude Desktop**, **GitHub Copilot** ve **Claude Code**) TestFly Java ekosistemine bağlayan, sıfır bağımlılıklı, hafif bir [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) sunucusudur.

Python üzerinde hantal ve kırılgan bir tarayıcı otomasyon motoru sürdürmek yerine TestFly, modern **Bring Your Own Browser (Kendi Tarayıcını Getir)** modelini benimser:

1. **Tarayıcı Yönetimi:** Sektör standardı olan ultra hızlı **Playwright MCP** (`@modelcontextprotocol/server-playwright`) tarafından üstlenilir.
2. **TestFly Zekası & Kod Üretimi:** **TestFly Köprüsü** (`@testfly/mcp`) tarafından yönetilir; tarayıcı etkileşimlerini TestFly Java 21 testlerine (`BaseTest`, `BasePage`, `getByRole`, `assertThat`) derler, `.testfly/action-cache.json` otonom plan önbelleğini yönetir ve yapay zeka self-healing `.patch` yamalarını uygular.

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              YAPAY ZEKA KODLAMA ASİSTANLARI                            │
│                     Cursor  ·  Claude Desktop  ·  GitHub Copilot  ·  Claude Code       │
└──────────────────────────────────────────┬─────────────────────────────────────────────┘
                                           │
                    ┌──────────────────────┴──────────────────────┐
                    │ JSON-RPC (stdio / SSE)                      │ JSON-RPC (stdio / SSE)
                    ▼                                             ▼
┌──────────────────────────────────────┐     ┌───────────────────────────────────────────┐
│     TARAYICI KATMANI: PLAYWRIGHT MCP │     │       TEST MOTORU: TESTFLY MCP KÖPRÜSÜ    │
│     (@modelcontextprotocol/server)   │     │               (@testfly/mcp)              │
│  ┌────────────────────────────────┐  │     │  ┌─────────────────────────────────────┐  │
│  │  Canlı CDP Tarayıcı Yönetimi   │  │     │  │  Framework Odaklı Kod Üretim Motoru │  │
│  │  • browser_navigate            │  │     │  │  • Java 21 BaseTest & POM sınıfları │  │
│  │  • browser_click / fill_form   │  │     │  │  • Erişilebilirlik odaklı getByRole │  │
│  │  • browser_snapshot (DOM Ağacı)│  │     │  │  • Akıcı doğrulamalar (assertThat)  │  │
│  └────────────────┬───────────────┘  │     │  └──────────────────┬──────────────────┘  │
│                   │                  │     │                     │                     │
│                   ▼                  │     │  ┌──────────────────┴──────────────────┐  │
│  ┌────────────────────────────────┐  │     │  │  Otonom Plan & Self-Healing Deposu  │  │
│  │  İzole Chrome / Edge / Web     │  │     │  │  • .testfly/action-cache.json       │  │
│  │  (Sıfır ek yük, canlı çalışma) │  │     │  │  • target/remediations/*.patch      │  │
│  └────────────────────────────────┘  │     │  └─────────────────────────────────────┘  │
└──────────────────────────────────────┘     └───────────────────────────────────────────┘
```

---

## ⚡ Hızlı Kurulum (Tek Tıkla)

### Seçenek 1: TestFly VS Code Eklentisi Üzerinden (Önerilen)
1. **TestFly Studio** VS Code eklentisini (`testfly-vscode-1.1.0.vsix`) kurun.
2. Command Palette'i açın (`Cmd + Shift + P` veya `Ctrl + Shift + P`).
3. **`TestFly: 1-Click Multi-Assistant MCP Setup`** komutunu seçin.
4. Hem Playwright MCP hem de TestFly Köprüsü; **Cursor**, **Claude Desktop** ve **VS Code Native Copilot** için otomatik olarak yapılandırılır.

---

### Seçenek 2: Manuel JSON Yapılandırması

Asistanınızın konfigürasyon dosyasına (`~/.cursor/mcp.json` veya `claude_desktop_config.json`) her iki sunucuyu da ekleyin:

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

Python, pip, uv veya sanal ortam kurulumu **kesinlikle gerekmez**. Yalnızca **Node.js 18+** yeterlidir.

---

## 🛠️ MCP Araçları Kataloğu

TestFly Köprüsü, yapay zeka ajanlarına 7 odaklı ve yüksek değerli araç sunar:

| Araç (Tool) | Parametreler | Açıklama |
| :--- | :--- | :--- |
| `generate_testfly_code` | `actions`, `className`, `packageName`, `framework` | Kaydedilen tarayıcı aksiyonlarını Java 21 TestFly testlerine dönüştürür (`BaseTest`, `BasePage`, `getByRole`, `assertThat`). |
| `inspect_action_cache` | `workspaceRoot` | Otonom `act("Goal")` hedeflerinin `.testfly/action-cache.json` içindeki 0ms replay adımlarını okur ve inceler. |
| `manage_action_cache` | `workspaceRoot`, `action`, `goal` | Belirli bir hedefin önbelleğini geçersiz kılar (invalidate) veya tüm önbelleği temizleyerek LLM'in yeniden derlemesini sağlar. |
| `list_remediations` | `workspaceRoot` | Test koşumunda `AiHealingEngine` tarafından onarılan ve `target/remediations/` altına yazılan `.patch` dosyalarını listeler. |
| `apply_remediation_patch` | `workspaceRoot`, `patchFilePath` | Üretilen `.patch` dosyasını `git apply` ile doğrudan Java test kaynak koduna işler. |
| `init_testfly_project` | `directory` | Eksiksiz bir TestFly 1.0.6 + Java 21 Maven test projesini (`pom.xml`, `testfly.yml`, duman testi) oluşturur. |
| `calculate_shards` | `totalNodes`, `targetNodeIndex`, `items` | LPT (Longest Processing Time) bin-packing algoritması ile testleri CI düğümlerine dengeli paylaştırır. |

---

## 🤖 Uçtan Uca İş Akışı: AI Nasıl Test Yazar?

Bir yapay zeka asistanı (Cursor veya Claude) bu entegrasyonu şu şekilde kullanır:

1. **Adım 1 — Sayfaya Git ve İncele:**  
   AI, Playwright MCP'nin `browser_navigate` aracını çağırarak hedef siteyi açar ve `browser_snapshot` ile erişilebilirlik ağacını alır.
2. **Adım 2 — Etkileşimde Bulun:**  
   AI, Playwright MCP'nin `browser_click` ve `browser_type` araçlarıyla formları doldurur.
3. **Adım 3 — TestFly Java Kodu Üret:**  
   AI, TestFly Köprüsü'nün `generate_testfly_code` aracını çağırarak kaydedilen adımları gönderir. Köprü derlemeye hazır Java 21 kodu döner:
   ```java
   package io.testfly.examples.testng;

   import io.testfly.locator.Role;
   import io.testfly.locator.RoleOptions;
   import io.testfly.test.BaseTest;
   import org.testng.annotations.Test;

   public class LoginTest extends BaseTest {

       @Test
       public void executeRecordedFlow() {
           open();
           getByRole(Role.TEXTBOX, new RoleOptions().setName("Kullanıcı Adı")).type("standard_user");
           getByRole(Role.TEXTBOX, new RoleOptions().setName("Şifre")).type("secret_sauce");
           getByRole(Role.BUTTON, new RoleOptions().setName("Giriş Yap")).click();
           assertThatPage().hasUrl("https://example.com/panel");
       }
   }
   ```
4. **Adım 4 — Kaydet ve Çalıştır:**  
   AI üretilen kodu `src/test/java/...` altına yazar ve `mvn test` ile çalıştırır. Sıfır boilerplate, sıfır flakiness.
