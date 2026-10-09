// @ts-check
const { themes } = require('prism-react-renderer');

module.exports = function createConfig() {
  const currentLocale = process.env.DOCUSAURUS_CURRENT_LOCALE || 'en';

  const siteTitle =
    currentLocale === 'tr'
      ? "TestFly — Java Test Otomasyon SDK'sı"
      : 'TestFly — Java Test Automation SDK';

  const siteTagline =
    currentLocale === 'tr'
      ? "Java Test Otomasyon SDK'sı — web, API, mobil ve AI/MCP destekli test otomasyonu, Selenium'u gizlemeden."
      : 'Java Test Automation SDK — web, API, mobile, and AI/MCP-powered test automation without hiding Selenium.';

  /** @type {import('@docusaurus/types').Config} */
  const config = {
    title: siteTitle,
    tagline: siteTagline,
    favicon: 'img/favicon.svg',

    // GitHub Pages URL
    url: 'https://hakanngul.github.io',
    // 👇 DÜZELTİLDİ: Repo adı küçük harf "testfly" ile eşleşmeli
    baseUrl: '/testfly/',

    // GitHub bilgileri
    organizationName: 'hakanngul',
    projectName: 'testfly',
    // 👇 KALDIRILDI: GitHub Actions kullanıyorsun, bu sadece CLI deploy içindir
    // deploymentBranch: 'gh-pages',
    trailingSlash: false,

    onBrokenLinks: 'throw',
    onBrokenMarkdownLinks: 'warn',

    // Goatcounter path'i baseUrl ile uyumlu
    headTags: [
      {
        tagName: 'script',
        attributes: { type: 'text/javascript' },
        innerHTML:
          "window.goatcounter={path:function(p){return '/testfly/docs-site'+p;}};",
      },
      {
        tagName: 'script',
        attributes: {
          async: 'true',
          src: 'https://gc.zgo.at/count.js',
          'data-goatcounter': 'https://testfly.goatcounter.com/count',
        },
      },
    ],

    i18n: {
      defaultLocale: 'en',
      locales: ['en', 'tr'],
      path: 'i18n',
      localeConfigs: {
        en: {
          label: 'English',
          direction: 'ltr',
          htmlLang: 'en-US',
        },
        tr: {
          label: 'Türkçe',
          direction: 'ltr',
          htmlLang: 'tr-TR',
        },
      },
    },

    presets: [
      [
        'classic',
        /** @type {import('@docusaurus/preset-classic').Options} */
        ({
          docs: {
            sidebarPath: require.resolve('./sidebars.js'),
            editUrl: 'https://github.com/hakanngul/testfly/edit/main/docs-site/',
          },
          blog: false,
          theme: {
            customCss: require.resolve('./src/css/custom.css'),
          },
          sitemap: {
            changefreq: 'weekly',
            priority: 0.5,
            filename: 'sitemap.xml',
          },
        }),
      ],
    ],

    themes: [
      [
        require.resolve('@easyops-cn/docusaurus-search-local'),
        {
          hashed: true,
          indexBlog: false,
          highlightSearchTermsOnTargetPage: true,
          explicitSearchResultPath: true,
        },
      ],
    ],

    themeConfig:
      /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
      ({
        image: 'img/logo.svg',
        colorMode: {
          defaultMode: 'light',
          disableSwitch: false,
          respectPrefersColorScheme: true,
        },
        navbar: {
          title: 'TestFly',
          logo: {
            alt: 'TestFly Logo',
            src: 'img/logo.svg',
          },
          items: [
            {
              type: 'docSidebar',
              sidebarId: 'docsSidebar',
              position: 'left',
              label: 'Docs',
            },
            {
              type: 'localeDropdown',
              position: 'right',
            },
            {
              href: 'https://central.sonatype.com/artifact/io.github.hakanngul/testfly',
              label: 'Maven Central',
              position: 'right',
            },
            {
              href: 'https://github.com/hakanngul/testfly',
              label: 'GitHub',
              position: 'right',
            },
          ],
        },
        footer: {
          style: 'light',
          links: [
            {
              title: 'Docs',
              items: [
                { label: 'Getting Started', to: '/docs/getting-started' },
                { label: 'Configuration', to: '/docs/configuration' },
                { label: 'Step Logging', to: '/docs/guides/step-logging' },
                { label: 'CI/CD', to: '/docs/ci/github-actions' },
              ],
            },
            {
              title: 'Community',
              items: [
                { label: 'GitHub Issues', href: 'https://github.com/hakanngul/testfly/issues' },
                { label: 'GitHub Discussions', href: 'https://github.com/hakanngul/testfly/discussions' },
              ],
            },
            {
              title: 'More',
              items: [
                { label: 'GitHub', href: 'https://github.com/hakanngul/testfly' },
                { label: 'Maven Central', href: 'https://central.sonatype.com/artifact/io.github.hakanngul/testfly' },
                { label: 'Changelog', to: '/docs/changelog' },
              ],
            },
          ],
          copyright: `Copyright © ${new Date().getFullYear()} TestFly. Built with Docusaurus.`,
        },
        prism: {
          theme: themes.oneLight,
          darkTheme: themes.dracula,
          additionalLanguages: ['java', 'yaml', 'bash', 'markup', 'gherkin', 'groovy', 'json'],
        },
        algolia: undefined,
      }),
  };

  return config;
};