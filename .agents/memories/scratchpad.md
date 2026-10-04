# TestFly — Güncel Durum (Scratchpad)

## Mevcut Durum
- Yerel pom: io.github.hakanngul:testfly:1.0.7; Java21/JDK HTTP. development doğrulandı; main commit/push yasak.
- Interceptor/mock/SSL/timeout/concurrency tamamlandı. Report race 44a0403 ile push; önceki 1361 test sonucu tarihsel.

## Docs Denetimi & Düzeltmeler — 2026-10-04
- 2026-10-04 tarihli Docs audit raporu üzerinden düzeltmeler (EN+TR) uygulandı (önceki düzeltmeler 299a4c6 commit’inde).
- **Koordinat/Sürüm:** Tüm sayfalarda `io.testfly` yerine `io.github.hakanngul:testfly` kullanıldı. Central sürümü olan yerler `1.0.4` bırakıldı ve Central uyarısı eklendi. Load test modülü sonradan (1.0.5) eklendiği için `loadtest/getting-started` sayfasında `1.0.7` (local install) olarak bırakıldı.
- **API:** `why-waitengine`, `infinite-scroll`, `oauth-sso` için `WaitEngine` API uyumsuzlukları giderildi. `report-adapters` içindeki IOException durumu ve `Allure` page'indeki label hataları çözüldü. OpenApiValidator'daki (URL ve Content-Type eksikliği) bug tespit edildi, workaround eklendi (Kod düzeltmesi henüz yapılmadı). `LoadScenario.assertStatus` içindeki !=200 durumu için potansiyel bug not edildi.
- **Config & CI:** Yeni `api.*` değerleri eklendi, hatalı CI overrides (`TESTFLY_HEADLESS`) düzeltildi.
- **Eksik TR Sayfalar:** allure, bitbucket, docker-k8s, from-restassured TR dosyaları yazıldı. `loadtest/distributed-docker-k8s` İngilizce ve Türkçe olarak hatalı iddialardan (100K RPS, reportportal live streaming) arındırıldı.

## Yeni Ajan Yeteneği (Skill) — testfly-test-authoring
- Ajanların TestFly'ı kullanarak WebUI, API, TestNG, JUnit 5, Cucumber ve Load testleri yazabilmesi için `testfly-test-authoring` skill'i eklendi. SKILL.md kararlar, kurallar ve checklist içeriyor.

## Git — 2026-10-04
- Kullanıcı commit/push istedi: MD temizliği ve SemanticLocatorTest global ayar izolasyonu development kapsamında. mvn test: 1362/0 hata; EN/TR docs build başarılı. GPG terminal hatası: bu commitlerde geçici imzasız mod; global ayar değişmedi. Release/tag yok.

## Linkler
[[MAP]] | [[wiki/api-testing]] | [[memories/log]] | [[skills/testfly-test-authoring/SKILL]]
