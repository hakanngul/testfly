---
description: "Selenium'da OAuth 2.0 / SSO akışlarını test edin: token'ları ApiClient ile alın, uygun olduğunda tarayıcı girişini atlayın ve yönetici-ve-kullanıcı senaryoları için MultiSessionManager kullanın."
id: oauth-sso
title: OAuth / SSO girişi
sidebar_label: OAuth / SSO
---

# OAuth / SSO girişi

OAuth 2.0 veya SAML tek oturum açmayı bir tarayıcıda test etmek yavaş ve kırılgan olabilir: kimlik sağlayıcının arayüzü değişir, MFA açılır pencereleri otomasyonu engeller ve testler zamanının çoğunu uygulamanız yerine oturum açma sayfalarında harcar.

Pratik yaklaşım, **kimlik doğrulamayı yetkilendirme testinden ayırmaktır**: bir token almak için API'yi veya önceden hazırlanmış bir oturumu kullanın, ardından bunu tarayıcıya enjekte edin. Tam tarayıcı tabanlı SSO akışlarını bir veya iki açık regresyon testine ayırın.

---

## Seçenek 1: Önce API girişi (hızlı ve kararlı)

Uygulamanız bir erişim token'ını `localStorage`, bir çerez veya bir `Authorization` başlığında kabul ediyorsa, token uç noktasını doğrudan `ApiClient` ile çağırın ve sonucu enjekte edin:

```java title="OAuthLoginTest.java"
import io.testfly.api.ApiResponse;
import io.testfly.client.ApiClient;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

import java.util.Map;

public class OAuthLoginTest extends BaseTest {

    @Test
    public void logsInViaTokenInjection() {
        // 1. Get token from the identity provider
        ApiResponse tokenResponse = ApiClient.post("https://idp.example.com/oauth/token")
            .body(Map.of(
                "grant_type", "password",
                "client_id", "test-client",
                "username", "testuser",
                "password", "testpass"))
            .send();

        String accessToken = tokenResponse.json("$.access_token");

        // 2. Open the app and inject the token
        open("/");
        sessionStorage().set("access_token", accessToken);

        // 3. Navigate to a protected route
        open("/dashboard");

        assertThat(find("h1")).hasText("Dashboard");
    }
}
```

:::info Kimlik bilgilerini testlerin dışında tutun
İstemci sırları ve test parolaları için `testfly.yml` içinde ortam değişkenlerini veya `${VAR}` yer tutucularını kullanın.
:::

---

## Seçenek 2: Tam tarayıcı SSO akışı

Tarayıcı yönlendirme sürecini gerçekten test etmeniz gerektiğinde, yönlendirmeleri açıkça takip edin ve URL değişimini doğrulayın:

```java title="SsoFlowTest.java"
public class SsoFlowTest extends BaseTest {

    @Test
    public void ssoRedirectReturnsToAppWithCode() {
        open("/login");
        find("#sso-login").click();   // triggers redirect to IdP

        // IdP login page
        getWait().waitForUrlContains("idp.example.com");
        find("#username").type("testuser");
        find("#password").type("testpass");
        find("#submit").click();

        // Redirect back to app with authorization code
        getWait().waitForUrlContains("/callback?code=");

        assertThat(find("h1")).hasText("Dashboard");
    }
}
```

:::tip Test kiracılarında MFA'dan kaçının
MFA'yı atlayan özel bir test IdP kiracısı oluşturun veya MFA'sı devre dışı bırakılmış bir test kullanıcısı kullanın. TOTP/SMS otomasyonu mümkündür ancak kırılganlık ekler.
:::

---

## Seçenek 3: Çok oturumlu yönetici + kullanıcı

Bazı SSO senaryoları iki tarayıcı gerektirir: bir kullanıcıyı hazırlayan bir yönetici ve o kullanıcının oturum açması. `MultiSessionManager.withSession(...)` kullanın:

```java
@Test
public void adminInviteCreatesLoginForUser() {
    open("/admin/users");
    find("#invite-user").click();
    find("#email").type("newuser@example.com");
    find("#send-invite").click();
    assertThat(find("#toast")).hasText("Invitation sent");

    MultiSessionManager.withSession("newuser", () -> {
        open("/signup?token=" + inviteToken);
        find("#password").type("Welcome123!");
        find("#complete").click();
        assertThat(find("h1")).hasText("Welcome");
    });
}
```

---

## Hangi seçeneği seçmeli

| Senaryo | Önerilen yaklaşım |
|---|---|
| Kimliği doğrulanmış özelliklerin günlük işlevsel testleri | **Önce API girişi** — token enjekte edin, IdP arayüzünü atlayın |
| SSO entegrasyonu / yönlendirme regresyonu | **Tam tarayıcı akışı** — en fazla bir veya iki test |
| Yönetici-ve-kullanıcı çapraz tarayıcı akışları | **Çok oturum** — iki bağlamı da birbirinden izole tutun |
| Token süresi sonu / yenileme mantığının test edilmesi | Zamanı ilerletmek için `TestClock` ile **önce API** |

---

## Sık karşılaşılan tuzaklar

- **IdP seçicilerini sabit kodlama.** Kimlik sağlayıcı arayüzleri değişir. Rutin testler için önce API girişini tercih edin.
- **Testlerde gerçek parolaları saklama.** Yapılandırma yer tutucularını veya ortam değişkenlerini kullanın.
- **Yönlendirmeyi beklememek.** `Thread.sleep` yerine `getWait().waitForUrlContains(...)` kullanın.
- **Üçüncü taraf çerezlerini unutmak.** Bazı IdP'ler headless/CI modunda çerezleri reddeder; yalnızca gerekirse `--disable-features=SameSiteByDefaultCookies` ekleyin ve test edin.

---

**Daha derin referans:**
- [API Testi](/docs/guides/api-testing) — `ApiClient` ve `ApiResponse`
- [Çok Oturumlu Test](/docs/guides/browser-lifecycle) — tek testte iki tarayıcı çalıştırma
- [Saat Taklidi](/docs/clock-mocking) — beklemeden token süresi sonunu test etme