import { getLocale } from '../i18n/store';
import { useState, useRef, useEffect, type FormEvent, type ReactNode, type InputHTMLAttributes } from 'react';
import { Link, useNavigate, useSearchParams, useLocation, Navigate } from 'react-router-dom';
import { useApp } from '../AppContext';
import { findEvent } from '../data/events';
import type { User } from '../domain/types';
import { Icon } from '../components/Icon';
import '../styles/auth.css';
import { registrationErrors, validRegistrationPhone, nameError, type RegistrationFields } from '../domain/registrationValidation';
type Errors = Record<string, string | undefined>;
export const validEmail = (s: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s.trim());
export const validPhone = validRegistrationPhone;
export const validTelegram = (s: string) => !s.trim() || /^@?[a-zA-Z][a-zA-Z0-9_]{4,31}$/.test(s.trim());
export const levelOptions = ['Не знаю', ...Array.from({ length: 9 }, (_, i) => `C${9 - i}`), ...Array.from({ length: 9 }, (_, i) => `D${9 - i}`)];
export function useAuthDestination() {
    const [params] = useSearchParams();
    const event = findEvent(params.get('event') || undefined);
    const requestedState = params.get('eventState') || '';
    const eventState = ['full', 'closed', 'cancelled', 'finished', 'live', 'partner-deadline'].includes(requestedState) ? requestedState : '';
    const returnToParticipation = params.get('next') === 'waitlist-offer';
    const query = new URLSearchParams();
    if (event) query.set('event', event.id);
    if (event && eventState) query.set('eventState', eventState);
    if (returnToParticipation) query.set('next', 'waitlist-offer');
    const suffix = query.size ? '?' + query.toString() : '';
    const eventQuery = eventState ? '?state=' + encodeURIComponent(eventState) : '';
    return { event, suffix, eventState, eventPath: event ? `/event/${event.id}${eventQuery}` : '/events', destination: returnToParticipation ? '/waitlist-offer' + (event ? '?event=' + event.id : '') : event ? `/book/${event.id}${eventQuery}` : '/games' };
}
export function AuthFrame({ title, intro, children }: {
    title: string;
    intro: string;
    children: ReactNode;
}) { const { event, eventPath } = useAuthDestination(); return <section className="auth-page view-space"><Link className="back-link" to={eventPath}><Icon name="back"/>{event ? 'К мероприятию' : 'Все мероприятия'}</Link><div className="auth-card"><div className="eyebrow">Ваш аккаунт 1-Padel</div><h1>{title}</h1><p className="auth-intro">{intro}</p>{event && <div className="auth-context"><Icon name={event.symbol}/><div><strong>{event.title} · {event.level}</strong><span>{event.date} · {event.time} · {event.club}</span></div></div>}{children}</div></section>; }
export function AuthField({ label, error, hint, ...input }: {
    label: string;
    error?: string;
    hint?: string;
} & InputHTMLAttributes<HTMLInputElement>) { const id = input.id || input.name; return <div className="auth-field"><label htmlFor={id}>{label}</label><input {...input} id={id} aria-invalid={!!error} aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}/>{error ? <p className="form-error" id={`${id}-error`}>{error}</p> : hint ? <p className="auth-field-hint" id={`${id}-hint`}>{hint}</p> : null}</div>; }
function DemoNote() { return <p className="demo-auth-note">Демонстрационный аккаунт: данные остаются в браузере, пароли не сохраняются. Для демовхода подходит любой пароль от 8 до 100 символов. Реальные запросы, письма и подключение Google не выполняются. Используйте вымышленные контакты.</p>; }
function PasswordField({ value, onChange, error, id = 'auth-password', label = 'Пароль', newPassword = false, onBlur, hint }: {
    value: string;
    onChange: (s: string) => void;
    error?: string;
    id?: string;
    label?: string;
    newPassword?: boolean;
    onBlur?: () => void;
    hint?: string;
}) { const [show, setShow] = useState(false); return <div className="auth-field"><label htmlFor={id}>{label}</label><div className="auth-password"><input id={id} name={id} type={show ? 'text' : 'password'} autoComplete={newPassword ? 'new-password' : 'current-password'} value={value} onBlur={onBlur} onChange={e => onChange(e.target.value)} minLength={8} maxLength={100} required aria-invalid={!!error} aria-describedby={`${id}-hint`}/><button type="button" onClick={() => setShow(v => !v)} aria-label={show ? 'Скрыть пароль' : 'Показать пароль'} aria-pressed={show}>{show ? 'Скрыть' : 'Показать'}</button></div><p id={`${id}-hint`} className={error ? 'form-error' : 'auth-field-hint'}>{error || hint || 'От 8 до 100 символов.'}</p></div>; }
function GoogleButton() { const { signIn } = useApp(); const navigate = useNavigate(); const { suffix } = useAuthDestination(); return <div className="auth-google-block"><button type="button" className="auth-google" onClick={() => { signIn({ id: 'demo:google-alex', first: 'Алексей', last: 'Петров', email: 'alex@example.com', authProvider: 'google', emailVerified: true }); navigate(`/complete-profile${suffix}`); }}><span className="google-letter" aria-hidden="true">G</span>Продолжить с Google<Icon name="arrow"/></button><p>Демо Google: затем нужно добавить телефон.</p></div>; }
function AlreadySignedIn() { const { user } = useApp(); const { suffix, destination, event } = useAuthDestination(); return <AuthFrame title="Вы уже вошли" intro={`${user?.first}, продолжите с вашим демопрофилем.`}><Link className="btn full" to={user?.phone ? destination : `/complete-profile${suffix}`}>{user?.phone ? (event ? 'Продолжить запись' : 'Мои записи') : 'Добавить телефон'}<Icon name="arrow"/></Link><Link className="text-btn auth-centered" to="/profile">Открыть профиль</Link></AuthFrame>; }
function formErrors(errors: Errors) { return Object.entries(errors).some(([key, value]) => key !== 'request' && value) ? <p className="auth-error-summary" role="alert">Проверьте отмеченные поля.</p> : null; }
const emailId = (email: string) => `demo:${email.trim().toLowerCase()}`;
function emailName(email: string) { const part = email.split('@')[0].split(/[._+\-]/)[0].replace(/[^\p{L}]/gu, ''); return part ? part[0].toUpperCase() + part.slice(1).toLowerCase() : 'Игрок'; }
export function LoginPage() {
    const { user, signIn, notify } = useApp();
    const { event, suffix } = useAuthDestination();
    const navigate = useNavigate();
    const [params, setParams] = useSearchParams();
    const suppliedEmail = (useLocation().state as { email?: unknown } | null)?.email;
    const [email, setEmail] = useState(() => typeof suppliedEmail === 'string' && validEmail(suppliedEmail) ? suppliedEmail.trim() : '');
    const [password, setPassword] = useState('');
    const [errors, setErrors] = useState<Errors>({});
    const formRef = useRef<HTMLFormElement>(null);
    const invalidCredentialsPreview = params.get('state') === 'invalid-credentials';
    const credentialsMessage = 'Неверный email или пароль. Проверьте данные и попробуйте ещё раз.';
    // Direct review links must show the requested state without a form submission.
    const requestError = invalidCredentialsPreview ? credentialsMessage : errors.request;

    if (user) return <AlreadySignedIn />;

    function retry() {
        const next = new URLSearchParams(params);
        next.delete('state');
        setParams(next, { replace: true });
        setErrors({});
        const field = formRef.current?.elements.namedItem('login-email');
        if (field instanceof HTMLInputElement) field.focus();
    }

    function submit(e: FormEvent<HTMLFormElement>) {
        e.preventDefault();
        const next = {
            email: validEmail(email) ? undefined : 'Укажите корректный email.',
            password: password.length >= 8 && password.length <= 100 ? undefined : 'Пароль должен содержать от 8 до 100 символов.'
        };
        setErrors(next);
        if (Object.values(next).some(Boolean)) return;
        if (invalidCredentialsPreview) {
            setErrors({ request: credentialsMessage });
            return;
        }
        signIn({ id: emailId(email), first: emailName(email), last: '', email: email.trim().toLowerCase(), emailVerified: true, authProvider: 'email' });
        setPassword('');
        notify('Демовход выполнен. Добавьте контакт для участия.');
        navigate(`/complete-profile${suffix}`, { replace: true });
    }

    return <AuthFrame title="С возвращением" intro={event ? 'Войдите, затем подтвердите участие в выбранном мероприятии.' : 'Ваши мероприятия, записи и результаты — рядом.'}>
        {requestError && <div className="auth-state-banner auth-state-error" role="alert">
            <Icon name="info" />
            <div>
                <strong>Не удалось войти</strong>
                <p>{requestError}</p>
                <button className="text-btn" type="button" onClick={retry}>Исправить данные</button>
            </div>
        </div>}
        <GoogleButton />
        <div className="auth-divider">или по email</div>
        <form ref={formRef} className="auth-page-form" onSubmit={submit} noValidate>
            <AuthField label="Email" name="login-email" type="email" autoComplete="email" inputMode="email" required maxLength={150} placeholder="you@example.com" value={email} onChange={e => setEmail(e.target.value)} error={errors.email} />
            <PasswordField value={password} onChange={setPassword} error={errors.password} />
            <Link className="text-btn auth-forgot" to={`/forgot-password${suffix}`}>Не помню пароль</Link>
            {formErrors(errors)}
            <button className="btn full" type="submit">Войти<Icon name="arrow" /></button>
        </form>
        <p className="auth-page-switch">Первый раз? <Link to={`/register${suffix}`}>Создать профиль</Link></p>
        <DemoNote />
    </AuthFrame>;
}
export function RegisterPage() {
    const { user, signIn } = useApp();
    const { event, suffix, destination } = useAuthDestination();
    const navigate = useNavigate();
    const [params, setParams] = useSearchParams();
    const [fields, setFields] = useState<RegistrationFields>({ first: '', last: '', email: '', phone: '', telegram: '', level: '', locale: getLocale(), password: '', confirm: '', terms: false });
    const [errors, setErrors] = useState<Errors>({});
    const [touched, setTouched] = useState<Set<string>>(() => new Set());
    const [submitted, setSubmitted] = useState(false);
    const formRef = useRef<HTMLFormElement>(null);
    const state = params.get('state');
    const [retryAt, setRetryAt] = useState(() => Date.now() + 15 * 60 * 1000);
    const [remaining, setRemaining] = useState(900);
    useEffect(() => {
        if (state !== 'rate-limit') return;
        const update = () => setRemaining(Math.max(0, Math.ceil((retryAt - Date.now()) / 1000)));
        update(); const timer = window.setInterval(update, 1000);
        return () => window.clearInterval(timer);
    }, [state, retryAt]);
    const serverErrors: Record<string, { field?: string; title: string; message: string }> = {
        'email-exists': { field: 'email', title: 'Email уже используется', message: 'Профиль с этим email уже существует. Войдите или восстановите пароль.' },
        'phone-exists': { field: 'phone', title: 'Телефон уже используется', message: 'Этот телефон связан с другим профилем. Восстановите доступ к нему. Если номер ваш, а профиль незнаком — обратитесь к организатору.' },
        'name-rejected': { field: 'last', title: 'Не удалось проверить фамилию', message: 'Проверьте написание фамилии. Если всё верно, обратитесь к организатору: проверка могла ошибиться. Не меняйте настоящую фамилию ради регистрации.' },
        'captcha': { title: 'Проверка безопасности не завершена', message: 'Не удалось выполнить проверку безопасности. Повторите её, затем отправьте форму. Введённые данные сохранены на этой странице.' }
    };
    const server = state ? serverErrors[state] : undefined;
    const locked = state === 'rate-limit' && remaining > 0;
    const errorFor = (key: string) => server?.field === key ? server.message : errors[key];
    function clearPreview() {
        const next = new URLSearchParams(params); next.delete('state');
        setParams(next, { replace: true });
    }
    function update<K extends keyof RegistrationFields>(key: K, value: RegistrationFields[K]) {
        const next = { ...fields, [key]: value }; setFields(next);
        const checked = registrationErrors(next);
        setErrors(previous => {
            const result = { ...previous };
            if (submitted || touched.has(key)) result[key] = checked[key];
            if (key === 'password' && (submitted || touched.has('confirm'))) result.confirm = checked.confirm;
            return result;
        });
    }
    function blur(key: keyof RegistrationFields) {
        setTouched(previous => new Set(previous).add(key));
        setErrors(previous => ({ ...previous, [key]: registrationErrors(fields)[key] }));
    }
    function focusField(key: string) {
        const id = key === 'password' ? 'auth-password' : key === 'confirm' ? 'confirm-password' : `register-${key}`;
        const input = formRef.current?.querySelector<HTMLElement>(`#${id}`);
        input?.focus(); input?.scrollIntoView({ block: 'center', behavior: 'smooth' });
    }
    function submit(e: FormEvent<HTMLFormElement>) {
        e.preventDefault(); if (locked) return;
        setSubmitted(true);
        const next = registrationErrors(fields); setErrors(next);
        const first = Object.keys(next).find(key => next[key as keyof RegistrationFields]);
        if (first) { focusField(first); return; }
        if (server) { if (server.field) focusField(server.field); return; }
        const signup: User = { id: emailId(fields.email), first: fields.first.trim(), last: fields.last.trim(), email: fields.email.trim().toLowerCase(), phone: fields.phone.trim(), telegram: fields.telegram.trim(), level: fields.level === 'Не знаю' ? undefined : fields.level, preferredLocale: fields.locale as 'ru' | 'es' | 'en', authProvider: 'email', emailVerified: true };
        signIn(signup); setFields(v => ({ ...v, password: '', confirm: '' })); navigate(destination, { replace: true });
    }
    if (user) return <AlreadySignedIn />;
    const bind = (key: Exclude<keyof RegistrationFields, 'terms'>) => ({ value: fields[key], onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => update(key, e.target.value), onBlur: () => blur(key) });
    return <AuthFrame title="Знакомимся — и на корт" intro={event ? 'Создайте профиль, затем подтвердите участие. Мероприятие уже выбрано.' : 'Один профиль для мероприятий, записей и результатов.'}>
        {server && <div className="auth-state-banner auth-state-error" role="alert"><Icon name="info"/><div><strong>{server.title}</strong><p>{server.message}</p>{state?.includes('exists') && <div className="auth-error-actions"><Link className="text-btn" to={`/login${suffix}`} state={{ email: fields.email }}>Войти в аккаунт</Link><Link className="text-btn" to={`/forgot-password${suffix}`}>Восстановить доступ</Link></div>}<button className="text-btn" type="button" onClick={() => { const key = server.field; clearPreview(); if (key) focusField(key); }}>Вернуться к заполнению</button><p className="auth-field-hint">Демонстрация ответа сервера. Реальная проверка не выполнялась.</p></div></div>}
        {state === 'rate-limit' && <div className="auth-state-banner auth-state-warning" role="status"><Icon name="info"/><div><strong>{locked ? 'Регистрация временно приостановлена' : 'Можно повторить попытку'}</strong><p>{locked ? 'Сервис ограничил частые обращения. Это не означает, что ваши данные неверны. Пока можно проверить и исправить поля ниже.' : 'Проверьте данные и отправьте форму ещё раз.'}</p><p>Повторная отправка через <strong role="timer">{Math.floor(remaining / 60)}:{String(remaining % 60).padStart(2, '0')}</strong></p><p className="auth-field-hint">Демонстрация ожидания 15 минут. В рабочем сайте срок приходит от сервера.</p><button type="button" className="text-btn" onClick={() => { setRetryAt(Date.now()); setRemaining(0); clearPreview(); }}>Завершить демопоказ ожидания</button></div></div>}
        <GoogleButton /><div className="auth-divider">или зарегистрируйтесь по email</div>
        <p className="registration-required">Все поля обязательны, кроме Telegram. Ошибки можно исправить здесь — без повторного заполнения формы.</p>
        <form ref={formRef} className="auth-page-form" onSubmit={submit} noValidate>
            <div className="name-fields"><AuthField label="Имя" name="register-first" autoComplete="given-name" required maxLength={100} {...bind('first')} error={errorFor('first')} hint="3–100 символов. Буквы, пробел, дефис, точка, апостроф."/><AuthField label="Фамилия" name="register-last" autoComplete="family-name" required maxLength={100} {...bind('last')} error={errorFor('last')} hint="Настоящая фамилия, 3–100 символов, без цифр."/></div>
            <AuthField label="Email" name="register-email" type="email" inputMode="email" autoComplete="email" required maxLength={150} placeholder="you@example.com" {...bind('email')} error={errorFor('email')} hint="Укажите постоянную почту, к которой у вас есть доступ."/>
            <AuthField label="Телефон" name="register-phone" type="tel" inputMode="tel" autoComplete="tel" required maxLength={20} placeholder="+54 911 5555 0101" {...bind('phone')} error={errorFor('phone')} hint="С кодом страны. Цифры, пробелы, дефисы; без скобок. До 20 символов."/>
            <AuthField label="Telegram — необязательно" name="register-telegram" maxLength={33} placeholder="@username" {...bind('telegram')} error={errorFor('telegram')} hint="Username из Telegram, не имя профиля и не ссылка. Можно оставить пустым."/>
            <div className="name-fields"><div className="auth-field"><label htmlFor="register-level">Уровень</label><select id="register-level" {...bind('level')} required aria-invalid={!!errors.level} aria-describedby="level-help"><option value="">Выберите</option>{levelOptions.map(l => <option key={l}>{l}</option>)}</select><p className={errors.level ? 'form-error' : 'auth-field-hint'} id="level-help">{errors.level || '«Не знаю» — организатор поможет оценить уровень.'}</p></div><div className="auth-field"><label htmlFor="register-locale">Язык общения</label><select id="register-locale" {...bind('locale')}><option value="ru">Русский</option><option value="es">Español</option><option value="en">English</option></select></div></div>
            <PasswordField value={fields.password} onChange={s => update('password', s)} onBlur={() => blur('password')} error={errors.password} newPassword hint="От 8 до 100 символов. Заглавные буквы, цифры и спецсимволы не обязательны."/>
            {fields.password && <p className={`password-progress ${fields.password.length >= 8 ? 'valid' : ''}`} role="status">{fields.password.length >= 8 ? '✓ Длина пароля подходит' : `Ещё ${8 - fields.password.length} симв. до минимальной длины`}</p>}
            <PasswordField id="confirm-password" label="Повторите пароль" value={fields.confirm} onChange={s => update('confirm', s)} onBlur={() => blur('confirm')} error={errors.confirm} newPassword hint="Введите точно такой же пароль."/>
            <label className="auth-consent"><input id="register-terms" type="checkbox" checked={fields.terms} onChange={e => update('terms', e.target.checked)} required aria-invalid={!!errors.terms} aria-describedby={errors.terms ? 'terms-error' : undefined}/><span>Я ознакомился с <Link to="/legal/terms" target="_blank" rel="noopener">условиями участия и использования сервиса</Link>. <Link to="/legal/privacy" target="_blank" rel="noopener">Как используются мои данные</Link>.</span></label>
            {errors.terms && <p id="terms-error" className="form-error">{errors.terms}</p>}
            {submitted && Object.values(errors).some(Boolean) && <div className="registration-error-list" role="alert"><strong>Проверьте отмеченные поля</strong><p>Данные не отправлены. Нажмите на поле, чтобы исправить его.</p>{Object.entries(errors).filter(([,error]) => error).map(([key]) => <button key={key} type="button" onClick={() => focusField(key)}>{({first:'Имя',last:'Фамилия',email:'Email',phone:'Телефон',telegram:'Telegram',level:'Уровень',password:'Пароль',confirm:'Повтор пароля',terms:'Условия участия'} as Record<string,string>)[key]}</button>)}</div>}
            <button className="btn full" type="submit" disabled={locked}>{locked ? 'Ожидаем разрешения на повтор' : 'Создать профиль'}<Icon name="arrow"/></button>
        </form>
        <p className="auth-page-switch">Уже с нами? <Link to={`/login${suffix}`} state={{ email: fields.email }}>Войти</Link></p><DemoNote />
    </AuthFrame>;
}
// Keep previously shared URLs usable without requiring an email code.
export function LegacyVerifyEmailRedirect() {
    const { user } = useApp();
    const { suffix, destination } = useAuthDestination();
    return <Navigate to={user ? (user.phone ? destination : `/complete-profile${suffix}`) : `/register${suffix}`} replace />;
}
export function CompleteProfilePage() { const { user, updateUser, notify } = useApp(); const { suffix, destination } = useAuthDestination(); const navigate = useNavigate(); const [first, setFirst] = useState(user?.first || ''); const [last, setLast] = useState(user?.last || ''); const [phone, setPhone] = useState(user?.phone || ''); const [telegram, setTelegram] = useState(user?.telegram || ''); const [level, setLevel] = useState(user?.level || ''); const [locale, setLocale] = useState(user?.preferredLocale || 'ru'); const [terms, setTerms] = useState(false); const [errors, setErrors] = useState<Errors>({}); if (!user)
    return <AuthFrame title="Сначала войдите" intro="После входа добавьте телефон для связи по вашему участию."><Link className="btn full" to={`/login${suffix}`}>Войти<Icon name="arrow"/></Link></AuthFrame>; function submit(e: FormEvent<HTMLFormElement>) { e.preventDefault(); const next = { first: nameError(first, 'Имя'), last: nameError(last, 'Фамилия'), phone: validPhone(phone) ? undefined : 'Телефон обязателен. Укажите код страны, например +54 911 5555 0101.', telegram: validTelegram(telegram) ? undefined : 'Проверьте Telegram: например @username.', level: level ? undefined : 'Выберите уровень или «Не знаю».', terms: terms ? undefined : 'Ознакомьтесь с условиями и отметьте согласие.' }; setErrors(next); if (Object.values(next).some(Boolean))
    return; updateUser({ first: first.trim(), last: last.trim(), phone: phone.trim(), telegram: telegram.trim(), level: level === 'Не знаю' ? undefined : level, preferredLocale: locale as 'ru' | 'es' | 'en' }); notify('Демопрофиль дополнен'); navigate(destination, { replace: true }); } return <AuthFrame title="Остался ваш контакт" intro="Телефон обязателен: по нему организатор сможет связаться с вами перед мероприятием."><div className="auth-state-banner"><Icon name="check"/><p>{user.authProvider === 'google' ? 'Демовход через Google выполнен.' : 'Вход в демопрофиль выполнен.'} Участие ещё не подтверждено.</p></div><form className="auth-page-form" onSubmit={submit} noValidate><div className="name-fields"><AuthField label="Имя" name="complete-first" autoComplete="given-name" required maxLength={100} value={first} onChange={e => setFirst(e.target.value)} error={errors.first}/><AuthField label="Фамилия" name="complete-last" autoComplete="family-name" required maxLength={100} value={last} onChange={e => setLast(e.target.value)} error={errors.last}/></div><AuthField label="Телефон" name="complete-phone" type="tel" autoComplete="tel" required maxLength={20} placeholder="+54 911 5555 0101" value={phone} onChange={e => setPhone(e.target.value)} error={errors.phone}/><AuthField label="Telegram — необязательно" name="complete-telegram" placeholder="@username" maxLength={33} value={telegram} onChange={e => setTelegram(e.target.value)} error={errors.telegram}/><div className="name-fields"><div className="auth-field"><label htmlFor="complete-level">Уровень</label><select id="complete-level" value={level} onChange={e => setLevel(e.target.value)} required aria-invalid={!!errors.level}><option value="">Выберите</option>{levelOptions.map(l => <option key={l}>{l}</option>)}</select>{errors.level && <p className="form-error">{errors.level}</p>}</div><div className="auth-field"><label htmlFor="complete-locale">Язык общения</label><select id="complete-locale" value={locale} onChange={e => setLocale(e.target.value as 'ru' | 'es' | 'en')}><option value="ru">Русский</option><option value="es">Español</option><option value="en">English</option></select></div></div><label className="auth-consent"><input type="checkbox" checked={terms} onChange={e => setTerms(e.target.checked)}/><span>Я ознакомился с <Link to="/legal/terms" target="_blank" rel="noopener">условиями участия и использования сервиса</Link>.</span></label>{errors.terms && <p className="form-error">{errors.terms}</p>}{formErrors(errors)}<button className="btn full" type="submit">Сохранить и продолжить<Icon name="arrow"/></button></form><DemoNote /></AuthFrame>; }
export function ForgotPasswordPage() { const { suffix } = useAuthDestination(); const [email, setEmail] = useState(''); const [error, setError] = useState(''); const [submitted, setSubmitted] = useState(false); function submit(e: FormEvent<HTMLFormElement>) { e.preventDefault(); if (!validEmail(email)) {
    setError('Укажите корректный email.');
    return;
} setError(''); setSubmitted(true); } return <AuthFrame title="Восстановить доступ" intro="Укажите email, который использовали при регистрации.">{submitted ? <div className="auth-recovery-result" role="status"><span className="confirm-icon"><Icon name="info"/></span><h2>Следующий шаг — новый пароль</h2><p>Для <strong>{email.trim()}</strong> показан пример восстановления. Письмо не отправлялось.</p><Link className="btn full" to={`/reset-password${suffix}`} state={{ recoveryEmail: email.trim() }}>Открыть демосмену пароля<Icon name="arrow"/></Link><button className="text-btn auth-centered" onClick={() => setSubmitted(false)}>Изменить email</button></div> : <form className="auth-page-form" onSubmit={submit} noValidate><AuthField label="Email" name="recovery-email" type="email" inputMode="email" autoComplete="email" required maxLength={150} value={email} onChange={e => setEmail(e.target.value)} error={error}/><button type="submit" className="btn full">Продолжить<Icon name="arrow"/></button></form>}<Link className="text-btn auth-centered" to={`/login${suffix}`}>Вернуться ко входу</Link><DemoNote /></AuthFrame>; }
export function ResetPasswordPage() { const location = useLocation(); const { suffix } = useAuthDestination(); const email = (location.state as {
    recoveryEmail?: string;
} | null)?.recoveryEmail; const [password, setPassword] = useState(''); const [confirm, setConfirm] = useState(''); const [error, setError] = useState(''); const [done, setDone] = useState(false); if (!email)
    return <AuthFrame title="Ссылка недействительна" intro="Ссылка для смены пароля отсутствует или срок её действия истёк."><Link className="btn full" to={`/forgot-password${suffix}`}>Запросить восстановление<Icon name="arrow"/></Link><DemoNote /></AuthFrame>; function submit(e: FormEvent<HTMLFormElement>) { e.preventDefault(); if (password.length < 8 || password.length > 100) {
    setError('Пароль должен содержать от 8 до 100 символов.');
    return;
} if (password !== confirm) {
    setError('Пароли не совпадают.');
    return;
} setPassword(''); setConfirm(''); setError(''); setDone(true); } return <AuthFrame title={done ? 'Можно вернуться ко входу' : 'Новый пароль'} intro={done ? 'Демонстрация смены пароля завершена. Реальный пароль не менялся.' : `Придумайте новый пароль для ${email}.`}>{done ? <><span className="confirm-icon"><Icon name="check"/></span><Link className="btn full" to={`/login${suffix}`}>Войти<Icon name="arrow"/></Link></> : <form className="auth-page-form" onSubmit={submit} noValidate><PasswordField label="Новый пароль" value={password} onChange={setPassword} newPassword/><PasswordField id="reset-confirm" label="Повторите пароль" value={confirm} onChange={setConfirm} newPassword/>{error && <p className="form-error" role="alert">{error}</p>}<button className="btn full" type="submit">Сохранить новый пароль</button></form>}<DemoNote /></AuthFrame>; }
export function OAuthErrorPage() { const { suffix } = useAuthDestination(); return <AuthFrame title="Вход через Google не завершён" intro="Вход был отменён или сервис временно недоступен. Попробуйте ещё раз либо используйте email."><div className="auth-state-banner auth-state-warning"><Icon name="info"/><p>Выбранное мероприятие сохранено. Запись на него ещё не создана.</p></div><GoogleButton /><Link className="btn secondary full" to={`/login${suffix}`}>Войти по email</Link><DemoNote /></AuthFrame>; }
