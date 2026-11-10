import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

import en from './en.json';
import es from './es.json';
import ru from './ru.json';

export const supportedLanguages = ['es', 'ru', 'en'] as const;
export type SupportedLanguage = (typeof supportedLanguages)[number];

const languageStorageKey = 'one-padel-language';

function isSupportedLanguage(value: string | null): value is SupportedLanguage {
  return supportedLanguages.includes(value as SupportedLanguage);
}

function initialLanguage(): SupportedLanguage {
  const saved = window.localStorage.getItem(languageStorageKey);
  if (isSupportedLanguage(saved)) return saved;
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
  window.localStorage.setItem(languageStorageKey, language);
  document.documentElement.lang = language;
});

document.documentElement.lang = i18n.language;

export default i18n;
