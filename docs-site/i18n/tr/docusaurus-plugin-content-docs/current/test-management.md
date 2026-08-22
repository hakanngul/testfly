---
description: "Selenium test sonuçlarını TestRail ve Xray'e otomatik gönderin: test metotlarınızda ek raporlama kodu olmadan tek bir ek açıklama yeterli."
id: test-management
title: TestRail & Xray Entegrasyonu
sidebar_position: 16
---

# TestRail & Xray Entegrasyonu

TestFly, test sonuçlarını **TestRail** ve/veya **Xray** alanına otomatik olarak gönderir — test metotlarınızda tek bir ek açıklamanın ötesinde ekstra kod gerekmez.

---

## Hızlı Başlangıç

### 1 — Testlerinizi ek açıklamalarla işaretleyin

```java
import io.testfly.testmanagement.TestRailCase;
import io.testfly.testmanagement.XrayTest;

public class LoginTest extends BaseTest {

    @Test
    @TestRailCase("C1234")
    @XrayTest("PROJ-99")
    public void validLogin() {
        open();
        find("input#email").type("admin@example.com");
        find("input#password").type("secret");
        find("button[type='submit']").click();
        assertThat(By.id("dashboard")).isVisible();
    }

    // Tek testte birden çok kimlik
    @Test
    @TestRailCase({"C1234", "C5678"})
    @XrayTest({"PROJ-99", "PROJ-100"})
    public void checkoutFlow() { ... }
}
```

### 2 — `testfly.yml` içinde yapılandırın

```yaml
testmanagement:
  testrail:
    enabled: true
    url: https://yourcompany.testrail.io
    username: user@example.com
    apiKey: YOUR_API_KEY
    projectId: 1
    suiteId: 2
    runName: "TestFly – CI run"

  xray:
    enabled: true
    mode: cloud
    clientId: YOUR_CLIENT_ID
    clientSecret: YOUR_CLIENT_SECRET
    projectKey: PROJ
```

Bu kadar — `mvn test` çalıştırın; sonuçlar her iki araçta da görünür.

---

## TestRail

### Kimlik doğrulama

TestRail, HTTP Basic kimlik doğrulaması kullanır. `apiKey`, TestRail örneğinizde **My Settings → API Keys** altında üretilen API anahtarıdır (oturum açma parolanız değil).

### Çalıştırma (run) yönetimi

Varsayılan olarak TestFly, paket başlangıcında yeni bir test çalıştırması oluşturur:

```yaml
testmanagement:
  testrail:
    autoCreateRun: true      # varsayılan — her seferinde yeni bir çalıştırma oluşturur
    runName: "Regression – ${BUILD_NUMBER}"
```

Sonuçları **mevcut bir çalıştırmaya** göndermek için otomatik oluşturmayı devre dışı bırakın ve çalıştırma kimliğini sağlayın:

```yaml
testmanagement:
  testrail:
    autoCreateRun: false
    runId: 42
```

### Durum eşlemesi

| TestFly | TestRail |
|---|---|
| `PASSED`  | 1 — Passed |
| `FAILED`  | 5 — Failed |
| `SKIPPED` | 4 — Retest |

Başarısız testler, istisna mesajını TestRail sonuç yorumu olarak içerir; bu da kök-neden triyajını hızlandırır.

### Case ID biçimi

Hem `"C1234"` hem de `"1234"` kabul edilir — baştaki `C` isteğe bağlıdır.

```java
@TestRailCase("C1234")   // ✓
@TestRailCase("1234")    // ✓ aynı case
```

---

## Xray

### Bulut modu (Jira Cloud)

```yaml
testmanagement:
  xray:
    enabled: true
    mode: cloud
    clientId: YOUR_CLIENT_ID
    clientSecret: YOUR_CLIENT_SECRET
    projectKey: PROJ
```

TestFly, `https://xray.cloud.getxpecto.com/api/v2/authenticate` adresinden bir JWT belirteci alır ve sonuçları aynı sunucuya aktarır. `clientId` / `clientSecret` değerlerini Jira → **Xray → API Keys** içinde oluşturun.

### Server / Data Center modu

```yaml
testmanagement:
  xray:
    enabled: true
    mode: server
    jiraUrl: https://jira.yourcompany.com
    username: automation-user
    password: ${JIRA_PASSWORD}      # env-var ikamesini destekler
    projectKey: PROJ
```

Sonuçlar `{jiraUrl}/rest/raven/1.0/import/execution` adresine aktarılır.

### Bir Test Planına bağlama

```yaml
testmanagement:
  xray:
    testPlanKey: PROJ-1      # her yürütmeyi bu plana bağlar
```

### Durum eşlemesi

| TestFly | Xray |
|---|---|
| `PASSED`  | `PASS` |
| `FAILED`  | `FAIL` |
| `SKIPPED` | `TODO` |

### Toplu aktarım

Her sonucu hemen gönderen TestRail'in aksine, Xray sonuçları **çalıştırma sırasında toplanır** ve paket sonunda tek bir yürütme yükü olarak aktarılır. Bu, API çağrılarını azaltır ve Xray yürütme kaydını tutarlı tutar.

---

## Yapılandırma Başvurusu

```yaml
testmanagement:

  testrail:
    enabled: false              # etkinleştirmek için true
    url:                        # https://yourcompany.testrail.io
    username:                   # e-posta veya kullanıcı adı
    apiKey:                     # My Settings → API Keys adresinden API anahtarı
    projectId: 0                # TestRail proje kimliği
    suiteId: 0                  # tek-suite projeler için atlayın
    runName: "TestFly Run"
    autoCreateRun: true         # false → aşağıdaki runId değerini sağlayın
    runId: 0                    # autoCreateRun: false iken kullanılır

  xray:
    enabled: false              # etkinleştirmek için true
    mode: cloud                 # "cloud" | "server"
    # Bulut:
    clientId:
    clientSecret:
    # Server/DC:
    jiraUrl:
    username:
    password:
    # Ortak:
    projectKey:                 # örn. "PROJ"
    testPlanKey:                # isteğe bağlı — mevcut bir Test Planına bağlar
```

---

## CI ile kullanma

Kimlik bilgilerini CI sırları olarak saklayın ve ortam değişkenleriyle iletin:

```yaml
# testfly.yml
testmanagement:
  testrail:
    enabled: true
    url: https://yourcompany.testrail.io
    username: ${TESTRAIL_USER}
    apiKey: ${TESTRAIL_KEY}
    projectId: 1
```

```yaml
# GitHub Actions
- name: Run tests
  env:
    TESTRAIL_USER: ${{ secrets.TESTRAIL_USER }}
    TESTRAIL_KEY:  ${{ secrets.TESTRAIL_KEY }}
  run: mvn test
```

Ortam değişkeni ikamesi (`${VAR_NAME}`), önce `System.getenv()`, ardından `System.getProperty()` kaynaklarından çözümlenir.

---

## Sınıf Düzeyinde Ek Açıklama

Sınıftaki her metodu aynı TestRail case'ine veya Xray test anahtarına bağlamak için ek açıklamayı sınıf düzeyinde uygulayın:

```java
@TestRailCase("C999")   // bu sınıftaki her metot C999'a raporlanır
@XrayTest("PROJ-500")
public class SmokeTests extends BaseTest {

    @Test
    public void homepageLoads() { ... }

    @Test
    public void loginPageLoads() { ... }
}
```

Metot düzeyindeki ek açıklamalar, sınıf düzeyindekilerden önceliklidir.