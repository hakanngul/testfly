---
description: "TestFly test sonuçlarını yerleşik adapter ile Allure 2 JSON biçiminde dışa aktarın."
id: allure
title: Allure Rapor Entegrasyonu
sidebar_position: 4
---

# Allure Rapor Entegrasyonu

Yerleşik `AllureReportAdapter`, suite tamamlanınca her test için Allure 2 uyumlu JSON çıktısı üretir. Aspect weaver veya Allure TestNG agent bağımlılığı gerekmez.

## Etkinleştirme

```yaml
reporting:
  allure:
    enabled: true
```

Çıktı yolu sabit olarak `target/allure-results/` kullanılır. `reporting.allure.resultsDir` desteklenen bir ayar değildir. Adapter framework tarafından kaydedilir; test metadata, durum, adımlar ve mevcut hata ekran görüntülerini dönüştürür.

## İçerik

- Test adı, tam ad (`Class#method`) ve `suite`, `testClass`, `thread`, `framework` (`testng`), `language` (`java`) label'ları. Biliniyorsa `browser`, test retry edildiyse `flaky` label'ı da eklenir.
- `start` / `stop` zaman damgaları suite bitiş zamanından türetilir (`stop` = suite bitişi, `start` = `stop` − test süresi). Bunlar her testin gerçek başlangıç/bitiş zamanı değildir.
- PASS/PASSED/INFO → passed; FAIL/FAILED → failed; SKIPPED → skipped; diğer durumlar → broken.
- StepLogger adımları ve süreleri.
- Hata detayları ve mevcut screenshot verisinden PNG ekleri.

## Raporu görüntüleme

JSON üretimi yerleşiktir; HTML görüntülemek için Allure CLI'yi ayrıca kurmanız gerekir.

```bash
allure serve target/allure-results
allure generate target/allure-results -o target/allure-report --clean
allure open target/allure-report
```

## GitHub Actions

Testlerden sonra sonuçları artifact olarak saklayabilirsiniz:

```yaml
- name: Run TestFly Tests
  run: mvn test
- name: Upload Allure results
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: allure-results
    path: target/allure-results
```

GitHub Pages'a yayınlamak için Allure CLI ile oluşturulan HTML veya history çıktısını deployment job'una verin; gerekli token izinlerini workflow içinde tanımlayın.
