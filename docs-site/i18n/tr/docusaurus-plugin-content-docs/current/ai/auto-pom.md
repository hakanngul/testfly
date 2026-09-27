---
id: auto-pom
title: Auto-POM (Öğrenilmiş Sayfa Modeli)
sidebar_label: Auto-POM (Öğrenilmiş Sayfa Modeli)
sidebar_position: 3
description: Doğal dil hedeflerini sıfır AI gecikmesiyle deterministik aksiyonlara dönüştüren kalıcı, kendi kendini inşa eden Page Object Model bilgi tabanı.
---

# Auto-POM (Öğrenilmiş Sayfa Modeli)

Geleneksel Page Object Model (POM) yaklaşımları, QA mühendislerinin onlarca Java sınıfını, `@FindBy` anotasyonlarını ve kırılgan CSS/XPath seçicilerini manuel olarak yönetmesini gerektirir. Öte yandan, yalın AI test ajanları her test adımında pahalı LLM API'lerine çağrı yaparak testlerin aşırı yavaşlamasına (adım başı 10–30 sn) ve öngörülemeyen token maliyetlerine yol açar.

**TestFly Auto-POM**, bu iki dünyanın en iyi yönlerini birleştirir: Test koşumu sırasında web sayfalarındaki elementleri, aralarındaki hiyerarşik ilişkileri ve etkileşim kalıplarını otonom olarak öğrenir ve kalıcı bir Sayfa Bilgi Tabanına (`.testfly/page-knowledge.json`) kaydeder.

Sonraki test koşumlarında — testler tamamen farklı senaryolarda veya farklı cümlelerle yazılmış olsa dahi — TestFly doğal dil hedeflerini **yerel olarak, 0 ms AI gecikmesi ve 0 token maliyetiyle** çözer.

```text
┌────────────────────────────────────────────────────────────────────────┐
│                   Doğal Dil Hedefi / Test Adımı                        │
│               "Open Bilgilerim from profile menu"                      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
                      ┌───────────────────────────┐
                      │ 1. ActionCache Eşleşti mi?│
                      │    (Birebir Hedef Cümlesi)│
                      └─────────────┬─────────────┘
                        Evet (Hit)  │    Hayır (Miss)
            ┌───────────────────────┴────────────────────────┐
            ▼                                                ▼
┌───────────────────────┐                        ┌───────────────────────────┐
│ Deterministik Replay  │                        │ 2. Auto-POM Bilgi İsabeti?│
│       < 10ms          │                        │ (LocalIntentResolver)     │
└───────────────────────┘                        └─────────────┬─────────────┘
                                                   Evet (Hit)  │    Hayır (Miss)
                                       ┌───────────────────────┴─────────────┐
                                       ▼                                     ▼
                           ┌───────────────────────┐             ┌───────────────────────┐
                           │ Yerel Auto-POM Planı  │             │ 3. AI Sağlayıcı (LLM) │
                           │ Hover #profile-nav    │             │   DeepSeek ile Derle  │
                           │ Click a[href*='...']  │             └───────────┬───────────┘
                           │ 0 ms / 0 Token Maliyet│                         │
                           └───────────────────────┘                         ▼
                                                                 ┌───────────────────────┐
                                                                 │   KnowledgeLearner    │
                                                                 │ (Auto-POM'a Kaydet)   │
                                                                 └───────────────────────┘
```

---

## Nasıl Çalışır?

### 1. AI Çağrısı Öncesi İki Kademeli Yerel Çözümleme

`act(goal)` metodu veya Cucumber adımı `the agent executes goal "..."` çalıştığında:

1. **1. Kademe: ActionCache (Compile & Freeze):**
   Aynı hedef cümlesi bu URL deseni için daha önce dondurulmuşsa, TestFly dondurulan adımları doğrudan tekrar çalıştırır.
2. **2. Kademe: Auto-POM (`LocalIntentResolver`):**
   Hedef cümle farklı ifade edilmiş olsa bile (örneğin `"Click Bilgilerim"`, `"Open Bilgilerim from user profile menu"` veya `"Profil menüsünden Bilgilerim sayfasına git"`), TestFly mevcut URL'in **Öğrenilmiş Sayfa Modeline** bakar.
   - İlgili elementler ve açılır menü kuralları tanınıyorsa, TestFly aksiyon planını **hiçbir LLM ağ isteği yapmadan tamamen yerel olarak** üretir.
3. **3. Kademe: LLM Derleyici (`KnowledgeLearner`):**
   Hedef veya element tamamen yeniyse, yapılandırılan AI sağlayıcısı devreye girer. AI aksiyonları ürettiğinde `KnowledgeLearner`, keşfedilen yeni elementleri otomatik olarak çıkarıp `.testfly/page-knowledge.json` dosyasına işler.

---

## Sayfa Bilgi Tabanı Şeması

Öğrenilen elementler ve sayfa modelleri `.testfly/page-knowledge.json` dosyası altında tutulur:

```json
{
  "/tr/uyelik/giris": {
    "urlPattern": "/tr/uyelik/giris",
    "pageName": "Customer LoginPage",
    "elements": {
      "username": {
        "name": "username",
        "locator": "#userNameLP",
        "actionType": "TYPE",
        "parentTrigger": null,
        "synonyms": ["kullanıcı adı", "username", "e-posta", "eposta"]
      },
      "password": {
        "name": "password",
        "locator": "#realpassLP",
        "actionType": "TYPE",
        "parentTrigger": null,
        "synonyms": ["şifre", "password", "parola"]
      },
      "submit_button": {
        "name": "submit_button",
        "locator": "#btnLoginSubmitLP",
        "actionType": "CLICK",
        "parentTrigger": null,
        "synonyms": ["giriş yap", "login", "giriş", "submit"]
      }
    }
  },
  "/": {
    "urlPattern": "/",
    "pageName": "Customer HomePage",
    "elements": {
      "profile_menu": {
        "name": "profile_menu",
        "locator": "#profile-nav",
        "actionType": "HOVER",
        "parentTrigger": null,
        "synonyms": ["profil", "hesabım", "kullanıcı menüsü", "user profile", "account"]
      },
      "bilgilerim": {
        "name": "bilgilerim",
        "locator": "a[href='/tr/hesabim/bilgilerim']",
        "actionType": "CLICK",
        "parentTrigger": "profile_menu",
        "synonyms": ["bilgilerim", "kişisel bilgiler", "hesap bilgileri", "bilgiler"]
      }
    }
  }
}
```

### Alan Açıklamaları

| Alan | Açıklama |
| :--- | :--- |
| `name` | Elementin standart anlamsal adı (örn: `username`, `profile_menu`, `bilgilerim`). |
| `locator` | Canlı DOM üzerinde doğrulanmış geçerli CSS seçici veya XPath ifadesi. |
| `actionType` | Temel aksiyon tipi (`CLICK`, `TYPE`, `HOVER`). |
| `parentTrigger` | Öncesinde etkileşime girilmesi gereken üst konteynerin adı (örn: açılır menü için hover elementi). |
| `synonyms` | `LocalIntentResolver` tarafından eşleştirilen çok dilli eşanlamlı kelimeler listesi. |

---

## Desteklenen Etkileşim Modelleri

### 1. Açılır ve Üzerine Gelinen (Hover Dropdown) Menüler
Bir element `parentTrigger` belirttiğinde (örneğin `bilgilerim` için `profile_menu`), `LocalIntentResolver` hedef elemente tıklamadan önce otomatik olarak üst element üzerine `HOVER` adımını ekler:

```gherkin
# LLM'e hiç sorulmadan Auto-POM üzerinden çalıştırılır:
When the agent executes goal "Click Bilgilerim from user profile menu"

# Üretilen Plan:
# Adım 1 [HOVER]: Hover over profile_menu trigger (#profile-nav)
# Adım 2 [CLICK]: Execute bilgilerim on Customer HomePage (a[href='/tr/hesabim/bilgilerim'])
```

### 2. Çoklu Form Girişleri
Tırnak içindeki argümanları barındıran form hedefleri, değerleri ayrıştırır ve sayfadaki ilgili alanlarla eşleştirir:

```gherkin
When the agent executes goal "Enter username 'admin' and password 'secret', then click Login"

# Üretilen Plan:
# Adım 1 [TYPE]: Type 'admin' into #userNameLP
# Adım 2 [TYPE]: Type 'secret' into #realpassLP
# Adım 3 [CLICK]: Click submit button #btnLoginSubmitLP
```

---

## Sürekli Öğrenme ve Kendi Kendini Onarma

1. **Sıfır Bakımla Zenginleşen Hafıza:**  
   LLM yeni bir senaryo için aksiyon planı ürettiğinde, `KnowledgeLearner` planın adımlarını inceler ve `.testfly/page-knowledge.json` dosyasını otomatik olarak günceller.
2. **Arayüz Değişikliklerine Direnç (Self-Healing):**  
   Arayüz güncellemesi sebebiyle öğrenilmiş bir seçici başarısız olursa, TestFly ilgili element önbelleğini geçersiz kılar, AI'dan elementi yeniden bulmasını ister, bilgi tabanını günceller ve teste kaldığı yerden devam eder.
