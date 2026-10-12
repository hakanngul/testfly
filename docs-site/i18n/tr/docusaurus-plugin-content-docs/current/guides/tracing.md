---
description: "TestFly'da adım izleri: başarısız her test için zaman çizelgesi, ekran görüntüleri ve her adımın erişilebilirlik (ARIA) veya DOM snapshot'ını içeren bağımsız bir HTML dosyası."
id: tracing
title: Adım İzleri
sidebar_position: 7
---

# Adım İzleri

İz (trace), bir testin her adımında sayfanın nasıl göründüğünü anlatan tek bir HTML dosyasıdır.
Herhangi bir tarayıcıda açabilirsiniz. Sunucu veya ek araç gerektirmez. TestFly bunu test
başarısız olduğunda, isterseniz her test için yazar.

```yaml title="testfly.yml"
tracing:
  enabled: true
```

Dosya `target/traces/{SınıfAdı}/{testMetodu}-trace.html` altına yazılır ve HTML raporu, testin
ayrıntı panelinden ona bağlantı verir. TestNG, JUnit 5 ve Cucumber ile çalışır.

## İz neyi gösterir

- **Adım zaman çizelgesi**: `StepLogger` ile loglanan her adım, her `Locator` eylemi ve her
  doğrulama, testin başlangıcından itibaren geçen süreyle.
- Ekran görüntüsüyle loglanan adımlar için **ekran görüntüsü** ve hata anında son ekran görüntüsü.
- **Her adımın başındaki sayfanın snapshot'ı.** Görmek için bir adıma tıklayın.
- Başarısız test için **son adımdan sonraki sayfa** ve öncesiyle **Diff** karşılaştırması.
- Hata mesajı, stack trace ve kayıt açıksa video kaydı.

Varsayılan snapshot erişilebilirlik ağacıdır, yani `ariaSnapshot()` ile yazdırılan metin:

```
- heading "Ödeme" [level=1]
- textbox "Kart numarası"
- button "Öde" [disabled]
```

Küçüktür, okunması kolaydır ve sayfanın CSS sınıfları veya sarmalayıcı öğeleri değişse de
değişmez. Satırlarını bir
[`matchesAriaSnapshot`](/docs/guides/assertions#erişilebilirlik-doğrulamaları) şablonuna
kopyalayabilirsiniz.

## Yapılandırma

| Anahtar | Varsayılan | Anlamı |
|---|---|---|
| `tracing.enabled` | `false` | Başarısız testler için iz yaz. |
| `tracing.captureOnPass` | `false` | Başarılı testler için de yaz. |
| `tracing.snapshots` | `aria` | Her adımın kaydettiği: `none`, `aria`, `dom` veya `both`. |
| `tracing.snapshotMaxKb` | `256` | Adım başına tutulan en uzun snapshot. Daha uzunları kesilir ve `… truncated` ile biter. |
| `tracing.snapshotOnFailureAfter` | `true` | Başarısız test için son adımdan sonraki sayfayı da kaydet. |

`dom`, script, stil ve süslemelerden arındırılmış sayfa HTML'ini kaydeder. `aria`'dan büyüktür
ve yakalanırken budayıcı sayfa öğelerine kısa süreliğine bir işaret niteliği ekler. Erişilebilirlik
ağacının göstermediği işaretlemeye (örneğin CSS sınıflarına) ihtiyaç duyduğunuzda kullanın.

## Maliyet

Snapshot almak adım başına bir betik çağrısıdır, tipik bir sayfada birkaç milisaniye. 
`tracing.enabled` `false` iken, testin çalışan bir tarayıcısı yokken (API ve veritabanı testleri)
veya sayfa okunamadığında hiçbir şey yakalanmaz. Snapshot hiçbir testi başarısız yapmaz.

Gizli içerik (`aria-hidden`, `display: none`) `aria` snapshot'larında yer almaz ve Shadow DOM'a
girilmez. Bkz. [Erişilebilirlik doğrulamaları](/docs/guides/assertions#erişilebilirlik-doğrulamaları).

## Açması güvenli

Sayfa içeriği gömülmeden önce kaçışlanır. Bu yüzden `</script>` veya HTML yorumu içeren bir
sayfa izi bozamaz ve iz içinde kod çalıştıramaz.
