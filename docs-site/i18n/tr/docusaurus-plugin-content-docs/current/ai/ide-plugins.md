---
id: ide-plugins
title: IDE Eklentileri & Stüdyo
sidebar_label: IDE Eklentileri (VS Code & IntelliJ)
sidebar_position: 3
description: VS Code ve IntelliJ IDEA için etkileşimli kenar çubukları, Action Cache gezginleri ve AI self-healing yama inceleyicileri içeren görsel eklentiler.
---

# IDE Eklentileri & Stüdyo Uzantıları

TestFly, geliştirme ortamınızı interaktif bir yapay zeka test stüdyosuna dönüştürmek için hem **Visual Studio Code** hem de **JetBrains IDE'leri (IntelliJ IDEA, Aqua)** için resmi eklentiler sunar.

Sıfır Python. Yerel IDE performansı.

---

## 1. Visual Studio Code Eklentisi (`testfly-vscode`)

**TestFly Studio** eklentisi TestFly'ı **Cursor**, **GitHub Copilot** ve **Claude Desktop** ile entegre eder.

```
┌────────────────────────────────────────────────────────┐
│ TESTFLY STUDIO (Activity Bar)                          │
│                                                        │
│ ▶ Hızlı Aksiyonlar (Quick Actions)                     │
│   • ⚡ 1-Click MCP Setup (Cursor, Claude, Copilot)     │
│   • 🎯 Open Action Cache Explorer                      │
│   • 🩹 AI Self-Healing Patch Reviewer                  │
│   • ⚙️ Visual testfly.yml Editor                       │
│                                                        │
│ ▶ Ortam ve Proje Durumu                                │
│   • JDK 21+: Hazır                                     │
│   • Maven: Hazır                                       │
│   • testfly.yml: Mevcut (✓)                            │
│   • Action Cache: 3 hedef önbellekte                   │
│   • Self-Healing Yamaları: 1 bekleyen                  │
└────────────────────────────────────────────────────────┘
```

### Öne Çıkan Yetenekler
- **⚡ Tek Tıkla Çoklu Asistan Kurulumu:** Playwright MCP ve TestFly Köprüsünü; Cursor (`~/.cursor/mcp.json`), Claude Desktop (`claude_desktop_config.json`), VS Code Native (`.vscode/mcp.json`) ve Claude Code için tek tıkla yapılandırır.
- **🎯 Action Cache Explorer Webview:** Derlenmiş tüm `act("Hedef")` planlarını, adımları, lokatörleri ve zaman damgalarını görsel kartlar halinde listeler. Tek tıkla hedef önbelleğini sıfırlamanızı sağlar.
- **🩹 AI Self-Healing Patch Reviewer Webview:** `AiHealingEngine` tarafından onarılan kırık lokatörlerin `.patch` dosyalarını renkli diff formatında açar ve **"Apply Patch to Java Code"** butonuyla koda anında uygular.
- **⚙️ Görsel `testfly.yml` Editörü:** Zaman aşımları, paralel thread sayısı ve Yapay Zeka Sağlayıcısı (Gemini, Claude, OpenAI) ayarlarını form üzerinden düzenleme imkanı sunar.

### Paketleme ve Kurulum
En güncel VSIX paketini kaynak koddan derlemek için:
```bash
cd testfly-mcp/vscode-extension
npx @vscode/vsce package
```
VS Code içinde:
1. Extensions görünümünü açın (`Cmd + Shift + X` / `Ctrl + Shift + X`).
2. Sağ üstteki `...` menüsüne tıklayın.
3. **Install from VSIX...** seçeneğini seçip `testfly-vscode-1.1.0.vsix` dosyasını seçin.

---

## 2. IntelliJ IDEA Eklentisi (`testfly-mcp-jetbrains`)

JetBrains IDE'leri için geliştirilen **TestFly Studio** eklentisi, VS Code eklentisindeki tüm yetenekleri IntelliJ'nin sağ şeridinde çalışan **etkileşimli bir "Tool Window" (Yan Panel)** olarak sunar.

### Etkileşimli Yan Panel Sekmeleri
1. **📊 Dashboard Sekmesi:**
   - Gerçek zamanlı ortam ve proje durumu rozetleri (`testfly.yml`, `pom.xml`, Java 21, MCP kaydı).
   - Hızlı butonlar: Tek Tıkla MCP Kurulumu, `testfly.yml` Oluşturma, Yenileme ve Dokümantasyon.
2. **⚡ Action Cache Sekmesi:**
   - `.testfly/action-cache.json` içindeki otonom `act()` planlarını listeler.
   - Sonraki test koşumunda yapay zekanın hedefi yeniden derlemesini sağlamak için **Invalidate Goal** butonu.
   - Tüm önbelleği silmek için **Clear All** butonu.
3. **🩹 AI Patches Sekmesi:**
   - `target/remediations/` altındaki bekleyen onarım `.patch` dosyalarını listeler.
   - Kod değişikliklerini gösteren dahili diff görüntüleyici.
   - IDE içinde doğrudan `git apply` çalıştıran **`[ ✅ Apply Patch to Java Code ]`** butonu.

### Paketleme ve Kurulum
Eklenti paketini kaynak koddan derlemek için:
```bash
cd testfly-mcp/jetbrains-plugin
./gradlew buildPlugin
```
IntelliJ IDEA / Aqua içinde:
1. **Settings / Preferences** menüsünü açın (`Cmd + ,` macOS / `Ctrl + Alt + S` Windows/Linux).
2. **Plugins** sekmesine gidin.
3. Sağ üstteki **Dişli çarka (⚙️)** tıklayıp **Install Plugin from Disk...** seçeneğini seçin.
4. `testfly-mcp/jetbrains-plugin/build/distributions/testfly-mcp-jetbrains-1.1.0.zip` dosyasını seçin.
5. IDE'yi yeniden başlatın. Sağ kenar şeridinde **TestFly** paneli belirecektir.
