---
description: "Neden erişilebilirlik öncelikli locator'lar? Rolleri, etiketleri ve görünür metni hedefleyen testler, CSS ve DOM yeniden düzenlemelerine dayanır, anlamlı mesajlarla başarısız olur ve gerçek kullanıcıların sayfayı deneyimleme şeklini yansıtır."
id: why-accessibility-first
title: Neden erişilebilirlik öncelikli locator'lar?
sidebar_label: Why accessibility-first?
sidebar_position: 4
---

# Neden erişilebilirlik öncelikli locator'lar?

Bir UI test paketinin en pahalı kısmı onu yazmak değildir — **uygulama değiştikçe onu hayatta tutmaktır**. Her CSS sınıfı adı değişikliği, her yerleşim düzeni yeniden düzenlemesi, her framework geçişi, kendisini uygulama ayrıntılarına sabitleyen testleri bozar.

Erişilebilirlik öncelikli locator'lar hedefi *sayfanın nasıl inşa edildiğinden* *sayfanın ne anlama geldiğine* değiştirir. Bir ekran okuyucunun veya insan kullanıcının sorduğu soruyu sorarlar: "Add to cart* etiketli düğme nerede?" yerine "`.btn-primary` sınıfına sahip öğe nerede?"

---

## Yeniden düzenleme sorunu

Tipik bir Selenium testi şöyle görünür:

```java title="Önce"
click(By.cssSelector(".login-form .btn-primary"));
```

Ardından tasarım sistemi `.btn-primary` değerini `.btn--brand` olarak değiştirir, giriş formu bir modal içinde yeniden inşa edilir veya bir düğme bağlantıya dönüşür. Test başarısız olur — özellik bozuk olduğu için değil, locator yapıya bağlı olduğu için.

Erişilebilirlik öncelikli locator'larla:

```java title="Sonra"
click(getByRole(Role.BUTTON).withName("Sign in"));
```

Düğme taşınabilir, CSS'i veya etiketi değişebilir ve test yine de onu bulur — erişilebilirlik ağacına kendisini "Sign in" adlı bir düğme olarak sunmaya devam ettiği sürece.

---

## TestFly'da "erişilebilirlik öncelikli" ne anlama gelir

TestFly, Playwright ve modern test araçları tarafından popülerleştirilen aynı locator ailesini sunar:

| Locator | Hedefler |
|---|---|
| `getByRole(Role.BUTTON)` | ARIA rolü — düğme, bağlantı, başlık, metin kutusu, onay kutusu |
| `getByLabel("Password")` | Bir `<label>` veya `aria-label` ile ilişkilendirilmiş form kontrolü |
| `getByText("Add to cart")` | Görünür metin içeriği |
| `getByPlaceholder("Search…")` | Giriş yer tutucusu |
| `getByTestId("checkout")` | `data-testid` özniteliği — uygulama ayrıntısı kaçış kapağı |
| `getByAltText("Product photo")` | Görüntü alternatif metni |

Bu locator'lar `By.cssSelector` veya `By.xpath` yerine geçmez; onlar ilk tercihtir ve gerçekten ihtiyacınız olduğunda ham Selenium locator'ları kullanılabilir kalır.

---

## Hatalar okunabilir hale gelir

`By.cssSelector(".cart .btn")` üzerindeki bir hata size şunu söyler:

```
NoSuchElementException: no such element: .cart .btn
```

`getByRole(Role.BUTTON).withName("Add to cart")` üzerindeki bir hata size şunu söyler:

```
Locator could not find a button with accessible name "Add to cart"
```

İkinci mesaj eyleme geçirilebilir. Niyete işaret eder, yukarı kodlamaya değil.

---

## Daha iyi testler, daha iyi erişilebilirlik

Erişilebilirlik öncelikli locator'lar olumlu bir döngü oluşturur:

1. Testler erişilebilirlik ağacına dayanır.
2. Erişilebilirlik ağacı yalnızca UI anlamsal olarak anlamlı olduğunda doğrudur.
3. Bu nedenle ekipler etiketleri, rolleri ve adları doğru tutar.
4. Uygulama, ekran okuyucu kullanıcıları ve klavye gezinimi kullananlar için yan etki olarak daha kullanışlı hale gelir.

Başka bir deyişle, testleri istikrarlı kılan aynı disiplin ürünü daha kapsayıcı da yapar.

---

## `data-testid` ne zaman kullanılır

`getByTestId` kasıtlı olarak varsayılan değil, yedektir. Şu durumlarda kullanın:

- Görünür metin veya rol testi kırılgan yapardı (ör. çevrilmiş dizeler).
- Öğenin tasarım gereği erişilebilir adı veya rolü yoktur.
- Anlamsal işaretlemenin mevcut olmadığı, izole edilmiş bir bileşeni test ediyorsunuz.

Her öğe için `data-testid` kullanmaktan kaçının. Yine de bir uygulama ayrıntısıdır — sadece istikrarlı bir tanedir.

---

## Sonraki adımlar

- [Semantik Locator'lar](/docs/guides/semantic-locators) — `getByRole`, `getByLabel` ve diğerleri için tam API başvurusu
- [Neden sade Selenium değil?](/docs/why/why-not-plain-selenium) — boilerplate-locator testlerinin maliyeti
- [Akıllı Locator](/docs/guides/smart-locator) — tek bir locator yeterli olmadığında yedek stratejiler