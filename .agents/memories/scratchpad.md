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
- **Konu:** Assertion System Architecture Decision
- **Durum:** TAMAMLANDI.
- **Yapılanlar:**
  1. `AssertionSupport` arayüzüne `assertTrue`, `assertEquals` gibi basic primitive assertion'ların eklenmemesine (reddedilmesine) karar verildi. API şişkinliğini önlemek için bu işlemler AssertJ veya TestNG'ye devredildi. TestFly sadece auto-retry özellikli Web/UI tabanlı assertion'lar (`LocatorAssert`, `PageAssert`) sağlayacak.

### 2. Kaynaklar & Bağlantılar
- [[io.testfly.locator.Locator]]
- [[io.testfly.performance.PerformanceCollector]]
- [[io.testfly.accessibility.AccessibilityChecker]]
