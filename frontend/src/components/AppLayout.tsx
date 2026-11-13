import { NavLink, Outlet } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

import { CookieConsent } from './CookieConsent';
import { SessionAndNetwork } from './SystemState';
import { AccountNav } from './AccountNav';
import { LanguagePicker } from './LanguagePicker';

export function AppLayout() {
  const { t } = useTranslation();
  const preview = window.location.pathname.startsWith('/preview');
  const markUrl = `${import.meta.env.BASE_URL}mark.svg`;
  return (
    <>
      <a className="skip-link" href="#main-content">
        {t('app.skip')}
      </a>
      <header className="topbar">
        <div className="container topbar__inner">
          {preview ? (
            <NavLink className="brand" to="/" aria-label={t('app.name')}>
              <img src={markUrl} alt="" width="34" height="34" />
              <span>{t('app.name')}</span>
            </NavLink>
          ) : (
            <a className="brand" href="/" aria-label={t('app.name')}>
              <img src={markUrl} alt="" width="34" height="34" />
              <span>{t('app.name')}</span>
            </a>
          )}
          <nav className="nav" aria-label={t(preview ? 'app.preview' : 'app.navigation')}>
            {preview ? (
              <>
                <NavLink to="/" end>
                  {t('nav.home')}
                </NavLink>
                <NavLink to="/torneos">{t('nav.catalog')}</NavLink>
                <NavLink to="/clubs">{t('clubs.title')}</NavLink>
                <NavLink to="/components">{t('nav.components')}</NavLink>
              </>
            ) : (
              <>
                <a href="/">{t('nav.home')}</a>
                <a href="/torneos">{t('nav.catalog')}</a>
                <a href="/clubs">{t('clubs.title')}</a>
              </>
            )}
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
        {preview && <span>{t('app.phase')}</span>}
        <nav aria-label={t('legal.navigation')}>
          {preview ? (
            <NavLink to="/terminos">{t('legal.terminos')}</NavLink>
          ) : (
            <a href="/terminos">{t('legal.terminos')}</a>
          )}
          {preview ? (
            <NavLink to="/privacidad">{t('legal.privacidad')}</NavLink>
          ) : (
            <a href="/privacidad">{t('legal.privacidad')}</a>
          )}
          {preview ? (
            <NavLink to="/cookies">{t('legal.cookies')}</NavLink>
          ) : (
            <a href="/cookies">{t('legal.cookies')}</a>
          )}
        </nav>
        <CookieConsent />
      </footer>
    </>
  );
}
