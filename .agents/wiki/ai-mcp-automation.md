---
tags:
  - wiki
  - ai
  - mcp
  - model-context-protocol
  - self-healing
date: 2026-09-10
status: active
type: wiki
---

# Yapay Zeka & testfly-mcp Otomasyonu

TestFly, modern yapay zeka asistanlarının (Claude, Cursor, Copilot vb.) test otomasyonunu doğrudan yönetebilmesi için resmi **Model Context Protocol (MCP)** sunucusunu barındırır.

---

## 1. testfly-mcp Yetenekleri

- **88 Adet Standart MCP Aracı:** Tarayıcı oturumu açma, DOM inceleme, akıllı seçici üretimi, form doldurma ve assertion çalıştırma.
- **Canlı Kod Üretimi:** Kullanıcı tarayıcıda gezinirken erişilebilirlik öncelikli (`getByRole`) tip güvenli Java kodları üretir.
- **Self-Healing (Kendi Kendini Onaran Seçiciler):** Değişen veya silinen DOM seçicilerinde AI ve DOM ağacı analiziyle en yakın alternatifi otomatik bulur.

---

## 2. CLI ve Web Studio

```bash
# MCP sunucu tanı testi
testfly-mcp doctor

# Etkileşimli görsel stüdyoyu başlatma
testfly-mcp ui
```

---

## İlgili Bağlantılar
- WebUI Test Mimarisi: `[[wiki/webui-testing]]`
- Temel Mimari: `[[wiki/architecture]]`
- Ana Harita: `[[MAP]]`
- Wiki Dizin: `[[wiki/index]]`
