---
description: "Selenium'da işletim sistemi diyalogları olmadan bir dosya yükleyin: TestFly'nin upload(By, path) yöntemi dosya girişini doğrudan ayarlar; yollar sınıf yolundan veya mutlak bir konumdan çözümlenir."
id: upload-a-file
title: Bir dosya yükleme
sidebar_label: Upload a file
---

# Bir dosya yükleme

Bir `<input type="file">` dosya girişini doğrudan ayarlayın — kırılgan işletim sistemi dosya seçici otomasyonu yok. `BasePage` bir `upload(By, path)` yardımcısı sunar:

```java title="UploadPage.java"
import io.testfly.test.BasePage;
import org.openqa.selenium.By;

public class UploadPage extends BasePage {

    public void attachResume(String path) {
        upload(By.id("file-input"), path);   // sets the input's value directly
        click(By.id("submit"));
    }
}
```

```java
// From your test:
new UploadPage().attachResume("testfiles/resume.pdf");  // classpath-relative
new UploadPage().attachResume("/absolute/path/to/photo.png");
```

- Yol, **sınıf yoluna göre** (ör. `testfiles/resume.pdf`) veya **mutlak** olabilir. TestFly yolu çözümler ve dosya yoksa net bir hata ile hızlıca başarısız olur.
- Gizli/ekran dışı girişlerle çalışır — giriş değerini ayarlar, bu nedenle görünür bir diyalog gerekmez.
- **Çoklu** yükleme için, uygulamanızın beklediği şekilde girişe ayrılmış yollar iletin veya dosya başına yardımcıyı çağırın.

:::tip
Yüklemeler bir sayfa nesnesi üzerinden gider çünkü `upload(...)` bir `BasePage` yardımcısıdır. Dosyayı `src/test/resources` altında tutun; böylece sınıf yoluna göre yol her makinede ve CI'da çalışır.
:::

**Daha derin referans:** [BasePage](/docs/guides/base-page) — sayfa nesneleri için temel sınıf ve yardımcıları.