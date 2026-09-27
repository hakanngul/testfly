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
- **Konu:** Customer Web Agentic Mode (Doğal Dil & Compile & Freeze)
- **Durum:** TAMAMLANDI & DOĞRULANDI. `Agentic.feature` sıfır CSS selector ile saf doğal dille koşuldu, DeepSeek-V3 ile aksiyonlar derlendi ve başarıyla PASSED aldı. İkinci koşuda ActionCache HIT (Compile & Freeze) ile anında replay edildi.

### 2. Kök Neden & Çözülen Darboğazlar
1. **DomPruner Gizli Element Filtreleme (testfly Core):**
   - Sayfada `display: none` olan header hızlı giriş barı (`#txtUserNameLGB`) DOM'da ilk sırada yer aldığı için LLM tarafından seçiliyordu. `DomPruner.java` içine JS seviyesinde `offsetParent === null` ve `display: none` elementleri budama özelliği eklendi.
2. **PageAssert Settle & Retry (testfly Core):**
   - Form submit sonrası yönlendirme / AJAX geçişi sürerken `assertWithAi` hemen DOM çektiği için henüz giriş yapılmamış görünüyordu. `PageAssert.java` içine 3 saniyelik settle & retry penceresi eklendi.
3. **Model Uyumluluğu:**
   - `testfly.yml` içindeki model `deepseek-chat` olarak ayarlandı.
4. **Doğrulama:**
   - 1. Koşu: ActionCache MISS -> AI ile derlendi -> Login oldu -> AI assertion'lar doğrulandı -> PASSED.
   - 2. Koşu: ActionCache HIT -> Dondurulmuş plan doğrudan çalıştırıldı -> PASSED.

### 3. Kaynaklar & Bağlantılar
- Dosyalar: `DomPruner.java`, `PageAssert.java`, `Agentic.feature`, `AgenticSteps.java`


