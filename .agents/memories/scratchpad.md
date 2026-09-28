---
tags:
  - memory
  - scratchpad
  - ephemeral
date: 2026-09-28
status: active
char_limit: 2200
---

# Aktif Çalışma Not Defteri (Scratchpad)

### 1. Aktif Odak ve Son Durum
- **Konu:** Java 21 (JDK 21 LTS) Migration & Modernization.
- **Durum:** TAMAMLANDI. Tüm testler (1311) geçiyor.
- **Yapılanlar:**
  1. `pom.xml` ve belgelendirmeler (AGENTS.md, GEMINI.md, README.md) Java 21 LTS baseline'ına güncellendi.
  2. `JdkLoadEngine` içinde thread-pool yerine Project Loom (Virtual Threads) kullanıldı.
  3. `Locator` nesnesi Sequenced Collections (`getFirst()`, `getLast()`, `first()`, `last()`) kullanacak şekilde modernize edildi.
  4. `Locator` sınıfındaki selector tipleri exhaustive Java 21 Switch Expressions formuna (oklu syntax) çevrildi.
  5. TestFly 1.0.5 JDK 21 bazlı olarak derlenip yerel `.m2` reposuna (`io.github.hakanngul:testfly:1.0.5`) yüklendi.
- **Bekleyen İşler:** Mevcut `docs/superpowers/plans/2026-09-28-jdk21-migration.md` planındaki 6 task %100 başarıyla bitirildi. 

### 2. Kaynaklar & Bağlantılar
- [[io.testfly.loadtest.internal.JdkLoadEngine]]
- [[io.testfly.locator.Locator]]
- [[io.testfly.unit.LocatorTest]]
