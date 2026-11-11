import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { catalogParams, eventTypes, genders, getCatalog, getClubs, levels } from '../api/discovery';
import { ApiError } from '../api/client';
import { Button } from './Button';
import { EventCard } from './EventCard';
import { StatusView } from './StatusView';

export function Catalogue() {
  const { t, i18n } = useTranslation();
  const [search, setSearch] = useSearchParams();
  const params = catalogParams(search);
  const query = useQuery({
    queryKey: ['catalog', params.toString(), i18n.language],
    queryFn: ({ signal }) => getCatalog(params, signal),
    retry: false,
  });
  const clubs = useQuery({
    queryKey: ['public-clubs'],
    queryFn: ({ signal }) => getClubs(signal),
    retry: false,
  });
  const update = (key: string, value: string) => {
    const next = new URLSearchParams(search);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== 'page') next.delete('page');
    setSearch(next);
  };
  const select = (key: string, label: string, options: readonly string[], prefix?: string) => (
    <label className="field" key={key}>
      <span className="field__label">{t(label)}</span>
      <select
        className="field__input"
        value={search.get(key) ?? ''}
        onChange={(event) => update(key, event.target.value)}
      >
        <option value="">{t('catalog.all')}</option>
        {options.map((value) => (
          <option value={value} key={value}>
            {prefix ? t(`${prefix}.${value}`) : value}
          </option>
        ))}
      </select>
    </label>
  );
  return (
    <section
      className="catalogue page-stack"
      id="events-catalog"
      aria-labelledby="catalog-title"
      aria-busy={query.isFetching}
    >
      <div className="catalog-heading">
        <div>
          <p className="kicker">{t('catalog.kicker')}</p>
          <h2 id="catalog-title">{t('catalog.title')}</h2>
        </div>
        <Button variant="ghost" onClick={() => setSearch({})}>
          {t('catalog.reset')}
        </Button>
      </div>
      <div className="catalog-filters">
        {select('type', 'catalog.type', eventTypes, 'eventType')}
        {select('gender', 'catalog.gender', genders, 'gender')}
        {select('level', 'catalog.level', levels, 'level')}
        {select('modality', 'catalog.modality', ['INDIVIDUAL', 'DOBLES'], 'modality')}
        <label className="field">
          <span className="field__label">{t('catalog.club')}</span>
          <select
            className="field__input"
            value={search.get('clubId') ?? ''}
            onChange={(event) => update('clubId', event.target.value)}
          >
            <option value="">{t('catalog.all')}</option>
            {clubs.data?.map((club) => (
              <option key={club.id} value={club.id}>
                {club.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field__label">{t('catalog.from')}</span>
          <input
            className="field__input"
            type="date"
            value={params.get('from') ?? ''}
            onChange={(event) => update('from', event.target.value)}
          />
        </label>
        <label className="field">
          <span className="field__label">{t('catalog.to')}</span>
          <input
            className="field__input"
            type="date"
            value={search.get('to') ?? ''}
            onChange={(event) => update('to', event.target.value)}
          />
        </label>
      </div>
      {clubs.isError && (
        <div className="catalog-message" role="alert">
          {t('catalog.clubsError')}{' '}
          <Button variant="ghost" onClick={() => void clubs.refetch()}>
            {t('state.retry')}
          </Button>
        </div>
      )}
      {query.isPending ? (
        <StatusView tone="loading" title={t('catalog.loading')} text={t('catalog.loadingText')} />
      ) : query.isError ? (
        <StatusView
          tone="error"
          title={t(
            query.error instanceof ApiError && query.error.status === 400
              ? 'catalog.invalid'
              : 'state.errorTitle',
          )}
          text={t('catalog.errorText')}
          action={<Button onClick={() => void query.refetch()}>{t('state.retry')}</Button>}
        />
      ) : (
        <>
          <p className="catalog-count" role="status" aria-live="polite">
            {t('catalog.results', { count: query.data.totalElements })}
          </p>
          {query.data.items.length ? (
            <div className="events-grid">
              {query.data.items.map((event) => (
                <EventCard key={event.id} event={event} />
              ))}
            </div>
          ) : (
            <StatusView
              tone="empty"
              title={t('catalog.empty')}
              text={t('catalog.emptyText')}
              action={
                <Button variant="secondary" onClick={() => setSearch({})}>
                  {t('catalog.reset')}
                </Button>
              }
            />
          )}
          <nav className="pagination" aria-label={t('catalog.pagination')}>
            <Button
              variant="secondary"
              disabled={query.data.page === 0}
              onClick={() => update('page', String(query.data.page - 1))}
            >
              {t('catalog.previous')}
            </Button>
            <span>
              {t('catalog.page', {
                page: query.data.page + 1,
                total: Math.max(1, query.data.totalPages),
              })}
            </span>
            <Button
              variant="secondary"
              disabled={query.data.page + 1 >= query.data.totalPages}
              onClick={() => update('page', String(query.data.page + 1))}
            >
              {t('catalog.next')}
            </Button>
          </nav>
        </>
      )}
      <p className="muted catalog-note">{t('catalog.timezone')}</p>
    </section>
  );
}
