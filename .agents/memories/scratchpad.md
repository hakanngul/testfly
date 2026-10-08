# TestFly — Güncel Durum (Scratchpad)

## Mevcut Durum
- Maven: 1.0.7-SNAPSHOT; Java 21 ve JDK HTTP transport korunuyor.
- development dalı; main commit/push yasak. Her git işleminden önce dal kontrolü zorunlu.
- Interceptor/doc konu ayrımı önceki f77bd59 commit’inde; API geliştirmeleri ve kalan değişiklikler kullanıcı isteğiyle development commit’ine kaydedildi; release yok.

## API Geliştirmeleri — 2026-10-01
- ApiMockRule: request/test kapsamı, ilk eşleşme, interceptor sonrası sentetik response. Snapshot, dış status retry, hook uyumu ve cleanup korunuyor.
- SSL: PKCS12/JKS truststore, request override, opt-in trustAll. Hostname kontrolü JDK HTTPS identity ile korunur; eksik kimlik bağlamı fail-closed. JVM SSL ayarı değiştirilmez; özel profiller test kapanışında kapatılır.
- Transport registry: default client paylaşımı, connectTimeoutSeconds=30; requestTimeout(Duration)/timeout(int) son seçim kazanır. maxConcurrentRequests=0 sınırsız, pozitif değer runtime fiziksel gönderimleri fair semaphore ile sınırlar; mock permit tüketmez. Aktif scope varken limit değiştirilemez.
- Doğrulama: mvn test ve temiz verify (gpg.skip=true) başarılı; son temiz koşu 1360 test, 0 failure/error/skip. Yeni ApiFeaturesTest 11 test; çalıştırılabilir ApiMockExamplesTest 2 test ayrıca geçti. TR/EN npm run build başarılı.

## PR Yönetimi — 2026-10-01
- #28/#29 kapatıldı. #27 development→main; API commitleri 5e12d2f ile push edildi, GitHub unit/integration kontrolleri başarılı.
- Main ruleset 24296278 aktif: PR, strict GitHub Actions Unit Tests, conversation resolution, force-push/silme engeli; bypass yok, approval=0.
- Açık P2 report write race düzeltildi: ReportAdapterRegistry.generateAll synchronized, adapter’lar snapshot kayıt sırasıyla caller thread’de çalışır; hata izolasyonu korunur. Regresyon testi shared-output write/read sırasını doğrular.
- İlgili raporlama testleri ve tam mvn test başarılı. Düzeltme development commit/push kapsamında; review PRRT_kwDOUBZN7c6njS4- çözülüp yeni CI izlenecek. Sürüm/release yok.

- Docs: testfly.dev Cloudflare Workers geçişi (2026-10-08), bkz. log.
## Linkler
[[wiki/api-testing]] | [[MAP]] | [[rules/git-release-workflow]] | [[memories/log]]
