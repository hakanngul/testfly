---
id: ide-plugins
title: IDE Eklentileri (IntelliJ IDEA & VS Code)
sidebar_label: IDE Eklentileri (IDEA & VS Code)
sidebar_position: 3
description: JetBrains AI Assistant, Claude Code ve GitHub Copilot'ı TestFly ile sıfır konfigürasyonla bağlayan resmi IDE eklentileri.
---

# IDE Eklentileri (IntelliJ IDEA & VS Code)

TestFly, hem **IntelliJ IDEA** hem de **Visual Studio Code** için yapay zeka entegrasyonunu, ortam kontrolünü ve proje başlatma işlemlerini otomatikleştiren resmi eklentiler sunar.

---

## 1. IntelliJ IDEA Eklentisi (`testfly-mcp-jetbrains`)

IntelliJ IDEA eklentisi, TestFly'ı **JetBrains AI Assistant** ile entegre eder.

### Öne Çıkan Özellikler
- **Sıfır Konfigürasyonla AI Kaydı:** `testfly-mcp` sunucusunu `aiAssistantMcpServers.xml` dosyasına otomatik ekler.
- **Özel Menü:** Üst menü çubuğuna **`Tools → TestFly MCP`** menüsünü ekler.
- **Teşhis (Diagnostics):** Python, Selenium ve AI Assistant bağlantı durumunu tek tıkla doğrular.
- **Stüdyo Başlatıcı:** **Etkileşimli Web Stüdyosu**'nu (`testfly-mcp ui`) tek tıkla açar.
- **Şablon Üretici:** Proje kök dizinine kurumsal standartlarda bir `testfly.yml` dosyası oluşturur.

### Kurulum Adımları
1. Dağıtım zip dosyasını bulun:
   ```text
   testfly-mcp/jetbrains-plugin/build/distributions/testfly-mcp-jetbrains-1.0.0.zip
   ```
2. IntelliJ IDEA'da **Settings / Preferences** (`Cmd + ,` macOS, `Ctrl + Alt + S` Windows/Linux) penceresini açın.
3. Sol menüden **Plugins** seçeneğini tıklayın.
4. Sağ üstteki **Dişli çark (⚙️)** ikonuna tıklayıp **Install Plugin from Disk...** deyin.
5. `testfly-mcp-jetbrains-1.0.0.zip` dosyasını seçip **OK** deyin.
6. İstendiğinde IDE'yi yeniden başlatın (**Restart IDE**).

---

## 2. Visual Studio Code Eklentisi (`testfly-mcp`)

VS Code eklentisi, TestFly'ı **Claude Code** ve **GitHub Copilot** ile entegre eder.

### Öne Çıkan Özellikler
- **Durum Çubuğu Öğesi:** Sol alt köşede `$(radio-tower) TestFly MCP` göstergesi yer alır.
- **Hızlı İşlem Menüsü (QuickPick):** Durum çubuğuna tıklandığında teşhis yapma, AI kaydı, stüdyoyu başlatma veya `testfly.yml` üretme seçeneklerini sunar.
- **Otomatik Kayıt:** `~/.claude/settings.json` ve çalışma alanı `.mcp.json` dosyalarını otomatik günceller.
- **Pip Yönetimi:** Terminalde tek tıkla `pip install --upgrade testfly-mcp` çalıştırır.

### Kurulum Adımları
1. Hazırlanan VSIX dosyasını bulun:
   ```text
   testfly-mcp/vscode-extension/testfly-mcp-1.0.0.vsix
   ```
2. VS Code'da **Extensions** sekmesini açın (`Cmd + Shift + X`).
3. Sağ üstteki **üç nokta (`...`)** menüsünden **Install from VSIX...** seçeneğini tıklayın.
4. `testfly-mcp-1.0.0.vsix` dosyasını seçin.
5. Eklenti anında aktifleşecektir.

---

## 3. Bağlantının Doğrulanması

Kurulum tamamlandıktan sonra:
1. IDE'nizdeki AI chat panelini açın (JetBrains AI Assistant veya Claude / Copilot).
2. Şu prompt'u girin:
   > *"TestFly için hangi MCP araçlarına sahipsin?"*
3. AI asistanınız TestFly araçlarını (`start_browser`, `navigate`, `generate_java_page_object` vb.) listeliyorsa bağlantı başarıyla kurulmuş demektir.
