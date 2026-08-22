---
description: "Kendini onaran Selenium locator'ları: bir seçici bozulduğunda, framework id, name, text ve data-testid üzerinden geri dönüş yapar ve her onarımı işaretler."
id: self-healing
title: Selenium Kendini Onaran Locator
sidebar_label: Kendini Onaran Locator'lar
sidebar_position: 10
---

# Kendini Onaran Locator'lar

Bir locator — bir sınıf yeniden adlandırıldığı, bir `id` taşındığı veya işaretleme yeniden düzenlendiği için — eşleşmeyi bıraktığında, framework testi başarısız etmek yerine **orijinal locator'ınızdan türetilen alternatif stratejilerle otomatik olarak yeniden deneyebilir**. Her onarım kaydedilir; böylece seçiciyi kendi hızınızda sonradan düzeltebilirsiniz.

Kendini onarma **isteğe bağlıdır** ve **şeffaftır**: test, locator çalışmışcasına devam eder ve HTML raporunda bir `⚠ healed` rozeti görünür.

---

## Etkinleştirme

Varsayılan olarak kapalıdır. `testfly.yml` içinde açın:

```yaml
locators:
  selfHealing: true
```

---

## Nasıl çalışır

Kendini onarma, [`WaitEngine`](./wait-engine) içine bağlanır. `waitForVisible(...)` veya `waitForClickable(...)` zaman aşımına uğradığında (`TimeoutException` / `NoSuchElementException`), framework başarısız olan `By` tanımını ayrıştırır, sıralı bir geri dönüş locator listesi türetir ve bulunan **ve görünür** ilk öğeyi döndürür. Hiçbiri eşleşmezse, orijinal istisna yeniden fırlatılır — kendini onarma, gerçekten eksik olan bir öğeyi asla gizlemez.

`WaitEngine` içinde yaşadığı için onarma, framework'ün beklemeleri üzerine inşa edilen her şeye otomatik olarak uygulanır — `BasePage` eylemleri, semantik locator'lar ve doğrudan `WaitEngine` çağrıları. Özel bir API çağırmazsınız.

### Geri dönüş stratejileri

Geri dönüşler **orijinal locator'ın kendi içeriğinden** türetilir — framework asla havadan seçici uydurmaz. Stratejiler şu sırayla denenir:

| # | Strateji | Nereden türetilir | Ne ile yeniden dener |
|---|---|---|---|
| 1 | `id-from-css` / `id-from-xpath` | CSS `#foo` veya XPath `@id='foo'` | `By.id` |
| 2 | `name-from-css` / `name-from-xpath` | CSS `[name='foo']` veya XPath `@name='foo'` | `By.name` |
| 3 | `exact-text-from-xpath` / `contains-text-from-xpath` | XPath `text()='foo'` / `contains(text(),'foo')` | XPath metin eşleşmesi |
| 4 | `class-from-css` | Bileşik bir CSS seçicisinin son `.className` parçası | `By.className` |
| 5 | `data-testid-from-css` | CSS `[data-testid='foo']` | `By.cssSelector` |
| 6 | `placeholder-from-css` | CSS `[placeholder='foo']` | `By.cssSelector` |

**Örnek.** Artık eşleşmeyen `By.cssSelector("div.header input#email")` locator'ı `By.id("email")`'e, ardından `By.className("header")`'e geri döner — böylece `id` hâlâ mevcut olduğu sürece bir sarmalayıcı yeniden adlandırması testi bozmaz.

---

## Neyin onarıldığını görme

Her onarım, testi, orijinal locator'ı, çalışan locator'ı ve kullanılan stratejiyi yakalayan bir `HealEvent` üretir. Bunları incelemek için iki yol vardır.

### HTML rapor rozeti

En az bir locator'ı onaran her test, [HTML raporunda](../reporting/html-report) adının yanında bir `⚠ healed` rozetiyle işaretlenir. O testte otomatik olarak onarılan locator sayısı için üzerine gelin.

### `target/healed-locators.json`

Suite sonunda tam onarım logu `target/healed-locators.json` dosyasına dışa aktarılır — her onarım için orijinal locator, onarılmış locator, strateji ve bir zaman damgası içeren bir kayıt. Bu dosyayı **düzeltilecek kırılgan seçicilerin yapılacaklar listesi** olarak ele alın.

```json
[
  {
    "testId": "LoginTest.loginWithValidCredentials",
    "originalLocator": "By.cssSelector: div.header input#email",
    "healedLocator": "By.id: email",
    "strategy": "id-from-css",
    "timestamp": 1719920400000
  }
]
```

---

## Kendini onarma vs. SmartLocator

İkisi de locator'ları daha dayanıklı kılar ancak farklı sorunları çözer:

| | Kendini onarma (bu sayfa) | [`SmartLocator`](./smart-locator) |
|---|---|---|
| Ne zaman faaliyete geçer | Bir locator **çalışma zamanında başarısız olduktan sonra** | **Önceden** — adayları kendiniz önden sağlarsınız |
| Alternatifleri kim sağlar | Framework bunları başarısız locator'dan türetir | Siz onları açıkça listelersiniz |
| İsteğe bağlılık kapsamı | Global (`locators.selfHealing: true`) | Çağrı sitesi başına |
| En uygun olduğu yer | Beklenmedik kaymayı yakalamak ve sonraki için loglamak | Ortamlar arasında farklı olduğunu zaten bildiğiniz locator'lar |

İyi bir araya gelirler: varyasyon *öngördüğünüz* yerlerde `SmartLocator` kullanın ve öngörmediğiniz kaymaları işaretleyen bir güvenlik ağı olarak kendini onarmayı açık bırakın.

---

## Ne zaman kullanılır

| Durum | Kendini onarma etkinleştirilsin mi? |
|---|---|
| İşaretlemesi sürekli değişen aktif geliştirmedeki uygulama | Evet — seçicileri güncellerken CI'ı yeşil tutar |
| Hangi locator'ların kaydığını görmek istiyorsunuz | Evet — JSON logu sizin düzeltme listeniz |
| Herhangi bir locator değişikliğinin **derlemeyi başarısız etmesi** gereken sıkı suite | Hayır — kaymanın başarısızlık olarak görünmesi için kapalı bırakın |

:::tip Onarmanın çürümeyi gizlemesine izin vermeyin
Onarılmış bir test bir **uyarıdır, göz ardı edilecek bir geçiş değil**. `target/healed-locators.json` dosyasını düzenli olarak inceleyin ve alttaki seçicileri güncelleyin — sürekli onarılan bir locator, sonunda hiçbir geri dönüşün kurtaramayacağı şekilde bozulacak olan bir locator'dır.
:::