import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useSession } from '../api/account';
import {
  BookingError,
  bookingTerms,
  doublePath,
  ownBooking,
  searchCandidates,
  searchPartners,
  sendBooking,
} from '../api/booking';
import type { BookingTerms, Partner, PartnerInput, Registration } from '../api/booking';
import { safeWebUrl } from '../api/eventDetails';
import { playerEventsKey } from '../api/playerEvents';
import { Button } from './Button';
import { Field } from './Field';

type Mode = 'WITH_PARTNER' | 'ADD_LATER' | 'SEARCH';
const soloStatuses = ['SOLO_SEARCH', 'SOLO_ADD_LATER'];
const blankPartner = { nombre: '', apellido: '', telefono: '', telegramUsername: '' };

/** Compare server snapshots only after the author lost the mutation response. */
function bookingChanged(before: Registration, latest: Registration) {
  return (
    latest.registered &&
    (!before.registered ||
      latest.registrationId !== before.registrationId ||
      latest.status !== before.status ||
      latest.partnerId !== before.partnerId)
  );
}

export function EventBooking({
  id,
  doubles,
  open,
}: {
  id: string;
  doubles: boolean;
  open: boolean;
}) {
  const { t, i18n } = useTranslation();
  const session = useSession();
  const client = useQueryClient();
  const player = session.data?.userType === 'PLAYER';
  const key = ['event-booking', id, session.data?.id];
  const registration = useQuery({
    queryKey: key,
    queryFn: () => ownBooking(id),
    enabled: player,
    gcTime: 0,
    retry: false,
  });
  const current = registration.isError ? undefined : registration.data;
  const solo = current?.status !== undefined && soloStatuses.includes(current.status);
  const [mode, setMode] = useState<Mode>('WITH_PARTNER');
  const [adding, setAdding] = useState(false);
  const [share, setShare] = useState(false);
  const [guest, setGuest] = useState(false);
  const [fields, setFields] = useState(blankPartner);
  const [search, setSearch] = useState('');
  const [debounced, setDebounced] = useState('');
  const [selected, setSelected] = useState<Partner>();
  const [terms, setTerms] = useState<{ offer: BookingTerms; accepted: boolean }>();
  const [busy, setBusy] = useState(false);
  const actionLock = useRef(false);
  const [notice, setNotice] = useState('');
  const [needProfile, setNeedProfile] = useState(false);
  const [uncertain, setUncertain] = useState<{ before: Registration; saved: boolean }>();
  const [sent, setSent] = useState<number[]>([]);
  const showPartner = doubles && (adding || (!current?.registered && mode === 'WITH_PARTNER'));
  const destination = `${window.location.pathname}#tournament-registration`;
  const profile = `/perfil?destination=${encodeURIComponent(destination)}`;

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(search.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [search]);

  const partners = useQuery({
    queryKey: ['booking-partner', id, session.data?.id, debounced],
    enabled: player && open && showPartner && !guest && !selected && debounced.length >= 2,
    queryFn: ({ signal }) => searchPartners(id, debounced, signal),
    retry: false,
  });
  const candidates = useQuery({
    queryKey: ['booking-candidates', id, session.data?.id],
    queryFn: () => searchCandidates(id),
    enabled: player && open && doubles && Boolean(solo),
    retry: false,
  });

  function resetTerms() {
    setTerms(undefined);
    setNotice('');
    setNeedProfile(false);
  }

  function updateForm(next: Mode) {
    setMode(next);
    resetTerms();
  }

  function invalidateEvent() {
    void client.invalidateQueries({ queryKey: ['event-details', id] });
    void client.invalidateQueries({ queryKey: ['booking-candidates', id] });
    void client.invalidateQueries({ queryKey: playerEventsKey });
  }

  async function checkResult(before: Registration, saved = false) {
    try {
      const latest = await ownBooking(id);
      client.setQueryData(key, latest);
      if (bookingChanged(before, latest)) {
        setNotice(t('booking.saved'));
        setTerms(undefined);
        setAdding(false);
        invalidateEvent();
      } else if (saved) {
        setUncertain({ before, saved });
        setNotice(t('booking.uncertain'));
        return;
      } else {
        setNotice(t('booking.retrySafe'));
      }
      setUncertain(undefined);
    } catch {
      setUncertain({ before, saved });
      setNotice(t('booking.uncertain'));
    }
  }

  async function run(action: () => Promise<void>) {
    if (actionLock.current) return;
    actionLock.current = true;
    setBusy(true);
    setNotice('');
    try {
      await action();
    } finally {
      actionLock.current = false;
      setBusy(false);
    }
  }

  function explain(error: BookingError) {
    if (error.result.needLevel || error.result.needContact) {
      setNeedProfile(true);
      return t(error.result.needLevel ? 'booking.needLevel' : 'booking.needContact');
    }
    if (error.status === 401 || error.status === 403) return t('booking.session');
    if (error.status === 429) return t('system.rateLimitText');
    if (error.status === 409) {
      setTerms(undefined);
      return t('booking.conflict');
    }
    if (error.status === 404) return t('booking.closed');
    return error.result.code
      ? t(`booking.errors.${error.result.code}`, { defaultValue: t('booking.rejected') })
      : error.status === 400 && error.result.message
        ? error.result.message
        : t('booking.rejected');
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!current || uncertain) return;
    if (session.data?.hasValidContact === false) {
      setNeedProfile(true);
      setNotice(t('booking.needContact'));
      return;
    }
    const before = current;
    void run(async () => {
      let path = `/players/tournaments/${id}/register`;
      let data = {};
      if (showPartner) {
        const partner: PartnerInput | undefined = selected
          ? { existingUserId: selected.id }
          : guest
            ? fields
            : undefined;
        if (!partner) {
          setNotice(t('booking.choosePartner'));
          return;
        }
        try {
          if (!terms) {
            const offer = await bookingTerms(id);
            if (offer) {
              setTerms({ offer, accepted: false });
              return;
            }
          } else if (!terms.accepted) {
            setNotice(t('booking.acceptRequired'));
            return;
          }
        } catch {
          setNotice(t('booking.termsFailed'));
          return;
        }
        path = `${doublePath(id)}/${adding ? 'add-partner' : 'register'}`;
        data = { ...partner, ...(terms ? { pairTermsToken: terms.offer.token } : {}) };
      } else if (doubles) {
        path = `${doublePath(id)}/register-solo`;
        data = { soloType: mode, shareContacts: mode === 'SEARCH' && share };
      }
      try {
        await sendBooking(path, data);
        await checkResult(before, true);
      } catch (error) {
        if (error instanceof BookingError) {
          setNotice(explain(error));
          if (error.status === 400 || error.status === 409) void registration.refetch();
        } else {
          await checkResult(before);
        }
      }
    });
  }

  function propose(candidateId: number) {
    void run(async () => {
      try {
        await sendBooking(`${doublePath(id)}/propose-pair/${candidateId}`);
        setSent((previous) => [...previous, candidateId]);
        setNotice(t('booking.proposalSent'));
      } catch (error) {
        setNotice(error instanceof BookingError ? explain(error) : t('booking.proposalUncertain'));
      }
    });
  }

  const matches = candidates.data?.filter((candidate) => candidate.playerId !== session.data?.id);
  const canSubmit = open && Boolean(current && (!current.registered || adding));
  const position = current?.waitlistPosition;
  const partnerName = [current?.partnerFirstName, current?.partnerLastName]
    .filter(Boolean)
    .join(' ');
  const topic = terms?.offer;
  const regulation = safeWebUrl(topic?.regulationUrl);
  const amount = (value: number, currency: string) =>
    new Intl.NumberFormat(i18n.language, { style: 'currency', currency }).format(value);
  return (
    <section className="card page-stack" id="tournament-registration">
      <h2>{t('booking.title')}</h2>
      {session.isPending && <p role="status">{t('account.loading')}</p>}
      {session.isError && (
        <Button onClick={() => void session.refetch()}>{t('account.retry')}</Button>
      )}
      {session.data && !player && open && (
        <a href={`/login?destination=${encodeURIComponent(destination)}`}>{t('booking.login')}</a>
      )}
      {player && registration.isPending && <p role="status">{t('account.loading')}</p>}
      {registration.isError && (
        <Button onClick={() => void registration.refetch()}>{t('account.retry')}</Button>
      )}
      {current?.registered && (
        <div>
          <strong>
            {t(`booking.status.${current.status}`, { defaultValue: t('booking.registered') })}
          </strong>
          {position != null && <p>{t('booking.position', { position })}</p>}
          {partnerName && <p>{t('booking.partnerName', { name: partnerName })}</p>}
          {open && solo && !adding && (
            <Button
              onClick={() => {
                setAdding(true);
                resetTerms();
              }}
            >
              {t('booking.addPartner')}
            </Button>
          )}
        </div>
      )}
      {canSubmit && (
        <form className="page-stack" onSubmit={submit}>
          <fieldset disabled={busy || Boolean(uncertain)} className="booking-fields">
            {doubles && !adding && (
              <fieldset className="booking-fields">
                <legend>{t('booking.mode')}</legend>
                {(['WITH_PARTNER', 'ADD_LATER', 'SEARCH'] as const).map((value) => (
                  <label key={value}>
                    <input
                      type="radio"
                      name="booking-mode"
                      checked={mode === value}
                      onChange={() => updateForm(value)}
                    />{' '}
                    {t(`booking.modeLabel.${value}`)}
                  </label>
                ))}
              </fieldset>
            )}
            {!adding && doubles && mode === 'SEARCH' && (
              <label>
                <input
                  type="checkbox"
                  checked={share}
                  onChange={(event) => setShare(event.target.checked)}
                />{' '}
                {t('booking.share')}
              </label>
            )}
            {showPartner && (
              <>
                <fieldset className="booking-fields">
                  <legend>{t('booking.partner')}</legend>
                  <label>
                    <input
                      type="radio"
                      name="booking-partner-kind"
                      checked={!guest}
                      onChange={() => {
                        setGuest(false);
                        resetTerms();
                      }}
                    />{' '}
                    {t('booking.existing')}
                  </label>
                  <label>
                    <input
                      type="radio"
                      name="booking-partner-kind"
                      checked={guest}
                      onChange={() => {
                        setGuest(true);
                        setSelected(undefined);
                        resetTerms();
                      }}
                    />{' '}
                    {t('booking.guest')}
                  </label>
                </fieldset>
                {guest ? (
                  <>
                    {Object.keys(blankPartner).map((name) => (
                      <Field
                        key={name}
                        label={t(`booking.field.${name}`)}
                        id={`booking-${name}`}
                        type={name === 'telefono' ? 'tel' : 'text'}
                        value={fields[name as keyof typeof fields]}
                        required={name !== 'telegramUsername'}
                        maxLength={name === 'telefono' ? 20 : 100}
                        onChange={(event) => {
                          setFields({ ...fields, [name]: event.target.value });
                          resetTerms();
                        }}
                      />
                    ))}
                  </>
                ) : selected ? (
                  <div className="button-row">
                    <span>{selected.nombreCompleto}</span>
                    <Button
                      type="button"
                      variant="secondary"
                      onClick={() => {
                        setSelected(undefined);
                        resetTerms();
                      }}
                    >
                      {t('booking.change')}
                    </Button>
                  </div>
                ) : (
                  <>
                    <Field
                      id="booking-search"
                      label={t('booking.search')}
                      value={search}
                      onChange={(event) => setSearch(event.target.value)}
                    />
                    {debounced === search.trim() && debounced.length >= 2 && (
                      <div aria-live="polite" className="page-stack">
                        {partners.isFetching && <p>{t('account.loading')}</p>}
                        {partners.isError && (
                          <Button
                            type="button"
                            variant="secondary"
                            onClick={() => void partners.refetch()}
                          >
                            {t('account.retry')}
                          </Button>
                        )}
                        {partners.data?.length === 0 && <p>{t('booking.noMatches')}</p>}
                        {partners.data?.map((result) => (
                          <Button
                            key={result.id}
                            type="button"
                            variant="secondary"
                            onClick={() => {
                              setSelected(result);
                              resetTerms();
                            }}
                          >
                            {result.nombreCompleto} · {result.nivelJugador}
                          </Button>
                        ))}
                      </div>
                    )}
                  </>
                )}
              </>
            )}
            {topic && (
              <div className="page-stack">
                <p>
                  {t('booking.terms', {
                    price: amount(topic.pairPrice, topic.currency),
                    late: amount(topic.lateObligation, topic.currency),
                    deadline: new Date(topic.freeUntil).toLocaleString(i18n.language, {
                      timeZone: 'America/Argentina/Buenos_Aires',
                    }),
                  })}
                </p>
                {regulation && (
                  <a href={regulation} target="_blank" rel="noreferrer">
                    {t('booking.regulation')}
                  </a>
                )}
                <label>
                  <input
                    type="checkbox"
                    checked={Boolean(terms?.accepted)}
                    onChange={(event) => setTerms({ offer: topic, accepted: event.target.checked })}
                  />{' '}
                  {t('booking.agree')}
                </label>
              </div>
            )}
            <div className="button-row">
              <Button type="submit">{t(adding ? 'booking.addPartner' : 'booking.submit')}</Button>
              {adding && (
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => {
                    setAdding(false);
                    resetTerms();
                  }}
                >
                  {t('booking.back')}
                </Button>
              )}
            </div>
          </fieldset>
        </form>
      )}
      {busy && <p role="status">{t('account.loading')}</p>}
      {notice && <p role="status">{notice}</p>}
      {uncertain && (
        <Button
          disabled={busy}
          onClick={() => void run(() => checkResult(uncertain.before, uncertain.saved))}
        >
          {t('booking.check')}
        </Button>
      )}
      {needProfile && <Link to={profile}>{t('booking.profile')}</Link>}
      {open && solo && current?.status === 'SOLO_SEARCH' && (
        <div className="page-stack">
          <h3>{t('booking.candidates')}</h3>
          {candidates.isError && (
            <Button onClick={() => void candidates.refetch()}>{t('account.retry')}</Button>
          )}
          {matches?.length === 0 && <p>{t('booking.noCandidates')}</p>}
          {matches?.map((candidate) => (
            <div key={candidate.registrationId} className="button-row">
              <span>
                {candidate.playerName}
                {candidate.nivel ? ` · ${candidate.nivel}` : ''}
              </span>
              <Button
                type="button"
                variant="secondary"
                disabled={busy || Boolean(uncertain) || sent.includes(candidate.registrationId)}
                onClick={() => propose(candidate.registrationId)}
              >
                {t(
                  sent.includes(candidate.registrationId)
                    ? 'booking.proposalSent'
                    : 'booking.propose',
                )}
              </Button>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
