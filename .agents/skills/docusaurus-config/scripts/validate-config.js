#!/usr/bin/env node

/**
 * Validation script for Docusaurus configuration files.
 *
 * Usage:
 *   node validate-config.js [path/to/docusaurus.config.js]
 */

const fs = require('fs');
const path = require('path');

const KNOWN_CONFIG_KEYS = new Set([
  'title',
  'url',
  'baseUrl',
  'tagline',
  'favicon',
  'organizationName',
  'projectName',
  'deploymentBranch',
  'githubHost',
  'githubPort',
  'trailingSlash',
  'presets',
  'themes',
  'plugins',
  'themeConfig',
  'customFields',
  'headTags',
  'stylesheets',
  'scripts',
  'clientModules',
  'ssrTemplate',
  'titleDelimiter',
  'noIndex',
  'onBrokenLinks',
  'onBrokenMarkdownLinks',
  'onDuplicateRoutes',
  'baseUrlIssueBanner',
  'i18n',
  'staticDirectories',
  'future',
]);

function findDefaultConfigFile() {
  const candidates = [
    path.resolve(process.cwd(), 'docs-site/docusaurus.config.js'),
    path.resolve(process.cwd(), 'docs-site/docusaurus.config.ts'),
    path.resolve(process.cwd(), 'docusaurus.config.js'),
    path.resolve(process.cwd(), 'docusaurus.config.ts'),
  ];

  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return null;
}

function loadConfig(configPath) {
  const absolutePath = path.resolve(configPath);
  if (!fs.existsSync(absolutePath)) {
    throw new Error(`Config file not found at: ${absolutePath}`);
  }

  // Attempt to require directly if CommonJS
  try {
    const loaded = require(absolutePath);
    let resolvedConfig = loaded;
    if (typeof loaded === 'function') {
      resolvedConfig = loaded();
    } else if (loaded && loaded.default) {
      if (typeof loaded.default === 'function') {
        resolvedConfig = loaded.default();
      } else {
        resolvedConfig = loaded.default;
      }
    }
    if (resolvedConfig && typeof resolvedConfig === 'object') {
      return { config: resolvedConfig, rawContent: fs.readFileSync(absolutePath, 'utf8') };
    }
  } catch (err) {
    // If require fails (e.g. TypeScript or ESM without transpile), parse statically
  }

  const rawContent = fs.readFileSync(absolutePath, 'utf8');
  return { config: null, rawContent };
}

function extractRegex(content, regex) {
  const match = content.match(regex);
  return match ? match[1] : null;
}

function validate(configPath) {
  console.log(`\n🔍 Inspecting Docusaurus config: ${configPath}\n`);

  let loaded;
  try {
    loaded = loadConfig(configPath);
  } catch (e) {
    console.error(`❌ Error reading file: ${e.message}`);
    process.exit(1);
  }

  const { config, rawContent } = loaded;
  const errors = [];
  const warnings = [];

  let title = config ? config.title : extractRegex(rawContent, /title:\s*['"`](.*?)['"`]/);
  let url = config ? config.url : extractRegex(rawContent, /url:\s*['"`](.*?)['"`]/);
  let baseUrl = config ? config.baseUrl : extractRegex(rawContent, /baseUrl:\s*['"`](.*?)['"`]/);

  // 1. Validate 'title'
  if (!title) {
    errors.push("'title' is required and must be a non-empty string.");
  } else {
    console.log(`✅ Title: "${title}"`);
  }

  // 2. Validate 'url'
  if (!url) {
    errors.push("'url' is required (e.g. 'https://example.com').");
  } else {
    if (url.endsWith('/')) {
      errors.push(`'url' must NOT end with a trailing slash. Found: "${url}" (Fix: "${url.replace(/\/+$/, '')}")`);
    } else if (!url.startsWith('http://') && !url.startsWith('https://')) {
      warnings.push(`'url' typically starts with http:// or https://. Found: "${url}"`);
    } else {
      console.log(`✅ URL: "${url}" (valid, no trailing slash)`);
    }
  }

  // 3. Validate 'baseUrl'
  if (!baseUrl) {
    errors.push("'baseUrl' is required (e.g. '/' or '/docs/').");
  } else {
    if (!baseUrl.startsWith('/') || !baseUrl.endsWith('/')) {
      errors.push(`'baseUrl' must both start AND end with '/'. Found: "${baseUrl}"`);
    } else {
      console.log(`✅ BaseURL: "${baseUrl}" (valid format)`);
    }
  }

  // 4. Validate top-level keys if object was successfully evaluated
  if (config) {
    const keys = Object.keys(config);
    for (const key of keys) {
      if (!KNOWN_CONFIG_KEYS.has(key)) {
        warnings.push(`Unknown root field '${key}'. Custom fields should be placed inside 'customFields: { ${key}: ... }' to prevent validation issues.`);
      }
    }

    if (config.plugins && Array.isArray(config.plugins)) {
      console.log(`✅ Plugins: ${config.plugins.length} configured`);
    }
    if (config.presets && Array.isArray(config.presets)) {
      console.log(`✅ Presets: ${config.presets.length} configured`);
    }
    if (config.i18n) {
      console.log(`✅ i18n locales: ${JSON.stringify(config.i18n.locales || [])}`);
    }
  }

  // Summary output
  console.log('\n--- Validation Result ---');
  if (warnings.length > 0) {
    console.log('\n⚠️ Warnings:');
    warnings.forEach((w) => console.log(`  - ${w}`));
  }

  if (errors.length > 0) {
    console.log('\n❌ Errors found:');
    errors.forEach((e) => console.log(`  - ${e}`));
    console.log('\nPlease fix the errors above before building or deploying.');
    process.exit(1);
  } else {
    console.log('\n✨ Configuration passed validation successfully!\n');
    process.exit(0);
  }
}

const targetPath = process.argv[2] || findDefaultConfigFile();

if (!targetPath) {
  console.error('❌ Could not find a docusaurus.config.js or docusaurus.config.ts automatically.');
  console.error('Usage: node validate-config.js <path-to-docusaurus.config.js>');
  process.exit(1);
}

validate(targetPath);
