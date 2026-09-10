---
tags:
  - map
  - index
  - routing
  - graph-hub
date: 2026-09-10
status: active
type: map
---

# Proje Rota ve Bilgi Haritası (MAP.md)

Bu dosya, projedeki tüm bilgi kaynaklarına yönlendirme yapan ana navigasyon merkezidir ("Google Maps"). 
Ajan, token yakmamak için körlemesine arama yapmaz; önce bu haritaya bakar ve sadece hedeflenen düğüme (`[[...]]`) gider.

```
                         ┌─────────────┐
                         │   MAP.md    │ (Merkezi GPS Düğümü)
                         └──────┬──────┘
       ┌────────────────────────┼────────────────────────┐
       ▼                        ▼                        ▼
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│   KURALLAR   │         │    BELLEK    │         │     WIKI     │
│[[AGENTS]]    │         │[[scratchpad]]│         │[[wiki/index]]│
│[[rules/      │         │[[soul]]      │         │[[wiki/       │
│memory-       │         │              │         │architecture]]│
│protocol]]    │         │              │         │              │
└──────────────┘         └──────────────┘         └──────────────┘
```

---

## 1. Anayasa ve Kurallar (Rules)
- `[[AGENTS]]` — Proje genel geliştirici/ajan anayasası, teknoloji yığını ve kod standartları.
- `[[rules/memory-protocol]]` — "2 Yol & 3 Parça", hafıza döngüsü ve token tasarruf kuralları.

## 2. Kimlik ve Öz (Soul)
- `[[soul]]` — Ajanın kimliği, kıdemi, kırmızı çizgileri ve çalışma yaklaşımı (Tek paragraf).

## 3. Dinamik Hafıza / Kısa Bellek (Memories)
- `[[memories/scratchpad]]` — En son çalışma notları, aktif hedefler ve açık maddeler (Maksimum 2.200 karakter).

## 4. Kalıcı LLM Wiki Bilgi Deposu (Knowledge Base)
- `[[wiki/index]]` — Wiki ana giriş kapısı ve kavram dizini.
- `[[wiki/architecture]]` — TestFly framework mimari katmanları, ThreadLocal yapısı ve modüller.
- `[[wiki/webdriver-lifecycle]]` — WebDriver yaşam döngüsü (per-test / per-suite) ve izolasyon.
- `[[wiki/memory-system]]` — TestFly & Obsidian Graph hafıza sisteminin teknik detayları.

## 5. İşleyiş Becerileri ve Prosedürler (Skills)
- `[[skills/archify/SKILL]]` — Sistem mimarisi, iş akışı, sequence ve yaşam döngüsü için interaktif HTML diyagram üreticisi.
- `[[skills/memory-sync/SKILL]]` — Hafıza budama, scratchpad temizliği ve wiki senkronizasyon rutini.
- `[[skills/testfly-workflow/SKILL]]` — TestFly framework geliştirme, birim test ve sürüm yönetimi iş akışı.
- `[[skills/docusaurus-config/SKILL]]` — Dokümantasyon sitesi (Docusaurus) yapılandırma ve derleme adımları.
- `[[skills/automation-architecture/SKILL]]` — SDET otomasyon mimarisi, POM, SmartLocator ve assertion standartları.

## 6. Proje Çekirdek Dosyaları (Codebase Anchors)
- [pom.xml](file:///Users/hagul/Projects/TestFramework/testfly/pom.xml) — Maven bağımlılıkları ve konfigürasyon.
- [testfly.yml](file:///Users/hagul/Projects/TestFramework/testfly/testfly.yml) — Framework varsayılan çalışma ayarları.
- [src/main/java/io/testfly/](file:///Users/hagul/Projects/TestFramework/testfly/src/main/java/io/testfly/) — Framework çekirdek kodları.
- [src/test/java/io/testfly/unit/](file:///Users/hagul/Projects/TestFramework/testfly/src/test/java/io/testfly/unit/) — Birim test paketi.

---
Obsidian Graph Bağlantısı: `[[MAP]]` merkez düğümü projedeki tüm `[[wiki]]`, `[[rules]]`, `[[skills]]` ve `[[memories]]` dosyalarını birbirine bağlar.
