import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useApp } from '../AppContext';
import { findEvent, liveEvent } from '../data/events';
import { arrival, entryLabel, fullName, initials, money } from '../domain/events';
import type { Registration, User } from '../domain/types';
import { Icon } from '../components/Icon';
import { Modal } from '../components/Modal';
import { Policy } from '../components/Policy';
import { AuthField, validEmail, validPhone, validTelegram, levelOptions } from './AuthPages';
import '../styles/auth.css';
function GuestAccount({ profile = false }: {
    profile?: boolean;
}) { return <section className="guest-empty view-space"><span className="empty-symbol"><Icon name="user"/></span><h1>{profile ? 'Один профиль для всех записей' : 'Ваша первая игра впереди'}</h1><p>Выберите мероприятие. Создать профиль можно прямо при записи — выбранное событие сохранится.</p><Link className="btn" to="/events">Выбрать мероприятие<Icon name="arrow"/></Link><Link className="text-btn" to="/login">Уже есть аккаунт? Войти</Link></section>; }
const isHistory = (r: Registration) => r.status === 'cancelled' || r.status === 'completed';
const statusLabels: Record<Registration['status'], string> = { confirmed: 'Участие подтверждено', waiting: 'В резерве', offered: 'Вам предложено место', cancelled: 'Запись отменена', completed: 'Мероприятие завершено', 'solo-search': 'Ищу партнёра', 'solo-later': 'Добавлю партнёра позже' };
const paymentLabels = { unpaid: 'Ожидает оплаты', paid: 'Оплачено', pending: 'Оплата проверяется', failed: 'Не удалось оплатить', refunded: 'Возврат выполнен' };
const deadline = (value?: string) => { if (!value)
    return null; const d = new Date(value); return Number.isNaN(d.getTime()) ? value : d.toLocaleString('ru-RU', { timeZone: 'America/Argentina/Buenos_Aires', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' }); };
const awaitingParticipation = (r: Registration) => r.status === 'confirmed' && r.participationStatus === 'pending';
const profileFields = (user: User) => ({ first: user.first, last: user.last, email: user.email || '', phone: user.phone || '', telegram: user.telegram || '', level: user.level || 'Не знаю', locale: user.preferredLocale || 'ru' });
export function ProfilePage() {
    const { user, registrations, signOut, updateUser, notify } = useApp();
    const navigate = useNavigate();
    const [params, setParams] = useSearchParams();
    const [logoutOpen, setLogoutOpen] = useState(false);
    const [editOpen, setEditOpen] = useState(() => params.get('dialog') === 'edit');
    const [draft, setDraft] = useState(() => user ? profileFields(user) : { first: '', last: '', email: '', phone: '', telegram: '', level: 'Не знаю', locale: 'ru' });
    const [errors, setErrors] = useState<Record<string, string | undefined>>({});
    if (!user)
        return <GuestAccount profile/>;
    const confirmed = registrations.filter(r => r.status === 'confirmed').length;
    const waiting = registrations.filter(r => ['waiting', 'offered', 'solo-search', 'solo-later'].includes(r.status)).length;
    function submit(e: FormEvent<HTMLFormElement>) { e.preventDefault(); const next = { first: draft.first.trim().length >= 3 ? undefined : 'Укажите имя от 3 символов.', last: draft.last.trim().length >= 3 ? undefined : 'Укажите фамилию от 3 символов.', email: validEmail(draft.email) ? undefined : 'Укажите корректный email.', phone: validPhone(draft.phone) ? undefined : 'Укажите телефон с кодом страны.', telegram: validTelegram(draft.telegram) ? undefined : 'Проверьте имя в Telegram.' }; setErrors(next); if (Object.values(next).some(Boolean))
        return; if (params.get('state') === 'save-error') {
        setErrors({ save: 'Не удалось сохранить изменения. Ваши данные остались в форме. Попробуйте ещё раз.' });
        return;
    } const email = draft.email.trim().toLowerCase(); updateUser({ first: draft.first.trim(), last: draft.last.trim(), email, phone: draft.phone.trim(), telegram: draft.telegram.trim(), level: draft.level === 'Не знаю' ? undefined : draft.level, preferredLocale: draft.locale as 'ru' | 'es' | 'en', emailVerified: email === user?.email ? user?.emailVerified : false }); setEditOpen(false); notify('Данные демопрофиля сохранены'); }
    return <section className="profile view-space account-page"><div className="profile-top"><div className="avatar" aria-hidden="true">{initials(fullName(user))}</div><div><div className="eyebrow">Мой профиль</div><h1>{fullName(user).trim()}</h1><p>{user.level ? `Уровень ${user.level}` : 'Уровень уточняется'} · {user.preferredLocale === 'es' ? 'Español' : user.preferredLocale === 'en' ? 'English' : 'Русский'}</p></div></div>
    {!validPhone(user.phone || '') && <div className="auth-state-banner auth-state-warning"><Icon name="info"/><div><strong>Добавьте телефон</strong><p>Он нужен для связи по вашему участию.</p><Link className="text-btn" to="/complete-profile">Дополнить профиль<Icon name="arrow"/></Link></div></div>}

    <div className="account-counts" aria-label="Мои записи"><Link to="/games"><strong>{confirmed}</strong><span>Подтверждено</span></Link><Link to="/games"><strong>{waiting}</strong><span>Резерв и поиск пары</span></Link></div>
    <div className="content-section"><div className="account-section-heading"><h2>Личные данные</h2><button className="text-btn" onClick={() => { setDraft(profileFields(user)); setErrors({}); setEditOpen(true); }}>Изменить</button></div><dl className="account-contact-list"><div><dt>Email</dt><dd>{user.email || 'Не указан'}</dd></div><div><dt>Телефон</dt><dd>{user.phone || 'Не указан'}</dd></div><div><dt>Telegram</dt><dd>{user.telegram || 'Не указан'}</dd></div><div><dt>Уровень</dt><dd>{user.level || 'Нужна помощь с оценкой'}</dd></div></dl><p className="auth-field-hint">Контакты используются для участия в мероприятиях. Они не публикуются в списке игроков.</p></div>
    <div className="content-section"><h2>Всё к следующей встрече</h2><p className="account-description">Мероприятия, состав пары, резерв и статусы оплаты — в разделе «Мои записи».</p><Link className="btn full" to="/games">Мои записи<Icon name="arrow"/></Link></div><div className="content-section"><h2>Ваша история на корте</h2><p className="account-description">Результаты появятся после участия. У лиги свой рейтинг; общий рейтинг по турнирам — отдельное направление, пока недоступное в этом интерфейсе.</p></div>
    <div className="prototype-note">Демоданные сохраняются только в этом браузере. Реальный аккаунт, бронирования и платежи здесь не создаются.</div><button className="text-btn account-signout" onClick={() => setLogoutOpen(true)}>Выйти из демопрофиля<Icon name="arrow"/></button>
    <Modal open={editOpen} title="Личные данные" onClose={() => setEditOpen(false)}><form className="auth-page-form" onSubmit={submit} noValidate><div className="name-fields"><AuthField label="Имя" name="profile-first" autoComplete="given-name" required maxLength={100} value={draft.first} onChange={e => setDraft(v => ({ ...v, first: e.target.value }))} error={errors.first}/><AuthField label="Фамилия" name="profile-last" autoComplete="family-name" required maxLength={100} value={draft.last} onChange={e => setDraft(v => ({ ...v, last: e.target.value }))} error={errors.last}/></div><AuthField label="Email" name="profile-email" type="email" autoComplete="email" required maxLength={120} value={draft.email} onChange={e => setDraft(v => ({ ...v, email: e.target.value }))} error={errors.email}/><AuthField label="Телефон" name="profile-phone" type="tel" autoComplete="tel" required maxLength={24} value={draft.phone} onChange={e => setDraft(v => ({ ...v, phone: e.target.value }))} error={errors.phone}/><AuthField label="Telegram — необязательно" name="profile-telegram" maxLength={33} value={draft.telegram} onChange={e => setDraft(v => ({ ...v, telegram: e.target.value }))} error={errors.telegram}/><div className="name-fields"><div className="auth-field"><label htmlFor="profile-level">Уровень</label><select id="profile-level" value={draft.level} onChange={e => setDraft(v => ({ ...v, level: e.target.value }))}>{levelOptions.map(level => <option key={level}>{level}</option>)}</select></div><div className="auth-field"><label htmlFor="profile-locale">Язык общения</label><select id="profile-locale" value={draft.locale} onChange={e => setDraft(v => ({ ...v, locale: e.target.value }))}><option value="ru">Русский</option><option value="es">Español</option><option value="en">English</option></select></div></div>{Object.entries(errors).some(([key, value]) => key !== 'save' && value) && <p className="form-error" role="alert">Проверьте отмеченные поля.</p>}{errors.save && <div className="auth-state-banner auth-state-warning" role="alert"><div><strong>Изменения не сохранены</strong><p>{errors.save}</p><button className="text-btn" type="button" onClick={() => { const next = new URLSearchParams(params); next.delete('state'); setParams(next, { replace: true }); setErrors({}); }}>Попробовать ещё раз</button><p className="auth-field-hint">Демоошибка снята после нажатия. Затем сохраните данные снова.</p></div></div>}<p className="auth-field-hint">Это локальное демоизменение.</p><button className="btn full" type="submit">Сохранить данные<Icon name="check"/></button></form></Modal>
    <Modal open={logoutOpen} title="Выйти из демопрофиля?" onClose={() => setLogoutOpen(false)}><p className="confirm-sub">Выход очистит имя и демозаписи в этом браузере. На действующий сайт это не повлияет.</p><div className="modal-actions"><button className="btn secondary" onClick={() => setLogoutOpen(false)}>Остаться</button><button className="btn" onClick={() => { signOut(); setLogoutOpen(false); notify('Вы вышли из демопрофиля'); navigate('/home'); }}>Выйти</button></div></Modal>
  </section>;
}
export function MyEventsPage() {
    const { user, registrations, cancel, updateRegistration, notify } = useApp();
    const navigate = useNavigate();
    const [params, setParams] = useSearchParams();
    const [tab, setTab] = useState<'upcoming' | 'history'>(() => registrations.length > 0 && registrations.every(isHistory) ? 'history' : 'upcoming');
    const [action, setAction] = useState<{
        type: 'cancel' | 'offer' | 'payment';
        eventId: string;
    } | null>(() => { const dialog = params.get('dialog'); if (dialog !== 'payment' && dialog !== 'cancel')
        return null; const selected = registrations.find(r => (!params.get('event') || r.eventId === params.get('event')) && (dialog === 'payment' ? r.status === 'confirmed' && !awaitingParticipation(r) && (!r.paymentStatus || ['unpaid', 'failed'].includes(r.paymentStatus)) : !isHistory(r))); return selected ? { type: dialog, eventId: selected.eventId } : null; });
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [terms, setTerms] = useState(false);
    if (!user)
        return <GuestAccount />;
    const sorted = [...registrations].filter(r => tab === 'history' ? isHistory(r) : !isHistory(r)).sort((a, b) => { const ea = findEvent(a.eventId), eb = findEvent(b.eventId); if (!ea)
        return 1; if (!eb)
        return -1; return ((ea.day === 1 ? 32 : ea.day) - (eb.day === 1 ? 32 : eb.day)) || ea.time.localeCompare(eb.time); });
    const target = registrations.find(r => r.eventId === action?.eventId);
    const targetEvent = findEvent(action?.eventId);
    function open(type: 'cancel' | 'offer' | 'payment', r: Registration) { setAction({ type, eventId: r.eventId }); setError(''); setTerms(false); }
    async function confirmCancel() { if (!target || busy)
        return; setBusy(true); setError(''); try {
        if (params.get('state') === 'cancel-error')
            throw new Error('Не удалось отменить запись. Ваше участие сохранено. Попробуйте ещё раз.');
        await cancel(target.eventId);
        notify(target.status === 'waiting' ? 'Вы вышли из деморезерва' : 'Демозапись отменена и перенесена в историю');
        setAction(null);
    }
    catch (e) {
        setError(e instanceof Error ? e.message : 'Не удалось отменить запись. Попробуйте ещё раз.');
    }
    finally {
        setBusy(false);
    } }
    function confirmOffer() { if (!target || !awaitingParticipation(target)) {
        setError('Предложение уже изменилось. Закройте окно и проверьте запись.');
        return;
    } if (!terms) {
        setError('Ознакомьтесь с условиями и подтвердите согласие.');
        return;
    } if (!validPhone(user?.phone || '')) {
        setAction(null);
        navigate(`/complete-profile?event=${encodeURIComponent(target.eventId)}`);
        return;
    } updateRegistration(target.eventId, { participationStatus: 'confirmed' }); setAction(null); notify('Участие подтверждено в демопримере'); }
    function confirmPayment() { if (!target || target.status !== 'confirmed') {
        setError('Статус записи изменился. Оплата недоступна.');
        return;
    } updateRegistration(target.eventId, { paymentStatus: 'paid' }); setAction(null); notify('Демооплата отмечена. Деньги не списывались.'); }
    return <section className="view-space account-page"><div className="games-header"><div className="eyebrow">Ваш календарь мероприятий</div><h1 className="page-title">Мои записи</h1><p>{user.first}, здесь ваши мероприятия, резерв и поиск партнёра.</p></div><div className="account-tabs" role="tablist" aria-label="Записи"><button role="tab" aria-selected={tab === 'upcoming'} className={tab === 'upcoming' ? 'active' : ''} onClick={() => setTab('upcoming')}>Предстоящие <span>{registrations.filter(r => !isHistory(r)).length}</span></button><button role="tab" aria-selected={tab === 'history'} className={tab === 'history' ? 'active' : ''} onClick={() => setTab('history')}>История <span>{registrations.filter(isHistory).length}</span></button></div><div className="games-list account-events">
    {!sorted.length && <div className="account-empty-card"><span className="empty-symbol"><Icon name="calendar"/></span><h2>{tab === 'history' ? 'История пока пустая' : 'Выберите следующую встречу'}</h2><p>{tab === 'history' ? 'Завершённые и отменённые мероприятия появятся здесь.' : 'Здесь появятся мероприятия, на которые вы запишетесь. Создание аккаунта само по себе место не бронирует.'}</p><Link className="btn" to="/events">Выбрать мероприятие<Icon name="arrow"/></Link></div>}
    {sorted.map(r => {
            const event = findEvent(r.eventId), solo = ['solo-search', 'solo-later'].includes(r.status), pendingParticipation = awaitingParticipation(r);
            const waitingForPartner = event?.format === 'pairs' && r.status === 'waiting' && !r.partner && !r.partnerId;
            return <article className={`game-ticket account-ticket ${awaitingParticipation(r) ? 'account-offer-ticket' : ''}`} key={r.eventId}><div className="account-ticket-info"><span className={`pill ${['waiting', 'offered', 'solo-search', 'solo-later'].includes(r.status) ? 'amber' : isHistory(r) ? 'gray' : ''}`}><Icon name={r.status === 'confirmed' ? 'check' : r.status === 'completed' ? 'trophy' : 'clock'}/>{pendingParticipation ? 'В основном составе · ждём подтверждения' : statusLabels[r.status]}</span><h2>{event ? `${event.title} · ${event.level}` : 'Мероприятие недоступно'}</h2>{event ? <><p>{event.date} · {event.time} · {event.duration}</p><p>{event.club} · {event.address}</p><p className="account-entry">{entryLabel(event)} · {money(event.price)} ARS за человека{event.format === 'pairs' ? ` · ${money(event.price * 2)} ARS за пару` : ''}</p>{r.partner && <p><strong>Ваш партнёр:</strong> {r.partner}</p>}
      {r.status === 'waiting' && <div className="account-status-note"><Icon name="clock"/><p>{waitingForPartner ? 'Вы в резерве без партнёра. Готовые пары проходят вперёд. После формирования пары вы займёте место в конце очереди готовых пар.' : <>Место в очереди: <strong>{r.position ?? 'уточняется'}</strong>. Участие пока не подтверждено.</>}</p></div>}
      {pendingParticipation && <div className="account-status-note"><Icon name="info"/><p>Вы уже переведены в основной состав. Подтвердите, что придёте, или откажитесь от места. Таймера подтверждения нет.</p></div>}
      {solo && <div className="account-status-note"><Icon name="users"/><p>{r.status === 'solo-search' ? 'Вы ищете партнёра. Запись пары ещё не подтверждена.' : 'Добавьте партнёра, чтобы завершить запись пары.'}{r.partnerDeadline ? <> Срок: <strong>{deadline(r.partnerDeadline)}</strong>.</> : ''}</p></div>}
      {solo && r.proposalStatus && <p className="account-proposal-status">{({ pending: 'Предложение партнёру отправлено — ожидаем ответ.', accepted: 'Партнёр принял предложение.', declined: 'Предложение отклонено — можно выбрать другого партнёра.', expired: 'Срок предложения партнёру истёк.' })[r.proposalStatus]}</p>}
      {r.status === 'confirmed' && <div className="joined-label"><Icon name="calendar"/>Приезжайте к {arrival(event.time)}</div>}
      {r.paymentStatus && <div className={`account-payment-status ${r.paymentStatus === 'failed' ? 'warning' : ''}`}><span>Оплата</span><strong>{paymentLabels[r.paymentStatus]}</strong></div>}
      {r.status === 'cancelled' && <p className="account-proposal-status">Финансовый статус показывается отдельно: отмена записи сама по себе не подтверждает возврат.</p>}
    </> : <p>Подробности отсутствуют. Состояние вашей записи сохранено.</p>}</div><div className="account-ticket-actions">
      {pendingParticipation && <button className="btn" onClick={() => open('offer', r)}>Подтвердить участие<Icon name="check"/></button>}
      {(solo || waitingForPartner) && <Link className="btn" to={r.partnerMode === 'add-later' ? `/partners/${r.eventId}?mode=add` : `/partners/${r.eventId}`}>{r.partnerMode === 'add-later' ? 'Добавить партнёра' : 'Найти партнёра'}<Icon name="arrow"/></Link>}
      {r.status === 'confirmed' && !pendingParticipation && (!r.paymentStatus || ['unpaid', 'failed'].includes(r.paymentStatus)) && <button className="btn" onClick={() => open('payment', r)}>{r.paymentStatus === 'failed' ? 'Повторить демооплату' : 'Посмотреть оплату'}<Icon name="arrow"/></button>}
      {event && <Link className="btn secondary" to={`/event/${event.id}`}>О мероприятии<Icon name="arrow"/></Link>}
      {r.status === 'completed' && event?.live && <Link className="text-btn" to="/live">Результаты<Icon name="arrow"/></Link>}
      {!isHistory(r) && <button className="text-btn" onClick={() => open('cancel', r)}>{r.status === 'waiting' ? 'Выйти из резерва' : awaitingParticipation(r) ? 'Отказаться от места' : solo ? 'Отменить заявку' : 'Отменить запись'}</button>}
    </div></article>;
        })}</div>
    <aside className="account-live-example"><div><span className="eyebrow">Отдельный демопример</span><h2>Как выглядит турнир в игре</h2><p>{liveEvent.title} · корты, результаты и история матчей. Пример не добавляется в ваши записи.</p></div><Link className="btn secondary" to="/live">Посмотреть пример<Icon name="arrow"/></Link></aside>
    <Modal open={!!action} title={action?.type === 'offer' ? 'Подтвердить участие?' : action?.type === 'payment' ? 'Оплата участия' : target?.status === 'waiting' ? 'Выйти из резерва?' : 'Отменить участие?'} kicker={action?.type === 'payment' ? 'Демонстрация оплаты' : 'Управление записью'} onClose={() => { if (!busy)
        setAction(null); }}>
      {targetEvent && <div className="confirm-event"><strong>{targetEvent.title} · {targetEvent.level}</strong><p>{targetEvent.date} · {targetEvent.time} · {targetEvent.club}</p>{target?.partner && <p>Партнёр: {target.partner}</p>}</div>}
      {action?.type === 'payment' ? <><div className="account-payment-total"><span>{targetEvent?.format === 'pairs' ? 'За пару' : 'За участие'}</span><strong>{targetEvent ? money(targetEvent.price * (targetEvent.format === 'pairs' ? 2 : 1)) : '—'} ARS</strong></div><p className="confirm-sub">Это макет оплаты. Не нужно вводить банковские данные: средства не списываются, платёж организатору не поступает.</p><button className="btn full" onClick={confirmPayment}>Подтвердить демооплату<Icon name="check"/></button></> : action?.type === 'offer' ? <><p>Вы уже в основном составе. Подтверждение сообщает организатору, что вы придёте.</p>{targetEvent && <Policy event={targetEvent}/>}<label className="auth-consent"><input type="checkbox" checked={terms} onChange={e => setTerms(e.target.checked)}/><span>Условия участия и отмены понятны.</span></label><p className="prototype-note">Подтверждается только демозапись. Реальные места не бронируются.</p><div className="modal-actions"><button className="btn secondary" onClick={() => setAction(null)}>Закрыть</button><button className="btn" onClick={confirmOffer}>Подтвердить участие</button></div></> : <>{target?.participationStatus === 'pending' ? <p className="confirm-sub">Вы будете удалены из основного состава. Место автоматически получит следующий участник резерва; в парном турнире — следующая готовая пара.</p> : target?.status === 'waiting' ? <p className="confirm-sub">Вы потеряете текущую позицию в очереди. При повторной записи попадёте в конец резерва.</p> : target?.status === 'confirmed' && targetEvent ? <details className="account-cancel-policy" open><summary>Условия отмены</summary><Policy event={targetEvent}/></details> : <p className="confirm-sub">Заявка будет отменена. Для участия потребуется записаться снова.</p>}<p className="prototype-note">Это демонстрация. Запись перейдёт в историю. Реальные платежи, возвраты и бронирования не изменяются.</p><div className="modal-actions"><button className="btn secondary" disabled={busy} onClick={() => setAction(null)}>Оставить запись</button><button className="btn" disabled={busy} onClick={confirmCancel}>{busy ? 'Отменяем…' : 'Подтвердить отмену'}</button></div></>}{error && <div role="alert"><p className="form-error">{error}</p>{action?.type === 'cancel' && params.get('state') === 'cancel-error' && <><button className="text-btn" type="button" onClick={() => { const next = new URLSearchParams(params); next.delete('state'); setParams(next, { replace: true }); setError(''); }}>Попробовать ещё раз</button><p className="auth-field-hint">В демопримере кнопка снимает ошибку. Затем подтвердите отмену повторно.</p></>}</div>}
    </Modal></section>;
}
