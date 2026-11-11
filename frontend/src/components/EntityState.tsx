import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { StatusView } from './StatusView';
import { ApiError } from '../api/client';
import { ReadError, SystemPage } from './SystemState';

export function NotFoundPage() {
  return <SystemPage kind="notFound" />;
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
  if (error) return <ReadError error={error} retry={retry} />;
  return children;
}
