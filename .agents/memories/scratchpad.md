# TestFly — Güncel Durum (Scratchpad)

## Mevcut Durum
- Maven sürümü: 1.0.7-SNAPSHOT; Java 21, JDK HTTP transport korunuyor.
- Geliştirme development dalında. main commit/push yasak; her git işleminden önce dal kontrolü yapılır.
- JDK 21 modernizasyonu, specialist ajan profilleri ve archify diyagramları önceki çalışmalarda tamamlandı.

## API Interceptor Zinciri — 2026-10-01
- Uygulandı: immutable ApiRequest, ApiInterceptor/Chain, ApiResponse builder; test/request kapsamlı kayıt, ardışık proceed ve sentetik yanıt.
- Ortak sync/async pipeline, dış YAML retry, legacy hook uyumu, body snapshot/replay; cancellation ve kesilebilir backoff.
- Virtual-thread runtime, test auth/cookie/context izolasyonu, batch semaphore ve lifecycle cleanup. Kapanmış teste geç rapor engellenir; gerçek request/cURL başlıkları maskelenir.
- TR/EN rehber ve derlenen logging/refresh/mock/sync-async örnekleri eklendi. Yeni zorunlu bağımlılık/sürüm değişikliği yok; Kullanıcı isteğiyle development commit’i oluşturuldu; push yapılmadı.
- Doğrulama: temiz API odaklı mvn clean verify (yerel GPG kapalı) 78 test, 0 hata; 30 yeni chain/execution testi dahil. Docusaurus npm run build EN/TR başarılı.
- Tam paket yeşil değil: varsayılan mvn test takılması değişikliksiz HEAD'de de görüldü. Ayrı JVM'li geniş verify: 1348 test, 6 hata (driver registry/clock/page knowledge); driver/clock hataları HEAD'de yeniden üretildi. Page knowledge hatasının baseline tekrarı doğrulanmadı.
- Mimari: [[wiki/api-testing]]; ayrıntı: [[memories/log]].

## API Dokümantasyon Düzeni — 2026-10-01
- API Testing rehberi TR/EN konu sayfalarına ayrıldı; Interceptor Zinciri sol menüde ayrı öğe. Başlangıç rehberi korunup konu bağlantıları eklendi.

## Sonraki Odak
- Tam test paketinin izolasyon/koşucu sorunlarını ayrı görevde çözmek.
- Kullanıcının raporlama talebi: HtmlReportGenerator/report-template thread-safe geliştirmeleri bekliyor.

## Linkler
[[MAP]] | [[rules/git-release-workflow]] | [[memories/log]]
