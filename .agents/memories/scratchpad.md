# TestFly — Güncel Durum (Scratchpad)

## Aktif Odak
- Dal: `chore/docs-cloudflare-workers`; `main` dalına commit/push yasak. PR #44 Anthropic başvuru sonucu gelene kadar merge edilmeyecek.
- Docs-site mimari/API uyum denetimi tamamlandı; kullanıcı henüz düzeltme uygulaması istemedi, yalnız plan istedi.

## Docs Denetimi — 2026-10-08
- Docusaurus config doğrulaması ve EN/TR `npm run build` başarılı; derleme Java örneklerinin doğruluğunu denetlemiyor.
- Önerilen UI standardı: semantik `Locator` (`getByRole/getByLabel/getByTestId`), sonra `find(String)`; `By` yalnız WaitEngine/frame/shadow/upload/SmartLocator/raw Selenium interop gibi sınır API'lerinde. Yeni örneklerde deprecated `$()` kullanılmamalı; framework-managed driver için varsayılan `BasePage()` tercih edilmeli.
- Kritik stale/derlenmeyen docs: `guides/base-page`, `ai/prompt-recipes`, `ai/testfly-mcp`, `guides/video-recording`, `why/why-waitengine`, `migration/from-selenium-testng`, `extensibility/plugins`, TR `recipes/oauth-sso` ve loadtest sayfaları. Yanlış örnekler: `io.testfly.core/locators`, `RoleOptions`, `.fill/.val`, `getWait().waitFor*`, olmayan `@LoadEngine`, eski `@LoadTest` alanları ve feeder/load DSL metotları.
- Maven koordinat/sürüm çelişkisi var: yürütülebilir `pom.xml` = `io.github.hakanngul:testfly:1.0.7`; docs içinde `io.testfly`, 1.0.0/1.0.4/1.0.7/1.1.0 ve TR Gradle'da 2.6.0 karışık. Düzeltmeden önce yayınlanmış koordinat/sürüm tek kaynak olarak netleştirilmeli.
- EN sayfalarının 4 TR karşılığı eksik: `ci/bitbucket-pipelines`, `loadtest/distributed-docker-k8s`, `migration/from-restassured`, `reporting/allure`.

## Sonraki Açık Görevler
- Kullanıcı onaylarsa P0 derleme hataları → P1 Locator/POM standardı → P2 EN/TR eşitleme → snippet compile/lint kapısı sırasıyla uygulanacak.
- Her docs değişiminde çift dil korunacak, config değişirse validator; sonunda `npm run build` çalıştırılacak.

## Linkler
[[wiki/webui-testing]] | [[wiki/architecture]] | [[rules/docusaurus-workflow]] | [[rules/git-release-workflow]] | [[memories/log]]
