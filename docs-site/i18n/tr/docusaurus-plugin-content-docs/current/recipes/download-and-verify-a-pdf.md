---
description: "Selenium'da bir PDF indirin ve doğrulayın: DownloadManager, yarım indirmeleri yöneterek dosyanın indirme dizininde görünmesini bekler; böylece dosyanın var olduğunu ve boş olmadığını doğrulayabilirsiniz."
id: download-and-verify-a-pdf
title: PDF indirme ve doğrulama
sidebar_label: Download & verify a PDF
---

# PDF indirme ve doğrulama

Bir indirme bağlantısına tıklayın, dosyanın inmesini bekleyin ve gerçek bir PDF olduğunu doğrulayın. `DownloadManager` indirme dizinini düzenli aralıklarla denetler ve tamamlanana kadar yarım (`.crdownload`) dosyaları yok sayar — `Thread.sleep()` yok.

```java title="InvoiceTest.java"
import io.testfly.browser.DownloadManager;
import io.testfly.test.BaseTest;
import java.io.File;
import org.openqa.selenium.By;
import org.testng.annotations.Test;

public class InvoiceTest extends BaseTest {

    @Test
    public void downloadsInvoicePdf() {
        open("/invoices/123");
        find("#download-pdf").click();

        // Dynamic filename (timestamped) → wait for any file, up to 15s:
        File pdf = DownloadManager.waitForAnyFile(15);

        softAssert().that(pdf.getName().endsWith(".pdf"), "Downloaded file should be a PDF");
        softAssert().that(pdf.length() > 0, "Downloaded file should not be empty");
    }
}
```

Bilinen bir dosya adı mı var? Bunun yerine ada göre bekleyin:

```java
File pdf = DownloadManager.waitForFile("invoice-123.pdf", 15);
```

İndirme dizinini yapılandırmada ayarlayın (varsayılan: `./target/downloads`):

```yaml title="testfly.yml"
browser:
  downloadDir: ./target/downloads
```

:::tip Testler arası temiz durum
`DownloadManager.clearDownloads()` çağrısını `@BeforeMethod` içinde yapın; böylece önceki çalıştırmadan kalan bayat bir dosya bekleme koşulunu sağlayamaz.
:::

**Daha derin referans:** [DownloadManager](/docs/guides/download-manager) — bekleme stratejileri, yarım indirme yönetimi ve temizlik.