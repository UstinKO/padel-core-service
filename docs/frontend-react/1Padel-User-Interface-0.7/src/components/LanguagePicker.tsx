import { useLocale } from '../i18n/react';
import { localeNames, locales, setLocale, type Locale } from '../i18n/store';
export function LanguagePicker({expanded = false}: {expanded?: boolean}) {
  const locale = useLocale();
  return <label className={`language-picker ${expanded ? 'expanded' : ''}`}>
    <span className={expanded ? '' : 'sr-only'}>{ {ru:'Язык сайта',es:'Idioma del sitio',en:'Site language'}[locale] }</span>
    <select aria-label={{ru:'Язык сайта',es:'Idioma del sitio',en:'Site language'}[locale]} value={locale} onChange={e=>setLocale(e.target.value as Locale)}>
      {locales.map(value=><option key={value} value={value} translate="no">{expanded ? localeNames[value] : value.toUpperCase()}</option>)}
    </select>
  </label>;
}
