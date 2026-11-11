import { useTranslation } from 'react-i18next';
import type { AdditionalPointsDto, Americano, King, Playoff } from '../api/live';

export function AdditionalPoints({ points }: { points: AdditionalPointsDto }) {
  const { t } = useTranslation();
  return (
    <details>
      <summary>{points.total}</summary>
      <ul>
        {points.stages.map((stage) => (
          <li key={stage.roundNumber}>
            {t('live.round', { number: stage.roundNumber })}: {stage.points}
          </li>
        ))}
      </ul>
    </details>
  );
}
function Person({
  id,
  name,
  openPlayer,
  mine,
  level,
}: {
  id: number | null;
  name: string | null;
  openPlayer: (id: number) => void;
  mine?: number | null;
  level?: string | null;
}) {
  const { t } = useTranslation();
  return (
    <>
      {id != null ? (
        <button className="live-person-button" onClick={() => openPlayer(id)}>
          {name || t('details.nameHidden')}
        </button>
      ) : (
        name || t('details.nameHidden')
      )}
      {id === mine && <small className="live-you">{t('live.you')}</small>}
      {level && <small>{t(`level.${level}`, { defaultValue: level })}</small>}
    </>
  );
}
export function KingRanking({
  snapshot,
  openPlayer,
  mine,
}: {
  snapshot: King;
  openPlayer: (id: number) => void;
  mine?: number | null;
}) {
  const { t } = useTranslation();
  return (
    <div className="live-table-scroll" tabIndex={0} role="region" aria-label={t('live.ranking')}>
      <table className="live-table">
        <caption>{t('live.kingRanking')}</caption>
        <thead>
          <tr>
            {[
              'position',
              'player',
              'total',
              'matchPoints',
              'bonus',
              'additional',
              'played',
              'wins',
              'losses',
            ].map((key) => (
              <th key={key} scope="col">
                {t(`live.${key}`)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {snapshot.ranking.map((row) => (
            <tr key={row.playerId} className={mine === row.playerId ? 'live-mine' : ''}>
              <td>{row.rank}</td>
              <th scope="row">
                <Person
                  id={row.playerId}
                  name={row.playerName}
                  level={row.playerNivel}
                  openPlayer={openPlayer}
                  mine={mine}
                />
              </th>
              <td>
                <strong>{row.totalPoints}</strong>
              </td>
              <td>{row.matchPoints}</td>
              <td>{row.bonusPoints}</td>
              <td>
                <AdditionalPoints points={row.additionalPoints} />
              </td>
              <td>{row.gamesPlayed}</td>
              <td>{row.wins}</td>
              <td>{row.losses}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
export function AmericanoRanking({
  snapshot,
  openPlayer,
  mine,
}: {
  snapshot: Americano;
  openPlayer: (id: number) => void;
  mine?: number | null;
}) {
  const { t } = useTranslation();
  return (
    <div className="live-table-scroll" tabIndex={0} role="region" aria-label={t('live.ranking')}>
      <table className="live-table">
        <caption>{t('live.americanoRanking')}</caption>
        <thead>
          <tr>
            {[
              'position',
              'player',
              'total',
              'matchPoints',
              'additional',
              'played',
              'wins',
              'draws',
              'losses',
              'difference',
              'bye',
            ].map((key) => (
              <th key={key} scope="col">
                {t(`live.${key}`)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {snapshot.ranking.map((row) => (
            <tr key={row.playerId} className={mine === row.playerId ? 'live-mine' : ''}>
              <td>{row.position}</td>
              <th scope="row">
                <Person
                  id={row.playerId}
                  name={row.playerName}
                  level={row.playerNivel}
                  openPlayer={openPlayer}
                  mine={mine}
                />
              </th>
              <td>
                <strong>{row.totalScore}</strong>
              </td>
              <td>{row.matchPoints}</td>
              <td>
                <AdditionalPoints points={row.additionalPoints} />
              </td>
              <td>{row.matchesPlayed}</td>
              <td>{row.matchesWon}</td>
              <td>{row.matchesDrawn}</td>
              <td>{row.matchesLost}</td>
              <td>{row.pointDifference}</td>
              <td>{row.byeCount}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
export function PlayoffRanking({
  snapshot,
  openPlayer,
  mine,
}: {
  snapshot: Playoff;
  openPlayer: (id: number) => void;
  mine?: number | null;
}) {
  const { t } = useTranslation();
  return (
    <div className="live-table-scroll" tabIndex={0} role="region" aria-label={t('live.ranking')}>
      <table className="live-table">
        <caption>{t('live.playoffRanking')}</caption>
        <thead>
          <tr>
            {['position', 'team', 'teamMatch1', 'teamMatch2', 'sets', 'games', 'situation'].map(
              (key) => (
                <th key={key} scope="col">
                  {t(`live.${key}`)}
                </th>
              ),
            )}
          </tr>
        </thead>
        <tbody>
          {snapshot.ranking.map((row) => (
            <tr key={row.id}>
              <td>{row.currentPosition}</td>
              <th scope="row">
                <small>{t('live.teamNumber', { number: row.teamNumber })}</small>
                <Person
                  id={row.player1.id}
                  name={row.player1.name}
                  level={row.player1.level}
                  openPlayer={openPlayer}
                  mine={mine}
                />
                <span> / </span>
                <Person
                  id={row.player2.id}
                  name={row.player2.name}
                  level={row.player2.level}
                  openPlayer={openPlayer}
                  mine={mine}
                />
              </th>
              <td>
                {row.match1GamesWon ?? '—'}:{row.match1GamesLost ?? '—'}
              </td>
              <td>
                {row.match2GamesWon ?? '—'}:{row.match2GamesLost ?? '—'}
              </td>
              <td>
                {row.setsWon}:{row.setsLost}
              </td>
              <td>
                {row.gamesWon}:{row.gamesLost}
              </td>
              <td>
                {t(`live.teamStatus.${row.tournamentStatus}`, {
                  defaultValue: t('live.teamStatus.WAITING'),
                })}
                {row.eliminatedStage && <small>{t(`live.stage.${row.eliminatedStage}`)}</small>}
                {row.queuePosition != null && (
                  <small>
                    {t('live.queuePosition', { number: row.queuePosition })} ·{' '}
                    {row.queueOpponentName}
                  </small>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
