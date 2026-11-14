import { useEffect, useRef, useState } from 'react';
import type { FormEvent, ChangeEvent } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { apiFetch, ApiError } from '../api/client';
import {
  accountChanged,
  jsonPost,
  login,
  profileKey,
  safeDestination,
  sessionKey,
  useSession,
} from '../api/account';
import type { AccountResult, Profile, PublicConfig } from '../api/account';
import { Button } from '../components/Button';
import { Field } from '../components/Field';
import { Captcha } from '../components/Captcha';

const levels = ['C9', 'C8', 'C7', 'C6', 'C5', 'C4', 'D9', 'D8', 'D7', 'D6'];
type Mode = 'login' | 'register' | 'request' | 'reset' | 'profile';
type Values = Record<string, string>;
const defaults: Values = {
  nombre: '',
  apellido: '',
  email: '',
  telefono: '',
  telegramUsername: '',
  nivelJugador: '',
  preferredLocale: 'es',
  password: '',
  confirmPassword: '',
  currentPassword: '',
  newPassword: '',
  website: '',
};

export function AccountPage({ mode }: { mode: Mode }) {
  const session = useSession();
  const { t } = useTranslation();
  const profile = useQuery({
    queryKey: [...profileKey, session.data?.id],
    queryFn: () => apiFetch<Profile>('/api/players/me'),
    enabled: mode === 'profile' && session.data?.userType === 'PLAYER',
    retry: false,
  });
  if (mode === 'profile' && session.data?.userType === 'PLAYER' && !profile.data)
    return (
      <section className="account">
        <h1>{t('account.profile')}</h1>
        <p role={profile.error ? 'alert' : 'status'}>
          {t(profile.error ? 'account.failed' : 'account.loading')}
        </p>
        <Button onClick={() => void profile.refetch()}>{t('account.retry')}</Button>
      </section>
    );
  return (
    <AccountForm
      key={`${mode}-${profile.data?.id ?? 'guest'}`}
      mode={mode}
      initialProfile={profile.data}
    />
  );
}

function AccountForm({ mode, initialProfile }: { mode: Mode; initialProfile?: Profile }) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const client = useQueryClient();
  const session = useSession();
  const [values, setValues] = useState<Values>(() =>
    initialProfile
      ? {
          ...defaults,
          nombre: initialProfile.nombre,
          apellido: initialProfile.apellido,
          email: initialProfile.email,
          telefono: initialProfile.telefono ?? '',
          telegramUsername: initialProfile.telegramUsername ?? '',
          nivelJugador: initialProfile.nivelJugador,
          preferredLocale: initialProfile.preferredLocale,
        }
      : defaults,
  );
  const [remember, setRemember] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  const [notice, setNotice] = useState('');
  const [captchaToken, setCaptchaToken] = useState('');
  const [generation, setGeneration] = useState(0);
  const [passwordPending, setPasswordPending] = useState(false);
  const [passwordError, setPasswordError] = useState<unknown>();
  const summary = useRef<HTMLDivElement>(null);
  const destination =
    safeDestination(params.get('destination')) ??
    (params.get('tournamentId')?.match(/^[1-9][0-9]*$/)
      ? `/torneo/${params.get('tournamentId')}#tournament-registration`
      : '/players/dashboard');
  const token = params.get('token') ?? '';
  const config = useQuery({
    queryKey: ['account', 'config', i18n.language],
    queryFn: () => apiFetch<PublicConfig>('/api/public/config'),
    retry: false,
    enabled: mode === 'register' || mode === 'login',
  });
  const profile = useQuery({
    queryKey: [...profileKey, session.data?.id],
    queryFn: () => apiFetch<Profile>('/api/players/me'),
    enabled: mode === 'profile' && session.data?.userType === 'PLAYER',
    retry: false,
  });
  const validation = useQuery({
    queryKey: ['account', 'reset-token', token],
    queryFn: () =>
      apiFetch<{ valid: boolean; code: string }>(
        `/api/password-reset/validate?token=${encodeURIComponent(token)}`,
      ),
    enabled: mode === 'reset' && Boolean(token),
    retry: false,
    gcTime: 0,
  });
  useEffect(() => {
    if (error || passwordError) summary.current?.focus();
  }, [error, passwordError]);
  const currentError = error ?? passwordError;
  const fieldErrors =
    currentError instanceof ApiError ? (currentError.problem?.fieldErrors ?? []) : [];
  function errorMessage(value: { code: string; message: string }) {
    return t(`account.errors.${value.code}`, { defaultValue: value.message });
  }
  function errorTarget(name: string) {
    if (name === 'contact') return 'account-telefono';
    return name === 'recaptchaToken' || (name in defaults && name !== 'website')
      ? `account-${name}`
      : null;
  }
  function field(name: string) {
    return {
      name,
      disabled: pending || passwordPending,
      value: values[name] ?? '',
      onChange: (event: ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
        setValues((previous) => ({ ...previous, [name]: event.target.value })),
      error:
        fieldErrors
          .filter((value) => value.field === name)
          .map(errorMessage)
          .join(' ') || undefined,
    };
  }
  function continueTo(value: string | null) {
    const target = safeDestination(value) ?? destination;
    accountChanged();
    if (target.startsWith('/preview/')) void navigate(target.slice('/preview'.length));
    else window.location.assign(target);
  }
  function describe(value: unknown): string {
    if (value instanceof ApiError) {
      if (value.status === 429)
        return t('account.rateLimit', { seconds: value.problem?.retryAfter ?? 60 });
      return value.problem
        ? t(`account.errors.${value.problem.code}`, { defaultValue: value.problem.detail })
        : t('account.failed');
    }
    return t('account.uncertain');
  }
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (pending || passwordPending) return;
    setPending(true);
    setError(undefined);
    setPasswordError(undefined);
    setNotice('');
    try {
      if (mode === 'login') {
        const result = await login(values.email, values.password, remember, destination);
        client.setQueryData(sessionKey, result.session);
        continueTo(result.destination);
      } else if (mode === 'register') {
        await jsonPost<AccountResult>('/api/players/registro', {
          ...values,
          destination,
          recaptchaToken: captchaToken,
        });
        setNotice('registered');
        // Отдельный вход использует ту же серверную стратегию сессии и CSRF.
        try {
          const result = await login(values.email, values.password, remember, destination);
          client.setQueryData(sessionKey, result.session);
          continueTo(result.destination);
        } catch {
          setNotice('registeredLogin');
        }
      } else if (mode === 'request') {
        await jsonPost<AccountResult>('/api/password-reset/request', {
          email: values.email,
          destination,
        });
        setNotice('resetSent');
      } else if (mode === 'reset') {
        await jsonPost<AccountResult>('/api/password-reset/confirm', {
          token,
          newPassword: values.newPassword,
          confirmPassword: values.confirmPassword,
          destination,
        });
        setNotice('passwordSaved');
        void client.invalidateQueries({ queryKey: ['account', 'reset-token'] });
      } else {
        const result = await jsonPost<Profile>(
          '/api/players/me',
          {
            nombre: values.nombre,
            apellido: values.apellido,
            email: values.email,
            telefono: values.telefono,
            telegramUsername: values.telegramUsername,
            nivelJugador: values.nivelJugador,
            preferredLocale: values.preferredLocale,
          },
          'PUT',
        );
        client.setQueryData([...profileKey, session.data?.id], result);
        accountChanged();
        setNotice('saved');
      }
    } catch (value) {
      setError(value);
      if (!(value instanceof ApiError)) {
        void client.invalidateQueries({ queryKey: sessionKey });
        void client.invalidateQueries({ queryKey: profileKey });
      }
    } finally {
      setPending(false);
      setCaptchaToken('');
      setGeneration((value) => value + 1);
      setValues((previous) => ({
        ...previous,
        password: '',
        confirmPassword: '',
        newPassword: '',
      }));
    }
  }
  async function changePassword(event: FormEvent) {
    event.preventDefault();
    if (pending || passwordPending) return;
    setPasswordPending(true);
    setPasswordError(undefined);
    setError(undefined);
    setNotice('');
    try {
      await jsonPost<AccountResult>('/api/players/me/password', {
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
        confirmPassword: values.confirmPassword,
      });
      setNotice('passwordSaved');
    } catch (value) {
      setPasswordError(value);
    } finally {
      setPasswordPending(false);
      setValues((previous) => ({
        ...previous,
        currentPassword: '',
        newPassword: '',
        confirmPassword: '',
      }));
    }
  }
  const continuation = `?destination=${encodeURIComponent(destination)}`;
  if (mode === 'profile' && session.isPending) return <p role="status">{t('account.loading')}</p>;
  if (mode === 'profile' && !session.data?.authenticated)
    return (
      <section className="account">
        <h1>{t('account.profile')}</h1>
        <p>{t('account.sessionRequired')}</p>
        <Link
          to={`/login?destination=${encodeURIComponent(location.pathname.startsWith('/preview') ? location.pathname : '/preview/perfil')}`}
        >
          {t('account.login')}
        </Link>
      </section>
    );
  if (mode === 'profile' && session.data?.userType !== 'PLAYER')
    return (
      <section className="account">
        <h1>{t('account.profile')}</h1>
        <p role="alert">{t('account.playerRequired')}</p>
        <a href="/admin">{t('account.admin')}</a>
      </section>
    );
  if (mode === 'profile' && !profile.data)
    return (
      <section className="account">
        <h1>{t('account.profile')}</h1>
        <p role={profile.error ? 'alert' : 'status'}>
          {profile.error ? describe(profile.error) : t('account.loading')}
        </p>
        <Button onClick={() => void profile.refetch()}>{t('account.retry')}</Button>
      </section>
    );
  const tokenInvalid = mode === 'reset' && (!token || validation.data?.valid === false);
  const registrationDone = mode === 'register' && notice.startsWith('registered');
  return (
    <section className="account">
      <h1>{t(`account.${mode}`)}</h1>
      {params.get('expired') === 'true' && mode === 'login' && (
        <p role="status">{t('system.sessionText')}</p>
      )}
      {Boolean(error || passwordError) && (
        <div ref={summary} tabIndex={-1} role="alert" className="account-errors">
          <p>{describe(error ?? passwordError)}</p>
          <ul>
            {fieldErrors.map((value, index) => (
              <li key={`${value.field}-${index}`}>
                {errorTarget(value.field) ? (
                  <a href={`#${errorTarget(value.field)}`}>
                    {t(`account.${value.field}`, { defaultValue: t('account.form') })}:{' '}
                    {errorMessage(value)}
                  </a>
                ) : (
                  <span>
                    {t(`account.${value.field}`, { defaultValue: t('account.form') })}:{' '}
                    {errorMessage(value)}
                  </span>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
      {notice && <p role="status">{t(`account.${notice}`)}</p>}
      {notice === 'saved' &&
        /^\/preview\/torneo\/[1-9]\d{0,18}#tournament-registration$/.test(destination) && (
          <Link to={destination.slice('/preview'.length)}>{t('booking.return')}</Link>
        )}
      {tokenInvalid ? (
        <>
          <p role="alert">{t('account.tokenInvalid')}</p>
        </>
      ) : registrationDone ? (
        <Link to={`/login${continuation}`}>{t('account.login')}</Link>
      ) : (
        <form onSubmit={(event) => void submit(event)} aria-busy={pending}>
          {(mode === 'register' || mode === 'profile') && (
            <>
              <Field
                id="account-nombre"
                label={t('account.nombre')}
                autoComplete="given-name"
                required
                minLength={3}
                maxLength={100}
                {...field('nombre')}
              />
              <Field
                id="account-apellido"
                label={t('account.apellido')}
                autoComplete="family-name"
                required
                minLength={3}
                maxLength={100}
                {...field('apellido')}
              />
            </>
          )}
          {mode !== 'reset' && (
            <Field
              id="account-email"
              label={t('account.email')}
              type="email"
              autoComplete={mode === 'login' ? 'username' : 'email'}
              required
              maxLength={150}
              readOnly={mode === 'profile' && !profile.data?.guestAccount}
              {...field('email')}
            />
          )}
          {(mode === 'register' || mode === 'profile') && (
            <>
              <p>{t('account.contactHelp')}</p>
              <Field
                id="account-telefono"
                label={t('account.telefono')}
                type="tel"
                autoComplete="tel"
                maxLength={20}
                {...field('telefono')}
              />
              <Field
                id="account-telegramUsername"
                label={t('account.telegramUsername')}
                autoComplete="off"
                maxLength={33}
                {...field('telegramUsername')}
              />
              <label className="field">
                {t('account.nivelJugador')}
                <select
                  className="field__input"
                  id="account-nivelJugador"
                  required
                  {...field('nivelJugador')}
                >
                  <option value="">{t('account.chooseLevel')}</option>
                  {levels.map((level) => (
                    <option key={level} value={level}>
                      {level}
                    </option>
                  ))}
                </select>
              </label>
              {field('nivelJugador').error && (
                <p className="field__error">{field('nivelJugador').error}</p>
              )}
            </>
          )}
          {mode === 'profile' && (
            <label className="field">
              {t('account.preferredLocale')}
              <select
                id="account-preferredLocale"
                className="field__input"
                {...field('preferredLocale')}
              >
                {['es', 'ru', 'en'].map((language) => (
                  <option key={language} value={language}>
                    {t(`language.${language}`)}
                  </option>
                ))}
              </select>
            </label>
          )}
          {(mode === 'login' || mode === 'register') && (
            <>
              <Field
                id="account-password"
                label={t('account.password')}
                type="password"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                required
                minLength={mode === 'register' ? 8 : undefined}
                maxLength={100}
                {...field('password')}
              />
              <label>
                <input
                  type="checkbox"
                  checked={remember}
                  onChange={(event) => setRemember(event.target.checked)}
                />{' '}
                {t('account.remember')}
              </label>
            </>
          )}
          {mode === 'reset' && (
            <Field
              id="account-newPassword"
              label={t('account.newPassword')}
              type="password"
              autoComplete="new-password"
              required
              minLength={6}
              maxLength={100}
              {...field('newPassword')}
            />
          )}
          {(mode === 'register' || mode === 'reset') && (
            <Field
              id="account-confirmPassword"
              label={t('account.confirmPassword')}
              type="password"
              autoComplete="new-password"
              required
              maxLength={100}
              {...field('confirmPassword')}
            />
          )}
          {mode === 'register' && (
            <>
              <div className="account-honeypot" aria-hidden="true">
                <input
                  name="website"
                  autoComplete="off"
                  tabIndex={-1}
                  value={values.website}
                  onChange={(event) =>
                    setValues((previous) => ({ ...previous, website: event.target.value }))
                  }
                />
              </div>
              <div
                id="account-recaptchaToken"
                tabIndex={-1}
                role="group"
                aria-label={t('account.captcha')}
              >
                {config.data?.captchaEnabled && (
                  <Captcha
                    siteKey={config.data.captchaSiteKey}
                    generation={generation}
                    onToken={setCaptchaToken}
                  />
                )}
              </div>
              {config.isError && <p role="alert">{t('account.configFailed')}</p>}
            </>
          )}
          <Button
            type="submit"
            disabled={
              pending ||
              passwordPending ||
              session.isPending ||
              (mode === 'register' &&
                (!config.data || (config.data.captchaEnabled && !captchaToken))) ||
              (mode === 'reset' && (!validation.data?.valid || Boolean(notice)))
            }
          >
            {pending
              ? t('account.sending')
              : t(mode === 'profile' ? 'account.save' : `account.${mode}`)}
          </Button>
        </form>
      )}
      {mode === 'profile' && (
        <form onSubmit={(event) => void changePassword(event)} aria-busy={passwordPending}>
          <h2>{t('account.changePassword')}</h2>
          <Field
            id="account-currentPassword"
            label={t('account.currentPassword')}
            type="password"
            autoComplete="current-password"
            required
            maxLength={100}
            {...field('currentPassword')}
          />
          <Field
            id="account-newPassword"
            label={t('account.newPassword')}
            type="password"
            autoComplete="new-password"
            required
            minLength={6}
            maxLength={100}
            {...field('newPassword')}
          />
          <Field
            id="account-confirmPassword"
            label={t('account.confirmPassword')}
            type="password"
            autoComplete="new-password"
            required
            maxLength={100}
            {...field('confirmPassword')}
          />
          <p>{t('account.googlePasswordHelp')}</p>
          <Button disabled={pending || passwordPending}>{t('account.changePassword')}</Button>
        </form>
      )}
      {(mode === 'login' || mode === 'register') && config.data?.googleEnabled && (
        <a
          className="button button--secondary"
          href={`/oauth2/authorization/google?destination=${encodeURIComponent(destination)}`}
        >
          {t('account.google')}
        </a>
      )}
      {mode === 'login' && (
        <Link to={`/players/registro${continuation}`}>{t('account.register')}</Link>
      )}
      {mode !== 'request' && (
        <Link to={`/recuperar-password/solicitar${continuation}`}>{t('account.request')}</Link>
      )}
      {mode !== 'login' && <Link to={`/login${continuation}`}>{t('account.login')}</Link>}
    </section>
  );
}
