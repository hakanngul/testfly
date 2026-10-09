---
id: overview
title: Yapay Zeka (AI) & MCP Otomasyonuna Genel Bakış
sidebar_label: Genel Bakış & Mimari
sidebar_position: 1
description: TestFly'ın Model Context Protocol (MCP) kullanarak yapay zeka asistanlarıyla gerçek tarayıcı testi ve doğrulanmış kod üretimi yapma yöntemi.
---

# AI & MCP Otomasyonuna Genel Bakış

TestFly iki ayrı AI kullanım yolu sunar:

1. **Java runtime:** `act()`, `byIntent()`, AI doğrulamaları, eylem önbelleği ve patch üretimi TestFly testlerinin içinde çalışır. Java framework'ünün AI sağlayıcısı, IDE asistanından ayrıca yapılandırılır.
2. **Harici MCP araçları:** asistan tarayıcı incelemesi için Playwright MCP, proje/kod üretimi ve cache/patch işlemleri için ayrı Node.js TestFly bridge kullanır.

```text
Asistan → Playwright MCP → tarayıcı incelemesi
        → TestFly Node bridge → Java kodu / cache / patch araçları
Java testleri → yapılandırılan AI sağlayıcısı → runtime eylem / doğrulama
```

2026-10-04 kaynak kontrolü: `package.json`, `@testfly/mcp` 1.1.0, Node.js 18+ ve `bin/testfly-mcp.js` tanımlıyor. Public npm registry bu paket için 404 döndürdüğünden bu rehber kaynak checkout kullanır. Kaynaktaki paket adı/sürümü npm yayını yapıldığını kanıtlamaz.

## Çalışma akışı

Locator seçmeden önce gerçek uygulamanın DOM'unu inceleyin. `io.testfly.locator` tarafından desteklenen semantik locatorları tercih edin. Üretilen kodu inceleyip TestFly bağımlılığınıza karşı derleyin; kod üretimi testin çalıştığını kanıtlamaz. CI'a almadan önce testi kontrollü ortamda çalıştırın.

## Bileşenler

| Bileşen | Başlangıç |
|---|---|
| Java runtime AI özellikleri | [Agentic testler](/docs/ai/agentic-testing) |
| Altı araçlı Node MCP bridge | [MCP Bridge & CLI](/docs/cli) |
| Canlı tarayıcı incelemesi | [Microsoft Playwright MCP](https://github.com/microsoft/playwright-mcp) |
| IDE kaynak projeleri | [IDE eklentileri](/docs/ai/ide-plugins) |
| Kod üretim promptları | [Hazır promptlar](/docs/ai/prompt-recipes) |

Önceki Python/Selenium sunucusu ve 88 araçlı recorder modeli tarihseldir. [ADR](/docs/ai/adr-001-mcp-recorder-architecture), [Web Studio](/docs/ai/interactive-studio) ve [recorder](/docs/ai/recorder) sayfaları bu tasarımı korur; bu komutlar güncel Node özellikleri olarak sunulmaz.
