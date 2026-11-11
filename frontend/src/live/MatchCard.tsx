import { useTranslation } from 'react-i18next';
import type { LiveMatch, LiveSide } from '../api/live';
import { sideNames } from '../api/live';

export function MatchCard({
  match,
  openPlayer,
  mine,
  compact = false,
}: {
  match: LiveMatch;
  openPlayer: (id: number) => void;
  mine?: number | null;
  compact?: boolean;
}) {
  const { t } = useTranslation();
  const side = (side: LiveSide, first: boolean) => (
    <div
      className={`live-side ${match.completed && match.firstWinner === first ? 'live-winner' : ''}`}
    >
      <div>
        {side.number != null && <small>{t('live.teamNumber', { number: side.number })}</small>}
        {side.players.length ? (
          side.players.map((person, index) => (
            <span className="live-person" key={`${person.id}:${index}`}>
              {person.id != null ? (
                <button className="live-person-button" onClick={() => openPlayer(person.id!)}>
                  {person.name || t('details.nameHidden')}
                </button>
              ) : (
                person.name || t('details.nameHidden')
              )}
              {mine != null && person.id === mine && (
                <small className="live-you">{t('live.you')}</small>
              )}
            </span>
          ))
        ) : (
          <span>{sideNames(side) || t('live.waitingOpponent')}</span>
        )}
        {side.matchOrdinal != null && (
          <small>{t('live.teamMatch', { number: side.matchOrdinal })}</small>
        )}
      </div>
      <strong aria-label={t('live.score')}>{side.games ?? side.score ?? '—'}</strong>
    </div>
  );
  const isMine =
    mine != null &&
    [...match.first.players, ...match.second.players].some((person) => person.id === mine);
  return (
    <article
      id={`${compact ? 'player-match' : 'match'}-${match.id}`}
      className={`live-match ${isMine ? 'live-mine' : ''} ${compact ? 'live-match-compact' : ''}`}
    >
      <header>
        <strong>
          {match.court == null ? t('live.waitingCourt') : t('live.court', { number: match.court })}
        </strong>
        <span>{t(`live.status.${match.status}`, { defaultValue: t('live.status.PENDING') })}</span>
      </header>
      {side(match.first, true)}
      {side(match.second, false)}
      <footer>
        {match.number != null && <span>{t('live.match', { number: match.number })}</span>}
        {match.priority && <span>{t('live.priority')}</span>}
        {match.queuePosition != null && (
          <span>{t('live.queuePosition', { number: match.queuePosition })}</span>
        )}
        {match.waitingMinutes != null && (
          <span>{t('live.waitingMinutes', { number: match.waitingMinutes })}</span>
        )}
        {match.nextMatchId != null && (
          <a href={`#match-${match.nextMatchId}`}>
            {t('live.nextMatch', { number: match.nextMatchId, slot: match.nextSlot ?? '—' })}
          </a>
        )}
      </footer>
    </article>
  );
}
