# TestFly — Güncel Durum (Scratchpad)

## Mevcut Durum
- Yerel pom: io.github.hakanngul:testfly:1.0.7; Java21. main commit/push yasak; her git işleminden önce `git branch --show-current`.
- Denetim remediation Faz 1 worktree'de (`testfly-worktrees/audit-phase1`, development, HEAD d6ca2ff) UNCOMMITTED. Ana checkout `chore/docs-cloudflare-workers` dalında, dokunulmadı.

## Faz 1 Sonucu — 2026-10-08
- Final doğrulama: `mvn clean verify -Dgpg.skip=true` 1444 test/0 hata (taban 1362→1405→1444), jacoco.exec var; docs EN+TR build OK. 5 mutasyonla regresyon testleri düzeltmesiz kırmızı doğrulandı, geri yükleme byte-identical.
- Yapılan: T1.2 release.yml sertleştirme, T1.4 kısmi koordinat/sürüm, T1.5, T1.6 jackson-bom 2.21.7, T1.7 HtmlReport kaçış+tek geçiş, T1.8-10/12 DriverManager (izin sızıntısı, per-suite recreate, sessionWaitSeconds), T1.11 LoadTestDetector, T1.14b hata mesajı, T1.16 kısmi, T1.17 Locator auto-wait, T1.18 innermost getByText, T1.20 JaCoCo, T1.21 kısmi, T1.22 docs örnekleri.
- Rapor: `testfly/target/audit-scratch/phase1-summary.md` (görev bazlı detay, takipler).

## Bekleyen Kullanıcı Kararları
- D-01 sürüm/koordinat + Central kök neden; D-02 retry; D-03 config katmanlama; D-04 yük testi 4xx/5xx/targetRps; D-06 lazy driver; T1.13 quitAllSuiteDrivers daraltma.
- T1.3 öncesi: release.yml fork kuru koşusu + GitHub `release` environment.

## v1.0.7 Hazırlık — 2026-10-09
- Faz 1 + sürüm hazırlığı development'a push edildi (4702096). CHANGELOG/README/AGENTS/profile sürümleri 1.0.7. Rapor: `.agents/tasks/release-v1.0.7-prep.md` (commit'lenmedi).
- Kalan: development→main PR, v1.0.7 tag (main merge commit'i), release env onayı, fork kuru koşusu. Sonra docs 1.0.4 pin'lerini çevir, `since="1.1.0"` (50 satır/21 dosya) kararı.

## Linkler
[[MAP]] | [[wiki/api-testing]] | [[memories/log]] | [[skills/testfly-test-authoring/SKILL]]
