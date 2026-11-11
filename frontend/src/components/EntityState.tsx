import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { StatusView } from './StatusView';
import { ApiError } from '../api/client';
import { Button } from './Button';

export function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <section className="page-stack">
      <h1>{t('details.notFound')}</h1>
      <p>{t('details.notFoundText')}</p>
      <Link className="button button--secondary" to="/torneos">
        {t('nav.catalog')}
      </Link>
    </section>
  );
}
export function EntityState({
  pending,
  error,
  retry,
  children,
}: {
  pending: boolean;
  error: Error | null;
  retry: () => void;
  children: ReactNode;
}) {
  const { t } = useTranslation();
  if (pending)
    return (
      <StatusView tone="loading" title={t('details.loading')} text={t('catalog.loadingText')} />
    );
  if (error instanceof ApiError && error.status === 404) return <NotFoundPage />;
  if (error)
    return (
      <StatusView
        tone="error"
        title={t('state.errorTitle')}
        text={t('state.errorText')}
        action={<Button onClick={retry}>{t('state.retry')}</Button>}
      />
    );
  return children;
}
