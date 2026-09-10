---
name: docusaurus-config
description: >-
  Work with, validate, and modify Docusaurus project configuration (docusaurus.config.js or docusaurus.config.ts).
  Use when configuring or modifying site title, URL, baseUrl, presets, themes, plugins, navbar, footer,
  custom fields, i18n, deployment options, or diagnosing Docusaurus build and configuration errors.
---

# Docusaurus Config

Work with, validate, and modify Docusaurus project configurations safely and deterministically.

## Structure

```text
.agents/skills/docusaurus-config/
├── SKILL.md                          # Main skill instructions & workflows
├── references/
│   └── detailed-guide.md             # Complete reference for Docusaurus configuration options
├── scripts/
│   └── validate-config.js            # Node.js deterministic config validation script
└── assets/
    └── templates/
        ├── docusaurus.config.ts.template  # Modern TypeScript configuration template
        └── docusaurus.config.js.template  # JavaScript (CommonJS / @ts-check) template
```

---

## Quick Start

Configuration lives in `docusaurus.config.js` or `docusaurus.config.ts` at the project root (e.g., `docs-site/docusaurus.config.js` in TestFly).

```typescript
import { Config } from '@docusaurus/types';

const config: Config = {
  title: 'My Site',               // Required
  url: 'https://example.com',     // Required, NO trailing slash
  baseUrl: '/',                   // Required, must start and end with /
  favicon: 'img/favicon.ico',
  organizationName: 'my-org',
  projectName: 'my-project',
  presets: [
    [
      '@docusaurus/preset-classic',
      { /* options */ },
    ],
  ],
  themeConfig: { /* theme config */ },
  customFields: { /* unknown or custom fields go here */ },
};

export default config;
```

---

## Core Principles

1. **Required Fields:** `title`, `url`, and `baseUrl` are strictly mandatory.
2. **URL Constraints:**
   - `url` must **never** end with a trailing slash (`https://example.com` ✅ vs `https://example.com/` ❌).
   - `baseUrl` must start **and** end with `/` (`/testfly/` ✅ vs `testfly` ❌).
3. **Custom Fields:** Docusaurus validates top-level keys. Any unknown or project-specific keys must be placed inside `customFields` to prevent schema validation crashes.
4. **Plugins & Themes:** Use string format or `[name, options]` tuple format.
5. **Format Preservation:** Never convert ESM to CommonJS (or vice versa) without explicit intent. Check existing project format first.

---

## Common Tasks & Workflow

### 1. Before Editing Configuration
- Inspect the existing config file (e.g. `docs-site/docusaurus.config.js`).
- Note whether the project uses TypeScript, ESM (`export default`), or CommonJS (`module.exports = function createConfig()` or `module.exports = config`).

### 2. When Modifying Configuration
- Add or modify fields adhering to Docusaurus types.
- Ensure any arbitrary properties or runtime variables are stored in `customFields`.
- For complex plugin configurations or multi-instance documentation, consult [references/detailed-guide.md](references/detailed-guide.md).

### 3. Validation & Testing
Run the validation script to verify formatting and requirements:

```bash
# Validate TestFly docs site configuration
node .agents/skills/docusaurus-config/scripts/validate-config.js docs-site/docusaurus.config.js

# Or validate default root config
node .agents/skills/docusaurus-config/scripts/validate-config.js
```

Then verify with a test build:
```bash
cd docs-site && npm run build
```

---

## Validation Checklist

When modifying or generating Docusaurus configuration, verify:

- [ ] **Required fields:**
  - `title` is set and non-empty.
  - `url` has no trailing slash.
  - `baseUrl` starts and ends with `/`.
- [ ] **Plugins & Themes:**
  - Official package names or valid shorthands are used.
  - Options are passed as a second array element `['plugin-name', { ... }]`.
  - No duplicate plugin or doc instance IDs.
- [ ] **Custom Data:**
  - Unknown fields are moved to `customFields`.
  - No unsupported root-level keys.
- [ ] **Syntax & Types:**
  - Valid JS/TS syntax without syntax errors.
  - Correct export method matching project structure.
- [ ] **Build Verification:**
  - Validation script passes with 0 errors.
  - `npm run build` succeeds without broken links or compilation errors.

---

## Detailed Reference & Templates

- **Comprehensive Guide:** See [references/detailed-guide.md](references/detailed-guide.md) for multi-instance docs, environment variables, i18n, troubleshooting errors, and API references.
- **Validation Script:** [scripts/validate-config.js](scripts/validate-config.js)
- **Templates:**
  - TypeScript Template: [assets/templates/docusaurus.config.ts.template](assets/templates/docusaurus.config.ts.template)
  - JavaScript Template: [assets/templates/docusaurus.config.js.template](assets/templates/docusaurus.config.js.template)
