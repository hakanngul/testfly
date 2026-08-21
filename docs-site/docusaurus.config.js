// @ts-check
const { themes } = require('prism-react-renderer');

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'TestFly — Java Test Automation SDK',
  tagline: 'Java Test Automation SDK — web, API, mobile, and AI/MCP-powered test automation without hiding Selenium',
  favicon: 'img/favicon.svg',

  url: 'https://testfly.github.io',
  baseUrl: '/testfly/',

  organizationName: 'testfly',
  projectName: 'testfly',
  deploymentBranch: 'gh-pages',
  trailingSlash: false,

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',

  // GoatCounter — cookieless, no personal data, no consent banner (same account as
  // testfly.github.io/testfly, see the site's /privacy page).
  //
  // Docs and the marketing site report into ONE GoatCounter site, so raw paths would
  // collide: the apex homepage and the docs homepage both count as "/". The first
  // script namespaces every docs hit under "/docs-site/..." so the two hostnames stay
  // separable in the dashboard. Order matters — settings must run before count.js.
  headTags: [
    {
      tagName: 'script',
      attributes: { type: 'text/javascript' },
      innerHTML:
        "window.goatcounter={path:function(p){return '/docs-site'+p;}};",
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
          editUrl: 'https://github.com/testfly/testfly/edit/master/docs-site/',
        },
        blog: false,
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
        // Explicit so sitemap generation can't be silently disabled.
        // Ships with preset-classic; emits build/sitemap.xml listing all pages.
        sitemap: {
          changefreq: 'weekly',
          priority: 0.5,
          filename: 'sitemap.xml',
        },
      }),
    ],
  ],

  // Offline, self-hosted search (no external service / account needed).
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
            href: 'https://central.sonatype.com/artifact/io.testfly/testfly',
            label: 'Maven Central',
            position: 'right',
          },
          {
            href: 'https://github.com/testfly/testfly',
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
              { label: 'GitHub Issues', href: 'https://github.com/testfly/testfly/issues' },
              { label: 'GitHub Discussions', href: 'https://github.com/testfly/testfly/discussions' },
            ],
          },
          {
            title: 'More',
            items: [
              { label: 'GitHub', href: 'https://github.com/testfly/testfly' },
              { label: 'Maven Central', href: 'https://central.sonatype.com/artifact/io.testfly/testfly' },
              { label: 'Changelog', to: '/docs/changelog' },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} TestFly. Built with Docusaurus.`,
      },
      prism: {
        theme: themes.oneLight,
        darkTheme: themes.dracula,
        additionalLanguages: ['java', 'yaml', 'bash', 'markup'],
      },
      algolia: undefined,
    }),
};

module.exports = config;
