import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

import { sessionQuery } from '../api/session';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { StatusView } from '../components/StatusView';

export function PreviewPage() {
  const { t } = useTranslation();
  const session = useQuery(sessionQuery);

  return (
    <div className="page-stack">
      <section className="hero">
        <div>
          <span className="eyebrow">{t('preview.eyebrow')}</span>
          <h1>{t('preview.title')}</h1>
          <p>{t('preview.lead')}</p>
          <Link className="button button--primary" to="/components">
            {t('nav.components')}
          </Link>
        </div>
        <div className="court-art" aria-hidden="true">
          <span />
        </div>
      </section>

      {session.isPending && (
        <StatusView tone="loading" title={t('state.loadingTitle')} text={t('state.loadingText')} />
      )}
      {session.isError && (
        <StatusView
          tone="error"
          title={t('state.errorTitle')}
          text={t('state.errorText')}
          action={<Button onClick={() => void session.refetch()}>{t('state.retry')}</Button>}
        />
      )}
      {session.data && (
        <Card className="session-card">
          <div>
            <span className="eyebrow">{t('app.preview')}</span>
            <h2>{t('preview.sessionTitle')}</h2>
            <p className="muted">
              {session.data.authenticated
                ? (session.data.fullName ?? session.data.email)
                : t('preview.anonymous')}
            </p>
          </div>
          <dl>
            <div>
              <dt>{t('preview.role')}</dt>
              <dd>
                {session.data.roles.length
                  ? session.data.roles.join(', ')
                  : t('preview.notAvailable')}
              </dd>
            </div>
            <div>
              <dt>{t('preview.club')}</dt>
              <dd>{session.data.clubId ?? t('preview.notAvailable')}</dd>
            </div>
          </dl>
        </Card>
      )}
    </div>
  );
}
