import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getClub, getClubs } from '../api/discovery';
import { canonicalUrl, safeWebUrl, validEntityId } from '../api/eventDetails';
import { EntityState, NotFoundPage } from '../components/EntityState';
import { Catalogue } from '../components/Catalogue';
import { ShareDialog } from '../components/ShareDialog';
import { usePageMetadata } from '../components/PageMetadata';

export function ClubsPage() {
  const { t } = useTranslation();
  const query = useQuery({
    queryKey: ['public-clubs'],
    queryFn: ({ signal }) => getClubs(signal),
    retry: false,
  });
  usePageMetadata(t('clubs.title'), null);
  return (
    <div className="page-stack">
      <header className="page-heading">
        <h1>{t('clubs.title')}</h1>
        <p>{t('clubs.lead')}</p>
      </header>
      <EntityState pending={query.isPending} error={query.error} retry={() => void query.refetch()}>
        {query.data?.length ? (
          <div className="events-grid">
            {query.data.map((club) => (
              <article className="card club-card" key={club.id}>
                <h2>
                  <Link to={`/clubs/${club.id}`}>{club.name}</Link>
                </h2>
                <p>
                  {club.address}
                  {club.city && ` · ${club.city}`}
                </p>
                <Link className="event-link" to={`/clubs/${club.id}`}>
                  {t('clubs.details')}
                  <span className="sr-only">: {club.name}</span> →
                </Link>
              </article>
            ))}
          </div>
        ) : (
          <p role="status">{t('clubs.empty')}</p>
        )}
      </EntityState>
    </div>
  );
}
export function ClubPage() {
  const { id = '' } = useParams();
  const valid = validEntityId(id);
  const { t } = useTranslation();
  const query = useQuery({
    queryKey: ['public-club', id],
    queryFn: ({ signal }) => getClub(id, signal),
    enabled: valid,
    retry: false,
  });
  const club = query.data;
  const canonical = club ? canonicalUrl(club.canonicalUrl, `/clubs/${id}`) : null;
  usePageMetadata(club?.name, canonical);
  if (!valid) return <NotFoundPage />;
  return (
    <EntityState pending={query.isPending} error={query.error} retry={() => void query.refetch()}>
      {club && (
        <div className="page-stack">
          <div className="entity-top">
            <Link to="/clubs">← {t('clubs.title')}</Link>
            {canonical && <ShareDialog url={canonical} title={club.name} />}
          </div>
          <header className="entity-heading">
            <p className="kicker">{t('home.kicker')}</p>
            <h1>{club.name}</h1>
            <p>
              {club.address}
              {club.city && ` · ${club.city}`}
            </p>
          </header>
          <section className="card">
            <h2>{t('clubs.about')}</h2>
            <p className="event-description">{club.description || t('clubs.noDescription')}</p>
            <div className="button-row">
              {safeWebUrl(club.mapUrl) && (
                <a
                  className="button button--secondary"
                  href={safeWebUrl(club.mapUrl)!}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {t('clubs.map')} ↗
                </a>
              )}
              {safeWebUrl(club.websiteUrl) && (
                <a
                  className="button button--secondary"
                  href={safeWebUrl(club.websiteUrl)!}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {t('clubs.website')} ↗
                </a>
              )}
            </div>
          </section>
          <Catalogue clubId={id} title={t('clubs.events')} />
        </div>
      )}
    </EntityState>
  );
}
