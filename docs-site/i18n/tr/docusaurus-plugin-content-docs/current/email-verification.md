---
description: "Selenium testlerinde e-postaları doğrulayın: mailbox() API'si ile Mailhog, Mailtrap, Graph API veya IMAP üzerinden onay bağlantılarını, şifre sıfırlamalarını ve OTP'leri doğrulayın."
id: email-verification
title: E-posta Doğrulama
sidebar_position: 11
---

# E-posta Doğrulama

TestFly'nin `mailbox()` API'si, uygulamanızın doğru e-postaları gönderdiğini doğrulamanızı sağlar — onay bağlantıları, şifre sıfırlamaları, karşılama mesajları — UI test akışınızın bir parçası olarak, ekstra araç gerektirmeden.

---

## Hızlı başlangıç

```java
// 1. Uygulamada e-postayı tetikleyin
find(By.id("email")).type("user@test.com");
find(By.id("register")).click();

// 2. Bekleyin ve doğrulayın
Email email = mailbox().waitForEmail(to("user@test.com").timeout(30));
email.assertSubject("Verify your account");
email.assertBodyContains("welcome to");

// 3. Doğrulama bağlantısını çıkarın ve takip edin
String verifyLink = email.extractLink("Verify Email");
open(verifyLink);
assertThat(By.id("success-msg")).hasText("Email verified!");
```

`to()` kısa yazımı doğrudan `BaseTest` ve `BaseJUnit5Test` içinde mevcuttur — statik import gerekmez.

---

## Altyapılar (backend'ler)

Altyapıyı `testfly.yml` içinde bir kez yapılandırın. Hangi altyapıyı kullandığınızdan bağımsız olarak test kodu aynıdır.

| Sağlayıcı | En uygun |
|---|---|
| `mailhog` | Yerel geliştirme, Docker CI |
| `mailtrap` | Paylaşılan ekip gelen kutuları, staging |
| `outlook` | Office 365 / kurumsal e-posta |
| `imap` | Gmail (uygulama parolası), Yahoo, herhangi bir IMAP sunucusu |

---

## Mailhog (yerel / Docker)

Mailhog'u uygulamanızın yanında Docker'da çalıştırın:

```bash
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog
```

TestFly'nin onu kullanmasını yapılandırın:

```yaml title="testfly.yml"
email:
  provider: mailhog
  mailhog:
    host: localhost
    port: 8025
```

Uygulamanızın SMTP'sini `localhost:1025` olarak ayarlayın. Mailhog, tüm giden postayı yakalar ve HTTP API'si üzerinden sunar — gerçek e-posta gönderilmez.

---

## Mailtrap (barındırılan sandbox)

```yaml title="testfly.yml"
email:
  provider: mailtrap
  mailtrap:
    apiToken:  ${MAILTRAP_TOKEN}
    accountId: ${MAILTRAP_ACCOUNT_ID}
    inboxId:   ${MAILTRAP_INBOX_ID}
```

Kimlik bilgilerinizi [mailtrap.io](https://mailtrap.io) → Email Testing → Inboxes → API Credentials adresinden alın.

---

## Outlook / Office 365

Uygulama-yalnızca (app-only) OAuth2 istemci kimlik bilgileriyle **Microsoft Graph API** kullanır — kullanıcı girişi gerekmez. Office 365 ve kişisel Outlook hesaplarıyla çalışır.

### Azure AD kurulumu

1. [portal.azure.com](https://portal.azure.com) → **App registrations** → **New registration** adresine gidin
2. **API permissions** → Add → Microsoft Graph → Application permissions → `Mail.Read` + `Mail.ReadWrite`
3. **Grant admin consent** düğmesine tıklayın
4. **Certificates & secrets** → New client secret → değeri kopyalayın

```yaml title="testfly.yml"
email:
  provider: outlook
  outlook:
    tenantId:     ${AZURE_TENANT_ID}
    clientId:     ${AZURE_CLIENT_ID}
    clientSecret: ${AZURE_CLIENT_SECRET}
    mailbox:      test-inbox@yourcompany.com
```

OAuth2 erişim belirteci otomatik olarak alınır ve süresi dolmadan yenilenir — manuel belirteç yönetimi gerekmez.

---

## IMAP (Gmail, Yahoo, kurumsal)

İsteğe bağlı Jakarta Mail bağımlılığını projenize ekleyin:

```xml title="pom.xml"
<dependency>
  <groupId>com.sun.mail</groupId>
  <artifactId>jakarta.mail</artifactId>
  <version>2.0.1</version>
  <scope>test</scope>
</dependency>
```

```yaml title="testfly.yml"
email:
  provider: imap
  imap:
    host:     imap.gmail.com
    port:     993
    ssl:      true
    username: ${EMAIL_USER}
    password: ${EMAIL_PASS}   # Gmail: hesap parolanızı değil, bir Uygulama Parolası kullanın
```

:::tip Gmail Uygulama Parolaları
[myaccount.google.com](https://myaccount.google.com) → Security → 2-Step Verification → App passwords adresine gidin. "Mail" için bir tane oluşturun ve `EMAIL_PASS` olarak kullanın.
:::

---

## Eşleştirme kriterleri

```java
// Alıcıya göre eşleştir (en yaygın olanı)
mailbox().waitForEmail(to("user@example.com"));

// Alıcı + konuya göre eşleştir
mailbox().waitForEmail(to("user@example.com").subject("Welcome!"));

// Gövde içeriğine göre eşleştir
mailbox().waitForEmail(to("user@example.com").containing("verify your account"));

// Yalnızca konu (alıcı filtresi yok)
mailbox().waitForEmail(any().subject("Password Reset"));

// Özel zaman aşımı
mailbox().waitForEmail(to("user@example.com").timeout(60));
```

---

## E-posta doğrulamaları ve bağlantı çıkarma

```java
Email email = mailbox().waitForEmail(to("user@example.com"));

// Konuyu doğrula
email.assertSubject("Verify your account");

// Gövde içeriğini doğrula (düz metin ve HTML gövdesini kontrol eder)
email.assertBodyContains("Click the link below");

// Ham alanlara eriş
String subject  = email.subject();
String from     = email.from();
String body     = email.body();     // düz metin
String htmlBody = email.htmlBody(); // HTML

// Görünür bağlantı metnine göre bir bağlantının href değerini çıkar
String link = email.extractLink("Verify Email");
open(link);
```

`extractLink`, görünür metni (büyük/küçük harf duyarsız olarak) tam eşleşen ilk `<a>` etiketini bulur. Eşleşen bağlantı bulunamazsa `AssertionError` fırlatır.

---

## Gelen kutusunu temizleme

Her testten önce temiz bir gelen kutusu sağlamak için `@BeforeMethod` içinde `mailbox().clear()` çağırın:

```java
@BeforeMethod
public void cleanInbox() {
    mailbox().clear();
}
```

Veya yapılandırmada otomatik temizlemeyi etkinleştirin:

```yaml title="testfly.yml"
email:
  autoClear: true   # her testten önce gelen kutusunu otomatik temizle
```

---

## Tam yapılandırma başvurusu

```yaml title="testfly.yml"
email:
  provider: mailhog        # mailhog | mailtrap | outlook | imap
  timeoutSeconds: 30       # varsayılan bekleme zaman aşımı
  pollIntervalMs: 1000     # gelen kutusunu yoklama sıklığı
  autoClear: false         # her testten önce gelen kutusunu temizle

  mailhog:
    host: localhost
    port: 8025

  mailtrap:
    apiToken:  ${MAILTRAP_TOKEN}
    accountId: ${MAILTRAP_ACCOUNT_ID}
    inboxId:   ${MAILTRAP_INBOX_ID}

  outlook:
    tenantId:     ${AZURE_TENANT_ID}
    clientId:     ${AZURE_CLIENT_ID}
    clientSecret: ${AZURE_CLIENT_SECRET}
    mailbox:      test-inbox@yourcompany.com

  imap:
    host:     imap.gmail.com
    port:     993
    ssl:      true
    username: ${EMAIL_USER}
    password: ${EMAIL_PASS}
    folder:   INBOX
```