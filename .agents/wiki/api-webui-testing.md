---
tags:
  - wiki
  - api-testing
  - webui
  - hybrid-testing
  - dataflow
date: 2026-09-10
status: active
type: wiki
---

# API ve WebUI Hibrit Test Mimarisi (Hybrid Testing)

TestFly, saf UI testlerinin yavaşlığını ve kırılganlığını ortadan kaldırmak için REST API ile WebUI otomasyonunu aynı test senaryosunda birleştiren hibrit test modelini destekler.

---

## 1. Temel İş Akışı
1. **Hızlı Ön Hazırlık (API Seeding):** `ApiClient` doğrudan arka uç REST endpoint'lerine çağrı yaparak oturum token'ı oluşturur ve gerekli veritabanı kayıtlarını (sipariş, kullanıcı vb.) milisaniyeler içinde hazırlar.
2. **Bağlam Paylaşımı (ScenarioContext):** Üretilen token ve ID'ler ThreadLocal izole `ScenarioContext` deposuna yazılır.
3. **Tarayıcı Enjeksiyonu (StorageHelper):** `StorageHelper` veya çerez yöneticisi, kimlik doğrulama çerezlerini doğrudan `WebDriver` oturumuna enjekte ederek kullanıcı giriş ekranını atlar.
4. **Hızlı UI Gezintisi (WaitEngine & SmartLocator):** Tarayıcı doğrudan hedeflenen sayfaya açılır ve kullanıcı işlemleri otomatik beklemelerle yürütülür.
5. **Çift Yönlü Doğrulama (Dual Assertions):** 
   - `SeleniumAssert` ile DOM elemanları ve görsel durum test edilir.
   - `ApiClient` ile arka uç veritabanının UI işlemlerini doğru kaydettiği doğrulanır.
6. **Bütünleşik Raporlama:** `StepLogger` hem REST istek/yanıt detaylarını hem de tarayıcı ekran görüntülerini tek bir HTML zaman çizelgesinde sunar.

---

## 2. İnteraktif Veri Akışı Şeması (Archify Dataflow)
Archify ile derlenmiş interaktif, karanlık/aydınlık tema destekli veri akışı şeması:
- [TestFly API & WebUI Dataflow Diagram (HTML)](file:///Users/hagul/Projects/TestFramework/testfly/docs-site/static/diagrams/testfly-api-webui-dataflow.html)

---

## İlgili Bağlantılar
- Mimari: `[[wiki/architecture]]`
- K6 Yük Testi: `[[wiki/load-testing]]`
- Ana Harita: `[[MAP]]`
- Wiki Dizin: `[[wiki/index]]`
- Archify Becerisi: `[[skills/archify/SKILL]]`
