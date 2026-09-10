---
tags:
  - wiki
  - memory
  - testfly
  - obsidian-graph
date: 2026-09-10
status: active
type: wiki
---

# TestFly Kalıcı Hafıza ve Obsidian Graph Sistemi

Bu doküman, projede uygulanan "2 Yol & 3 Parça" hafıza mimarisinin teknik işleyişini açıklar.

---

## 1. 2 Yol (Two Paths)
1. **Okuma Yolu (Read Path):**
   - Ajan asla doğrudan tüm proje dosyalarını taramaz.
   - Önce `[[memories/scratchpad]]` okunur.
   - Detay gerekiyorsa `[[MAP]]` haritasından ilgili `[[wiki/...]]` düğümüne sıçranır.
2. **Yazma Yolu (Write Path):**
   - Oturum bittiğinde anlık durum `[[memories/scratchpad]]` içine yazılır (2.200 karakter sınırı).
   - Kalıcı kurallar ve mimari kararlar `[[wiki/]]` sayfalarına dönüştürülür ve `[[MAP]]` güncellenir.

## 2. 3 Parça (Three Components)
- **Kural:** `[[AGENTS]]` ve `[[rules/memory-protocol]]`
- **Harita:** `[[MAP]]`
- **Depo:** `[[memories/scratchpad]]` ve `[[wiki/index]]`

## 3. Obsidian Graph Entegrasyonu
- Tüm başlıklar YAML frontmatter taşır (`tags`, `date`, `status`, `type`).
- Dosyalar arasındaki her referans `[[wikilink]]` standardındadır.
- Obsidian Graph View üzerinde açıldığında, `[[MAP]]` merkezli bir bilgi yıldızı ve birbirine bağlı adacıklar şeklinde görselleşir.

---

## İlgili Bağlantılar
- Protokol: `[[rules/memory-protocol]]`
- Ana Harita: `[[MAP]]`
- Ajan Ruhu: `[[soul]]`
- Hafıza Becerisi: `[[skills/memory-sync/SKILL]]`
