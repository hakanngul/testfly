---
tags:
  - wiki
  - api-testing
  - dataflow
  - rest-assured
  - contract-testing
date: 2026-10-01
status: active
type: wiki
---

# TestFly REST & GraphQL API Test Mimarisi

TestFly, `BaseApiTest` ve `ApiClient` üzerinden sıfır tarayıcı ek yüküyle yüksek performanslı API testleri yürütülmesini sağlar.

---

## 1. Temel İş Akışı ve Yaşam Döngüsü

1. **Test Başlatma (`BaseApiTest`):** TestNG tabanlı koşucu, `testfly.yml` dosyasındaki `api.baseUrl` ve zaman aşımı ayarlarını ThreadLocal bağlama yükler.
2. **Kimlik Doğrulama (`AuthStrategy`):** Bearer token, Basic Auth, OAuth2 veya API-Key stratejileri istek öncesinde otomatik enjekte edilir.
3. **Akıcı İstek İnşası (`ApiClient`):** Path değişkenleri, query parametreleri, özel başlıklar ve JSON gövdeleri tip güvenli olarak oluşturulur.
4. **Hızlı İletim & Değişmez Yanıt (`ApiResponse`):** Harici mikroservise doğrudan HTTP iletimi yapılır; dönen yanıt durum kodu, başlıklar, gövde ve milisaniye bazlı gecikmeyle `ApiResponse` modeline sarılır.
5. **Sözleşme & Yanıt Doğrulama:**
   - `SchemaAssert`: JSON Schema ile API sözleşmesinin bozulmadığı doğrulanır.
   - `ResponseAssert`: HTTP durum kodu, JsonPath sorguları ve yanıt süresi (SLA) sınırları kontrol edilir.
6. **Kayıt ve Raporlama:** `StepLogger`, cURL komutunu, istek gövdesini ve yanıt detaylarını TestFly HTML raporu zaman çizelgesine işler.

---

## 2. İnteraktif Veri Akışı Şeması (Archify Dataflow)

Archify ile derlenmiş bağımsız, karanlık/aydınlık tema ve trace animasyon destekli API veri akışı şeması:
- [TestFly API Test Dataflow Diagram (HTML)](file:///Users/hagul/Projects/TestFramework/testfly/docs-site/static/diagrams/testfly-api-dataflow.html)

---

## 3. API Interceptor Zinciri (1.1.0 API sözleşmesi)

- `ApiInterceptor.intercept(Chain)` blocking çalışır. `proceed(request)` sabit downstream indeksinden devam eder; ardışık tekrar gönderim ve sentetik response geçerlidir. Chain, interceptor dönüşünden sonra veya başka thread üzerinde kullanılamaz; null request/response reddedilir.
- `ApiRequest` immutable hazırlanmış method/URI/timeout/header/body snapshot'ıdır. JSON/form/multipart çağrı başında bir kez serileştirilir; builder header işlemleri case-insensitive, koleksiyonlar ve byte dizileri defensive copy kullanır. `ApiResponse.builder/newBuilder` sentetik yanıt ve yanıt değişimini sağlar.
- Test kapsamındaki thread-local interceptor'lar istek kapsamındakilerden önce çalışır. YAML retry zincirin dışındadır; her deneme özgün snapshot ve yeniden uygulanan effective auth ile başlar. Yalnız transport hataları ve ayarlanmış HTTP status'ları retry adayıdır; kullanıcı kodu/assertion hataları ve cancellation tekrar edilmez.
- Legacy request hook yalnız gerçek gönderimde; response hook dış denemenin nihai yanıtında bir kez çalışır. İç refresh yanıtı nihai FAIL oluşturmaz. Gerçek gönderimler INFO, otomatik retry WARN; nihai sonuç PASS/FAIL ve toplam süreyle raporlanır.
- Sync/async ortak pipeline kullanır. Async çağıranda snapshot alır, yönetilen virtual thread'e test kimliği/auth/interceptor/log/retry bağlamını taşır. Aynı testin concurrent cookie jar'ı paylaşılır; farklı testler ayrıdır. Stateful interceptor thread-safe olmalıdır; rastgele kullanıcı ThreadLocal'ları taşınmaz.
- `ApiExecution` internal runtime scope, cancellation, kesilebilir backoff ve executor yaşam döngüsünü yönetir. TestNG/JUnit5/Cucumber kapanışı bekleyen çağrıları iptal eder, kapanmış scope'a geç raporlama engellenir. Batch semaphore ile mantıksal concurrency sınırlar ve sonuç sırasını korur.
- TR/EN rehber örnekleri `ApiInterceptorExamples` test kaynağında derlenir. Yeni auth-refresh/cache/mock-server/SSL motoru veya zorunlu bağımlılık eklenmemiştir.

## 4. Mock, SSL ve Transport Ayarları

- `ApiMockRule` immutable predicate/response factory taşır. İstek kuralları test kurallarından önce, ilk eşleşme kazanacak şekilde çalışır. Snapshot çağıranda alınır; mock terminali interceptor'ların ardından ve legacy request hook/transport öncesindedir. Mock sonuçları sentetik işaretlidir, response hook/status retry davranışını korur; hatalı kullanıcı kodu retry edilmez. Cleanup thread-local mock kayıtlarını temizler.
- `ApiTransport` internal registry default JDK client'larını connect timeout ile paylaşır; özel SSL profillerini test scope'unda tutar ve cleanup'ta shutdownNow ile kapatır. Profilde truststore ilk kullanımda yüklenir; parola hash/map anahtarı veya log'a yazılmaz. Çağrı hazırlığı SSL seçimini yakalar.
- PKCS12/JKS özel truststore varsayılan trust anchor'larını değiştirir. İstek SSL seçimi YAML'ın tamamını override eder; aynı kapsamda trustAll + truststore geçersizdir. TrustAll peer leaf'i geçici trust anchor yapıp JDK X509ExtendedTrustManager'ın HTTPS endpoint identity doğrulamasına devreder; hostname kontrolü yoksa fail-closed. JVM SSL/global hostname ayarları değiştirilmez.
- `api.connectTimeoutSeconds` 30; `api.timeoutSeconds` 30; `api.maxConcurrentRequests` 0 (sınırsız). `.requestTimeout(Duration)` ile `.timeout(int)` aynı request timeout'u ayarlar; son seçim kazanır. Read-idle timeout API'si yoktur.
- Runtime genelindeki pozitif transport limiti fair semaphore ile gerçek HTTP gönderimlerini sınırlar. Mock permit kullanmaz, refresh/retry ayrı permit alır. Limit aktif scope'lar varken değiştirilemez. Permit bekleme cancellation ile kesilir; toplam süreye dahil, transport süresinden ayrıdır. Batch'in mantıksal concurrency limiti ayrıca korunur.
- Test koşucu: sınıflar ayrı JVM'lerde (reuseForks=false); ortak durum kullanan driver registry, clock ve page knowledge testleri singleThreaded. Diğer sınıflarda method parallel korunur.
- Çalıştırılabilir örnek: `ApiMockExamplesTest`; localhost ölçümü: `ApiTransportBenchmark`. TR/EN API Mocking, SSL Configuration ve Timeouts & Performance rehberleri sol menüdedir.

## İlgili Bağlantılar
- WebUI Test Mimarisi: `[[wiki/webui-testing]]`
- Hibrit Test: `[[wiki/api-webui-testing]]`
- Temel Mimari: `[[wiki/architecture]]`
- Ana Harita: `[[MAP]]`
- Wiki Dizin: `[[wiki/index]]`
- Archify Becerisi: `[[skills/archify/SKILL]]`
