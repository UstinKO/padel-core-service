import { useTranslation } from 'react-i18next';

import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field } from '../components/Field';
import { StatusView } from '../components/StatusView';

export function ComponentsPage() {
  const { t } = useTranslation();
  return (
    <div className="page-stack components-page">
      <header className="page-heading">
        <span className="eyebrow">{t('app.phase')}</span>
        <h1>{t('preview.componentTitle')}</h1>
        <p>{t('preview.componentLead')}</p>
      </header>
      <div className="component-grid">
        <Card>
          <h2>{t('preview.cardTitle')}</h2>
          <p className="muted">{t('preview.cardText')}</p>
          <div className="button-row">
            <Button>{t('preview.primaryAction')}</Button>
            <Button variant="secondary">{t('preview.secondaryAction')}</Button>
          </div>
        </Card>
        <Card>
          <Field label={t('preview.fieldLabel')} placeholder={t('preview.fieldPlaceholder')} />
        </Card>
      </div>
      <StatusView tone="success" title={t('preview.readyTitle')} text={t('preview.readyText')} />
      <StatusView tone="empty" title={t('preview.emptyTitle')} text={t('preview.emptyText')} />
    </div>
  );
}
