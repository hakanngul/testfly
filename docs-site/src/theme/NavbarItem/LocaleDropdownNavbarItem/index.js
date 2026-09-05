import React from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import {useLocation} from '@docusaurus/router';
import DropdownNavbarItem from '@theme/NavbarItem/DropdownNavbarItem';
import IconLanguage from '@theme/Icon/Language';
import styles from './styles.module.css';

export default function LocaleDropdownNavbarItem({
  mobile,
  dropdownItemsBefore = [],
  dropdownItemsAfter = [],
  queryString = '',
  ...props
}) {
  const {
    i18n: {currentLocale, locales, localeConfigs, defaultLocale},
    siteConfig: {baseUrl},
  } = useDocusaurusContext();
  const {pathname, search, hash} = useLocation();

  // In Docusaurus i18n builds:
  // For default locale ('en'): baseUrl is '/testfly/'
  // For localized locale ('tr'): baseUrl is '/testfly/tr/'
  // Calculate the root unlocalized baseUrl (e.g. always '/testfly/')
  let rootBaseUrl = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
  if (currentLocale !== defaultLocale && rootBaseUrl.endsWith(`/${currentLocale}/`)) {
    rootBaseUrl = rootBaseUrl.slice(0, -(`${currentLocale}/`.length));
  }

  // Extract relative path from pathname
  // e.g. pathname '/testfly/tr/docs/intro' with rootBaseUrl '/testfly/' -> 'tr/docs/intro' -> 'docs/intro'
  // e.g. pathname '/testfly/tr' -> ''
  // e.g. pathname '/testfly/docs/intro' -> 'docs/intro'
  let relativePath = pathname;
  if (relativePath.startsWith(rootBaseUrl)) {
    relativePath = relativePath.slice(rootBaseUrl.length);
  } else if (relativePath.startsWith(rootBaseUrl.slice(0, -1))) {
    relativePath = relativePath.slice(rootBaseUrl.length - 1);
  }

  // Remove leading slash
  if (relativePath.startsWith('/')) {
    relativePath = relativePath.slice(1);
  }

  // Strip any locale prefix from the beginning of relativePath (e.g. 'tr' or 'tr/...')
  for (const loc of locales) {
    if (loc !== defaultLocale) {
      if (relativePath === loc || relativePath === `${loc}/`) {
        relativePath = '';
      } else if (relativePath.startsWith(`${loc}/`)) {
        relativePath = relativePath.slice(loc.length + 1);
      }
    }
  }

  const localeItems = locales.map((locale) => {
    let targetBase;
    if (locale === defaultLocale) {
      targetBase = rootBaseUrl;
    } else {
      targetBase = `${rootBaseUrl}${locale}/`;
    }

    const targetPath = `${targetBase}${relativePath}`;
    const to = `pathname://${targetPath}${search}${hash}${queryString}`;

    return {
      label: localeConfigs[locale].label,
      lang: localeConfigs[locale].htmlLang,
      to,
      target: '_self',
      autoAddBaseUrl: false,
      className:
        locale === currentLocale
          ? mobile
            ? 'menu__link--active'
            : 'dropdown__link--active'
          : '',
    };
  });

  const items = [...dropdownItemsBefore, ...localeItems, ...dropdownItemsAfter];

  const dropdownLabel = mobile
    ? currentLocale === 'tr' ? 'Diller' : 'Languages'
    : localeConfigs[currentLocale].label;

  return (
    <DropdownNavbarItem
      {...props}
      mobile={mobile}
      label={
        <>
          <IconLanguage className={styles.iconLanguage} />
          {dropdownLabel}
        </>
      }
      items={items}
    />
  );
}
