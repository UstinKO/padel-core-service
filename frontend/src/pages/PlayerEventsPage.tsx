import { useEffect, useId, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useSession } from '../api/account';
import { ApiError } from '../api/client';
import {
  answer,
  cancelPair,
  getCancellationQuote,
  getPlayerEvents,
  playerEventsKey,
  setReplacementSearch,
  setVisibility,
  withdraw,
  type PlayerEvent,
  type Participation,
} from '../api/playerEvents';
import { Button } from '../components/Button';
import { EntityState } from '../components/EntityState';

type Confirmation =
  | { kind: 'withdraw' | 'cancelPair'; entry: PlayerEvent }
  | { kind: 'decline'; entry: PlayerEvent; request: Participation };

function ConfirmationDialog({
  selection,
  onClose,
  onSubmit,
  pending,
}: {
  selection: Confirmation;
  onClose: () => void;
  onSubmit: (action: () => Promise<unknown>) => void;
  pending: boolean;
}) {
  const { t, i18n } = useTranslation();
  const dialog = useRef<HTMLDialogElement>(null);
  const heading = useId();
  const eventId = selection.entry.event.id;
  const quote = useQuery({
    queryKey: [
      'pair-cancellation',
      eventId,
      selection.entry.registrationId,
      selection.entry.version,
    ],
    queryFn: ({ signal }) => getCancellationQuote(eventId, signal),
    enabled: selection.kind === 'cancelPair',
    retry: false,
    gcTime: 0,
  });
  useEffect(() => {
    const element = dialog.current;
    element?.showModal();
    return () => element?.close();
  }, []);
  const quoteData = quote.data;
  const costs =
    quoteData &&
    ([
      ['obligation', quoteData.obligation],
      ['alreadyPaid', quoteData.alreadyPaid],
      ['remainingDue', quoteData.remainingDue],
      ['refundable', quoteData.refundable],
    ] as const);
  const currency = quoteData?.currency;
  const formatAmount = (value: number, code: string) =>
    /^[A-Z]{3}$/i.test(code)
      ? new Intl.NumberFormat(i18n.language, { style: 'currency', currency: code }).format(value)
      : `${new Intl.NumberFormat(i18n.language, { minimumFractionDigits: 2 }).format(value)} ${code}`;
  function submit() {
    if (selection.kind === 'withdraw') onSubmit(() => withdraw(selection.entry));
    if (selection.kind === 'decline') {
      const id = selection.request.requestId;
      onSubmit(() => answer(id, 'DECLINE'));
    }
    if (selection.kind === 'cancelPair' && quoteData)
      onSubmit(() => cancelPair(eventId, quoteData.token));
  }
  return (
    <dialog
      ref={dialog}
      className="share-dialog"
      aria-labelledby={heading}
      onCancel={(event) => {
        event.preventDefault();
        if (!pending) onClose();
      }}
    >
      <h2 id={heading}>{t(`playerEvents.${selection.kind}`)}</h2>
      <p>{t(`playerEvents.${selection.kind}Prompt`)}</p>
      {selection.kind === 'decline' && <p>{selection.request.playerName}</p>}
      {selection.kind === 'cancelPair' && quote.isPending && (
        <p role="status">{t('account.loading')}</p>
      )}
      {selection.kind === 'cancelPair' && quote.isError && (
        <div role="alert">
          <p>{t('playerEvents.quoteError')}</p>
          <Button variant="secondary" onClick={() => void quote.refetch()}>
            {t('account.retry')}
          </Button>
        </div>
      )}
      {costs && currency && (
        <dl className="entity-facts">
          {costs.map(
            ([key, value]) =>
              value !== null && (
                <div key={key}>
                  <dt>{t(`playerEvents.${key}`)}</dt>
                  <dd>{formatAmount(value, currency)}</dd>
                </div>
              ),
          )}
        </dl>
      )}
      <div className="button-row">
        <Button variant="secondary" disabled={pending} onClick={onClose}>
          {t('details.close')}
        </Button>
        <Button
          disabled={
            pending ||
            (selection.kind === 'cancelPair' && (!quoteData || quote.isFetching || quote.isError))
          }
          onClick={submit}
        >
          {t('playerEvents.apply')}
        </Button>
      </div>
    </dialog>
  );
}

export function PlayerEventsPage() {
  const { t, i18n } = useTranslation();
  const session = useSession();
  const client = useQueryClient();
  const playerId = session.data?.userType === 'PLAYER' ? session.data.id : null;
  const key = [...playerEventsKey, playerId] as const;
  const query = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => getPlayerEvents(signal),
    enabled: playerId != null,
    retry: false,
    gcTime: 0,
  });
  const [view, setView] = useState<'upcoming' | 'history'>('upcoming');
  const [selection, setSelection] = useState<Confirmation | null>(null);
  const [pending, setPending] = useState(false);
  const [failed, setFailed] = useState(false);
  const [expired, setExpired] = useState(false);
  const actionInFlight = useRef(false);
  const history = view === 'history';
  const date = (value: string) =>
    new Intl.DateTimeFormat(i18n.language, {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'America/Argentina/Buenos_Aires',
    }).format(new Date(value));

  async function mutate(action: () => Promise<unknown>) {
    if (actionInFlight.current) return;
    actionInFlight.current = true;
    setPending(true);
    setFailed(false);
    try {
      await action();
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) setExpired(true);
      setFailed(true);
    } finally {
      setSelection(null);
      await query.refetch();
      actionInFlight.current = false;
      setPending(false);
    }
  }

  const readError = query.error ?? session.error;
  const sessionRequired =
    expired ||
    (readError instanceof ApiError && readError.status === 401) ||
    (!session.isPending && !session.error && !session.data?.authenticated);
  if (sessionRequired)
    return (
      <section className="card">
        <h1>{t('playerEvents.title')}</h1>
        <p>{t('account.sessionRequired')}</p>
        <Link to={`/login?destination=${encodeURIComponent(window.location.pathname)}`}>
          {t('account.login')}
        </Link>
      </section>
    );
  if (session.data?.authenticated && playerId == null)
    return (
      <section className="card">
        <h1>{t('playerEvents.title')}</h1>
        <p>{t('account.playerRequired')}</p>
      </section>
    );

  return (
    <section className="page-stack">
      <h1>{t('playerEvents.title')}</h1>
      <EntityState
        pending={!readError && (session.isPending || query.isPending)}
        error={readError}
        retry={() => {
          if (session.error) void session.refetch();
          else void query.refetch();
        }}
      >
        <div className="button-row" role="group" aria-label={t('playerEvents.title')}>
          {(['upcoming', 'history'] as const).map((tab) => (
            <Button
              key={tab}
              aria-pressed={view === tab}
              variant={view === tab ? 'primary' : 'secondary'}
              onClick={() => setView(tab)}
            >
              {t(`playerEvents.${tab}`)}
            </Button>
          ))}
        </div>
        {failed && <p role="alert">{t('playerEvents.actionError')}</p>}
        {query.data?.[view].length === 0 && (
          <p>{t(history ? 'playerEvents.noHistory' : 'playerEvents.noUpcoming')}</p>
        )}
        {query.data?.[view].map((entry) => {
          const { event, availableActions } = entry;
          const format = event.type === 'AMERICANO_TEAMS' ? 'TEAM_PAIRS' : event.type;
          return (
            <article className="card page-stack" key={entry.registrationId}>
              <header>
                <p className="kicker">{t(`eventFormat.${format}`, { defaultValue: event.name })}</p>
                <h2>
                  <a href={`/torneo/${event.id}`}>{event.name}</a>
                </h2>
                <div className="event-tags">
                  <span>{t(`playerEvents.registration.${entry.registrationStatus}`)}</span>
                  {entry.mode && <span>{t(`playerEvents.mode.${entry.mode}`)}</span>}
                  {entry.replacementNeeded && <span>{t('playerEvents.replacementNeeded')}</span>}
                </div>
              </header>
              <dl className="entity-facts">
                <div>
                  <dt>{t('details.when')}</dt>
                  <dd>
                    <time dateTime={event.startsAt}>{date(event.startsAt)}</time>
                  </dd>
                </div>
                {event.clubName && (
                  <div>
                    <dt>{t('catalog.club')}</dt>
                    <dd>{event.clubName}</dd>
                  </div>
                )}
                {entry.partnerName && (
                  <div>
                    <dt>{t('playerEvents.partner')}</dt>
                    <dd>{entry.partnerName}</dd>
                  </div>
                )}
                {entry.queuePosition !== null && (
                  <div>
                    <dt>{t('playerEvents.queue')}</dt>
                    <dd>{entry.queuePosition}</dd>
                  </div>
                )}
                {entry.paymentStatus && (
                  <div>
                    <dt>{t('playerEvents.payment')}</dt>
                    <dd>{t(`playerEvents.payments.${entry.paymentStatus}`)}</dd>
                  </div>
                )}
              </dl>
              {entry.participation.length > 0 && (
                <section className="page-stack">
                  <h3>{t('playerEvents.participationTitle')}</h3>
                  {entry.participation.map((request) => (
                    <div key={request.requestId}>
                      <p>
                        {request.playerName} · {t(`playerEvents.participation.${request.status}`)}
                      </p>
                      {!history && (
                        <div className="button-row">
                          {request.availableActions.includes('CONFIRM') && (
                            <Button
                              disabled={pending}
                              onClick={() =>
                                void mutate(() => answer(request.requestId, 'CONFIRM'))
                              }
                            >
                              {t('playerEvents.confirm')}
                            </Button>
                          )}
                          {request.availableActions.includes('DECLINE') && (
                            <Button
                              disabled={pending}
                              variant="secondary"
                              onClick={() => setSelection({ kind: 'decline', entry, request })}
                            >
                              {t('playerEvents.decline')}
                            </Button>
                          )}
                        </div>
                      )}
                    </div>
                  ))}
                </section>
              )}
              {entry.cancellations.length > 0 && (
                <ul>
                  {entry.cancellations.map((item) => (
                    <li key={item.eventId}>
                      <strong>{t(`playerEvents.initiator.${item.initiator}`)}</strong>
                      {' · '}
                      {t(`playerEvents.reason.${item.reasonCode}`)}
                      {item.organizerComment && <p>{item.organizerComment}</p>}
                    </li>
                  ))}
                </ul>
              )}
              {!history && (
                <div className="button-row">
                  {availableActions.includes('WITHDRAW') && (
                    <Button
                      variant="secondary"
                      disabled={pending}
                      onClick={() => setSelection({ kind: 'withdraw', entry })}
                    >
                      {t('playerEvents.withdraw')}
                    </Button>
                  )}
                  {availableActions.includes('CANCEL_PAIR') && (
                    <Button
                      variant="secondary"
                      disabled={pending}
                      onClick={() => setSelection({ kind: 'cancelPair', entry })}
                    >
                      {t('playerEvents.cancelPair')}
                    </Button>
                  )}
                  {availableActions.includes('CHANGE_CONTACT_VISIBILITY') && (
                    <Button
                      variant="secondary"
                      disabled={pending}
                      aria-pressed={entry.shareContacts}
                      onClick={() => void mutate(() => setVisibility(entry))}
                    >
                      {t('playerEvents.shareContacts')}
                    </Button>
                  )}
                  {availableActions.includes('SEARCH_REPLACEMENT') && (
                    <Button
                      variant="secondary"
                      disabled={pending}
                      aria-pressed={entry.replacementSearch}
                      onClick={() => void mutate(() => setReplacementSearch(entry))}
                    >
                      {t('playerEvents.searchReplacement')}
                    </Button>
                  )}
                </div>
              )}
            </article>
          );
        })}
      </EntityState>
      {selection && (
        <ConfirmationDialog
          selection={selection}
          onClose={() => setSelection(null)}
          pending={pending}
          onSubmit={(action) => {
            void client.cancelQueries({ queryKey: key });
            void mutate(action);
          }}
        />
      )}
    </section>
  );
}
