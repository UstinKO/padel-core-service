import { NavLink, Outlet } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

import { CookieConsent } from './CookieConsent';
import { SessionAndNetwork } from './SystemState';
import { AccountNav } from './AccountNav';
import { LanguagePicker } from './LanguagePicker';

export function AppLayout() {
  const { t } = useTranslation();
  const markUrl = `${import.meta.env.BASE_URL}mark.svg`;
  return (
    <>
      <a className="skip-link" href="#main-content">
        {t('app.skip')}
      </a>
      <header className="topbar">
        <div className="container topbar__inner">
          <NavLink className="brand" to="/" aria-label={t('app.name')}>
            <img src={markUrl} alt="" width="34" height="34" />
            <span>{t('app.name')}</span>
          </NavLink>
          <nav className="nav" aria-label={t('app.preview')}>
            <NavLink to="/" end>
              {t('nav.home')}
            </NavLink>
            <NavLink to="/torneos">{t('nav.catalog')}</NavLink>
            <NavLink to="/clubs">{t('clubs.title')}</NavLink>
            <NavLink to="/components">{t('nav.components')}</NavLink>
          </nav>
          <AccountNav />
          <LanguagePicker />
        </div>
      </header>
      <SessionAndNetwork />
      <main id="main-content" className="container main" tabIndex={-1}>
        <Outlet />
      </main>
      <footer className="container footer">
        <strong>{t('app.name')}</strong>
        <span>{t('footer.tagline')}</span>
        <span>{t('app.phase')}</span>
        <nav aria-label={t('legal.navigation')}>
          <NavLink to="/terminos">{t('legal.terminos')}</NavLink>
          <NavLink to="/privacidad">{t('legal.privacidad')}</NavLink>
          <NavLink to="/cookies">{t('legal.cookies')}</NavLink>
        </nav>
        <CookieConsent />
      </footer>
    </>
  );
}
