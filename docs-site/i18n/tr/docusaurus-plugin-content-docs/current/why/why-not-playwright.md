---
description: "Neden Playwright değil? Dürüst bir cevap: TestFly, Playwright'ın yerini almaz. Playwright'ın en iyi fikirlerini — erişilebilirlik öncelikli locator'lar, otomatik bekleme, web-öncelikli doğrulamalar — Selenium / Java / Grid ekosisteminde kalan ekiplere getirir."
id: why-not-playwright
title: Neden Playwright değil?
sidebar_label: Why not Playwright?
sidebar_position: 3
---

# Neden Playwright değil?

En baştan dürüst olalım: **TestFly, Playwright'ın yerini almaz.** Playwright, gerçekten farklı bir mimariye sahip mükemmel bir araçtır ve Selenium yatırımınız yoksa sıfırdan başlıyorsanız harika bir seçimdir.

Dolayısıyla "Neden Playwright değil?" bir yerinmek değil — belirli bir kitle için gerçek bir cevabı olan bir sorudur: **zaten Selenium / Java / JVM dünyasında olan** ve Playwright'ın ergonomisini beğenen ama stack'lerinden, Selenium Grid'lerinden veya ekibinin becerilerinden vazgeçmek istemeyen ekipler.

---

## Vazgeçmek zorunda olmadığınız ergonomi

Selenium'dan ayrılmadan insanların Playwright'ta sevdiği şeylere sahip olabilirsiniz. TestFly bu fikirleri Selenium ekosistemine getirir:

| Playwright fikri | TestFly'da |
|---|---|
| Erişilebilirlik öncelikli locator'lar | [`getByRole` / `getByLabel` / `getByText`](/docs/guides/semantic-locators) — erişilebilirlik ağacını hedefleyin, CSS/DOM yeniden düzenlemelerine dayanın |
| Otomatik bekleme | Otomatik bekleyen eylemler + [`WaitEngine`](/docs/guides/wait-engine) — `Thread.sleep()` ortadan kalkar |
| Web-öncelikli doğrulamalar | Doğru olana kadar otomatik yeniden deneyen `assertThat(...)` |
| Kural üstüne kural | Sıfır-boilerplate varsayılanlar, isteğe bağlı `testfly.yml` |

…üstelik tüm bunları mevcut **Selenium / Java / TestNG** stack'inizi ve **Selenium Grid**'inizi koruyarak ve ham Selenium'u asla gizlemeden.

---

## Playwright'ın gerçekten farklı olduğu yer

Dürüstlük iki yönlüdür. Bunlar gerçek mimari farklılıklardır, pazarlama değil:

- **Mimari** — Playwright, tarayıcıları kendi protokolü üzerinden hızlı, izole tarayıcı bağlamları ve yerleşik izleme ile sürer. TestFly, **Selenium WebDriver / W3C protokolü** üzerine inşa edilmiştir — tüm Selenium ekosistemini alırsınız, ancak WebDriver'ın modelini, Playwright'ın değil.
- **Dil ve çalışma zamanı** — Playwright çok dillidir ve kendi yönetilen tarayıcı derlemeleriyle birlikte gönderilir. TestFly **yalnızca JVM'dir** ve normalde yüklediğiniz tarayıcıları çalıştırır.
- **Ölçeklendirme** — Playwright'ın kendi worker/sharding modeli vardır; TestFly **Selenium Grid** ve TestNG paralelliğini kullanır, muhtemelen zaten çalıştırdığınız altyapıya yerleşir.

Tanimlı-ve-farklı ayrımının tamamı için köprü sayfasına bakın: [Playwright'tan geliyorsunuz](/docs/migration/coming-from-playwright).

---

## Dürüstçe seçim yapmak

| Seçin… | Eğer… |
|---|---|
| **Playwright** | Sıfırdan başlıyorsanız, Selenium yatırımınız yoksa, bağlam modelini / izlemeyi istiyorsanız, ekibiniz Node/Python'da rahatsa. |
| **TestFly** | **Selenium / Java / TestNG** üzerindeyseniz, **Selenium Grid** çalıştırıyor veya istiyorsanız ve bu stack'ten ayrılmadan Playwright tarzı ergonomi istiyorsanız. |

İkisi de meşrudur. TestFly, "biz bir Selenium dükkanıyız" demenin artık erişilebilirlik öncelikli locator'lardan, otomatik beklemeden ve web-öncelikli doğrulamalardan vazgeçmek anlamına gelmemesi için vardır.

---

## Sonraki adımlar

- [Playwright'tan geliyorsunuz](/docs/migration/coming-from-playwright) — tam köprü sayfası
- [Erişilebilirlik Öncelikli Locator'lar](/docs/guides/semantic-locators) — `getByRole`/`getByLabel` ailesi
- [Neden TestFly?](/docs/why/why-testfly) — genel felsefe