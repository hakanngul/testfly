---
description: "Selenium'da dosya indirmelerini test edin: DownloadManager, indirme dizinini yoklar, dosyanın görünmesini bekler ve kısmi indirmeleri ele alır."
id: download-manager
title: DownloadManager
sidebar_position: 10
---

# DownloadManager

`DownloadManager`, testler sırasında dosya indirmelerini doğrular. Yapılandırılmış indirme dizinini yoklar ve bir dosyanın görünmesini bekler; kısmi indirmeleri zarif bir şekilde ele alır.

---

## Yapılandırma

```yaml title="testfly.yml"
browser:
  downloadDir: ./target/downloads   # varsayılan
```

---

## Belirli bir dosyayı bekleme

```java
import io.testfly.browser.DownloadManager;

@Test
public void exportCsvTest() {
    click(By.id("export-csv"));

    File downloaded = DownloadManager.waitForFile("report.csv", 15);
    softAssert().that(downloaded.exists(), "Downloaded file should exist");
    softAssert().that(downloaded.length() > 0, "Downloaded file should not be empty");
}
```

---

## Herhangi bir dosyayı bekleme

Dosya adı dinamik olduğunda (örn. bir zaman damgası içerdiğinde):

```java
click(By.id("download-invoice"));

File invoice = DownloadManager.waitForAnyFile(15);
softAssert().that(invoice.getName().endsWith(".pdf"), "Downloaded file should be a PDF");
```

---

## Testler arasında temizlik

```java
@BeforeMethod
public void cleanDownloads() {
    DownloadManager.clearDownloads();
}
```

---

## Kısmi indirmeler nasıl ele alınır

`DownloadManager`, bu uzantılara sahip dosyaları tamamlanana kadar yok sayar:

| Uzantı | Tarayıcı |
|---|---|
| `.crdownload` | Chrome |
| `.part` | Firefox |

Bir dosya yalnızca var olduğunda, sıfırdan büyük bir boyuta sahip olduğunda ve kısmi indirme uzantısı taşımadığında döndürülür.

---

## İndirme dizinini alma

```java
File dir = DownloadManager.resolveDownloadDir();
System.out.println("Downloads at: " + dir.getAbsolutePath());
```

Dizin yoksa otomatik olarak oluşturulur.