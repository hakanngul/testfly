---
id: testfly-mcp
title: TestFly MCP Köprüsü
sidebar_label: MCP Köprüsü & Playwright
sidebar_position: 2
description: Playwright MCP ile eşleşen TestFly MCP Köprüsü mimarisi, araç kataloğu ve tek tıkla kurulum rehberi.
---

# TestFly MCP Bridge & Playwright

Güncel kaynak bridge, tarayıcı otomasyonunu Java araçlarından ayırır:

```text
AI asistanı
  ├─ Playwright MCP (@playwright/mcp): tarayıcı + DOM verileri
  └─ TestFly Node bridge: Java üretimi + proje/cache/patch araçları
                            ↓
                      Java 21 TestFly testleri
```

2026-10-04 kaynak kontrolü: `package.json`, `@testfly/mcp` 1.1.0, Node.js 18+ ve `bin/testfly-mcp.js` tanımlıyor. Public npm registry bu paket için 404 döndürdüğünden bu rehber kaynak checkout kullanır. Kaynaktaki paket adı/sürümü npm yayını yapıldığını kanıtlamaz.

## Kaynak kurulumu ve yapılandırma

Kaynak checkout, CLI komutları ve tam asistan yapılandırması için [MCP Bridge & CLI](/docs/cli) rehberini izleyin. `@modelcontextprotocol/server-playwright` kullanmayın: Microsoft'un paketi [`@playwright/mcp`](https://github.com/microsoft/playwright-mcp) adını taşır.

## Bridge araçları

| Araç | Amaç |
|---|---|
| `generate_testfly_code` | Verilen eylemlerden Java üretir; çıktıyı inceleyip derleyin. |
| `init_testfly_project` | Maven test projesi oluşturur. |
| `inspect_action_cache` | Önbellekteki planları okur. |
| `manage_action_cache` | Bir hedefi veya tüm planları önbellekten kaldırır. |
| `list_remediations` | Üretilen patch dosyalarını listeler. |
| `apply_remediation_patch` | Seçilen patch'i çalışma alanına uygular. |

Kaynak, stdio JSON-RPC üzerinden altı araç sunar. `generate_testfly_code` şeması `testng`, `junit5` ve `page_object` kabul etse de kontrol edilen uygulama `framework` seçimine göre dallanmadan TestNG `BaseTest` sınıfı üretir. Üretilen kod başlangıç noktasıdır; şemada veya eski rehberde geçtiği için JUnit, Page Object ya da Cucumber çıktısının uygulandığını varsaymayın.

Tarayıcı eylemleri ayrı tarayıcı MCP sunucusundan alınır. Bir eylem planını yeniden kullanmak yeni LLM isteğini önleyebilir; tarayıcı eylemleri, cache okuma ve doğrulamalar yine zaman alır. Replay için 0 ms garantisi yoktur.

## Erişilebilirlik ve doğrulama

[kaynak deposu](https://github.com/hakanngul/testfly-mcp) Node bridge ve IDE alt projelerini içerir. Public npm paketi kontrol tarihinde erişilebilir değildi. Burada kaynak checkout yolu gösterilir; yayın ve IDE marketplace erişimi ayrıca kontrol edilmelidir. Bu rehber harici IDE kurulumunun veya tarayıcı çalıştırmanın uçtan uca test edildiğini iddia etmez.

[Python recorder ADR'si](/docs/ai/adr-001-mcp-recorder-architecture), [Web Studio](/docs/ai/interactive-studio) ve [recorder](/docs/ai/recorder), tarihsel uygulamayı anlatır; bu komutlar Node CLI içinde sunulmaz.
