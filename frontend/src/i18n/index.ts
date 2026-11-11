import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

import en from './en.json';
import es from './es.json';
import ru from './ru.json';

export const supportedLanguages = ['es', 'ru', 'en'] as const;
export type SupportedLanguage = (typeof supportedLanguages)[number];

function isSupportedLanguage(value: string | null): value is SupportedLanguage {
  return supportedLanguages.includes(value as SupportedLanguage);
}
function initialLanguage(): SupportedLanguage {
  const query = new URLSearchParams(window.location.search).get('lang');
  if (isSupportedLanguage(query)) {
    document.cookie = `lang=${query}; Path=/; Max-Age=31536000; SameSite=Lax${window.location.protocol === 'https:' ? '; Secure' : ''}`;
    return query;
  }
  const cookie =
    document.cookie
      .split(';')
      .map((value) => value.trim())
      .find((value) => value.startsWith('lang='))
      ?.slice(5) ?? null;
  if (isSupportedLanguage(cookie)) return cookie;
  const browserLanguage = window.navigator.language.slice(0, 2);
  return isSupportedLanguage(browserLanguage) ? browserLanguage : 'es';
}

void i18n.use(initReactI18next).init({
  resources: {
    es: { translation: es },
    ru: { translation: ru },
    en: { translation: en },
  },
  lng: initialLanguage(),
  fallbackLng: 'es',
  supportedLngs: supportedLanguages,
  interpolation: { escapeValue: false },
});

i18n.on('languageChanged', (language) => {
  if (!isSupportedLanguage(language)) return;
  document.cookie = `lang=${language}; Path=/; Max-Age=31536000; SameSite=Lax${window.location.protocol === 'https:' ? '; Secure' : ''}`;
  const url = new URL(window.location.href);
  url.searchParams.set('lang', language);
  window.history.replaceState(window.history.state, '', url);
  document.documentElement.lang = language;
});

document.documentElement.lang = i18n.language;

export default i18n;
