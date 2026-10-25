---
id: browser-inspector
title: Browser Inspector (VS Code)
sidebar_position: 11
description: "VS Code için TestFly Browser Inspector: bir sayfayı Chrome veya Edge'de açın, ekran görüntüsünde ya da DOM ağacında öğe seçin, eşleşme sayılarıyla sıralı TestFly locator'larını ve page object alanlarını kopyalayın."
---

# Browser Inspector

Browser Inspector, TestFly VS Code eklentisinin bir parçasıdır ve [Mobile Inspector](../mobile/inspector.md)'ın yanında durur. Bir sayfayı makinenizde kurulu Chromium tabanlı bir tarayıcıda (Chrome, Edge veya Chromium) açar ve page object yazmanıza yardım eder:

- sayfanın tamamı tek bir ekran görüntüsü olarak, imlecin altındaki öğe çerçeveli;
- test id'leri, rolleri ve metinleri bir bakışta gösteren DOM ağacı;
- seçilen öğe için sıralı TestFly locator'ları (`getByTestId`, `getByRole`, `getByLabel`, `getByPlaceholder`, `findById`, `getByText`, `find`) ve her birinin sayfada kaç öğeyle eşleştiği;
- bir `BasePage` alt sınıfına yapıştırılmaya hazır `Locator` alanı;
- tarayıcıda tıklama ve yazma, geri, ileri ve yenileme.

## Kurulum

Browser Inspector, Mobile Inspector ile aynı paketle gelir:

```bash
code --install-extension tools/vscode-mobile-inspector/dist/testfly-mobile-inspector-0.3.0.vsix
```

VS Code 1.85 veya üstü ile Chrome, Edge ya da Chromium gerekir. Tarayıcı olağan kurulum yerinde bulunur; başka bir yer için `testflyInspector.browserPath` ayarını girin. WebDriver, Node paketi veya tarayıcı eklentisi gerekmez: inspector tarayıcıyı bir pipe üzerinden DevTools protokolüyle yönetir.

## Açmak

Etkinlik çubuğundaki **TestFly** simgesine tıklayın ve **Browser**'a geçin:

| Alan | Anlamı |
|---|---|
| Page URL | açılacak sayfa; `example.com` → `https://example.com`, `localhost:3000` → `http://localhost:3000` |
| Browser | bu makinede bulunan tarayıcılar; **Rescan** yeniden arar |
| Viewport | Desktop 1440 × 900, Laptop 1280 × 800, Tablet 768 × 1024 veya Mobile 390 × 844 |
| Headless | tarayıcı penceresi açılmaz; sayfa yalnızca inspector'da görünür |
| Keep session | çerez ve oturumlar bir TestFly profilinde çalıştırmalar arasında korunur (kendi tarayıcı profiliniz asla kullanılmaz); kapalıyken geçici bir profil kullanılır |

**Launch and inspect** paneli açar. Son açılan sayfalar **Recent** altında listelenir. Komut paletindeki **TestFly: Open Browser Inspector** bir URL sorar ve sayfayı tarayıcı penceresiyle ve korunan oturumla açar.

## Panel

| Alan | Gösterdiği |
|---|---|
| Araç çubuğu | tarayıcı ve sürümü, geri / ileri / yenile, adres çubuğu (URL yazıp Enter'a basın), viewport, **Refresh**, tıklama modu (Inspect / Click) ve durum |
| Sayfa (üstte) | sayfanın tamamı tek ekran görüntüsü olarak; üzerine gelince öğe çerçevelenir, tıklama öğeyi seçer (Inspect) ya da tarayıcıda tıklar (Click) |
| **DOM Tree** (sol alt) | etiket, metin, test id, id ve sınıflarla öğe ağacı; metin, etiket, test id veya role göre filtre; ok tuşlarıyla gezinme ve açma |
| **Locators** (sağ alt) | seçilen öğenin TestFly locator'ları, page object alanı ve o öğe üzerinde Click / Type |
| **Attributes** | tüm özellikler, rol, erişilebilir ad, metin, sınırlar ve durum |

Sayfa ile çekmece arasındaki ya da ağaç ile ayrıntılar arasındaki sınırı sürükleyerek boyutlandırabilirsiniz; boyutlar hatırlanır. **Headless** kapalıyken tarayıcı penceresini doğrudan kullanıp sayfayı yeniden okumak için **Refresh**'e basabilirsiniz.

## Locator bulmak

Sayfada veya ağaçta bir öğe seçin. **Locators** sekmesi bu öğeye çözülen locator'ları listeler; önce tekil olanlar, sonra TestFly'ın tercih sırasıyla:

| Tür | Örnek | Eşleşme sayısı |
|---|---|---|
| Test id | `getByTestId("sign-in")` | test id özelliği bu değere eşit öğeler |
| Rol | `getByRole(Role.BUTTON, "Sign in")` | bu role sahip ve erişilebilir adı bu adı içeren öğeler |
| Label | `getByLabel("Email")` | erişilebilir adı bu etiketi içeren form kontrolleri |
| Placeholder | `getByPlaceholder("you@example.com")` | placeholder'ı bu metni içeren öğeler |
| Id | `findById("em")` | bu id'ye sahip öğeler |
| Metin | `getByText("Forgot password?")` | metni bu metni içeren en içteki öğeler |
| CSS | `find("#login > button")` | sayfada tekil olan en kısa CSS yolu |

Sayılar, TestFly'ın çalışma anında kullandığı kurallarla sayfanın içinde hesaplanır: `Role`'ün rol seçicileri, `Locator`'ın erişilebilir ad hesabı, varsayılan olarak büyük-küçük harf duyarsız "içerir" eşleşmesi ve metinde yalnızca en içteki öğe. Birden büyük bir sayı, locator'ın kodda daraltılması gerektiği anlamına gelir (`.within(...)`, `.withText(...)` veya `.nth(i)`). Sayfanın `<title>` etiketi de testte olduğu gibi `getByText` sayısına girer.

**Page object field** kutusu en iyi locator'ı bir alan olarak gösterir, örneğin:

```java
private final Locator signIn = getByTestId("sign-in");
```

Bu satır herhangi bir `BasePage` alt sınıfında derlenir (`getByRole` için `import io.testfly.locator.Role;` gerekir). Her locator'ın yanındaki `<>` düğmesi o locator'ı alan olarak kopyalar.

Projeniz farklı bir test id özelliği okuyorsa (`locators.testIdAttribute`, örneğin `data-qa`), `testflyInspector.testIdAttribute` ayarını aynı değere getirin.

## Tarayıcıda işlem yapmak

- **Click** modu: ekran görüntüsüne tıklamak tarayıcıda aynı noktaya tıklar; inspector başlayan bir sayfa geçişini bekler ve sayfayı yeniden okur.
- Locators sekmesindeki **Click** ve **Type** seçilen öğe üzerinde çalışır: Type öğeye tıklar ve metni yazar.
- Geri, ileri ve yenile tarayıcı geçmişini kullanır; adres çubuğu herhangi bir URL'yi açar.

## Tuşlar

| Tuş | İşlem |
|---|---|
| `R` | Refresh |
| `I` / `C` | Inspect / Click modu |
| `/` | ağaç filtresine odaklan |
| `↑` `↓` | ağaçta gezin |
| `←` `→` | kapat / aç |
| `Esc` | seçimi kaldır |

## Ayarlar

| Ayar | Varsayılan | Anlamı |
|---|---|---|
| `testflyInspector.browserPath` | boş | Chrome, Edge veya Chromium çalıştırılabilir dosyası; boşken kurulu olan bulunur |
| `testflyInspector.testIdAttribute` | `data-testid` | `getByTestId()`'nin okuduğu özellik; `locators.testIdAttribute` ile aynı tutun |

## Sınırlar

- Yalnızca Chromium tabanlı tarayıcılar (Chrome, Edge, Chromium); Firefox ve Safari desteklenmez.
- iframe ve shadow root içindeki öğeler listelenmez.
- Ağaç ilk 5.000 öğeyi, ekran görüntüsü uzun bir sayfanın ilk 12.000 CSS pikselini içerir.
- Sayfa, yenilemede ve her işlemden sonra alınan bir anlık görüntüdür, canlı akış değildir.

## Sorun giderme

| Belirti | Çözüm |
|---|---|
| "No Chrome, Edge or Chromium found" | birini kurun ya da `testflyInspector.browserPath` ayarını çalıştırılabilir dosyasına getirin |
| Tarayıcı hemen kapanıyor | korunan oturum profilini başka bir örnek kullanıyor olabilir; onu kapatın veya **Keep session**'ı kapatın |
| Bir locator *N matches* gösteriyor | kodda daraltın ya da daha belirgin bir öğe veya üst öğe seçin |
| Panel "Starting…" durumunda kalıyor | eklentiyi kurduktan veya güncelledikten sonra pencereyi yeniden yükleyin |
