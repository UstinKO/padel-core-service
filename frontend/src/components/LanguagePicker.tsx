import { useTranslation } from 'react-i18next';

import { supportedLanguages, type SupportedLanguage } from '../i18n';

export function LanguagePicker() {
  const { i18n, t } = useTranslation();
  return (
    <label className="language-picker">
      <span className="sr-only">{t('language.label')}</span>
      <select
        aria-label={t('language.label')}
        value={i18n.language.slice(0, 2)}
        onChange={(event) => void i18n.changeLanguage(event.target.value as SupportedLanguage)}
      >
        {supportedLanguages.map((language) => (
          <option value={language} key={language}>
            {t(`language.${language}`)}
          </option>
        ))}
      </select>
    </label>
  );
}
