---
id: cli
title: TestFly MCP Bridge & CLI
sidebar_label: MCP Bridge & CLI
sidebar_position: 3
description: "Resmi TestFly MCP Bridge & CLI: NPX üzerinden sıfır bağımlılıkla Java 21 proje iskeleti oluşturma, yapay zeka asistanı entegrasyonu (Cursor, Claude, Copilot) ve Playwright MCP eşleşmesi."
---

# TestFly MCP Bridge & CLI

Ayrı Node.js bridge, proje oluşturma ve MCP araçları sunar. Tarayıcı incelemesini paket adı `@playwright/mcp` olan [Microsoft Playwright MCP](https://github.com/microsoft/playwright-mcp) sağlar.

## Kaynaktan kurulum

2026-10-04 kaynak kontrolü: `package.json`, `@testfly/mcp` 1.1.0, Node.js 18+ ve `bin/testfly-mcp.js` tanımlıyor. Public npm registry bu paket için 404 döndürdüğünden bu rehber kaynak checkout kullanır. Kaynaktaki paket adı/sürümü npm yayını yapıldığını kanıtlamaz.

```bash
git clone https://github.com/hakanngul/testfly-mcp.git
cd testfly-mcp
node bin/testfly-mcp.js --version
node bin/testfly-mcp.js init ../my-test-suite
```

Checkout için [kaynak deposu](https://github.com/hakanngul/testfly-mcp) kullanılır. Bridge script'i Node.js yerleşiklerini kullanır; Java testleri için JDK 21 ve Maven gerekir. Üretilen `pom.xml` ve `testfly.yml` dosyalarını [Başlangıç rehberi](/docs/getting-started) ile karşılaştırın, ardından yeni projede `mvn test` çalıştırın.

## Asistan yapılandırması

`/absolute/path/testfly-mcp` yerine gerçek checkout konumunu yazın. Bu `mcpServers` biçimi Claude Desktop ve Cursor gibi bunu destekleyen istemciler içindir; diğer istemciler farklı yapılandırma biçimi gerektirebilir.

```json
{
  "mcpServers": {
    "playwright": {
      "command": "npx",
      "args": ["-y", "@playwright/mcp@latest"]
    },
    "testfly": {
      "command": "node",
      "args": ["/absolute/path/testfly-mcp/bin/testfly-mcp.js"]
    }
  }
}
```

IDE kurulum eylemleri şu anda düzeltilmesi gereken paket referansları üretiyor. Sunucuları başlatmadan önce ürettikleri yapılandırmayı bu örnekle karşılaştırın; [IDE eklentileri](/docs/ai/ide-plugins) sayfasına bakın.

## MCP araçları

| Araç | Amaç |
|---|---|
| `generate_testfly_code` | Verilen eylemlerden Java üretir; çıktıyı inceleyip derleyin. |
| `init_testfly_project` | Maven test projesi oluşturur. |
| `inspect_action_cache` | Önbellekteki planları okur. |
| `manage_action_cache` | Bir hedefi veya tüm planları önbellekten kaldırır. |
| `list_remediations` | Üretilen patch dosyalarını listeler. |
| `apply_remediation_patch` | Seçilen patch'i çalışma alanına uygular. |

Kontrol edilen bridge kaynağı bu altı aracı tanımlar; bridge 88 tarayıcı otomasyon aracı sunmaz. Canlı tarayıcı verilerini Playwright MCP ile alın, ardından ilgili eylemleri kod üreticisine aktarın.

## CLI komutları

```bash
node bin/testfly-mcp.js init ../my-test-suite
node bin/testfly-mcp.js --version
node bin/testfly-mcp.js --help
```

`studio` ve `record`, tarihsel Python uygulamasına aittir. Node bridge komutları değildir. [Tarihsel recorder mimarisine](/docs/ai/adr-001-mcp-recorder-architecture) bakın.

## Sonraki adımlar

- [MCP mimarisi](/docs/ai/testfly-mcp)
- [Agentic testler](/docs/ai/agentic-testing)
- [Hazır promptlar](/docs/ai/prompt-recipes)
