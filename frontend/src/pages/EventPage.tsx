import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  canonicalUrl,
  getEventDetails,
  safeWebUrl,
  validEntityId,
  type Participant,
} from '../api/eventDetails';
import { previewLivePath } from '../api/live';
import { ShareDialog } from '../components/ShareDialog';
import { EntityState, NotFoundPage } from '../components/EntityState';
import { usePageMetadata } from '../components/PageMetadata';

function Participants({ title, items }: { title: string; items: Participant[] }) {
  const { t } = useTranslation();
  return (
    <section className="card">
      <h2>{title}</h2>
      {items.length ? (
        <ol className="participants">
          {items.map((item) => (
            <li key={item.id}>
              <span>{item.names.join(' / ') || t('details.nameHidden')}</span>
              {item.seekingPartner && <small>{t('details.seekingPartner')}</small>}
            </li>
          ))}
        </ol>
      ) : (
        <p>{t('details.noParticipants')}</p>
      )}
    </section>
  );
}

export function EventPage() {
  const { id = '' } = useParams();
  const valid = validEntityId(id);
  const { t, i18n } = useTranslation();
  const query = useQuery({
    queryKey: ['event-details', id, i18n.language],
    queryFn: ({ signal }) => getEventDetails(id, signal),
    enabled: valid,
    retry: false,
  });
  const details = query.data;
  const event = details?.event;
  const canonical = details ? canonicalUrl(details.canonicalUrl, `/torneo/${id}`) : null;
  usePageMetadata(event?.name, canonical);
  if (!valid) return <NotFoundPage />;
  const date = (value: string) =>
    new Intl.DateTimeFormat(i18n.language, {
      timeZone: 'America/Argentina/Buenos_Aires',
      dateStyle: 'long',
      timeStyle: 'short',
    }).format(new Date(value));
  const contact = safeWebUrl(details?.organizer?.whatsappUrl);
  const png = safeWebUrl(details?.pngUrl);
  return (
    <EntityState pending={query.isPending} error={query.error} retry={() => void query.refetch()}>
      {event && details && (
        <div className="page-stack">
          <div className="entity-top">
            <Link to="/torneos">← {t('nav.catalog')}</Link>
            {canonical && <ShareDialog url={canonical} title={event.name} />}
          </div>
          <header className="entity-heading">
            <p className="kicker">{t(`eventFormat.${details.format}`)}</p>
            <h1>{event.name}</h1>
            <div className="event-tags">
              <span>{t(`eventStatus.${event.status}`)}</span>
              <span>{t(`conductPhase.${details.phase}`)}</span>
              <span>{t(`gender.${event.gender}`)}</span>
              {event.level && (
                <span>{t(`level.${event.level}`, { defaultValue: event.level })}</span>
              )}
              <span>{t(`modality.${event.modality}`)}</span>
            </div>
          </header>
          <div className="entity-columns">
            <div className="page-stack">
              <section className="card">
                <h2>{t('details.about')}</h2>
                <dl className="entity-facts">
                  <div>
                    <dt>{t('details.when')}</dt>
                    <dd>
                      <time dateTime={event.startsAt}>{date(event.startsAt)}</time>
                      <small>{t('catalog.timezone')}</small>
                    </dd>
                  </div>
                  <div>
                    <dt>{t('catalog.club')}</dt>
                    <dd>
                      <Link to={`/clubs/${event.club.id}`}>
                        {event.club.name ?? t('catalog.unknownClub')}
                      </Link>
                      <small>{event.club.address}</small>
                    </dd>
                  </div>
                  <div>
                    <dt>{t('details.price')}</dt>
                    <dd>
                      {new Intl.NumberFormat(i18n.language, {
                        style: 'currency',
                        currency: event.currency,
                      }).format(event.price)}
                    </dd>
                  </div>
                  {event.duration && (
                    <div>
                      <dt>{t('details.duration')}</dt>
                      <dd>{event.duration}</dd>
                    </div>
                  )}
                </dl>
              </section>
              <section className="card">
                <h2>{t('details.rules')}</h2>
                <p className="event-description">{event.description || t('details.noRules')}</p>
                {event.cancellationDeadline && (
                  <p>
                    {t('details.cancellationDeadline')}:{' '}
                    <time dateTime={event.cancellationDeadline}>
                      {date(event.cancellationDeadline)}
                    </time>
                  </p>
                )}
              </section>
              <Participants
                title={t('details.confirmed')}
                items={event.participants?.confirmed ?? []}
              />
              <Participants
                title={t('catalog.waitlist')}
                items={event.participants?.waitlist ?? []}
              />
            </div>
            <aside className="page-stack entity-aside">
              <section className="card">
                <h2>{t('details.places')}</h2>
                <dl className="entity-facts">
                  <div>
                    <dt>{t('catalog.available')}</dt>
                    <dd>
                      {event.available}{' '}
                      {t(`capacityUnit.${event.capacityUnit}`, { count: event.available })}
                    </dd>
                  </div>
                  <div>
                    <dt>{t('catalog.occupied')}</dt>
                    <dd>
                      {event.occupied} / {event.capacity}
                    </dd>
                  </div>
                  <div>
                    <dt>{t('catalog.waitlist')}</dt>
                    <dd>
                      {event.waitlistCount}{' '}
                      {t(`capacityUnit.${event.capacityUnit}`, { count: event.waitlistCount })}
                    </dd>
                  </div>
                </dl>
                <p>{t(event.registrationOpen ? 'catalog.open' : 'catalog.closed')}</p>
                <div className="entity-actions">
                  {details.actions.map((action) => {
                    const url = safeWebUrl(action.url);
                    const livePath = action.type === 'LIVE' && url ? previewLivePath(url) : null;
                    if (livePath)
                      return (
                        <Link className="button button--secondary" to={livePath} key={action.type}>
                          {t(`eventAction.${action.type}`)}
                        </Link>
                      );
                    return (
                      url && (
                        <a className="button button--secondary" href={url} key={action.type}>
                          {t(`eventAction.${action.type}`)}
                        </a>
                      )
                    );
                  })}
                  {png && new URL(png).pathname.startsWith('/api/public/') && (
                    <a href={png}>{t('details.downloadPng')}</a>
                  )}
                </div>
              </section>
              <section className="card">
                <h2>{t('details.organizer')}</h2>
                {details.organizer?.contact ? (
                  <>
                    {contact ? (
                      <a
                        className="event-link"
                        target="_blank"
                        rel="noopener noreferrer"
                        href={contact}
                      >
                        {details.organizer.contact} ↗
                      </a>
                    ) : (
                      <p>{details.organizer.contact}</p>
                    )}
                  </>
                ) : (
                  <p>{t('details.noOrganizer')}</p>
                )}
              </section>
            </aside>
          </div>
        </div>
      )}
    </EntityState>
  );
}
