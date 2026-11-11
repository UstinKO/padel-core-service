import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { apiFetch } from '../api/client';
import { useSession } from '../api/account';
import { applyConsent, readConsent } from '../api/consent';
import { Button } from './Button';

export function CookieConsent() {
  const { t } = useTranslation();
  const session = useSession();
  const [consent, setConsent] = useState(readConsent);
  const [analytics, setAnalytics] = useState(consent.analytics);
  const [marketing, setMarketing] = useState(consent.marketing);
  const [pending, setPending] = useState(false);
  const [failed, setFailed] = useState(false);
  const dialog = useRef<HTMLDialogElement>(null);
  const settings = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    applyConsent();
    const refresh = () => {
      setConsent(readConsent());
      applyConsent();
    };
    window.addEventListener('focus', refresh);
    return () => window.removeEventListener('focus', refresh);
  }, []);
  async function save(choice: 'accept' | 'reject' | 'customize') {
    if (pending) return;
    setPending(true);
    setFailed(false);
    try {
      await apiFetch<void>(
        `/api/cookies/${choice}${choice === 'customize' ? `?analytics=${analytics}&marketing=${marketing}` : ''}`,
        { method: 'POST' },
      );
      const saved = readConsent();
      if (saved.choice === 'unknown') throw new Error('COOKIE_UNAVAILABLE');
      setConsent(saved);
      setAnalytics(saved.analytics);
      setMarketing(saved.marketing);
      applyConsent();
      dialog.current?.close();
      settings.current?.focus();
    } catch {
      setFailed(true);
    } finally {
      setPending(false);
    }
  }
  function open() {
    const current = readConsent();
    setAnalytics(current.analytics);
    setMarketing(current.marketing);
    setFailed(false);
    dialog.current?.showModal();
  }
  const disabled = pending || session.isPending;
  return (
    <>
      {consent.choice === 'unknown' && (
        <section className="cookie-banner" aria-label={t('legal.cookieSettings')}>
          <h2>{t('legal.cookieTitle')}</h2>
          <p>
            {t('legal.cookieText')} <Link to="/cookies">{t('legal.cookies')}</Link>
          </p>
          <div className="account-actions">
            <Button disabled={disabled} onClick={() => void save('accept')}>
              {t('legal.accept')}
            </Button>
            <Button variant="secondary" disabled={disabled} onClick={() => void save('reject')}>
              {t('legal.reject')}
            </Button>
            <Button variant="ghost" disabled={disabled} onClick={open}>
              {t('legal.customize')}
            </Button>
          </div>
          {failed && <p role="alert">{t('legal.saveFailed')}</p>}
        </section>
      )}
      <button ref={settings} className="button button--ghost" onClick={open}>
        {t('legal.cookieSettings')}
      </button>
      <dialog
        className="cookie-dialog"
        ref={dialog}
        aria-labelledby="cookie-dialog-title"
        onClose={() => settings.current?.focus()}
      >
        <h2 id="cookie-dialog-title">{t('legal.cookieSettings')}</h2>
        <p>{t('legal.necessary')}</p>
        <label>
          <input
            type="checkbox"
            checked={analytics}
            onChange={(event) => setAnalytics(event.target.checked)}
          />{' '}
          {t('legal.analytics')}
        </label>
        <label>
          <input
            type="checkbox"
            checked={marketing}
            onChange={(event) => setMarketing(event.target.checked)}
          />{' '}
          {t('legal.marketing')}
        </label>
        {failed && <p role="alert">{t('legal.saveFailed')}</p>}
        <div className="account-actions">
          <Button disabled={disabled} onClick={() => void save('customize')}>
            {t('legal.save')}
          </Button>
          <Button variant="secondary" disabled={pending} onClick={() => dialog.current?.close()}>
            {t('legal.close')}
          </Button>
        </div>
      </dialog>
    </>
  );
}
