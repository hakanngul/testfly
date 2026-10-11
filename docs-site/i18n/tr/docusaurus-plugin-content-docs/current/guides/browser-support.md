---
description: "TestFly özelliklerinin hangi tarayıcılarda çalıştığı. Locator'lar, doğrulamalar ve izler her yerde çalışır; ağ mock'lama, cihaz emülasyonu ve konum için yerel Chrome veya Edge gerekir."
id: browser-support
title: Tarayıcı Desteği
sidebar_position: 9
---

# Tarayıcı Desteği

TestFly Chrome, Edge, Firefox ve Safari üzerinde çalışır. Özelliklerin çoğu yalnızca WebDriver ve
JavaScript kullanır ve her yerde aynı davranır. Birkaçı **Chrome DevTools Protocol (CDP)**
gerektirir. Selenium bunu yalnızca TestFly **Chrome veya Edge'i yerelde başlattığında** sunar. Başka
bir sürücüde bu özellikler zayıflar ve her biri **tarayıcı oturumu başına bir uyarı** yazarak neyin
değiştiğini söyler.

:::warning Uzak sürücülerde CDP yoktur
**Selenium Grid, BrowserStack veya Sauce Labs** üzerindeki bir tarayıcı `RemoteWebDriver` ile sürülür ve
TestFly bugün bunun için DevTools bağlantısı kurmaz. Bu, uzaktaki tarayıcı **Chrome olsa bile**
geçerlidir.
:::

## Nerede ne çalışır

| Özellik | Yerel Chrome / Edge | Firefox, Safari | Uzak (Grid, bulut) |
|---|---|---|---|
| Locator'lar, eylemler, [actionability](/docs/guides/wait-engine#actionability-etkileşime-hazır-olma), beklemeler | Tam | Tam | Tam |
| [Doğrulamalar](/docs/guides/assertions), [ARIA snapshot'ları](/docs/guides/assertions#erişilebilirlik-doğrulamaları) | Tam | Tam | Tam |
| [Adım izleri](/docs/guides/tracing), ekran görüntüleri | Tam | Tam | Tam |
| Erişilebilirlik taramaları (axe-core) | Tam | Tam | Tam |
| [Ağ mock'lama](/docs/configuration#clock), `network.blockUrls`, `NetworkAssert` | Tam | **Yok.** Route'lar yok sayılır, istekler gerçek ağa gider. `NetworkAssert` başarısız olur. | **Yok** |
| [Video kaydı](/docs/guides/video-recording) | DevTools screencast | Periyodik ekran görüntüsü, kareler daha seyrek | Periyodik ekran görüntüsü |
| `DeviceEmulator` | Tam: görünüm, piksel oranı, dokunma, user agent, gezinmede korunur | Pencereyi yeniden boyutlandırır ve geçerli sayfada `navigator.userAgent` değerini değiştirir. Gezinmede kaybolur. | Firefox ile aynı |
| `GeoLocation` | Tam | Yalnızca geçerli sayfada geçerlidir. Gezinmede kaybolur. | Firefox ile aynı |
| [Saat mock'lama](/docs/clock-mocking) | Yenileme ve gezinmede korunur | Yalnızca geçerli sayfa | Yalnızca geçerli sayfa |
| [Konsol hataları](/docs/guides/console-errors) | Tarayıcı günlüğü: sayfa yüklenirken ve link ile gidilen sayfalarda oluşan hatalar yakalanır | Yalnızca test `open()` çağırdıktan sonra yapılan `console.error` çağrıları. Sayfa yüklenirken oluşan hatalar, yakalanmamış istisnalar ve link ile gidilen sayfalar kaçar. | Doğrulanmadı |
| Performans LCP ve CLS | Tam | `-1` raporlanır, doğrulamalar atlanır | Doğrulanmadı |

Chrome ve Edge için "Tam" ve konsol hataları satırı, projenin entegrasyon testlerinde gerçek bir
tarayıcıya karşı denenmiştir. Firefox, Safari ve uzak sütunları kaynak koddan çıkarılmıştır. Aşağıda
anlatılan deneysel Firefox işi dışında proje bunları o tarayıcılarda çalıştırmamıştır.

## Bunun testleriniz için anlamı

- Bir test **ağ mock'una** dayanıyorsa yerel Chrome veya Edge'de çalıştırın. Diğer tarayıcılarda mock
  sessizce yoktur ve test gerçek arka uca gider.
- Firefox ve Safari'de `failOnConsoleErrors`, sayfa yüklenirken hata oluşsa bile geçebilir.
- Bir özellik zayıfladığında, tarayıcı oturumu başına bir kez şöyle bir uyarı görünür:

```
[TestFly] NETWORK_MOCK is degraded on FirefoxDriver: Network mocking, URL blocking and network
assertions are not available; routes are ignored and requests reach the real network.
```

## Birden fazla tarayıcıda test

`browser.matrix`, paketi listelenen her tarayıcı için bir kez çalıştırır. Projenin kendi CI'ı tarayıcı
entegrasyon testlerini Chrome'da, Firefox'ta ise **deneysel** bir iş olarak çalıştırır: build'i
bozmadan başarısız olabilir ve Firefox'un nerede farklılaştığını gösterir. Yerelde yeniden üretmek için:

```bash
mvn verify -Preal-backends -Dtestfly.config=src/test/resources/testfly-real-backends-firefox.yml \
  -Dit.test=ConsoleErrorIntegrationTest
```

## Cihaz emülasyonu ipucu

Chrome'da `DeviceEmulator` mobil bir görünüm ayarlar. `<meta name="viewport"
content="width=device-width">` içermeyen bir sayfa, gerçek bir telefonda olduğu gibi 980 piksel
genişliğinde yerleşir. Bu yüzden `window.innerWidth` cihaz genişliği değil 980 olur.
