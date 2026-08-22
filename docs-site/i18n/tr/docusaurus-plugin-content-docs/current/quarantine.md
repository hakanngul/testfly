---
description: "Kararsız (flaky) Selenium testlerini karantinaya alın: bilinen-bozuk testleri işaretleyin; framework bunları kodu silmeden veya yorum satırına almadan otomatik olarak atlasın."
id: quarantine
title: Test Karantinası
sidebar_position: 15
---

# Test Karantinası

Test karantinası, bilinen-kararsız veya geçici olarak bozuk testleri işaretlemenizi sağlar; böylece framework bunları otomatik olarak atlar — test kodunu silmeden veya yorum satırına almadan ve herhangi bir CI yapıtı geçmişine güvenmeden.

---

## Nasıl çalışır

**Proje kökünüzde** (`pom.xml`'in yanında) `testfly-quarantine.yml` adında bir dosya oluşturun. Atlamak istediğiniz testleri listeleyin. Sürüm kontrolüne işleyin (commit edin). Her CI çalıştırması — taze bir klon bile — bunları atlar.

```yaml title="testfly-quarantine.yml"
quarantine:
  # Belirli bir test metodunu atla
  - com.example.tests.LoginTest#loginWithExpiredSession

  # Sınıfın tamamını atla (tüm metotlar)
  - com.example.tests.PaymentTest

  # İsteğe bağlı bir neden ekleyin — atlama mesajında ve günlüklerde gösterilir
  - test: com.example.tests.SearchTest#searchWithSpecialChars
    reason: "Unicode handling broken — JIRA-1234"
```

Bu kadar. Yapılandırma değişikliği, ek açıklama veya geçmiş gerekmez.

---

## Neden işlenmiş bir dosya?

CI boru hatları, her çalıştırmada depoyu taze olarak klonlar — çalıştırmalar arasında kalıcı bir `target/` dizini yoktur. Yerel olarak "N ardışık başarısızlık" izleyen herhangi bir çözüm, CI'da hemen bozulur.

İşlenmiş bir YAML dosyası bunu temiz bir şekilde çözer:

| Endişe | Yanıt |
|---|---|
| Taze klonlarda hayatta kalır | ✅ — git içindedir |
| Harici servis gerekmez | ✅ — yalnızca bir YAML dosyası |
| Denetlenebilir | ✅ — değişiklikler PR'lerden geçer |
| Tüm yürütme modlarıyla çalışır | ✅ — local, remote, BrowserStack, Sauce Labs |
| Ekip üyeleri arasında çalışır | ✅ — herkes aynı listeyi alır |

---

## Dosya biçimi

### Düz dize (basit)

```yaml
quarantine:
  - com.example.tests.LoginTest#loginTest          # belirli metot
  - com.example.tests.CheckoutTest                 # tüm sınıf
```

### Yapılandırılmış (nedenle)

```yaml
quarantine:
  - test: com.example.tests.LoginTest#loginTest
    reason: "Token refresh race condition — JIRA-789"
```

Her iki biçim aynı dosyada bir arada bulunabilir.

### Tanımlayıcı biçimi

| Test türü | Biçim | Örnek |
|---|---|---|
| TestNG metodu | `TamNitelikliSınıf#metotAdı` | `com.example.LoginTest#loginTest` |
| JUnit 5 metodu | `TamNitelikliSınıf#metotAdı` | `com.example.LoginTest#loginTest` |
| Sınıfın tamamı | `TamNitelikliSınıf` | `com.example.LoginTest` |
| Cucumber senaryosu | `@quarantine` etiketi `.feature` dosyasında | *(aşağıya bakın)* |

:::tip Tam nitelikli sınıf adını bulma
`package` bildiriminde göründüğü haliyle paket + sınıf adıdır.
`package com.example.tests;` + `class LoginTest` → `com.example.tests.LoginTest`
:::

---

## Cucumber

Cucumber karantinası **iki şekilde** çalışır — duruma uygun olanı kullanın veya ikisini birleştirin.

---

### Yöntem 1 — Özellik dosyasında etiket

`@quarantine` etiketini doğrudan senaryoya ekleyin. YAML girişi gerekmez.

```gherkin title="login.feature"
Feature: Login

  @quarantine
  Scenario: Login with expired session
    Given I have an expired session token
    When I try to log in
    Then I should see an error

  Scenario: Successful login
    Given I have valid credentials
    When I log in
    Then I should see the dashboard
```

Şu durumlarda idealdir: tek senaryolar ve özellik dosyasını sahiplenen takım onu yönetiyorsa.

---

### Yöntem 2 — `testfly-quarantine.yml` içindeki girişler

Cucumber için üç YAML giriş biçimi desteklenir:

#### Cucumber etiketine göre — birçok özelliği birden toplu karantinaya alma

Etiketi taşıyan herhangi bir senaryo, hangi özellik dosyasında olduğuna bakılmaksızın atlanır.

```yaml title="testfly-quarantine.yml"
quarantine:
  - "@smoke"                         # @smoke etiketli her senaryoyu atlar
  - test: "@regression"
    reason: "Payment refactor broke regression suite — JIRA-567"
```

Şu durumlarda idealdir: bir test kategorisinin tamamını (bir etiket grubunu) tek satırda karantinaya almak.

#### Özellik dosyasına göre — bir dosyadaki her senaryoyu karantinaya alma

```yaml
quarantine:
  - login.feature                    # yalnızca dosya adı
  - features/payment.feature         # göreli yol da çalışır
  - test: features/checkout.feature
    reason: "Checkout service unavailable — JIRA-456"
```

Yol, senaryonun URI'sinin sonuyla eşleştirilir; dolayısıyla hem `login.feature` hem de
`features/login.feature`, `classpath:src/test/resources/features/login.feature` ile eşleşir.

Şu durumlarda idealdir: hiçbir `.feature` dosyasına dokunmadan bir özellik alanının tamamını geçici olarak devre dışı bırakmak.

#### Özellik dosyası + senaryo adına göre — dosya düzenlemesi gerekmeden belirli bir senaryo

```yaml
quarantine:
  - "checkout.feature#Checkout with 3D Secure"
  - test: "login.feature#Login with expired token"
    reason: "Token refresh race condition — JIRA-789"
```

Biçim: `dosya.feature#Tam senaryo adı` (büyük/küçük harf duyarsız ad eşleştirmesi).

Şu durumlarda idealdir: özellik dosyasını düzenleyemediğiniz veya düzenlemek istemediğiniz belirli bir senaryoyu karantinaya almak
(örn. dosya ekipler arasında paylaşılıyor veya farklı bir depoda yönetiliyor).

---

### Karşılaştırma

| Yöntem | Özellik dosyasını düzenler? | Toplu destek | YAML gerekir? |
|---|---|---|---|
| Senaryoda `@quarantine` etiketi | Evet | Hayır — senaryo başına bir etiket | Hayır |
| YAML'de `"@tag"` | Hayır | Evet — etiket grubu başına bir satır | Evet |
| YAML'de `"feature.file"` | Hayır | Evet — dosyanın tamamı | Evet |
| YAML'de `"feature.file#Scenario name"` | Hayır | Hayır — tek bir senaryo | Evet |

---

### Etiket adını yapılandırma

```yaml title="testfly.yml"
quarantine:
  cucumberTag: quarantine   # varsayılan — etiket kurallarınızla çakışırsa değiştirin
```

---

## Dosya çözümleme sırası

Framework karantina dosyasını şu öncelik sırasına göre arar:

1. **Sistem özelliği** — `-Dtestfly.quarantine=/path/to/custom-quarantine.yml`
2. **Çalışma dizini** — `./testfly-quarantine.yml` (`pom.xml`'in yanında)
3. **Sınıf yolu** — `src/test/resources/testfly-quarantine.yml`

Dosya bulunamazsa, karantina sessizce devre dışı kalır (hata veya uyarı yok).

---

## Yapılandırma

```yaml title="testfly.yml"
quarantine:
  enabled: true           # dosyayı silmeden geçici olarak devre dışı bırakmak için false yapın
  cucumberTag: quarantine # Cucumber etiket adı (@ ön eki olmadan)
```

`enabled: true` varsayılan değerdir — dosya varsa her zaman uygulanır.

---

## Karantinayı geçici olarak devre dışı bırakma

Karantinadaki testler de dahil paketin tamamını çalıştırmak için (örneğin bir düzeltmeyi doğrulamak):

```bash
# Seçenek 1: boş bir dosyaya işaret eden sistem özelliği geçersiz kılması
mvn test -Dtestfly.quarantine=/dev/null

# Seçenek 2: yapılandırma özelliğiyle devre dışı bırakma
mvn test -Dtestfly.config=config/no-quarantine.yml
```

Veya profil-specific bir YAML'da `quarantine.enabled: false` ayarlayın:

```bash
mvn test -Dtestfly.profile=full-run
```

```yaml title="testfly-full-run.yml"
quarantine:
  enabled: false
```

---

## İş akışı

1. Bir test CI'da aralıklı olarak başarısız olur → bir bilet açın
2. Testi, neden olarak bilet numarasıyla `testfly-quarantine.yml` dosyasına ekleyin
3. İşleyin ve gönderin → CI bu testte başarısız olmayı bırakır
4. Kök nedeni düzeltin → girişi kaldırın → CI'da doğrulayın → bileti kapatın

Karantina **geçici bir bekleme alanıdır**, kalıcı bir mezarlık değil.

---

## Karantinadaki bir teste ne olur

- HTML raporunda ve JUnit XML'de durum **SKIPPED** olarak kaydedilir
- Atlama mesajı: `[Quarantined] com.example.LoginTest#loginTest — <reason>`
- Tarayıcı oturumu oluşturulmaz — atlanan test hiçbir maliyet gerektirmez
- Atlama toplamında sayılır; başarı oranını veya flakiness puanlarını etkilemez