import { Component, useEffect, useState, useMemo, useRef } from 'react';
import type { ReactNode } from 'react';
import { Link, useLocation, useNavigate, useRouteError } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { profileKey, safeDestination, sessionKey } from '../api/account';
import { Button } from './Button';
import { usePageMetadata } from './PageMetadata';

export type SystemKind =
  'session' | 'forbidden' | 'offline' | 'rateLimit' | 'unavailable' | 'notFound' | 'error';
export function SystemPage({
  kind,
  retry,
  retryAt,
}: {
  kind: SystemKind;
  retry?: () => void;
  retryAt?: number;
}) {
  const { t } = useTranslation();
  const [now, setNow] = useState(Date.now);
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    heading.current?.focus();
  }, []);
  usePageMetadata(t(kind === 'notFound' ? 'details.notFound' : `system.${kind}Title`), null);
  useEffect(() => {
    const current = document.querySelector<HTMLMetaElement>('meta[name="robots"]');
    const robots = current ?? document.createElement('meta');
    const old = robots.content;
    robots.name = 'robots';
    robots.content = 'noindex, nofollow';
    if (!current) document.head.append(robots);
    const canonical = document.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    canonical?.remove();
    return () => {
      if (current) robots.content = old;
      else robots.remove();
      if (canonical) document.head.append(canonical);
    };
  }, []);
  useEffect(() => {
    if (!retryAt) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [retryAt]);
  const remaining = retryAt ? Math.max(0, Math.ceil((retryAt - now) / 1000)) : 0;
  return (
    <section className="system-state" role="alert">
      <h1 ref={heading} tabIndex={-1}>
        {t(kind === 'notFound' ? 'details.notFound' : `system.${kind}Title`)}
      </h1>
      <p>{t(kind === 'notFound' ? 'details.notFoundText' : `system.${kind}Text`)}</p>
      {kind === 'rateLimit' && retryAt !== undefined && (
        <p role="status">{t('system.wait', { seconds: remaining })}</p>
      )}
      <div className="account-actions">
        {retry && (
          <Button disabled={remaining > 0} onClick={retry}>
            {t('state.retry')}
          </Button>
        )}
        {kind === 'session' && <Link to="/login">{t('account.login')}</Link>}
        <Link to="/torneos">{t('nav.catalog')}</Link>
      </div>
    </section>
  );
}
export function ReadError({ error, retry }: { error: Error; retry: () => void }) {
  const { kind, retryAt } = useMemo(() => {
    let kind: SystemKind = navigator.onLine ? 'error' : 'offline';
    let retryAt: number | undefined;
    if (error instanceof ApiError) {
      kind =
        (
          {
            401: 'session',
            403: 'forbidden',
            404: 'notFound',
            429: 'rateLimit',
            503: 'unavailable',
          } as Record<number, SystemKind>
        )[error.status] ?? kind;
      if (error.status === 429) {
        const blocked = Date.parse(error.problem?.blockedUntil ?? '');
        const seconds = error.problem?.retryAfter;
        retryAt =
          seconds !== undefined && Number.isFinite(seconds) && seconds >= 0
            ? error.receivedAt + seconds * 1000
            : Number.isFinite(blocked)
              ? blocked
              : undefined;
      }
    }
    return { kind, retryAt };
  }, [error]);
  return (
    <SystemPage
      key={`${kind}-${retryAt}`}
      kind={kind}
      retry={kind === 'forbidden' || kind === 'session' ? undefined : retry}
      retryAt={retryAt}
    />
  );
}
export function SessionAndNetwork() {
  const { t } = useTranslation();
  const [online, setOnline] = useState(navigator.onLine);
  const location = useLocation();
  const navigate = useNavigate();
  const client = useQueryClient();
  useEffect(() => {
    const expired = () => {
      client.removeQueries({ queryKey: profileKey });
      void client.invalidateQueries({ queryKey: sessionKey });
      if (location.pathname.endsWith('/login')) return;
      const prefix = window.location.pathname.startsWith('/preview') ? '/preview' : '';
      const destination =
        safeDestination(`${prefix}${location.pathname}${location.search}${location.hash}`) ??
        '/torneos';
      void navigate(`/login?expired=true&destination=${encodeURIComponent(destination)}`);
    };
    window.addEventListener('one-padel:session-expired', expired);
    return () => window.removeEventListener('one-padel:session-expired', expired);
  }, [client, location, navigate]);
  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    window.addEventListener('online', update);
    window.addEventListener('offline', update);
    return () => {
      window.removeEventListener('online', update);
      window.removeEventListener('offline', update);
    };
  }, []);
  return online ? null : (
    <p className="offline-banner" role="status">
      {t('system.offlineText')}
    </p>
  );
}
export function RouteErrorPage() {
  useRouteError();
  return <SystemPage kind="error" retry={() => window.location.reload()} />;
}
export class AppErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    return this.state.failed ? (
      <BoundaryFallback reset={() => this.setState({ failed: false })} />
    ) : (
      this.props.children
    );
  }
}
function BoundaryFallback({ reset }: { reset: () => void }) {
  const { t } = useTranslation();
  return (
    <main className="container main" role="alert">
      <h1>{t('system.errorTitle')}</h1>
      <p>{t('system.errorText')}</p>
      <Button onClick={reset}>{t('state.retry')}</Button>
    </main>
  );
}
