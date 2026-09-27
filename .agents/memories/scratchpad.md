---
tags:
  - memory
  - scratchpad
  - ephemeral
date: 2026-09-27
status: active
char_limit: 2200
---

# Aktif Çalışma Not Defteri (Scratchpad)

### 1. Aktif Odak ve Son Durum
- **Konu:** Auto-POM (Learned Page Model) & Sürekli Öğrenen AI Ajanı
- **Durum:** TAMAMLANDI & DOĞRULANDI. `io.testfly.agent.knowledge` paketi oluşturuldu (`LearnedElement`, `LearnedPageModel`, `PageKnowledgeStore`, `LocalIntentResolver`, `KnowledgeLearner`).
- **Sonuç:** Yeni senaryo `"Click Bilgilerim from user profile menu"` hedefi AI'a HİÇ GİTMEDEN doğrudan yerel hafızadan (Auto-POM Knowledge HIT) çözüldü ve 22 saniyede BUILD SUCCESS ile çalıştı!

### 2. Kök Neden & Mimari Kazanımlar
1. **Learned Page Model (Auto-POM):**
   - Sayfa elementleri ve açılır menü ilişkileri (`profile_menu` hover -> `bilgilerim` click) `.testfly/page-knowledge.json` dosyasına kaydedildi.
2. **LocalIntentResolver:**
   - Yeni testlerde farklı cümlelerle gelse bile element ve niyet eşleşirse LLM çağrılmadan 0 token ve 0 ms gecikmeyle yerel plan üretildi.
3. **KnowledgeLearner (Sürekli Öğrenme):**
   - AI'ın derlediği her başarılı aksiyon otomatik olarak sayfa modeline kaydedilerek hafıza büyütülüyor.
4. **Doğrulama:**
   - `PageKnowledgeTest`: 5 test PASS.
   - `Customer_web_testfly` `@AutoPom`: 1 test PASS (Auto-POM HIT).

### 3. Kaynaklar & Bağlantılar
- Dosyalar: `PageKnowledgeStore.java`, `LocalIntentResolver.java`, `KnowledgeLearner.java`, `ActionCompiler.java`, `Agentic.feature`


