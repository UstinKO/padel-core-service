import { useState, type FormEvent } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useApp } from '../AppContext';
import { findEvent } from '../data/events';
import { money } from '../domain/events';
import type { PairSelection } from '../domain/types';
import { partnerCandidates, partnerName, partnerUnavailable, validatePairSelection, type PartnerCandidate } from '../domain/pairs';
import { PairRegistration } from '../components/PairRegistration';
import { Icon } from '../components/Icon';
import { Modal } from '../components/Modal';
import { NotFoundPage } from './EventPage';
import '../styles/partners.css';
export function PartnerPage() { const { id } = useParams(); return <PartnerContent key={id} id={id}/>; }
function PartnerContent({ id }: {
    id?: string;
}) {
    const event = findEvent(id);
    const { user, registrations, updateRegistration, notify } = useApp();
    const [params] = useSearchParams();
    const [selection, setSelection] = useState<PairSelection>({ mode: 'with-partner' });
    const [accepted, setAccepted] = useState(false);
    const [error, setError] = useState('');
    const [proposal, setProposal] = useState<PartnerCandidate | null>(null);
    if (!event || event.format !== 'pairs')
        return <NotFoundPage />;
    const own = registrations.find(r => r.eventId === event.id && !['cancelled', 'completed'].includes(r.status));
    const reserveSolo = own?.status === 'waiting' && !own.partner;
    const canAdd = own?.status === 'solo-later' || reserveSolo && (own.partnerMode !== 'search' || params.get('mode') === 'add');
    const canPropose = !!own && (['solo-search', 'solo-later'].includes(own.status) || !!reserveSolo);
    const ready = !!own?.partner && ['confirmed', 'waiting'].includes(own.status);
    const candidateOptions = { user, eventLevel: event.level };
    const rows = partnerCandidates.filter(p => p.searching);
    const deadlinePassed = params.get('state') === 'partner-deadline' || own?.status === 'waiting';
    const proposed = partnerCandidates.find(p => p.id === own?.proposalTo);
    const proposalName = proposed ? partnerName(proposed) : own?.proposalTo;
    function addPartner(ev: FormEvent) { ev.preventDefault(); if (!event || !canAdd || !accepted)
        return; const problem = validatePairSelection(selection, candidateOptions); if (problem) {
        setError(problem);
        return;
    } updateRegistration(event.id, { partner: selection.partner || `${selection.first?.trim()} ${selection.last?.trim()}`, partnerId: selection.partnerId, partnerMode: 'with-partner', status: deadlinePassed ? 'waiting' : 'confirmed', position: deadlinePassed ? 3 : undefined, readyAt: new Date().toISOString(), proposalTo: undefined, proposalState: undefined }); setError(''); notify(deadlinePassed ? 'Пара добавлена в конец деморезерва' : 'Демопара подтверждена'); }
    function sendProposal() { if (!event || !proposal || !canPropose)
        return; const reason = partnerUnavailable(proposal, candidateOptions); if (reason) {
        setError(reason);
        setProposal(null);
        return;
    } updateRegistration(event.id, { proposalTo: proposal.id, proposalState: 'sent' }); setProposal(null); notify('Предложение сохранено в демопримере. Письмо не отправлялось.'); }
    return <section className="partner-page view-space"><Link className="back-link" to={`/event/${event.id}`}><Icon name="back"/>О мероприятии</Link><header className="partner-page-head"><div className="eyebrow">Постоянная пара</div><h1>{ready ? 'Ваша пара готова' : canAdd ? 'Завершите вашу пару' : 'Найдите своего партнёра'}</h1><p>{ready ? 'Вы играете вместе весь турнир. Статус участия и дальнейшие действия — в ваших записях.' : 'Один турнир, один партнёр. Добавьте знакомого игрока или предложите пару тому, кто тоже ищет компанию.'}</p></header><div className="partner-context"><Icon name="trophy"/><div><strong>{event.title} · {event.level}</strong><p>{event.date} · {event.time} · {event.club}</p></div></div>
  {ready ? <div className="partner-panel"><span className="pill">{own.status === 'waiting' ? `Пара в резерве · №${own.position ?? '—'}` : 'Участие подтверждено'}</span><h2 style={{ marginTop: 18 }}>{user?.first} {user?.last}<br />& {own.partner}</h2><p>{own.status === 'waiting' ? 'Состав пары готов. Участие подтвердится, когда освободится место и дойдёт ваша очередь.' : 'Ваше место подтверждено. Приходите за 15 минут до начала.'}</p><Link className="btn" to="/games">Мои записи <Icon name="arrow"/></Link></div> : <div className="partner-page-layout"><div className="partner-panel">{canAdd ? <form onSubmit={addPartner}><div className="eyebrow">{deadlinePassed ? 'Заявка в резерве' : 'Ваша заявка сохранена'}</div><h2 style={{ marginTop: 12 }}>Добавить партнёра</h2><p className="partner-hint">{deadlinePassed ? 'После добавления партнёра готовая пара перейдёт в конец очереди резерва.' : 'Укажите данные второго игрока до ' + (own.partnerDeadline || 'срока, указанного организатором') + '.'}</p><PairRegistration value={selection} onChange={v => { setSelection(v); setAccepted(false); setError(''); }} adding full={deadlinePassed}/><label className="consent"><input type="checkbox" checked={accepted} onChange={e => setAccepted(e.target.checked)} required/><span>Партнёр согласен на участие. Мы ознакомились с условиями турнира.</span></label>{error && <p className="partner-error" role="alert">{error}</p>}<button className="btn full" disabled={!accepted}>Добавить партнёра <Icon name="check"/></button></form> : own?.status === 'solo-search' || reserveSolo ? <><div className="eyebrow">Вы в списке поиска</div><h2 style={{ marginTop: 12 }}>Партнёр пока не найден</h2>{reserveSolo && <p>Вы в резерве. Пока пара не готова, готовые пары проходят вперёд. После формирования пары вы встанете в конец очереди готовых пар.</p>}<p>Вы можете предложить пару игроку из списка. Отправленное предложение ещё не подтверждает пару — нужно дождаться согласия.</p><label className="partner-contact-consent"><input type="checkbox" checked={!!own.allowContact} onChange={e => { updateRegistration(event.id, { allowContact: e.target.checked }); notify(e.target.checked ? 'Показ контактов включён в демопримере' : 'Контакты скрыты'); }}/><span>Показывать мои контакты в списке поиска партнёра</span></label><p className="partner-hint">Сейчас контакты {own.allowContact ? 'видны' : 'скрыты'}. Решение можно изменить.</p><Link className="text-btn" to="/games">Моя заявка <Icon name="arrow"/></Link></> : <><h2>Тоже ищете партнёра?</h2><p>Сначала подайте заявку «Ищу партнёра» на это мероприятие. После этого сможете отправлять предложения другим игрокам.</p><Link className="btn full" to={user ? `/book/${event.id}` : `/register?event=${event.id}`}>Подать заявку <Icon name="arrow"/></Link></>}
    {own?.proposalState === 'sent' && <div className="partner-pending" role="status"><strong>Ждём ответ от {proposalName}</strong>Предложение отправлено. Вы всё ещё ищете партнёра, пока другой игрок не примет его.</div>}<p className="partner-demo-note">Демонстрационные игроки и заявки. Сообщения и письма не отправляются.</p></div>
    <div className="partner-panel"><div className="eyebrow">На это мероприятие</div><h2 style={{ marginTop: 12 }}>Ищут партнёра</h2><p>Полные имена и категория помогут выбрать. Контакты показываются только с согласия игрока.</p><div className="partner-candidates">{rows.map(p => { const reason = partnerUnavailable(p, candidateOptions); const sent = own?.proposalState === 'sent' && own.proposalTo === p.id; return <article className="partner-candidate" key={p.id}><div className="partner-candidate-head"><span className="partner-mini-avatar">{p.first[0]}{p.last[0]}</span><div><strong>{partnerName(p)}</strong><small>{p.level} · ищет партнёра</small></div></div><p>{reason || (p.shareContacts ? `Контакт для демо: ${p.telegram}` : 'Контакты скрыты. Можно отправить предложение через сайт.')}</p>{canPropose ? <button className="btn secondary" disabled={!!reason || sent} onClick={() => { setError(''); setProposal(p); }}>{sent ? 'Предложение отправлено' : 'Предложить пару'}{!sent && <Icon name="arrow"/>}</button> : <Link className="btn secondary" to={user ? `/book/${event.id}` : `/register?event=${event.id}`}>Записаться и предложить пару</Link>}</article>; })}</div></div></div>}
    <Modal open={!!proposal} onClose={() => setProposal(null)} title="Предложить пару?" kicker="Требуется согласие второго игрока"><div className="confirm-event"><strong>{proposal && partnerName(proposal)}</strong><p>{event.title} · {event.level}</p><p>{event.date} · {event.time}</p></div><p>Игрок получит предложение. До его принятия ваша заявка остаётся в поиске, а пара не считается готовой.</p><p className="partner-demo-note">В этом макете письмо не отправляется. Можно проверить только состояние интерфейса.</p><div className="modal-actions"><button className="btn secondary" onClick={() => setProposal(null)}>Отмена</button><button className="btn" onClick={sendProposal}>Отправить предложение</button></div></Modal>
  </section>;
}
type InvitationState = 'pending' | 'accepted' | 'declined' | 'expired' | 'incompatible';
const invitationStates: InvitationState[] = ['pending', 'accepted', 'declined', 'expired', 'incompatible'];
export function PartnerInvitationPage() { const [params] = useSearchParams(); const state = params.get('state') || 'pending'; return <PartnerInvitationContent key={`${params.get('event') || 'pairs'}-${state}`} eventId={params.get('event') || 'pairs'} initial={invitationStates.includes(state as InvitationState) ? state as InvitationState : 'expired'}/>; }
function PartnerInvitationContent({ eventId, initial }: {
    eventId: string;
    initial: InvitationState;
}) {
    const event = findEvent(eventId);
    const { user, registrations, updateRegistration, notify } = useApp();
    const [state, setState] = useState<InvitationState>(initial);
    const [agreed, setAgreed] = useState(false);
    const [error, setError] = useState('');
    if (!event || event.format !== 'pairs')
        return <NotFoundPage />;
    const proposer = partnerCandidates[0];
    const own = registrations.find(r => r.eventId === eventId && !['cancelled', 'completed'].includes(r.status));
    function accept() { if (!agreed || !event)
        return; if (own && !['solo-search', 'solo-later'].includes(own.status) && !(own.status === 'waiting' && !own.partner)) {
        setError('У вашей заявки уже другой статус. Проверьте её в «Моих записях».');
        return;
    } const reason = partnerUnavailable(proposer, { user, eventLevel: event.level }); if (reason) {
        setError(reason);
        return;
    } if (own)
        updateRegistration(eventId, { status: own.status === 'waiting' ? 'waiting' : 'confirmed', readyAt: new Date().toISOString(), partner: partnerName(proposer), partnerId: proposer.id, partnerMode: 'with-partner', proposalState: 'accepted', proposalTo: undefined, position: own.status === 'waiting' ? 3 : undefined }); setState('accepted'); notify('Принятие показано в демопримере'); }
    function decline() { if (own)
        updateRegistration(eventId, { proposalState: 'declined' }); setState('declined'); }
    const title = { pending: 'Вас приглашают в пару', accepted: 'Теперь вы — одна пара', declined: 'Предложение отклонено', expired: 'Срок предложения истёк', incompatible: 'Эта пара не подходит по категории' }[state];
    return <section className="partner-invite view-space"><Link className="back-link" to={`/event/${event.id}`}><Icon name="back"/>О мероприятии</Link><div className="partner-invite-card" style={{ marginTop: 25 }}><div className="partner-invite-avatar">{state === 'accepted' ? <Icon name="check"/> : state === 'expired' ? <Icon name="clock"/> : 'АП'}</div><div className="eyebrow" style={{ marginTop: 20 }}>Предложение пары</div><h1>{title}</h1><p>{state === 'pending' ? `${partnerName(proposer)} предлагает сыграть вместе. Решение за вами: открытие ссылки само по себе ничего не подтверждает.` : state === 'accepted' ? `Ваш партнёр — ${partnerName(proposer)}. В этом формате вы играете постоянной парой весь турнир.` : state === 'declined' ? 'Ваша одиночная заявка сохраняется. Вы можете продолжить поиск другого партнёра.' : state === 'expired' ? 'Эта ссылка больше не действует. Попросите игрока отправить новое предложение или выберите другого партнёра.' : 'Для этого примера категории игроков несовместимы. Пара не сформирована; выберите подходящего партнёра или уточните уровень у организатора.'}</p>{state === 'accepted' && own?.status === 'waiting' && <div className="notice-banner">Пара сформирована и добавлена в конец очереди готовых пар. Место в основном составе пока не выделено.</div>}<div className="partner-invite-details"><strong>{event.title} · {event.level}</strong><p>{event.date} · {event.time}<br />{event.club}<br />{money(event.price)} ARS за человека · {money(event.price * 2)} за пару</p></div>
  {state === 'pending' ? <><p className="partner-hint">Предложение действует 48 часов. Если статус другого игрока изменится, подтвердить пару не получится.</p><label className="consent" style={{ textAlign: 'left' }}><input type="checkbox" checked={agreed} onChange={e => setAgreed(e.target.checked)}/><span>Хочу играть в этой паре и ознакомился с <Link to={`/event/${event.id}`}>условиями турнира</Link>.</span></label>{error && <p className="partner-error" role="alert">{error}</p>}<div className="partner-invite-actions"><button className="btn full" disabled={!agreed} onClick={accept}>Принять предложение <Icon name="check"/></button><button className="btn secondary full" onClick={decline}>Отклонить</button></div></> : <div className="partner-invite-actions"><Link className="btn full" to={state === 'accepted' && own ? '/games' : `/partners/${event.id}`}>{state === 'accepted' && own ? 'Мои записи' : 'К поиску партнёра'}<Icon name="arrow"/></Link><Link className="text-btn" to={`/event/${event.id}`}>О мероприятии</Link></div>}
  <p className="partner-demo-note">Демонстрационный пример приглашения. Реальные предложения не отправляются. Изменения сохраняются только в текущей демосессии.</p></div></section>;
}
