---
id: configuration
title: Mobil Yapılandırma
sidebar_position: 3
description: "testfly.yml'in mobile: bloğu: cihaz seçimi, paralel koşum için cihaz havuzu, uygulama sıfırlama, mobilecli sunucusunu otomatik başlatma, zaman aşımları, profiller ve güvenlik."
---

# Mobil Yapılandırma

Etkin yapılandırmada `mobile:` bölümü yoksa mobil tarafta hiçbir şey yüklenmez. `platform` dışındaki her anahtarın varsayılanı vardır.

```yaml
mobile:
  serverUrl: ${MOBILECLI_URL:-http://localhost:12000}
  autoStartServer: true        # loopback sunucu cevap vermezse mobilecli'yi başlat
  mobilecliPath: mobilecli     # mobilecli PATH'te değilse tam yolu
  platform: android            # android | ios (zorunlu)
  deviceId: ""                 # tam kimlik; deviceName'e göre önceliklidir
  deviceName: ""               # cihaz adına uygulanan regex
  devices: []                  # cihaz havuzu: her teste bir kimlik verilir
  pool: false                  # deviceName ile eşleşen tüm çevrimiçi cihazları havuza al
  app:
    bundleId: com.example.app  # Android paketi / iOS bundle id
    install: ""                # isteğe bağlı .apk/.ipa/.app yolu, cihaz açılırken kurulur
    reset: relaunch            # none | relaunch | clearData
  timeouts:
    actionMs: 5000             # bir eylemin veya doğrulamanın bekleme bütçesi
    pollMs: 100                # ekranın iki okuması arasındaki bekleme
    rpcMs: 30000               # mobilecli'ye tek bir isteğin üst sınırı
    reserveMs: 300000          # aynı JVM'de başka bir testin tuttuğu cihaz için bekleme
    serverStartMs: 20000       # otomatik başlatılan sunucunun cevap vermesi için bekleme
  allowRemote: false
  headers: {}                  # örn. Authorization: Bearer ${MOBILECLI_TOKEN}
  screenshotOnFailure: true
```

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `serverUrl` | `http://localhost:12000` | mobilecli sunucusu; TestFly `<serverUrl>/rpc` adresini çağırır. |
| `autoStartServer` | `true` | Loopback bir `serverUrl` cevap vermezse `mobilecli server start` çalıştırır. |
| `mobilecliPath` | `mobilecli` | `autoStartServer`'ın kullandığı komut. |
| `platform` | — (zorunlu) | `android` veya `ios`. |
| `deviceId` | `""` | Tam cihaz kimliği (AVD adı, seri numarası veya simülatör UDID'si). |
| `deviceName` | `""` | `deviceId` boşken kullanılan, cihaz adına uygulanan regex. |
| `devices` | `[]` | Cihaz havuzu: her teste bir kimlik verilir. |
| `pool` | `false` | Platformun `deviceName` ile eşleşen tüm çevrimiçi cihazlarını havuza alır. |
| `app.bundleId` | `""` | Test edilen uygulama: Android paketi veya iOS bundle id. |
| `app.install` | `""` | Cihaz açılırken kurulan `.apk`, `.ipa` veya `.app` yolu. |
| `app.reset` | `relaunch` | Her testten önce uygulanan `none`, `relaunch` veya `clearData`. |
| `timeouts.actionMs` | `5000` | Bir eylemin, okumanın, doğrulamanın veya `@WaitFor`'un bekleme bütçesi. |
| `timeouts.pollMs` | `100` | Ekranın iki okuması arasındaki bekleme. |
| `timeouts.rpcMs` | `30000` | mobilecli'ye tek bir isteğin üst sınırı. |
| `timeouts.reserveMs` | `300000` | Bir testin, aynı JVM'de başka bir testin tuttuğu cihazı ne kadar bekleyeceği. |
| `timeouts.serverStartMs` | `20000` | Otomatik başlatılan sunucunun cevap vermesi için ne kadar beklenir. |
| `allowRemote` | `false` | Loopback olmayan bir `serverUrl`'e izin verir (`https://` gerekir). |
| `headers` | `{}` | Her istekte gönderilen ek HTTP başlıkları, örneğin kimlik doğrulayan bir proxy için. |
| `screenshotOnFailure` | `true` | Başarısız teste cihaz ekran görüntüsü ekler. |

Değerler bilinen yer tutucuları destekler (`${MOBILE_DEVICE_ID:-}`); bkz. [Yapılandırma](../configuration).

## Cihaz seçimi

`deviceId`, platformun tam olarak bir çevrimiçi cihazıyla eşleşmelidir; yoksa `deviceName` regex'i eşleşmelidir; o da yoksa platformun tam olarak bir çevrimiçi cihazı olmalıdır. Sıfır veya birden çok eşleşme, mobilecli'nin bildirdiği cihazları listeleyen bir `DeviceSelectionException` verir.

## Cihaz havuzu (paralel koşum)

Cihazları `devices: [id1, id2]` ile listeleyin ya da `pool: true` ile platformun `deviceName` ile eşleşen tüm çevrimiçi cihazlarını kullanın. Her test boş bir cihaz alır; hepsi kullanımdaysa `reserveMs` kadar bekler, sonra `MobileTimeoutException` verir.

- Paralel mobil worker sayısı cihaz sayısını geçmemelidir.
- `devices`, `deviceId` veya `pool` ile; `pool` da `deviceId` ile birlikte kullanılamaz.
- `devices` içindeki çevrimdışı kimlikler bir uyarıyla atlanır.
- Rezervasyon JVM başınadır: cihazları paylaşan ayrı JVM'ler veya makineler koordine edilmez.

## Uygulama sıfırlama

`relaunch` her testten önce `bundleId`'yi kapatıp açar; `clearData` verisini silip açar; `none` dokunmaz. `none` dışında uygulama testten sonra kapatılır. Testler birbirinin bıraktığı durumdan devam edecekse (örneğin korunması gereken bir login) `none` kullanın.

## Sunucuyu otomatik başlatma {#server-auto-start}

`serverUrl` bir loopback adresiyse (`localhost`, `127.*`, `::1`) ve cevap gelmiyorsa TestFly `<mobilecliPath> server start --listen <host:port>` çalıştırır:

- uç nokta ve JVM başına bir kez; paralel testler aynı sunucuyu bekler, başarısız bir başlatma sonraki testlerin aynı hatayla hızlıca düşmesine yol açar;
- çıktı `target/mobilecli-autostart.log` dosyasına gider;
- sunucunun cevap vermesi için `timeouts.serverStartMs` kadar bekler;
- sunucu JVM kapanırken durdurulur.

Zaten cevap veren bir sunucu olduğu gibi kullanılır ve hiç durdurulmaz. Uzak sunucular hiçbir zaman başlatılmaz. Çalışan bir sunucu şart koşmak için `autoStartServer: false` yapın. mobilecli **ajanı** otomatik kurulmaz; cihaz başına bir kez kurun (`mobilecli agent install --device <id>`).

## Profiller

Mobil ayarları bir profilde tutun, `src/test/resources/testfly-<ad>.yml`, ve `-Dtestfly.profile=<ad>` ile seçin. Uygulama ve platform başına bir profil, aynı testlerin iOS'ta ve Android'de koşmasını sağlar:

```bash
mvn test -Dtestfly.profile=myapp-ios     -Dtest=LoginTest
mvn test -Dtestfly.profile=myapp-android -Dtest=LoginTest
```

`mobile:` bloğu olan bir profil `browser`, `execution` veya `timeouts` bloğu gerektirmez: eksik web değerleri etkisiz varsayılanlar alır (headless yerel Chrome, `http://localhost`, 10 sn / 30 sn), sizin verdiğiniz değerler korunur. `testfly-mobile-ios.yml`, `testfly-mobile-android.yml` ve `testfly-mobile-ios-login.yml` örnekleri tam bir profil gösterir.

## Güvenlik

mobilecli'de kimlik doğrulama yoktur, bu yüzden yalnızca loopback adresleri kabul edilir. Uzak bir sunucu için `allowRemote: true` ve `https://` adresi gerekir; örneğin TLS sonlandıran, kimlik doğrulayan bir proxy veya SSH tüneli arkasında mobilecli. Kimlik bilgilerini `headers` ve bir ortam yer tutucusu ile gönderin. Kimlik bilgilerini asla commit etmeyin: test hesapları ortam değişkenlerinde durur.
