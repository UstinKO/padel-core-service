import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { useSession } from '../api/account';
import {
  allMatches,
  playerName,
  roundKey,
  type LiveFormat,
  type LiveRound,
  type LiveSnapshot,
} from '../api/live';
import { previewLivePath } from '../api/live';
import { safeWebUrl, validEntityId } from '../api/eventDetails';
import { ShareDialog } from '../components/ShareDialog';
import { Button } from '../components/Button';
import { ReadError } from '../components/SystemState';
import { NotFoundPage } from '../components/EntityState';
import { usePageMetadata } from '../components/PageMetadata';
import { useLive } from '../live/useLive';
import { liveConnection } from '../live/realtime';
import { MatchCard } from '../live/MatchCard';
import { KingRanking, AmericanoRanking, PlayoffRanking } from '../live/Rankings';
import { exportBracket } from '../live/exportBracket';
import '../styles/live.css';

const tabs = ['courts', 'schedule', 'ranking', 'history'] as const;
function subscribeOnline(listener: () => void) {
  window.addEventListener('online', listener);
  window.addEventListener('offline', listener);
  return () => {
    window.removeEventListener('online', listener);
    window.removeEventListener('offline', listener);
  };
}
export function LivePage({ expected, alias = false }: { expected: LiveFormat; alias?: boolean }) {
  const { id = '' } = useParams();
  return (
    <LiveScreen key={`${expected}:${alias}:${id}`} id={id} expected={expected} alias={alias} />
  );
}
function LiveScreen({ id, expected, alias }: { id: string; expected: LiveFormat; alias: boolean }) {
  const { t, i18n } = useTranslation();
  const query = useLive(id, alias);
  const session = useSession();
  const mine = session.data?.userType === 'PLAYER' ? session.data.id : null;
  const [params, setParams] = useSearchParams();
  const [exporting, setExporting] = useState(false),
    [exportError, setExportError] = useState(false);
  const online = useSyncExternalStore(subscribeOnline, () => navigator.onLine);
  const data = query.data;
  const canonical = safeWebUrl(data?.metadata.canonicalUrl);
  usePageMetadata(data?.metadata.name ?? undefined, canonical);
  const selectedPlayer = params.get('player');
  const selectedName = data ? playerName(data, selectedPlayer) : null;
  const dialog = useRef<HTMLDialogElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const previousFocus = useRef<HTMLElement | null>(null);
  const tab =
    tabs.find((tab) => tab === params.get('tab')) ??
    (window.location.pathname.endsWith('/ranking')
      ? 'ranking'
      : window.location.pathname.endsWith('/history')
        ? 'history'
        : 'courts');
  const current =
    data?.rounds.find(
      (round) => data.format === 'KING_OF_COURT' && round.number === data.currentRound,
    ) ??
    data?.rounds
      .slice()
      .reverse()
      .find((round) => round.status === 'IN_PROGRESS') ??
    data?.rounds.at(-1);
  const selected = data?.rounds.find((round) => roundKey(round) === params.get('round')) ?? current;
  const change = (values: Record<string, string | null>, replace = false) =>
    setParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        for (const [key, value] of Object.entries(values)) {
          if (value) next.set(key, value);
          else next.delete(key);
        }
        return next;
      },
      { replace },
    );
  useEffect(() => {
    if (!data) return;
    const next = new URLSearchParams(params);
    let corrected = false;
    if (next.has('round') && !data.rounds.some((round) => roundKey(round) === next.get('round'))) {
      next.delete('round');
      corrected = true;
    }
    if (next.has('player') && !selectedName) {
      next.delete('player');
      corrected = true;
    }
    if (next.has('tab') && !tabs.some((tab) => tab === next.get('tab'))) {
      next.delete('tab');
      corrected = true;
    }
    if (corrected) void setParams(next, { replace: true });
  }, [data, params, selectedName, setParams]);
  const dialogOpen = Boolean(selectedName);
  useEffect(() => {
    const element = dialog.current;
    if (!element || !dialogOpen) return;
    previousFocus.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : heading.current;
    if (!element.open) element.showModal();
    const fallbackFocus = heading.current;
    const old = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      element.close();
      document.body.style.overflow = old;
      const target = previousFocus.current;
      if (target?.isConnected) target.focus();
      else fallbackFocus?.focus();
    };
  }, [dialogOpen]);
  const openPlayer = (player: number) => change({ player: String(player) });
  const roundTitle = (round: LiveRound) =>
    `${t(`live.phase.${round.phase}`, { defaultValue: t('live.phase.MAIN') })} · ${t('live.round', { number: round.number })}`;
  if (!validEntityId(id)) return <NotFoundPage />;
  if (!data) {
    if (query.error instanceof ApiError && query.error.status === 409) {
      const link = safeWebUrl(query.error.problem?.canonicalUrl);
      const preview = link && previewLivePath(link);
      return (
        <section className="card system-state">
          <h1>
            {t(
              query.error.problem?.code === 'LIVE_INSTANCE_CHANGED'
                ? 'live.instanceChanged'
                : 'live.unsupported',
            )}
          </h1>
          <p>{t('live.legacyExplanation')}</p>
          {preview ? (
            <Link to={preview}>{t('live.continue')}</Link>
          ) : (
            link && <a href={link}>{t('live.legacy')}</a>
          )}
        </section>
      );
    }
    if (query.error) return <ReadError error={query.error} retry={() => void query.refetch()} />;
    return <p role="status">{t('state.loading')}</p>;
  }
  if (data.format !== expected && data.format !== 'TEAM_PAIRS')
    return (
      <section className="card system-state">
        <h1>{t('live.wrongFormat')}</h1>
        {canonical && <a href={canonical}>{t('live.continue')}</a>}
      </section>
    );
  const stale =
    !online ||
    query.isError ||
    Boolean(
      data.metadata.topic &&
      (query.connection !== 'connected' || query.dataUpdatedAt < liveConnection.connectedTime()),
    );
  const playerMatches = allMatches(data).filter((match) =>
    [...match.first.players, ...match.second.players].some(
      (player) => String(player.id) === selectedPlayer,
    ),
  );
  const bracket =
    data.format === 'TEAM_PLAYOFF' ? data.rounds.filter((round) => round.phase === 'PLAYOFF') : [];
  const board = params.get('display') === 'board';
  const png = async () => {
    setExporting(true);
    setExportError(false);
    try {
      await exportBracket(bracket, data.metadata.name ?? '', roundTitle, (id) =>
        t('live.nextMatch', { number: id, slot: '—' }),
      );
    } catch {
      setExportError(true);
    } finally {
      setExporting(false);
    }
  };
  return (
    <div className={`page-stack live-view ${board ? 'live-projector' : ''}`}>
      <div className="entity-top">
        <Link to={`/torneo/${data.metadata.eventId}`}>← {t('live.about')}</Link>
        <div className="button-row">
          {canonical && <ShareDialog url={canonical} title={data.metadata.name ?? ''} />}
          <Button variant="secondary" onClick={() => change({ display: board ? null : 'board' })}>
            {t(board ? 'live.normalDisplay' : 'live.boardDisplay')}
          </Button>
        </div>
      </div>
      <header className="entity-heading">
        <p className="kicker">
          {t(`eventFormat.${data.format}`)} · {t('live.spectator')}
        </p>
        <h1 ref={heading} tabIndex={-1}>
          {data.metadata.name}
        </h1>
        <div className="event-tags">
          <span>{t(`conductPhase.${data.metadata.phase}`)}</span>
        </div>
      </header>
      <div
        className={`live-connection ${stale ? 'live-stale' : ''}`}
        role="status"
        aria-live="polite"
      >
        <strong>
          {t(
            !online
              ? 'live.offline'
              : stale
                ? 'live.stale'
                : query.isFetching
                  ? 'live.refreshing'
                  : 'live.current',
          )}
        </strong>
        {data.metadata.topic && <span>{t(`live.connection.${query.connection}`)}</span>}
        <span>
          {t('live.lastRead')}{' '}
          <time dateTime={new Date(query.dataUpdatedAt).toISOString()}>
            {new Intl.DateTimeFormat(i18n.language, {
              hour: '2-digit',
              minute: '2-digit',
              second: '2-digit',
            }).format(query.dataUpdatedAt)}
          </time>
        </span>
        <Button
          variant="secondary"
          disabled={query.isFetching}
          onClick={() => void query.refetch()}
        >
          {t('live.refresh')}
        </Button>
      </div>
      {query.error && (
        <p role="alert">
          {t(
            query.error instanceof ApiError && query.error.status === 429
              ? 'live.rateLimited'
              : 'live.staleExplanation',
          )}
        </p>
      )}
      {data.metadata.phase === 'NOT_STARTED' && (
        <section className="card">
          <h2>{t('live.notStarted')}</h2>
          <p>{t('live.notStartedText')}</p>
        </section>
      )}
      <nav className="live-tabs" aria-label={t('live.navigation')}>
        {tabs.map((item) => (
          <Link
            key={item}
            aria-current={tab === item ? 'page' : undefined}
            to={{
              search: (() => {
                const next = new URLSearchParams(params);
                next.set('tab', item);
                return next.toString();
              })(),
            }}
          >
            {t(`live.${item}`)}
          </Link>
        ))}
      </nav>
      {tab === 'ranking' ? (
        <LiveRanking data={data} openPlayer={openPlayer} mine={mine} />
      ) : tab === 'schedule' ? (
        <>
          {bracket.length > 0 && (
            <section className="page-stack">
              <div className="live-toolbar">
                <h2>{t('live.bracket')}</h2>
                <Button variant="secondary" disabled={exporting} onClick={() => void png()}>
                  {t(exporting ? 'live.exporting' : 'live.exportPng')}
                </Button>
              </div>
              {exportError && <p role="alert">{t('live.exportError')}</p>}
              <div
                className="live-bracket"
                role="region"
                tabIndex={0}
                aria-label={t('live.bracket')}
              >
                {bracket.map((round) => (
                  <section key={roundKey(round)}>
                    <h3>
                      {round.matches[0]?.stage
                        ? t(`live.stage.${round.matches[0].stage}`)
                        : roundTitle(round)}
                    </h3>
                    {round.matches.map((match) => (
                      <MatchCard key={match.id} match={match} openPlayer={openPlayer} mine={mine} />
                    ))}
                  </section>
                ))}
              </div>
            </section>
          )}
          <section className="page-stack">
            <h2>{t('live.schedule')}</h2>
            {data.rounds
              .filter((round) => round.phase !== 'PLAYOFF')
              .map((round) => (
                <RoundMatches
                  key={roundKey(round)}
                  round={round}
                  title={roundTitle(round)}
                  openPlayer={openPlayer}
                  mine={mine}
                />
              ))}
            {!data.rounds.length && <p>{t('live.empty')}</p>}
          </section>
        </>
      ) : (
        <section className="page-stack">
          <label className="field live-round-picker">
            <span className="field__label">
              {t(tab === 'history' ? 'live.historyRound' : 'live.selectRound')}
            </span>
            <select
              className="field__input"
              value={
                params.get('round') ??
                (tab === 'history' ? 'all' : selected ? roundKey(selected) : '')
              }
              onChange={(event) =>
                change({ round: event.target.value === 'all' ? null : event.target.value })
              }
            >
              {tab === 'history' && <option value="all">{t('live.allCompleted')}</option>}
              {!data.rounds.length && <option value="">{t('live.empty')}</option>}
              {data.rounds.map((round) => (
                <option key={roundKey(round)} value={roundKey(round)}>
                  {roundTitle(round)}
                </option>
              ))}
            </select>
          </label>
          {tab === 'history'
            ? data.rounds
                .filter((round) => !params.has('round') || roundKey(round) === params.get('round'))
                .map((round) => ({
                  ...round,
                  matches: round.matches.filter((match) => match.completed),
                }))
                .filter((round) => round.matches.length)
                .map((round) => (
                  <RoundMatches
                    key={roundKey(round)}
                    round={round}
                    title={roundTitle(round)}
                    openPlayer={openPlayer}
                    mine={mine}
                  />
                ))
            : selected && (
                <RoundMatches
                  round={selected}
                  title={roundTitle(selected)}
                  openPlayer={openPlayer}
                  mine={mine}
                />
              )}
          {(!data.rounds.length ||
            (tab === 'history' && !allMatches(data).some((match) => match.completed))) && (
            <p>{t('live.empty')}</p>
          )}
          {tab === 'courts' && data.format === 'TEAM_PLAYOFF' && data.queue.length > 0 && (
            <section className="page-stack">
              <h2>{t('live.queue')}</h2>
              <div className="live-courts">
                {data.queue.map((match) => (
                  <MatchCard key={match.id} match={match} openPlayer={openPlayer} mine={mine} />
                ))}
              </div>
            </section>
          )}
        </section>
      )}
      <dialog
        ref={dialog}
        className="share-dialog live-player-dialog"
        aria-labelledby="live-player-title"
        onCancel={() => change({ player: null })}
        onClick={(event) => {
          if (event.target === event.currentTarget) change({ player: null });
        }}
      >
        <div className="dialog-heading">
          <h2 id="live-player-title">{selectedName}</h2>
          <Button variant="ghost" onClick={() => change({ player: null })}>
            {t('details.close')}
          </Button>
        </div>
        <p>{t('live.playerMatches')}</p>
        <div className="page-stack">
          {playerMatches.length ? (
            playerMatches.map((match) => (
              <MatchCard key={match.id} match={match} openPlayer={openPlayer} mine={mine} compact />
            ))
          ) : (
            <p>{t('live.empty')}</p>
          )}
        </div>
      </dialog>
    </div>
  );
}
function RoundMatches({
  round,
  title,
  openPlayer,
  mine,
}: {
  round: LiveRound;
  title: string;
  openPlayer: (id: number) => void;
  mine: number | null;
}) {
  const { t } = useTranslation();
  return (
    <section className="page-stack">
      <h2>{title}</h2>
      <div className="live-courts">
        {round.matches.map((match) => (
          <MatchCard key={match.id} match={match} openPlayer={openPlayer} mine={mine} />
        ))}
      </div>
      {round.byePlayerNames.length > 0 && (
        <p>
          {t('live.resting')}: {round.byePlayerNames.join(' / ')}
        </p>
      )}
      {!round.matches.length && <p>{t('live.empty')}</p>}
    </section>
  );
}
function LiveRanking({
  data,
  openPlayer,
  mine,
}: {
  data: LiveSnapshot;
  openPlayer: (id: number) => void;
  mine: number | null;
}) {
  const { t } = useTranslation();
  switch (data.format) {
    case 'KING_OF_COURT':
      return <KingRanking snapshot={data} openPlayer={openPlayer} mine={mine} />;
    case 'AMERICANO':
      return <AmericanoRanking snapshot={data} openPlayer={openPlayer} mine={mine} />;
    case 'TEAM_PLAYOFF':
      return <PlayoffRanking snapshot={data} openPlayer={openPlayer} mine={mine} />;
    case 'TEAM_PAIRS':
      return <p>{t('live.notStarted')}</p>;
  }
}
