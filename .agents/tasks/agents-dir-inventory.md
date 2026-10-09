# TestFly AI-Ajan Katmanı Envanteri (Salt-Okunur Denetim)

Tarih: 2026-10-09 · Dal: `development` (HEAD `e47e75a` = `origin/development`) · Worktree: `testfly-worktrees/audit-phase1`
Hiçbir dosya silinmedi / taşınmadı / düzenlenmedi. Yazılan tek dosya bu rapordur.

---

## 1. Kısa Özet (ELI5)

**Soru: "Projeyi derlemek/çalıştırmak için `.agents` ve `.kiro` HİÇ gerekli mi?" → HAYIR. Hiçbiri gerekli değil.**

- Maven (`pom.xml`), GitHub Actions, Jenkins, `src/` ve docs-site build'i `.agents`'a tek bir referans bile vermiyor (aşağıdaki grep kanıtları). `.agents` silinse `mvn verify` ve `npm run build` aynı şekilde çalışır.
- `.agents` bir "ajan için not defteri + el kitabı" klasörüdür. Sadece AI ajanı (Kiro/Antigravity/Gemini) okursa işe yarar; derleyici, test koşucusu ve CI bunu hiç okumaz.
- `.kiro` bu repoda **yok** (ne ana checkout'ta ne worktree'de) ve `.gitignore`'da zaten yasaklı. Kiro'nun kendi şeyleri repoda değil, senin ev dizininde: `~/.kiro` (4 GB).
- Kafa karıştıran "workflow" kayıtları da repoda **değil**; `~/.kiro/sessions/<oturum>/workflows/wf_*` altında geçici durur (25 adet, toplam ~1 MB). Repoya yazılmazlar.
- `.agents`'ın 11 MB'ının **8 MB'ı (%73) tek bir skill**: `archify` (genel amaçlı diyagram aracı, TestFly'a özel değil, 1 kez eklenmiş, hiç güncellenmemiş).
- Gerçekten "okunan" ve kullanılan çekirdek küçük: `AGENTS.md` + `MAP.md` + `memories/scratchpad.md` + `log.md` + 3 kural + `soul.md` (~ 100 KB). Wiki (24 dosya) kısmen kullanılıyor.
- Worktree (`audit-phase1`) güvenli: `development` ve `origin/development` aynı commit'te (`e47e75a`), yani push'lar gerçekten uzak repoya gitmiş. Ama 2 şey "açık": worktree'de commit edilmemiş değişiklikler var ve ana checkout (`chore/docs-cloudflare-workers`) **farklı/eski** bir dalda.

---

## 2. Projenin Çalışması İçin ŞART Olanlar

| Yol | Neden şart |
|---|---|
| `pom.xml` | Maven build, bağımlılıklar, surefire/jacoco/gpg profilleri |
| `src/main/java/io/testfly/**`, `src/test/java/io/testfly/**` | Framework kodu + birim testleri |
| `testfly.yml` | Framework yerel koşu konfigürasyonu |
| `.mvn`/`target/` | `target/` üretilen çıktı (git'te yok, 48 MB) |
| `docs-site/**` (+ `package.json`, `docusaurus.config.js`) | Dokümantasyon sitesi (`npm run build`) |
| `.github/workflows/{testfly-ci,release,deploy-docs}.yml`, `.github/dependabot.yml` | CI/CD ve release |
| `ci/Jenkinsfile` | Jenkins pipeline (kullanıyorsan) |
| `LICENSE`, `README.md`, `CHANGELOG.md`, `SECURITY.md`, `CONTRIBUTING.md` | Yayın/OSS gereklilikleri (Maven Central) |

**AI-ajan katmanından HİÇBİRİ şart değil.** Kanıtlar:

```
grep -nE '\.agents|\.kiro|\.claude|\.gemini|AGENTS|archify|\.impeccable|MAP\.md' pom.xml      -> 0 sonuç
grep -rnE '\.agents|\.kiro|\.claude|archify|AGENTS|\.impeccable' .github ci                  -> 0 sonuç
grep -rlE '\.agents/|\.kiro|archify' src                                                      -> 0 sonuç
grep -nE '\.agents|AGENTS' docs-site/docusaurus.config.js docs-site/package.json docs-site/sidebars.js -> 0 sonuç
grep -rIn -E '\.agents|archify' README.md CONTRIBUTING.md testfly.yml .env.example           -> 0 sonuç
.gitattributes:5: ".agents/** linguist-vendored"  (tek referans; sadece GitHub dil istatistiğini etkiler)
```

---

## 3. Ana Envanter Tablosu

Sınıf: GEREKLİ = projenin/ajanın çalışması için olmazsa olmaz · İSTEĞE BAĞLI = faydalı ama silinebilir · GEREKSİZ = kanıta göre değeri yok.
"Kullanılıyor" = bir ajan/kural/log'un fiilen atıf yaptığına dair kanıt var mı.
Boyutlar `du -sh`, commit sayısı `git log --oneline -- <yol>`.

### 3a. Kök seviye ajan dosyaları

| Yol | Ne işe yarıyor | Boyut | Git'te | Referans veren | Son değişiklik (commit sayısı) | Sınıf | Kullanılıyor mu + kanıt |
|---|---|---|---|---|---|---|---|
| `AGENTS.md` | Ajanların tek giriş noktası: proje özeti, komutlar, stil, git kuralı | 19 KB | Evet | `MAP.md`, `soul.md`, kurallar | 2026-10-08 (15) | **GEREKLİ** (ajan çalıştırıyorsan) | EVET – log.md ve MAP.md atıf yapıyor; Kiro/Antigravity kök `AGENTS.md`'yi otomatik okur. *Worktree'de şu an uncommitted değişiklik var (` M AGENTS.md`).* |
| `GEMINI.md` | Antigravity/Gemini proje talimatı | 4.8 KB | **development'ta SİLİNDİ** (`d6ca2ff`); ana checkout'ta (eski dal) hâlâ var | – | 2026-10-01 | GEREKSİZ (AGENTS.md'ye taşındı) | HAYIR – `git show --stat d6ca2ff`: `GEMINI.md 88 ----` |
| `PRODUCT.md` | Ürün tanımı | 4 KB | development'ta SİLİNDİ (`d6ca2ff`) | – | 2026-10-01 | GEREKSİZ | HAYIR – aynı commit |
| `CLAUDE.md`, `.cursor/`, `.claude/`, `.gemini/`, `.kiro/` (repo içi), `.github/copilot-instructions.md` | – | – | **Hiçbiri yok** | – | – | – | Repo içinde bu araçlar için dosya yok (`ls -A`, `git ls-files` = 0). `.gitignore` zaten `.kiro`, `.claude/*`, `.agent`, `.qwen`, `ai-docs`, `docs-agent` satırlarını içeriyor |
| `DESIGN.md` | Görsel/WCAG tasarım kuralları (AGENTS.md'den linkli) | 14 KB | Evet | `AGENTS.md` | 2026-09-10 (1) | İSTEĞE BAĞLI | EVET (AGENTS.md linki); ama `.impeccable` ile birlikte tek commit |
| `ROADMAP.md` | Yol haritası | 12 KB | Evet | `wiki/roadmap.md`, README | 2026-10-04 (10) | İSTEĞE BAĞLI (insan dokümanı) | EVET – düzenli güncelleniyor |
| `.impeccable/` | `config.json` (`{"buildPath":"comp"}`) + `design.json` – bir tasarım aracı yapılandırması | <10 KB | Evet (2 dosya) | **Hiç kimse** | 2026-09-10 (1) | **GEREKSİZ** | HAYIR – `grep -rIl impeccable .` (archify hariç) = 0 sonuç |
| `.testfly/healed-locators.json` | Self-healing önbelleği (çalışma-zamanı çıktısı) | <1 KB | Evet (kazara) | `src/.../healing/HealingCache.java` (runtime üretir) | 2026-08-22 (1) | GEREKSİZ (çalışma artefaktı; `.gitignore` sadece iki diğer .testfly dosyasını yok sayıyor) | Ajan katmanı DEĞİL; yanlışlıkla commit edilmiş çıktı |
| `review/` (`audit-raporu.md`, `kontrol-edilecekler.md`) | Eski denetim notları | küçük | **Takipli ama `.gitignore`'da** (`review`) | **Hiç kimse** | 2026-09-07 (1) | GEREKSİZ | HAYIR – `grep -rIl 'audit-raporu\|kontrol-edilecekler' .` = 0 |
| `docs/` (11 dosya: architecture, internals, public-api, diyagramlar…) | İnsan/geliştirici dokümanları | 640 KB | Evet | AGENTS.md "Useful References" | 2026-10-04 (28) | İSTEĞE BAĞLI (ajan katmanı değil, insan dokümanı) | EVET |

### 3b. `.agents/` (toplam **11 MB, 261 dosya**; 260'ı git'te takipli)

| Yol | Ne işe yarıyor | Boyut / dosya | Git'te | Referans veren | Son değişiklik (commit) | Sınıf | Kullanılıyor mu + kanıt |
|---|---|---|---|---|---|---|---|
| `MAP.md` | Ajan için "içindekiler / GPS" | 8 KB / 1 | Evet | `AGENTS.md`, `soul.md`, kurallar | 2026-10-01 (12) | **GEREKLİ** (bu bellek sistemini kullanacaksan) | EVET – AGENTS.md "STOP – READ THIS" bölümü zorunlu kılıyor. ⚠ İçinde 4 adet **ana checkout'a sabit** `file:///Users/hagul/.../testfly/...` yolu var (worktree'yi göstermez) |
| `soul.md` | Ajan "kimliği" tek paragraf | 4 KB / 1 | Evet | `AGENTS.md`, `MAP.md`, `wiki/index.md` | 2026-10-01 (2) | İSTEĞE BAĞLI | EVET (referans var) ama içeriği `git-release-workflow` + AGENTS.md ile tekrar |
| `memories/scratchpad.md` | Kısa hafıza (≤2.200 karakter) | 1.7 KB | Evet | AGENTS.md (zorunlu okuma) | 2026-10-08 (44 ile `memories`) | **GEREKLİ** (ajan akışının kalbi) | EVET – en sık güncellenen dosya |
| `memories/log.md` | Kronolojik günlük | 37 KB / 300 satır | Evet | AGENTS.md | 2026-10-08 | İSTEĞE BAĞLI (sadece append; ajan okumak zorunda değil) | EVET (yazılıyor), okunma BELİRSİZ |
| `rules/memory-protocol.md` | "2 Yol & 3 Parça" hafıza kuralı | 7.5 KB | Evet | AGENTS.md, MAP | 2026-10-01 (4 ile `rules`) | İSTEĞE BAĞLI | EVET (referans), AGENTS.md ile büyük ölçüde TEKRAR |
| `rules/git-release-workflow.md` | `main`'e commit yasağı + sürüm akışı | 4.2 KB | Evet | AGENTS.md, MAP | 2026-10-01 | **GEREKLİ** (koruma kuralı) | EVET – "main'e dokunma" kuralının asıl kaynağı |
| `rules/docusaurus-workflow.md` | docs-site değişikliklerinde TR/EN + build kuralı | 1.6 KB | Evet | AGENTS.md, MAP | 2026-09-10 | İSTEĞE BAĞLI | EVET |
| `wiki/` (24 md + `diagrams/`) | Mimari/konu bilgi tabanı (`index`, `architecture`, `api-testing`…) | 2.4 MB (diagrams 2.3 MB) | Evet | `MAP.md`, `wiki/index.md` | 2026-10-04 (12) | İSTEĞE BAĞLI | KISMEN – md sayfaları MAP'ten linkli; `wiki/diagrams/*.html` (3 html + 3 json) **hiçbir md'den linklenmemiş** (`grep "diagrams/" .agents/**/*.md` = 0) |
| `skills/archify/` | Genel amaçlı HTML diyagram üretici (v2.17, 3. parti, tt-a1i) | **8.0 MB / 199 dosya** (`examples` 4 MB, `test` 1.6 MB, `renderers` 1.1 MB, `assets` 764 KB) | Evet | `MAP.md`, `.obsidian/workspace.json`, wiki sayfaları | 2026-09-10 (**1**) | **GEREKSİZ / İSTEĞE BAĞLI (en büyük atık adayı)** | KISMEN – log.md:160'a göre Web UI mimari diyagramları bununla üretildi (tek sefer); ürettiği 5 HTML `docs-site/static/diagrams/` içinde ama docs'tan **linklenmiyor** |
| `skills/testfly/` | TestFly SDET mimari/standart kılavuzu | 24 KB / 1 | Evet | `MAP.md`, plugin ajanları (7 dosya) | 2026-09-30 (1) | **GEREKLİ** (bilgi değeri yüksek) | EVET – plugin ajanları ve MAP atıf yapıyor. 20 KB, ~485 satır (büyük) |
| `skills/testfly-test-authoring/` | Tüketici projede test yazma rehberi + 6 referans | 32 KB / 7 | **Sadece development'ta** (ana checkout dalında yok) | `scratchpad.md`, `log.md`, `docs-site/docs/changelog.md` | 2026-10-04 (1) | İSTEĞE BAĞLI | EVET – scratchpad "Linkler" bölümünde. ⚠ `MAP.md`'de **yok** (yetim) |
| `skills/testfly-workflow/` | Framework geliştirme/birim test/sürüm runbook'u | 8 KB / 1 | Evet | `MAP.md`, plugin ajanları | 2026-10-01 (2) | İSTEĞE BAĞLI | EVET (plugin ajanları 3 yerde atıf) |
| `skills/docusaurus-config/` | Docusaurus config doğrulama (script + şablon) | 36 KB / 5 | Evet | `AGENTS.md`, `rules/docusaurus-workflow.md`, MAP | 2026-09-10 (1) | İSTEĞE BAĞLI | EVET (kural zorunlu kılıyor) ama script'in fiilen çalıştırıldığına log'da iz BELİRSİZ (log.md'de 'validate-config' araması yapılmadı) |
| `skills/memory-sync/` | scratchpad budama prosedürü | 4 KB / 1 | Evet | AGENTS.md, MAP, wiki | 2026-09-10 (1) | İSTEĞE BAĞLI | EVET (AGENTS.md atıf) |
| `skills/wiki-lint/` | Wiki sağlık kontrolü prosedürü | 4 KB / 1 | Evet | MAP, `wiki/index`, log.md | 2026-09-28 (1) | İSTEĞE BAĞLI | KISMEN – log.md'de 1 kez geçiyor |
| `plugins/testfly-agents/` (`plugin.json` + 8 ajan `.md`) | 8 uzman ajan tanımı (orchestrator, webui, api, load, reporting, qa, lead_architect, architect_orchestrator) | 56 KB / 9 | Evet | Sadece `.obsidian/workspace.json` ve `tasks/release-v1.0.7-plan.md` (gezinti kaydı / bir plan metni). **AGENTS.md ve MAP.md atıf YAPMIYOR** | 2026-09-30 (2) | **GEREKSİZ / BELİRSİZ** | **Büyük ihtimalle HAYIR.** Frontmatter'daki araç adları (`view_file`, `replace_file_content`, `write_to_file`, `run_command`) Antigravity-stili; Kiro ajan formatı farklı (`~/.kiro/agents/*.json`, `prompt`+`tools`). Repoda bunları yükleyen bir tüketici bulunamadı. Antigravity'nin yükleyip yüklemediği DOĞRULANAMADI |
| `.obsidian/` (5 json) | Obsidian görüntüleyici ayarları | 24 KB / 5 | Evet | – | 2026-09-28 (10) | GEREKSİZ (sadece Obsidian ile bakıyorsan) | `workspace.json` her açışta değişiyor (10 commit = gürültü). Ajan okumaz |
| `tasks/release-v1.0.7-plan.md` | Başka bir workflow'un ürettiği plan (v1.0.7 release hazırlığı) | 17 KB / 1 | **HAYIR – untracked** (`?? .agents/tasks/`) | – | 9 Eki 11:42 | GEÇİCİ | Bu rapor da buraya yazıldı. `.gitignore`'da değil → yanlışlıkla commit edilebilir |

### 3c. `.github/`, `ci/`

| Yol | Ne | Takipli | Sınıf | Not |
|---|---|---|---|---|
| `.github/workflows/*.yml` (3), `dependabot.yml`, `ISSUE_TEMPLATE/*`, `PULL_REQUEST_TEMPLATE.md`, `profile/README.md` | CI/CD, GitHub meta | Evet (9 dosya) | GEREKLİ (CI) | **Ajan dosyası yok** (copilot-instructions yok, agents/ yok) |
| `ci/Jenkinsfile` | Jenkins pipeline | Evet | İSTEĞE BAĞLI | Ajan referansı yok |

---

## 4. KULLANILAN / KULLANILMAYAN Listeleri

**KULLANILAN (kanıtlı):**
1. `AGENTS.md` – kök; ajan runtime'ı otomatik okur; sık güncelleniyor (15 commit).
2. `.agents/MAP.md`, `.agents/memories/scratchpad.md`, `.agents/memories/log.md` – AGENTS.md "zorunlu oku/yaz" diyor; scratchpad 44 commit alan klasörün parçası.
3. `.agents/rules/git-release-workflow.md` – `main` koruma kuralı, tekrar tekrar atıf.
4. `.agents/rules/{memory-protocol,docusaurus-workflow}.md`, `.agents/soul.md`.
5. `.agents/skills/testfly`, `testfly-workflow`, `docusaurus-config`, `memory-sync`, `testfly-test-authoring` – MAP/AGENTS/scratchpad/log'dan atıf.
6. `.agents/wiki/*.md` (md sayfaları) – MAP ve index üzerinden.
7. `docs/*.md`, `ROADMAP.md`, `DESIGN.md` – insan dokümanı; AGENTS.md'den linkli.

**KULLANILMAYAN / KANITSIZ:**
1. `.agents/plugins/testfly-agents/` – AGENTS.md/MAP.md bağlamıyor; yükleyen tüketici bulunamadı.
2. `.agents/skills/archify/` (8 MB) – tek seferlik diyagram üretimi (Eylül 10), sonra dokunulmadı.
3. `.agents/wiki/diagrams/` (2.3 MB) ve `docs-site/static/diagrams/` (3.9 MB, 10 dosya) – hiçbir md/docs/config linki yok.
4. `.agents/.obsidian/` – sadece Obsidian açıldıysa anlamlı.
5. `.impeccable/` – hiç referans yok.
6. `review/` – takipli ama `.gitignore`'da, hiç referans yok.
7. `.testfly/healed-locators.json` – runtime artefaktı, yanlışlıkla takipli.
8. `GEMINI.md`, `PRODUCT.md` – development'ta silinmiş; sadece eski dalda (ana checkout) kalıntı.

---

## 5. Çakışan / Tekrarlayan Şeyler

| Bilgi | Kaç yerde | Nerede |
|---|---|---|
| "main'e commit/push yasak, önce `git branch --show-current`" | **5+** | `AGENTS.md` (satır 10 ve 34), `soul.md`, `rules/git-release-workflow.md`, `skills/testfly-workflow/SKILL.md` (§6), log.md |
| Hafıza sistemi ("2 Yol & 3 Parça", 2.200 karakter) | **5** | `AGENTS.md`, `rules/memory-protocol.md`, `wiki/memory-system.md`, `MAP.md`, `skills/memory-sync` |
| Mimari anlatımı | 3 | `docs/architecture.md`, `.agents/wiki/architecture.md`, `.agents/skills/testfly/SKILL.md` |
| Yol haritası | 2 | `ROADMAP.md`, `.agents/wiki/roadmap.md` |
| Ajan talimatı (araç başına) | 2→1 | `GEMINI.md` + `AGENTS.md` birleştirildi (`d6ca2ff`); ana checkout'ta eski kopya duruyor |
| TestFly skill'leri | 3 | `skills/testfly` (20 KB), `testfly-workflow`, `testfly-test-authoring` – kapsamları kısmen çakışıyor |
| Diyagram HTML'leri | 2 | `.agents/wiki/diagrams/` vs `docs-site/static/diagrams/` (aynı archify çıktıları) |
| Orkestratör/uzman ajan | 2 | `plugins/testfly-agents/agents/testfly_orchestrator.md` ve `testfly_architect_orchestrator.md` aynı işi tarif ediyor |

---

## 6. Güvenle Silinebilecek Adaylar (SADECE ÖNERİ – silme yapılmadı)

Geri alınabilirlik: **git'te takipli olanlar** `git revert` / `git checkout <commit> -- <yol>` ile geri gelir. **Untracked olanlar kalıcı kaybolur.**

| Aday | Boyut | Neden güvenli | Geri alma |
|---|---|---|---|
| `.agents/skills/archify/` | 8.0 MB | Build/CI bağımlılığı yok (bkz. §2); genel araç; tek commit; sadece bir kez kullanılmış. Silince MAP.md'deki 1 satır ve `.obsidian/workspace.json` referansı temizlenmeli | Takipli → geri getirilebilir |
| `.agents/wiki/diagrams/` | 2.3 MB | Hiçbir md bağlamıyor; yeniden üretilebilir kaynak (`*.workflow.json`) ile birlikte arşivlenebilir | Takipli |
| `docs-site/static/diagrams/` | 3.9 MB | Docs'tan linklenmiyor (grep=0). ⚠ Ama canlı sitede doğrudan URL ile paylaşılmış olabilir → **önce kullanıcıya sor** | Takipli |
| `.agents/plugins/testfly-agents/` | 56 KB | Hiçbir dosya atıf yapmıyor; format Kiro'ya ait değil. ⚠ Antigravity kullanıyorsan yüklüyor olabilir (DOĞRULANAMADI) | Takipli |
| `.agents/.obsidian/` | 24 KB | Ajan okumaz; 10 commit gürültü. Obsidian kullanmıyorsan gereksiz | Takipli |
| `.impeccable/` | <10 KB | Hiç referans yok | Takipli |
| `review/` | küçük | `.gitignore`'da ama takipli (çelişki); hiç referans yok | Takipli (`git rm --cached` seçeneği de var) |
| `.testfly/healed-locators.json` | <1 KB | Runtime çıktısı; `.gitignore`'a eklenmeli | Takipli |
| Ana checkout'taki `GEMINI.md`, `PRODUCT.md`, `audit/`, `features/` | ~460 KB | `development`'ta zaten kaldırılmış/taşınmış; ana checkout eski dalda | Takipli (o dalda) |
| `.agents/tasks/` (untracked) | 17 KB+ | Geçici plan/rapor | ⚠ **Untracked → KALICI kayıp.** Silmeden önce içeriğe bak |
| `~/.kiro/sessions/*/workflows/wf_*` (25 adet) | ~1 MB | Tamamlanmış workflow kayıtları | Kiro tarafı; kalıcı |

**Silinmemeli:** `AGENTS.md`, `MAP.md`, `memories/scratchpad.md`, `rules/git-release-workflow.md`, `skills/testfly/`, `docs/`, `.github/workflows/`.

Tahmini kazanım: archify + diagrams + plugin + obsidian ≈ **10.4 MB** (`.agents`'ın %94'ü) → `.agents` ~0.6 MB'a iner.

---

## 7. Workflow / Worktree Karmaşasının Açıklaması

### Katman A – Repo içinde duranlar
`AGENTS.md`, `.agents/**`, `docs/`. Bunlar git'te; ajan okur.

### Katman B – Kiro'nun kendi ürettikleri (repo DIŞI, `~/.kiro`, toplam 4 GB)
| Yer | Ne | Boyut |
|---|---|---|
| `~/.kiro/sessions/<id>/workflows/wf_*/` | Her workflow için `workflow-definition.json`, `workflow-state.json`, `sessions.json`… (şu anki: `wf_a094c50157845921` = "investigate" şablonu, `wf-planner` ajanı) | 25 workflow, ~1 MB |
| `~/.kiro/sessions/` | Sohbet oturumları (24 klasör) | 140 MB |
| `~/.kiro/agents/*.json` | Global ajanlar: `kiro-api`, `kiro-web`, `kiro-apiframework`, `kirocrew-conductor`… | 304 KB |
| `~/.kiro/crew/` | KiroCrew verisi (modeller 2.1 GB) | 2.5 GB |
| `~/.kiro/extensions` | Eklentiler | 1.4 GB |
| `~/.kiro/steering/language.md`, `user-context.md` | Global talimat (her oturuma otomatik girer; "Türkçe cevap ver" + kişisel/Jira bilgisi – değer yazılmadı) | 2 dosya |
| `~/.kiro/settings/mcp.json` | 15 MCP sunucusu adı (browserstack, testrail, playwright, selenium, git, memory… ) | değerler okunmadı |
| `~/.agents`, `~/.claude`, `~/.gemini` | Diğer araçların global klasörleri | 3.1 MB / 96 KB / 199 MB |

### Neden workflow/worktree oluşuyor?
- **Workflow'lar:** Kiro runtime'ının yerleşik "workflow" aracı (örn. `investigate`, planner → coder → reviewer). Tanımlar `~/.kiro/sessions/.../workflow-definition.json` içinde, **repoda değil**. Hiçbir steering dosyasında (`~/.kiro/steering/*`, repo `AGENTS.md`, `.agents/rules`) "workflow"/"worktree" kelimesi geçmiyor (`grep -il 'workflow\|worktree' ~/.kiro/steering/*` = 0; ajan JSON'larında `run_workflow`/`worktree` = 0). Yani tetikleyici **repo içindeki bir dosya değil**; Kiro uygulamasının kendi orkestrasyon davranışı görünüyor. *(Bu, çıkarımdır; Kiro'nun iç sistem talimatı okunamaz/doğrulanamaz.)*
- **Worktree'nin nedeni (kanıtlı kısım):** Ana checkout `chore/docs-cloudflare-workers` dalında; iş `development` dalında yapılacaktı. Git aynı dalı iki yerde açmaz, ama farklı dalda olan ana checkout'a dokunmadan çalışmak için `git worktree add` kullanıldı. `scratchpad.md`: *"Ana checkout `chore/docs-cloudflare-workers` dalında, dokunulmadı."* Çıktı: `git worktree list` → iki kayıt.
- **Aynı worktree'ye birden fazla workflow yazıyor:** şu an `git status` şunları gösteriyor: `M AGENTS.md`, `M CHANGELOG.md`, `M README.md`, `M .github/profile/README.md`, `M docs-site/.../changelog.md` (EN+TR), `?? .agents/tasks/`. `tasks/release-v1.0.7-plan.md` başka bir workflow'un ürettiği plan. Yani eşzamanlı ajanlar aynı dizinde çalışıyor – bu karmaşanın asıl kaynağı.

### Hangisi repoya yazılır, hangisi geçici?
| Şey | Repoya yazılır mı |
|---|---|
| Kod/doküman değişiklikleri (commit edilirse) | Evet |
| `.agents/memories/*` | Evet (git'te) |
| `.agents/tasks/*` | Hayır (untracked, ignore'da da değil) |
| workflow tanım/durum dosyaları | Hayır (`~/.kiro`) |
| worktree klasörü | Repo dışı (`testfly-worktrees/`, 570 MB – `docs-site/node_modules` 498 MB + `target` 48 MB); git meta `.git/worktrees/audit-phase1` |

### Push soruna dair (kullanıcının ilk sorusu): 
`git rev-parse origin/development development` → ikisi de `e47e75a418dda3dc8981dce0a35db82dca254f63`; `git status -sb` → `## development...origin/development` (ahead/behind yok). Yani **development'a yapılan commit'ler uzak repoya gitmiş**. Yalnızca commit edilmemiş değişiklikler (yukarıdaki ` M` dosyalar) henüz hiçbir yerde değil.

### Nasıl azaltılır?
1. Worktree işi bitince: önce commit/stash, sonra `git worktree remove /Users/hagul/Projects/TestFramework/testfly-worktrees/audit-phase1` (570 MB). **Şu an uncommitted değişiklikler var → önce kaydet.**
2. Ana checkout'u `development` dalına al ya da worktree yerine tek dizinde çalış; AGENTS.md'ye "worktree açma, mevcut dalda çalış" gibi açık bir satır eklemek ajan davranışını yönlendirir (öneri; etkisi DOĞRULANAMADI).
3. `.agents/tasks/` için `.gitignore` satırı ekle.
4. Gereksiz katmanı (§6) azalt: ajan okunacak yüzey küçülür, "neler gerekli" karmaşası biter.

---

## 8. Kanıtlanamayan / Belirsiz Noktalar (tahmin ≠ bulgu)

1. **Kiro workflow tetikleyicisi:** Hangi talimatın workflow'u başlattığı repoda yok; Kiro'nun dahili davranışı olduğu *çıkarımdır*.
2. **`plugins/testfly-agents` kim tarafından yükleniyor:** Antigravity/Gemini için tasarlanmış gibi görünüyor (araç adları + `soul.md` tag'i `antigravity`) ama yükleyen tüketici bulunamadı. Antigravity'nin gerçekten okuduğu doğrulanmadı.
3. **`docs-site/static/diagrams/*.html`** canlı sitede doğrudan URL ile kullanılıyor olabilir; repo içinde link yok.
4. **`log.md`/wiki sayfalarının ajan tarafından gerçekten okunup okunmadığı:** yazılıyor kanıtı var, okunuyor kanıtı yok.
5. **MAP.md `[[wikilink]]` bütünlüğü:** denediğim hızlı betik güvenilmez çıktı verdi (regex hataları); kırık link sayısı **ölçülmedi**, bu rapordan sayı çıkarmayın.
6. **`docusaurus-config/scripts/validate-config.js`'in fiilen çalıştırıldığı** log'dan doğrulanmadı.
7. **Ana checkout vs worktree farkı:** ana checkout `0c6d612`'de (`chore/docs-cloudflare-workers`); `development` ile farkları: ana checkout'ta `GEMINI.md`, `PRODUCT.md`, `wrangler.jsonc`, `audit/` (10 dosya, 452 KB), `features/`, `docs-agent/` (ignore'lu), `.env` (ignore'lu, içeriğe bakılmadı) var; `skills/testfly-test-authoring` yok. Dalların tam diff'i alınmadı.
8. **`~/.kiro/steering/user-context.md`** kişisel/Jira bilgisi içeriyor (değer rapora yazılmadı); her oturuma otomatik girdiği için paylaşım/ekran görüntüsünde dikkat.

---

## 9. Toplamlar

| Ölçü | Değer |
|---|---|
| `.agents/` dosya sayısı | **261** (260 takipli + 1 untracked `tasks/…`; bu rapor +1) |
| `.agents/` boyutu | **11 MB** (`du -sh`); takipli ≈ 10.9 MB |
| – `skills/archify` | 8.0 MB / 199 dosya (%73) |
| – `wiki` | 2.4 MB / 24 dosya (2.3 MB diagrams) |
| – diğer 5 skill + plugin + kurallar + bellek + MAP + soul + obsidian | ≈ 0.6 MB |
| Kök ajan dosyaları (`AGENTS.md` 19K, `DESIGN.md` 14K, `ROADMAP.md` 12K) | 45 KB |
| `.github/` | 48 KB / 9 takipli · `ci/` 4 KB · `docs/` 640 KB / 11 |
| `docs-site/` | 534 MB (498 MB `node_modules`, 3.9 MB `static/diagrams`) |
| Worktree toplamı | 570 MB |
| `~/.kiro` | 4.0 GB (crew 2.5 GB, extensions 1.4 GB, sessions 140 MB) |
| Workflow kaydı | 25 adet, ~1 MB (repo dışı) |
| Repoda `.kiro`/`.claude`/`.gemini`/`.cursor`/copilot-instructions | **0 dosya** |

### Sonuç
AI-ajan katmanının **hiçbir parçası derleme/test/CI/docs için şart değil**. Ajanla çalışırken pratikte işe yarayan çekirdek: `AGENTS.md`, `MAP.md`, `scratchpad.md`, `git-release-workflow.md`, `skills/testfly`, (isteğe bağlı) wiki md sayfaları. En büyük sadeleştirme fırsatı: `archify` + diyagram HTML'leri + `.obsidian` + kullanılmayan plugin ≈ 10 MB ve ~215 dosya. Karışıklığın ana kaynağı repo değil, Kiro'nun repo dışı workflow/worktree davranışı ve aynı worktree'ye eşzamanlı yazan birden çok workflow.
