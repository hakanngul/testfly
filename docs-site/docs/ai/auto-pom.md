---
id: auto-pom
title: Auto-POM (Learned Page Model)
sidebar_label: Auto-POM (Learned Page Model)
sidebar_position: 3
description: Persistent, self-building Page Object Model knowledge base that turns natural language intent into deterministic actions with zero AI latency.
---

# Auto-POM (Learned Page Model)

Traditional Page Object Models (POM) require QA engineers to manually maintain dozens of Java classes, `@FindBy` annotations, and brittle CSS/XPath locators. On the other hand, naive AI agents query expensive LLM APIs on every single test step, resulting in slow tests (10–30s per step) and unpredictable token costs.

**TestFly Auto-POM** bridges this gap: it autonomously discovers and learns web page elements, relationships, and interaction patterns during test execution, saving them into a persistent, self-building Page Knowledge Base (`.testfly/page-knowledge.json`). 

On subsequent runs—even across completely different test scenarios or written in different words—TestFly resolves natural language goals **locally with 0 ms AI latency and 0 token cost**.

```text
┌────────────────────────────────────────────────────────────────────────┐
│                   Natural Language Goal / Step                         │
│               "Open Bilgilerim from profile menu"                      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
                      ┌───────────────────────────┐
                      │ 1. ActionCache Hit?       │
                      │    (Exact Goal Match)     │
                      └─────────────┬─────────────┘
                        Hit (Yes)   │    Miss (No)
            ┌───────────────────────┴────────────────────────┐
            ▼                                                ▼
┌───────────────────────┐                        ┌───────────────────────────┐
│ Deterministic Replay  │                        │ 2. Auto-POM Knowledge Hit?│
│       < 10ms          │                        │ (LocalIntentResolver)     │
└───────────────────────┘                        └─────────────┬─────────────┘
                                                   Hit (Yes)   │    Miss (No)
                                       ┌───────────────────────┴─────────────┐
                                       ▼                                     ▼
                           ┌───────────────────────┐             ┌───────────────────────┐
                           │ Local Auto-POM Plan   │             │ 3. AI Provider (LLM)  │
                           │ Hover #profile-nav    │             │   Compile via DeepSeek│
                           │ Click a[href*='...']  │             └───────────┬───────────┘
                           │ 0 ms / 0 Token Cost   │                         │
                           └───────────────────────┘                         ▼
                                                                 ┌───────────────────────┐
                                                                 │   KnowledgeLearner    │
                                                                 │ (Save to Auto-POM)    │
                                                                 └───────────────────────┘
```

---

## How It Works

### 1. Two-Tier Local Resolution Before Calling AI

When `act(goal)` or Cucumber step `the agent executes goal "..."` runs:

1. **Tier 1: ActionCache (Compile & Freeze):**
   If the exact same sentence was previously frozen for this URL pattern, TestFly replays the cached steps directly.
2. **Tier 2: Auto-POM (`LocalIntentResolver`):**
   If the goal is worded differently (e.g. `"Click Bilgilerim"` vs `"Open Bilgilerim from user profile menu"` vs `"Profil menüsünden Bilgilerim sayfasına git"`), TestFly queries the **Learned Page Model** for the current URL.
   - If the elements and interaction triggers are recognized, TestFly synthesizes the `ActionPlan` **entirely locally without making any LLM network request**.
3. **Tier 3: LLM Compiler (`KnowledgeLearner`):**
   If the element or goal is brand new, TestFly invokes the configured AI provider. When the AI returns the executable actions, `KnowledgeLearner` automatically extracts the discovered elements and records them into `.testfly/page-knowledge.json`.

---

## Page Knowledge Base Schema

Learned elements and page models are saved under `.testfly/page-knowledge.json`:

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

### Key Properties

| Property | Description |
| :--- | :--- |
| `name` | Canonical semantic identifier (e.g. `username`, `profile_menu`, `bilgilerim`). |
| `locator` | Valid CSS selector or XPath verified against the live DOM. |
| `actionType` | Primary action type (`CLICK`, `TYPE`, `HOVER`). |
| `parentTrigger` | Name of parent container that must be interacted with first (e.g. hover to reveal dropdown). |
| `synonyms` | Multi-lingual semantic aliases matched by `LocalIntentResolver`. |

---

## Supported Interaction Patterns

### 1. Dropdown & Hover Menus
When an element specifies a `parentTrigger` (such as `profile_menu` for `bilgilerim`), `LocalIntentResolver` automatically prepends a `HOVER` step on the parent before clicking the leaf element:

```gherkin
# Executed via Auto-POM without querying the LLM:
When the agent executes goal "Click Bilgilerim from user profile menu"

# Generated Plan:
# Step 1 [HOVER]: Hover over profile_menu trigger (#profile-nav)
# Step 2 [CLICK]: Click Bilgilerim link (a[href='/tr/hesabim/bilgilerim'])
```

### 2. Multi-Input Form Submissions
Form goals with quoted arguments extract values and match inputs via learned roles:

```gherkin
When the agent executes goal "Enter username 'admin' and password 'secret', then click Login"

# Generated Plan:
# Step 1 [TYPE]: Type 'admin' into #userNameLP
# Step 2 [TYPE]: Type 'secret' into #realpassLP
# Step 3 [CLICK]: Click submit button #btnLoginSubmitLP
```

---

## Continuous Learning & Self-Healing

1. **Zero-Maintenance Knowledge Enrichment:**  
   Every time the LLM compiles a new action plan for an unlearned feature, `KnowledgeLearner` inspects the plan's locators and descriptions, automatically updating `.testfly/page-knowledge.json`.
2. **Resilience to UI Refactoring:**  
   If a learned locator fails due to a UI redesign, TestFly invalidates that specific element in the cache, re-prompts the AI to rediscover the element, updates the knowledge base, and continues execution.
