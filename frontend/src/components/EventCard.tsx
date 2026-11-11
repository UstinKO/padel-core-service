import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { PublicEvent } from '../api/discovery';

export function EventCard({ event }: { event: PublicEvent }) {
  const { t, i18n } = useTranslation();
  const date = new Intl.DateTimeFormat(i18n.language, {
    timeZone: 'America/Argentina/Buenos_Aires',
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(event.startsAt));
  const price = new Intl.NumberFormat(i18n.language, {
    style: 'currency',
    currency: event.currency,
  }).format(event.price);
  return (
    <article className="event-card">
      <div className="event-card__top">
        <span className="event-kind">{t(`eventType.${event.type}`)}</span>
        <span className="event-status">{t(`eventStatus.${event.status}`)}</span>
      </div>
      <time dateTime={event.startsAt}>{date}</time>
      <h3>
        <Link to={`/torneo/${event.id}`}>{event.name}</Link>
      </h3>
      <p className="event-club">{event.club.name ?? t('catalog.unknownClub')}</p>
      <div className="event-tags">
        <span>{t(`gender.${event.gender}`)}</span>
        {event.level && <span>{t(`level.${event.level}`, { defaultValue: event.level })}</span>}
        <span>{t(`modality.${event.modality}`)}</span>
      </div>
      <dl className="event-capacity">
        <div>
          <dt>{t('catalog.available')}</dt>
          <dd>
            {event.available} {t(`capacityUnit.${event.capacityUnit}`, { count: event.available })}
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
      <div className="event-card__bottom">
        <strong>{price}</strong>
        <span>{t(event.registrationOpen ? 'catalog.open' : 'catalog.closed')}</span>
      </div>
      <Link
        className="event-link"
        aria-label={`${t('catalog.details')}: ${event.name}`}
        to={`/torneo/${event.id}`}
      >
        {t('catalog.details')}
        <span className="sr-only">: {event.name}</span>
        <span aria-hidden="true"> ↗</span>
      </Link>
    </article>
  );
}
