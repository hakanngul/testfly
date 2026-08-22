---
description: "Veri odaklı Selenium testleri: ekstra kalıp kod olmadan @TestData satırlarını CSV dosyalarından, Excel çalışma kitaplarından ve canlı veritabanı sorgularından yükleyin."
id: external-test-data
title: Harici Test Verisi Kaynakları
sidebar_position: 13
---

# Harici Test Verisi Kaynakları

TestFly 2.2.0, `@TestData` özelliğini CSV dosyalarından, Excel çalışma kitaplarından ve canlı veritabanı sorgularından satırları doğrudan yükleyecek şekilde genişletir — ekstra kalıp kod gerekmez.

---

## Genel bakış

| Ön ek | Kaynak | Ek bağımlılık? |
|--------|--------|-------------------|
| *(yok)* | `testdata/` içinde JSON / YAML dosyası | — |
| `csv:` | Sınıf yolundan (classpath) CSV dosyası | — |
| `excel:` | Apache POI üzerinden XLSX çalışma kitabı | `poi-ooxml` |
| `db:` | JDBC sorgusu — ilk sonuç satırı | Veritabanınız için JDBC sürücüsü |

---

## CSV

```java
@Test
@TestData("csv:testdata/logins.csv")
public void loginWithCsvData() {
    Map<String, Object> data = getTestData();
    String username = (String) data.get("username");
    String password = (String) data.get("password");
    new LoginPage(getDriver()).login(username, password);
    assertTrue(new DashboardPage(getDriver()).isLoaded());
}
```

Dosyanın ilk satırı sütun başlığı olarak kabul edilir. Varsayılan olarak ilk veri satırı yüklenir. Belirli bir satırı seçmek için `row` kullanın (sıfır tabanlı, başlık hariç):

```java
@TestData(value = "csv:testdata/logins.csv", row = 2)  // üçüncü veri satırı
public void loginAsThirdUser() { ... }
```

**Tip dönüşümü** otomatik olarak uygulanır:

| Hücre değeri | Java tipi |
|---|---|
| `42` | `Integer` |
| `3.14` | `Double` |
| `true` / `false` | `Boolean` |
| diğer her şey | `String` |

### CSV biçimi

Standart RFC 4180: virgülle ayrılmış, çift tırnakla sınırlandırılmış; tırnaklı bir alan içindeki `""` kaçışlı bir tırnaktır.

```
username,password,role,active
admin,secret,ADMIN,true
user1,"pass,1",USER,false
```

Ekstra bağımlılık gerekmez — CSV ayrıştırıcı framework içinde yerleşiktir.

---

## Excel (XLSX)

```java
@Test
@TestData(value = "excel:testdata/users.xlsx", sheet = "Login")
public void loginWithExcelData() {
    Map<String, Object> data = getTestData();
    String username = (String) data.get("username");
    // ...
}
```

- `sheet` — çalışma sayfası adı; atlanırsa varsayılan olarak ilk sayfa kullanılır
- `row` — sıfır tabanlı veri satırı (0 = başlık satırından sonraki ilk satır)

```java
@TestData(value = "excel:testdata/users.xlsx", sheet = "Admin", row = 1)
public void loginAsSecondAdminUser() { ... }
```

### Hücre tipi eşlemesi

| Excel tipi | Java tipi |
|---|---|
| Sayısal (tamsayı) | `long` |
| Sayısal (ondalık) | `double` |
| Sayısal (tarih biçimli) | `String` (ISO tarihi, örn. `"2024-03-15"`) |
| Boolean | `Boolean` |
| String | `String` |
| Boş | `""` |

### Gerekli bağımlılık

Apache POI'yı projenizin `pom.xml` dosyasına ekleyin:

```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
    <scope>test</scope>
</dependency>
```

O olmadan, çalışma zamanında net bir hata alırsınız:

```
[TestData] Apache POI is required for Excel sources.
Add 'org.apache.poi:poi-ooxml:5.2.5' to your pom.xml.
```

---

## Veritabanı

`database` yapılandırma bloğuna karşı bir JDBC sorgusu çalıştırır ve ilk sonuç satırını yükler:

```java
@Test
@TestData("db:SELECT username, password FROM test_users WHERE active = true LIMIT 1")
public void loginWithDbUser() {
    Map<String, Object> data = getTestData();
    String username = (String) data.get("username");
    // ...
}
```

Sütun etiketleri harita anahtarları haline gelir. Tipler doğrudan JDBC `ResultSet`'ten gelir — sayılar zaten `Integer` / `Long` / `Double`, boolean'lar `Boolean`, tarihler `java.sql.Date` olur.

### Gerekli yapılandırma

```yaml
database:
  url:      jdbc:postgresql://localhost/mydb
  username: ${DB_USER}
  password: ${DB_PASS}
```

Veritabanı kaynağı, `DbConnectionFactory` tarafından yönetilen aynı bağlantıyı yeniden kullanır — aynı test-başına yaşam döngüsüne katılır ve test sonunda otomatik olarak kapatılır.

---

## Dosya yerleşimi

CSV ve Excel dosyaları önce **sınıf yolu (classpath) kökünden**, ardından yedek olarak `testdata/` altından çözümlenir. Önerilen yerleşim `src/test/resources/testdata/` içidir:

```
src/test/resources/
  testdata/
    logins.csv
    users.xlsx
    admin.json
```

---

## Geriye dönük uyumluluk

Mevcut tüm `@TestData("users/admin.json")` kullanımları değişmemiştir. Yeni öznitelikler `sheet` ve `row` sırasıyla `""` ve `0` varsayılan değerlerine sahiptir; dolayısıyla mevcut ek açıklamalar hiçbir değişiklik gerektirmez.

---

## Ortam profilleriyle birleştirme

Ortam geçersiz kılması yalnızca JSON/YAML kaynakları için geçerlidir. CSV, Excel ve DB kaynakları `-Denv=` geçersiz kılmasını desteklemez — bunun yerine her ortam için belirli bir veri satırını seçmek üzere `row` kullanın.

---

## Sınıf düzeyinde ek açıklama

Ek açıklama sınıf düzeyinde de çalışır — sınıftaki tüm test metotları aynı veri kaynağını paylaşır:

```java
@TestData("csv:testdata/logins.csv")
public class LoginTests extends BaseTest {

    @Test
    public void loginSucceeds() { ... }

    @Test
    @TestData(value = "csv:testdata/logins.csv", row = 1)  // sınıf düzeyindeki satırı geçersiz kılar
    public void loginWithSecondUser() { ... }
}
```