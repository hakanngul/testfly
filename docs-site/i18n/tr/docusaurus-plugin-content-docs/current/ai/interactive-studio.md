---
id: interactive-studio
title: Etkileşimli Web Stüdyosu
sidebar_label: Etkileşimli Web Stüdyosu
sidebar_position: 4
description: TestFly MCP Web Stüdyosu ile tarayıcı denetimi, görsel kod üretimi ve testfly.yml yönetimi.
---

# Etkileşimli Web Stüdyosu

**TestFly MCP Stüdyosu**, QA mühendisleri ve geliştiricilerin doğrudan web tarayıcısı üzerinden canlı tarayıcı oturumlarını yönetmesini, MCP araçlarını test etmesini ve TestFly Java kodları üretmesini sağlayan sıfır bağımlılıklı yerel bir web uygulamasıdır.

```bash
testfly-mcp ui
```

Komutu çalıştırdığınızda stüdyo **`http://127.0.0.1:8765`** adresinde açılır ve varsayılan tarayıcınız otomatik olarak başlatılır.

---

## Modüller ve Özellikler

### 1. Browser Playground (Tarayıcı Denetimi)
**Browser Playground**, canlı tarayıcı oturumlarını görsel olarak incelemenizi sağlar:

- **Çalıştırma Modları:**
  - **🤖 Headless (Arka Plan):** Chrome masaüstünüzde harici bir pencere açmadan sessizce arka planda çalışır ve ekran görüntüleri doğrudan **Live Preview** paneline akar. *(Varsayılan)*
  - **🖥️ Visible Window:** Görsel hata ayıklama için Google Chrome'u masaüstünüzde fiziksel bir pencere olarak açar.
- **Gezinme ve Eylemler:** URL girip **Go** diyerek sayfayı yükleyin. Canlı öğelere tıklayın veya metin yazın.
- **Canlı Önizleme (Live Preview):** Sayfa geçişlerinden veya tıklamalardan sonra yüksek çözünürlüklü ekran görüntüsü otomatik olarak güncellenir.
- **Inspect Elements:** Sayfada tespit edilen tüm interaktif giriş alanlarını, butonları ve linkleri listeler.
- **A11y Audit:** Sayfadaki erişilebilirlik hatalarını (eksik label, renk kontrastı vb.) anında denetler.

---

### 2. Codegen Studio (Kod Üretimi)
Canlı tarayıcı oturumunu doğrudan TestFly Java kodlarına dönüştürür:

- **Desteklenen Kalıplar:**
  - **Page Object Modeli:** `BasePage` extend eden, modern a11y seçicilerine sahip sınıflar üretir.
  - **TestNG Testleri:** `BaseTest` extend eden, `assertThat(getDriver()).hasTitle(...)` içeren test sınıfları üretir.
  - **JUnit 5 Testleri:** `BaseJUnit5Test` extend eden test sınıfları üretir.
  - **Cucumber BDD:** Gherkin feature dosyası, `BaseCucumberSteps` ve `BaseCucumberTest` runner'ı üretir.
- **Tek Tıkla Kopyalama:** Renklendirilmiş Java kodunu tek tıkla panoya kopyalayıp projenize yapıştırabilirsiniz.

---

### 3. Tools Directory (88 Araç)
- 88 MCP aracını ada veya açıklamaya göre arayın ve filtreleyin.
- JSON parametre şemalarını inceleyin.
- Canlı oturum üzerinde herhangi bir aracı özel JSON girdileriyle test edin ve çıktısını anlık görüntüleyin.

---

### 4. Görsel `testfly.yml` Editörü
- Tarayıcı türünü (`chrome`, `firefox`, `edge`), çalışma modunu (`local` / `grid`), thread sayısını ve timeout sürelerini form üzerinden belirleyin.
- Canlı senkronize YAML önizlemesini inceleyin.
- **"Save testfly.yml to Project Root"** butonuna basarak dosyayı doğrudan proje kökünüze kaydedin.

---

### 5. Sistem Teşhisi (Doctor)
- Python ortamı, Selenium kütüphanesi, Google Chrome binary yolu ve IDE konfigürasyonlarını doğrulayan yerleşik kontrol paneli.
