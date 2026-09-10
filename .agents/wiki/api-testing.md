---
tags:
  - wiki
  - api-testing
  - dataflow
  - rest-assured
  - contract-testing
date: 2026-09-10
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

## İlgili Bağlantılar
- WebUI Test Mimarisi: `[[wiki/webui-testing]]`
- Hibrit Test: `[[wiki/api-webui-testing]]`
- Temel Mimari: `[[wiki/architecture]]`
- Ana Harita: `[[MAP]]`
- Wiki Dizin: `[[wiki/index]]`
- Archify Becerisi: `[[skills/archify/SKILL]]`
