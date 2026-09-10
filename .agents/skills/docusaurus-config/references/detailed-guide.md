# Docusaurus Configuration — Detailed Guide

This guide provides an in-depth reference for configuring, validating, and troubleshooting Docusaurus projects.

---

## Configuration File Structure

Docusaurus configuration can be defined in multiple formats located at the root of the Docusaurus project (e.g. `docusaurus.config.js` or `docusaurus.config.ts`):

### 1. TypeScript (Recommended)

Provides complete autocomplete and compile-time type checking.

```typescript
import { Config } from '@docusaurus/types';
import { themes as prismThemes } from 'prism-react-renderer';

const config: Config = {
  title: 'My Site',
  url: 'https://example.com',
  baseUrl: '/',
  // Configuration here
};

export default config;
```

### 2. JavaScript (ESM)

For projects using ECMAScript modules (`"type": "module"` in `package.json`).

```javascript
export default {
  title: 'My Site',
  url: 'https://example.com',
  baseUrl: '/',
  // Configuration here
};
```

### 3. JavaScript (CommonJS)

Standard Node.js module export, supported out of the box in all Docusaurus setups.

```javascript
// @ts-check
const { themes } = require('prism-react-renderer');

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'My Site',
  url: 'https://example.com',
  baseUrl: '/',
  // Configuration here
};

module.exports = config;
```

### 4. Async Configuration Function

Useful when dynamically loading ESM packages or fetching remote configurations during build.

```typescript
import { Config } from '@docusaurus/types';

export default async function createConfig(): Promise<Config> {
  // Import ESM-only packages dynamically if required
  const mdxMermaid = await import('mdx-mermaid');

  return {
    title: 'My Site',
    url: 'https://example.com',
    baseUrl: '/',
    // Configuration here
  };
}
```

---

## Required Fields

Every Docusaurus site configuration must contain these three core fields:

| Field | Type | Format / Constraints | Description |
|---|---|---|---|
| `title` | `string` | Non-empty string | Site title for metadata, SEO, and browser tab display. |
| `url` | `string` | Must **NOT** end with a trailing slash (`/`). Example: `'https://example.com'` | Full URL where the production site is hosted. |
| `baseUrl` | `string` | Must start **AND** end with a forward slash (`/`). Example: `'/'` or `'/docs/'` | Path from root where site is served. |

---

## Common Optional Fields

### Site Metadata

```javascript
tagline: 'Short description of your site',
favicon: 'img/favicon.ico', // Relative to static folder
organizationName: 'my-org', // GitHub org/user name (for deployment)
projectName: 'my-project',  // GitHub repo name (for deployment)
```

### Deployment

```javascript
deploymentBranch: 'gh-pages', // Branch for deployment (default: 'gh-pages')
trailingSlash: false,        // Boolean or undefined for trailing slash handling
```

### Internationalization (i18n)

```javascript
i18n: {
  defaultLocale: 'en',
  locales: ['en', 'tr', 'fr', 'es'],
  localeConfigs: {
    en: { label: 'English' },
    tr: { label: 'Türkçe' },
  },
}
```

### Themes & Presets Configuration

#### Using Presets (Recommended)
Presets bundle docs, blog, and theme options together cleanly:

```javascript
presets: [
  [
    '@docusaurus/preset-classic',
    {
      docs: {
        sidebarPath: './sidebars.js',
        editUrl: 'https://github.com/user/repo/tree/main/',
      },
      blog: {
        showReadingTime: true,
      },
      theme: {
        customCss: './src/css/custom.css',
      },
    },
  ],
],
```

#### Direct Theme Configuration (`themeConfig`)

```javascript
themes: ['@docusaurus/theme-classic'],
themeConfig: {
  navbar: {
    title: 'My Site',
    logo: {
      alt: 'My Site Logo',
      src: 'img/logo.svg',
    },
    items: [
      { to: '/docs/intro', label: 'Docs', position: 'left' },
      { to: '/blog', label: 'Blog', position: 'left' },
      { href: 'https://github.com/user/repo', label: 'GitHub', position: 'right' },
    ],
  },
  footer: {
    style: 'dark',
    links: [
      {
        title: 'Docs',
        items: [{ label: 'Getting Started', to: '/docs/intro' }],
      },
    ],
    copyright: `Copyright © ${new Date().getFullYear()} My Project, Inc.`,
  },
}
```

### Plugins

Plugins extend documentation, search, Google Analytics, or external integrations.

#### Formats

1. **String format (no options):**
   ```javascript
   plugins: ['@docusaurus/plugin-debug'],
   ```

2. **Array format (with options):**
   ```javascript
   plugins: [
     [
       '@docusaurus/plugin-content-docs',
       {
         id: 'community',
         path: 'community',
         routeBasePath: 'community',
       },
     ],
   ],
   ```

3. **Shorthand Notation:**
   Official Docusaurus packages can omit prefix:
   - `'classic'` → `'@docusaurus/preset-classic'`
   - `'plugin-debug'` → `'@docusaurus/plugin-debug'`

### Custom Fields (`customFields`)

Docusaurus validates top-level configuration fields. Unknown or arbitrary root properties will throw validation errors. Always store custom application data inside `customFields`:

```javascript
customFields: {
  apiKey: process.env.API_KEY,
  customValue: 'my-value',
  complexData: {
    nested: true,
  },
}
```

#### Accessing Custom Fields in Components

```jsx
import React from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';

export default function MyComponent() {
  const { siteConfig } = useDocusaurusContext();
  const apiKey = siteConfig.customFields?.apiKey;
  return <div>API Key: {apiKey}</div>;
}
```

---

## Validation Checklist

Before committing or testing a Docusaurus configuration change, verify:

- [ ] **Required fields present:**
  - `title` is a non-empty string.
  - `url` exists and does **not** end with a trailing slash (`/`).
  - `baseUrl` exists and starts **and** ends with a slash (e.g. `'/'` or `'/testfly/'`).
- [ ] **Plugins and Themes:**
  - Valid package names or official shorthands are used.
  - Options are passed as the 2nd element of a `[name, options]` tuple.
  - No duplicate plugins or docs IDs.
- [ ] **Custom Data:**
  - Any custom metadata or environment configs reside in `customFields`.
  - No unsupported arbitrary properties at the root level.
- [ ] **File Format & Syntax:**
  - Valid JS or TS syntax.
  - Export matches project module type (ESM `export default` or CommonJS `module.exports`).
  - Required `@docusaurus/types` dependency is available when using TS / `@ts-check`.
- [ ] **Build & Runtime Verification:**
  - Restart local dev server (`npm start` / `npm run start`).
  - Production build succeeds without errors (`npm run build`).
  - No broken links reported (`onBrokenLinks: 'throw'`).

---

## Common Patterns

### 1. Multi-Instance Docs

Host multiple independent documentation sets (e.g. SDK docs and community/guides):

```javascript
plugins: [
  [
    '@docusaurus/plugin-content-docs',
    {
      id: 'product',
      path: 'product',
      routeBasePath: 'product',
      sidebarPath: './sidebarsProduct.js',
    },
  ],
  [
    '@docusaurus/plugin-content-docs',
    {
      id: 'community',
      path: 'community',
      routeBasePath: 'community',
      sidebarPath: './sidebarsCommunity.js',
    },
  ],
],
```

### 2. Environment Variables

```javascript
const isDeployPreview = process.env.CONTEXT === 'deploy-preview';

const config = {
  url: process.env.SITE_URL || 'https://localhost:3000',
  baseUrl: isDeployPreview ? '/' : '/testfly/',
  customFields: {
    apiEndpoint: process.env.API_ENDPOINT,
  },
};
```

### 3. Babel Customization (`babel.config.js`)

If customizing Babel presets or plugins:

```javascript
module.exports = {
  presets: [require.resolve('@docusaurus/babel/preset')],
  plugins: [
    // Custom Babel plugins
  ],
};
```

---

## Troubleshooting

### Common Errors & Solutions

| Error Message | Cause | Resolution |
|---|---|---|
| `"url must not have a trailing slash"` | `url` property ends with `/` (e.g. `'https://example.com/'`) | Remove the trailing slash: `url: 'https://example.com'` |
| `"baseUrl must start and end with /"` | `baseUrl` missing leading or trailing slash | Format correctly: `baseUrl: '/docs/'` or `'/'` |
| `"Unknown field 'myField'"` | An unrecognized key was placed at the config root | Move the key into `customFields: { myField: 'value' }` |
| `"Cannot find module '@docusaurus/types'"` | TypeScript definitions not installed | Run `npm install --save-dev @docusaurus/types` |
| Broken link errors on build | Dead internal markdown links | Update markdown links or adjust `onBrokenLinks: 'warn'` |

---

## Best Practices

1. **Use TypeScript or `@ts-check`**: Catches misspellings and invalid keys before building.
2. **Read Existing Config First**: Match the existing module syntax (CommonJS vs. ESM) and preserve existing theme customizations.
3. **Preserve Path Formatting**: Ensure GitHub Pages / sub-path deployments have matching `baseUrl` and asset paths.
4. **Isolate Custom Settings**: Keep project-specific parameters inside `customFields` to prevent configuration schema validation failures.
5. **Always Test Both Dev & Build**: A config might work in dev mode (`npm start`) but fail strict checks during static build (`npm run build`).

---

## Additional Resources

- [Official Docusaurus Configuration API](https://docusaurus.io/docs/api/docusaurus-config)
- [Plugin Configuration Documentation](https://docusaurus.io/docs/using-plugins)
- [Theme Configuration Documentation](https://docusaurus.io/docs/using-themes)
- [Deployment Guide](https://docusaurus.io/docs/deployment)
