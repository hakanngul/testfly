---
description: "TestFly HTML raporu: başarı yüzdesi göstergesi, yeniden denemeler, ekran görüntüleri, tutarsızlık radarı ve karanlık mod içeren, sunucu gerektirmeyen bağımsız bir paneldir."
id: html-report
title: Selenium HTML Raporu
sidebar_label: HTML Raporu
sidebar_position: 1
---

# HTML Raporu

TestFly, her test çalıştırmasından sonra `target/testfly-report.html` konumunda bağımsız bir HTML raporu üretir. Bu rapor sunucu ya da ek araç gerektirmez — dosyayı bir tarayıcıda açmanız yeterlidir.

---

## Rapor konumu

```
target/
└── testfly-report.html   ← bunu açın
```

---

## Sekmeler

Raporun sol kenar çubuğunda üç sekme bulunur:

### Panel sekmesi

Çalıştırmanın üst düzey özeti:

- **Toplam / Geçti / Kaldı / Atlandı** sayıları
- **Süre** — toplam duvar saati (geçen gerçek) süresi
- **Başarı Oranı** — geçen testlerin yüzdesi, renk kodlu (yeşil / turuncu / kırmızı)
- **Yeniden Deneme Özeti** — yeniden denenmiş, kurtarılmış, hâlâ başarısız olan sayıları (yalnızca yeniden denemeler olduğunda gösterilir)
- **En Yavaş 5 Test** — toplam süreye göre sıralanmış
- **Sürücü Başlatma** yüzdelik grafiği

### Test Durumları sekmesi

Tüm testleri içeren tam tablo:

| Sütun | Açıklama |
|---|---|
| Sınıf | Basit sınıf adı |
| Test | Test yöntemi adı |
| Durum | GEÇTİ / KALDI / ATLANDI rozeti |
| Süre | ms |
| Yeniden Denemeler | Yeniden deneme sayısını gösteren rozet (0 olduğunda gizlenir) |

Herhangi bir satıra tıklayarak **ayrıntı panelini** genişletebilirsiniz:
- Hata mesajı (kırmızı, kalın)
- Tam yığın izi (sabit genişlikli, kaydırılabilir)
- Zaman damgaları, durum rozetleri ve satır içi ekran görüntüleriyle adım zaman çizelgesi

### Hatalar sekmesi

Test Durumları sekmesindeki ile aynı ayrıntı panelleri, ancak yalnızca başarısız testler için. Ayrıntı panelleri önceden genişletilmiştir; böylece neyin ters gittiğini tıklamadan hemen görebilirsiniz.

---

## Bağımsız format

Tüm ekran görüntüleri Base64 ile kodlanır ve satır içine gömülür. Rapor tek bir dosyadır; bunu:

- Bir paydaşa e-postayla gönderebilir
- Bir Jira biletine ekleyebilir
- Bir CI yapıtı olarak arşivleyebilir
- Paylaşılan bir klasörde saklayabilir

Görsel klasörü, varlık referansı veya sunucu gerekmez.

---

## Adım zaman çizelgesi

Testler `StepLogger` kullandığında her adım ayrıntı panelinde görünür:

```
 1  Açık oturum açma sayfası        +0ms     INFO
 2  Kimlik bilgilerini gir          +312ms   INFO
 3  Panelin görünür olduğunu doğrula  +891ms   PASS  [ekran görüntüsü]
```

Küçük resimler tıklanabilir — ışık kutusu (lightbox) katmanında tam boyutta açılırlar.

---

## Yapılandırma

Rapor yolu ve adı şu anda yapılandırılabilir değildir — dosya her zaman `target/testfly-report.html` olarak yazılır.

---

## Derleme Meta Verileri

Rapor, çalışma zamanında yakalanan CI bağlamını gösteren bir **Derleme Meta Verileri** kartı görüntüler:

| Alan | Kaynak |
|---|---|
| CI Sağlayıcısı | Ortam değişkenlerinden algılanır (`GITHUB_ACTIONS`, `JENKINS_URL`, `GITLAB_CI` vb.) |
| Derleme Numarası / Derleme Kimliği | Sağlayıcıya özgü çalıştırma tanımlayıcıları |
| Dal / Commit | Geçerli dal ve SHA |
| Depo / Eylemci | Depo kısa adı ve derlemeyi tetikleyen kullanıcı |
| İş Adı / Aracı Adı | CI işi ve çalıştırıcı/aracı adı |
| Derleme URL'si | Pipeline çalıştırmasına dönüş bağlantısı |

Meta veri yakalama, tanınan CI ortamlarında otomatiktir ve `testfly.yml` içinde devre dışı bırakılabilir:

```yaml title="testfly.yml"
ci:
  captureMetadata: false
```

Desteklenen sağlayıcıların ve değişkenlerin tam listesi için [CI Meta Verileri](../ci/ci-metadata) bölümüne bakın.

---

## CI kullanımı

Raporu, CI çalışma alanı temizlendikten sonra korumak için bir yapıt olarak yükleyin:

```yaml title="GitHub Actions"
- name: Raporu yükle
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: testfly-report
    path: target/testfly-report.html
```

`if: always()` kuralı, testler başarısız olsa bile raporun yüklenmesini sağlar.