import { useState, type FormEvent } from 'react';
import { Link, useParams, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useApp } from '../AppContext';
import { findEvent } from '../data/events';
import { money, entryLabel, occupancy } from '../domain/events';
import { Icon } from '../components/Icon';
import { Policy } from '../components/Policy';
import { Modal } from '../components/Modal';
import { PairRegistration } from '../components/PairRegistration';
import { validatePairSelection } from '../domain/pairs';
import { NotFoundPage } from './EventPage';
import type { Registration, PairSelection } from '../domain/types';
export function BookingPage() {
    const { id } = useParams();
    const original = findEvent(id);
    const [params, setParams] = useSearchParams();
    const { user, registrations, book } = useApp();
    const navigate = useNavigate();
    const [pair, setPair] = useState<PairSelection>({ mode: original?.format === 'pairs' && params.get('state') === 'partner-deadline' ? 'add-later' : 'with-partner' });
    const [accepted, setAccepted] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [uncertain, setUncertain] = useState(false);
    const [conflict, setConflict] = useState(false);
    const [result, setResult] = useState<Registration | null>(() => params.has('show') ? registrations.find(r => r.eventId === id) || null : null);
    if (!original)
        return <NotFoundPage />;
    if (['closed', 'cancelled', 'finished', 'live'].includes(params.get('state') || ''))
        return <Navigate to={'/event/' + original.id + '?state=' + params.get('state')} replace/>;
    const deadlinePassed = original.format === 'pairs' && params.get('state') === 'partner-deadline';
    const e = { ...original, partnerDeadlinePassed: deadlinePassed, ...(params.get('state') === 'full' || conflict ? { filled: original.max } : {}) };
    if (!user)
        return <Navigate to={`/register?event=${e.id}&eventState=${params.get('state') || 'open'}`} replace/>;
    if (!user.phone)
        return <Navigate to={`/complete-profile?event=${e.id}&eventState=${params.get('state') || 'open'}`} replace/>;
    if (e.live)
        return <Navigate to={'/live/' + e.format} replace/>;
    const existing = registrations.find(r => r.eventId === e.id && !['cancelled', 'completed'].includes(r.status));
    if (existing && !result && !busy)
        return <Navigate to="/games" replace/>;
    const waiting = e.max - occupancy(e) < (e.format === 'pairs' ? 2 : 1);
    const selectedPair = pair;
    const reserveApplication = waiting || (deadlinePassed && selectedPair.mode === 'add-later');
    const simulate = params.get('simulate');
    async function save() {
        if (!accepted || busy || !user)
            return;
        if (e.format === 'pairs') {
            const invalid = validatePairSelection(selectedPair, { user, eventLevel: e.level, full: waiting });
            if (invalid) {
                setError(invalid);
                return;
            }
        }
        setBusy(true);
        setError('');
        try {
            await new Promise(resolve => setTimeout(resolve, 450));
            if (simulate === 'conflict' && !conflict) {
                setConflict(true);
                setAccepted(false);
                setError('Последнее место занял другой игрок. Можно записаться в резерв — подтвердите обновлённые условия.');
                return;
            }
            if (simulate === 'offline')
                throw new Error('Не удалось отправить заявку. Проверьте соединение и попробуйте ещё раз.');
            if (simulate === 'unknown') {
                setUncertain(true);
                return;
            }
            setResult(await book(e, e.format === 'pairs' ? selectedPair : undefined));
        }
        catch (err) {
            setError(err instanceof Error ? err.message : 'Не удалось сохранить запись. Попробуйте ещё раз.');
        }
        finally {
            setBusy(false);
        }
    }
    const submit = (ev: FormEvent) => { ev.preventDefault(); void save(); };
    const close = () => navigate('/games');
    const incompleteReserve = result?.status === 'waiting' && e.format === 'pairs' && !result.partner;
    const isSolo = result?.status === 'solo-search' || result?.status === 'solo-later';
    const resultTitle = incompleteReserve ? 'Вы в резерве · без партнёра' : result?.status === 'waiting' ? `Вы в резерве · №${result.position}` : result?.status === 'solo-search' ? 'Вы в поиске партнёра' : result?.status === 'solo-later' ? 'Добавьте партнёра до срока' : 'Вы записаны!';
    return <section className="booking-flow view-space"><Link className="back-link" to={`/event/${e.id}`}><Icon name="back"/>О мероприятии</Link><div className="flow-layout"><div><div className="eyebrow">{entryLabel(e)}</div><h1 className="page-title">{waiting ? (e.format === 'pairs' ? 'Записаться в резерв' : 'Записаться в резерв') : deadlinePassed ? 'Запись после срока' : 'Подтвердите участие'}</h1><div className="auth-progress"><span>✓ Аккаунт</span><span className="active">2 · Участие</span></div>{e.format === 'pairs' && (waiting || deadlinePassed) && <div className="booking-state-notice" role="status"><Icon name="clock"/><div><strong>{waiting ? 'Основной состав заполнен' : 'Срок добавления партнёра прошёл'}</strong><p>{waiting ? 'Можно записаться готовой парой, добавить партнёра позже или искать его здесь. При освобождении места первыми проходят готовые пары. Пока у вас нет партнёра, место в основном составе не выделяется.' : 'Если выберете «Добавлю партнёра позже», заявка сохранится в резерве без партнёра. После его добавления готовая пара займёт место в конце очереди.'}</p></div></div>}<div className="confirm-event"><strong>{e.title} · {e.level}</strong><p>{e.date} · {e.time}</p><p>{e.club} · {e.address}</p><p>{money(e.price)} ARS за человека{e.format === 'pairs' ? ` · ${money(e.price * 2)} за пару` : ''}</p></div>{waiting && e.format !== 'pairs' && <div className="help-card"><strong>Состав уже собран</strong><p>Вы будете третьими в очереди. Резерв пока не подтверждает место в основном составе.</p></div>}<details className="booking-policy"><summary>Условия участия и отмены</summary><Policy event={e}/></details></div><form className="booking-confirm-panel" onSubmit={submit}><h2>{waiting ? 'Ваша заявка' : 'Ваша запись'}</h2><div className="booking-person"><Icon name="user"/><div><strong>{user.first} {user.last}</strong><p>{user.phone}</p></div></div>{e.format === 'pairs' && <PairRegistration value={selectedPair} onChange={setPair} full={waiting} expired={deadlinePassed}/>}
 <label className="consent"><input type="checkbox" checked={accepted} onChange={ev => setAccepted(ev.target.checked)} required/><span>Я ознакомился с условиями участия, отмены и опоздания{reserveApplication ? ' и понимаю, что место пока не подтверждено' : ''}.</span></label>{error && <div className="form-error" role="alert">{error}{simulate === 'offline' && <button className="text-btn" type="button" onClick={() => { setParams({}); setError('Соединение восстановлено в демопримере. Можно повторить запись.'); }}>Восстановить соединение</button>}</div>}
 {uncertain ? <div className="notice-banner"><Icon name="info"/><div><strong>Проверяем, сохранилась ли запись</strong><p>Ответ не получен. Не создавайте повторную заявку, пока не проверен её статус.</p><button type="button" className="btn secondary full" onClick={() => { setUncertain(false); setParams({}); setError('Статус проверен: заявки нет. Теперь можно повторить запись.'); }}>Проверить статус</button></div></div> : <button className="btn full" disabled={busy || !accepted}>{busy ? 'Сохраняем…' : waiting ? 'Записаться в резерв' : reserveApplication ? 'Сохранить заявку в резерве' : e.format === 'pairs' && selectedPair.mode !== 'with-partner' ? 'Сохранить заявку' : 'Подтвердить запись'}<Icon name="arrow"/></button>}
 <p className="demo-auth-note">Демонстрация. Реальная запись и оплата не создаются.</p></form></div><Modal open={!!result} onClose={close} title={resultTitle} kicker="Демозапись сохранена"><div className="confirm-icon"><Icon name={result?.status === 'waiting' || isSolo ? 'clock' : 'check'}/></div><p className="confirm-sub">{incompleteReserve ? 'Заявка сохранена. Готовые пары имеют приоритет. Когда вы сформируете пару, она встанет в конец очереди готовых пар.' : result?.status === 'waiting' ? 'Место пока не подтверждено. Позиция в очереди доступна в «Моих записях».' : isSolo ? 'Заявка сохранена. Готовая пара появится после добавления партнёра или принятия предложения.' : 'Мероприятие появилось в разделе «Мои записи».'}</p><div className="confirm-event"><strong>{e.title} · {e.level}</strong><p>{e.date} · {e.time} · {e.club}</p>{result?.partner && <p>{user.first} {user.last} и {result.partner}</p>}{result?.partnerDeadline && <p>Добавьте партнёра до {result.partnerDeadline}</p>}</div>{result?.status === 'confirmed' && <Policy event={e}/>}<div className="modal-sticky-actions"><button className="btn full" onClick={close}>Понятно · к моим записям <Icon name="check"/></button>{isSolo || incompleteReserve ? <Link className="text-btn" to={'/partners/' + e.id + (result?.partnerMode === 'add-later' ? '?mode=add' : '')}>Перейти к партнёру →</Link> : <Link className="text-btn" to="/app-info">Все записи под рукой · о приложении →</Link>}</div></Modal></section>;
}
