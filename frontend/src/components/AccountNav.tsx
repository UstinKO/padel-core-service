import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiFetch } from '../api/client';
import { accountChanged, profileKey, sessionKey, useSession } from '../api/account';
import type { AccountResult } from '../api/account';
import { Button } from './Button';

export function AccountNav() {
  const { t } = useTranslation();
  const session = useSession();
  const preview = window.location.pathname.startsWith('/preview');
  const client = useQueryClient();
  const [pending, setPending] = useState(false);
  const [failed, setFailed] = useState(false);
  async function logout() {
    setPending(true);
    setFailed(false);
    try {
      await apiFetch<AccountResult>('/logout', { method: 'POST' });
      client.removeQueries({ queryKey: profileKey });
      await client.invalidateQueries({ queryKey: sessionKey });
      accountChanged();
      if (!preview) window.location.assign('/');
    } catch {
      setFailed(true);
      await client.invalidateQueries({ queryKey: sessionKey });
    } finally {
      setPending(false);
    }
  }
  return (
    <div className="account-actions">
      {session.data?.authenticated ? (
        <>
          {session.data.userType === 'PLAYER' && (
            <Link to="/players/dashboard">{t('playerEvents.title')}</Link>
          )}
          {preview ? (
            <Link to="/perfil">{session.data.fullName ?? t('account.profile')}</Link>
          ) : (
            <a href="/perfil">{session.data.fullName ?? t('account.profile')}</a>
          )}
          <Button variant="ghost" disabled={pending} onClick={() => void logout()}>
            {t('account.logout')}
          </Button>
        </>
      ) : preview ? (
        <Link to="/login">{t('account.login')}</Link>
      ) : (
        <a href="/login">{t('account.login')}</a>
      )}
      {failed && <span role="alert">{t('account.logoutFailed')}</span>}
    </div>
  );
}
