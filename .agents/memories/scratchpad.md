# TestFly — Güncel Durum (Scratchpad)

## Mevcut Durum
- Maven: 1.0.7-SNAPSHOT; Java 21 ve JDK HTTP transport korunuyor.
- development dalı; main commit/push yasak. Her git işleminden önce dal kontrolü zorunlu.
- Interceptor/doc konu ayrımı önceki f77bd59 commit’inde; API geliştirmeleri ve kalan değişiklikler kullanıcı isteğiyle development commit’ine kaydedildi; push/release yok.

## API Geliştirmeleri — 2026-10-01
- ApiMockRule: request/test kapsamı, ilk eşleşme, interceptor sonrası sentetik response. Snapshot, dış status retry, hook uyumu ve cleanup korunuyor.
- SSL: PKCS12/JKS truststore, request override, opt-in trustAll. Hostname kontrolü JDK HTTPS identity ile korunur; eksik kimlik bağlamı fail-closed. JVM SSL ayarı değiştirilmez; özel profiller test kapanışında kapatılır.
- Transport registry: default client paylaşımı, connectTimeoutSeconds=30; requestTimeout(Duration)/timeout(int) son seçim kazanır. maxConcurrentRequests=0 sınırsız, pozitif değer runtime fiziksel gönderimleri fair semaphore ile sınırlar; mock permit tüketmez. Aktif scope varken limit değiştirilemez.
- Tam suite izolasyonu düzeltildi: sınıf başına ayrı JVM (4 fork); ortak durumlu registry/clock/page knowledge testleri singleThreaded. Eski timeout reflection testi Duration’a uyarlandı; assertion korunuyor.
- Doğrulama: mvn test ve temiz verify (gpg.skip=true) başarılı; son temiz koşu 1360 test, 0 failure/error/skip. Yeni ApiFeaturesTest 11 test; çalıştırılabilir ApiMockExamplesTest 2 test ayrıca geçti. TR/EN npm run build başarılı.
- Localhost benchmark 20 çağrıda tek TCP bağlantısı gözlemledi; ölçümler bilgilendirici, CI eşiği yok.
- Yeni mandatory dependency veya sürüm artışı yok. Önceden mevcut kullanıcı değişiklikleri korundu.

## Sonraki Odak
- Kullanıcının raporlama isteği: HtmlReportGenerator/report-template thread-safe geliştirmeleri bekliyor.

## Linkler
[[wiki/api-testing]] | [[MAP]] | [[rules/git-release-workflow]] | [[memories/log]]
