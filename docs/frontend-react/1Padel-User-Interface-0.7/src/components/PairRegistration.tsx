import { useId, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useApp } from '../AppContext';
import { findEvent } from '../data/events';
import type { PairSelection } from '../domain/types';
import { candidateSelection, partnerName, partnerUnavailable, searchPartnerCandidates, type PartnerCandidate } from '../domain/pairs';
import { Icon } from './Icon';
import '../styles/partners.css';
export function PairRegistration({ value, onChange, full = false, expired = false, adding = false }: {
    value: PairSelection;
    onChange: (value: PairSelection) => void;
    full?: boolean;
    expired?: boolean;
    adding?: boolean;
}) {
    const uid = useId();
    const { id } = useParams();
    const event = findEvent(id);
    const { user } = useApp();
    const [query, setQuery] = useState('');
    const [manual, setManual] = useState(false);
    const [active, setActive] = useState<'first' | 'last' | 'phone' | 'telegram' | null>(null);
    const lookup = manual ? (active ? value[active] || '' : '') : query;
    const matches = searchPartnerCandidates(lookup);
    function select(p: PartnerCandidate) { onChange(candidateSelection(p)); setQuery(''); setActive(null); }
    function mode(next: PairSelection['mode']) { onChange({ mode: next, allowContact: false }); setQuery(''); setActive(null); setManual(false); }
    function field(key: 'first' | 'last' | 'phone' | 'telegram', text: string) { onChange({ ...value, [key]: text, partnerId: undefined, partner: undefined }); setActive(key); }
    const rows = matches.length ? <div className="partner-suggestions" aria-label="Найденные игроки"><p>Возможно, ваш партнёр уже есть на сайте</p>{matches.map(p => { const reason = partnerUnavailable(p, { user, eventLevel: event?.level }); return <button type="button" className="partner-suggestion" key={p.id} disabled={!!reason} onClick={() => select(p)}><span className="partner-mini-avatar">{p.first[0]}{p.last[0]}</span><span><strong>{partnerName(p)}</strong><small>{reason || `${p.level} · WhatsApp …${p.phone.slice(-4)}`}</small></span>{!reason && <Icon name="arrow"/>}</button>; })}</div> : null;
    return <div className="pair-registration">{!adding && <fieldset className="pair-mode-fieldset"><legend>Как хотите участвовать?</legend><div className="pair-mode-list">{([
                ['with-partner', 'users', 'У меня есть партнёр', 'Выберите игрока или укажите его данные.'],
                ['add-later', 'clock', 'Добавлю партнёра позже', 'Сохраните заявку и завершите состав пары позже.'],
                ['search', 'chat', 'Ищу партнёра', 'Найдите игрока на это мероприятие.'],
            ] as const).map(([key, icon, title, description]) => <label key={key} className={`pair-mode-option ${value.mode === key ? 'selected' : ''} `}><input type="radio" name={`pair-mode-${uid}`} value={key} checked={value.mode === key} onChange={() => mode(key)}/><Icon name={icon}/><span><strong>{title}</strong><small>{description}</small></span></label>)}</div></fieldset>}
  {value.mode === 'with-partner' ? <div className="partner-entry"><div className="partner-entry-header"><h3>{full && !adding ? 'Ваш партнёр · резерв' : 'Ваш партнёр'}</h3>{value.partnerId && <button type="button" className="text-btn" onClick={() => { onChange({ mode: 'with-partner' }); setManual(false); }}>Изменить</button>}</div>{value.partnerId ? <div className="partner-selected"><span className="partner-mini-avatar"><Icon name="check"/></span><div><strong>{value.partner || `${value.first} ${value.last}`}</strong><small>Игрок выбран. Повторно заполнять данные не нужно.</small></div></div> : <><div className="partner-methods"><button type="button" className={!manual ? 'active' : ''} aria-pressed={!manual} onClick={() => { setManual(false); setActive(null); }}>Найти на сайте</button><button type="button" className={manual ? 'active' : ''} aria-pressed={manual} onClick={() => { setManual(true); setQuery(''); }}>Ввести данные</button></div>{!manual ? <><label className="partner-field" htmlFor={`${uid}-search`}>Имя, фамилия, WhatsApp или Telegram<input id={`${uid}-search`} value={query} onChange={e => setQuery(e.target.value)} placeholder="Например, Алексей или Martín" autoComplete="off"/></label>{rows}{query.trim().length >= 2 && !matches.length && <div className="partner-search-empty"><p>Такого игрока в демопримере нет.</p><button className="text-btn" type="button" onClick={() => setManual(true)}>Указать данные партнёра <Icon name="arrow"/></button></div>}<p className="partner-hint">Введите хотя бы два символа. Не уверены в написании имени — найдите по номеру телефона.</p></> : <div className="partner-fields">{([
                        ['first', 'Имя', 'Алексей', 'text'], ['last', 'Фамилия', 'Петров', 'text'], ['phone', 'WhatsApp с кодом страны', '+54 11 5555 0123', 'tel'], ['telegram', 'Telegram · необязательно', '@username', 'text']
                    ] as const).map(([key, label, placeholder, type]) => <div key={key}><label className="partner-field" htmlFor={`${uid}-${key}`}>{label}<input id={`${uid}-${key}`} type={type} value={value[key] || ''} maxLength={key === 'phone' ? 20 : 50} required={key !== 'telegram'} onChange={e => field(key, e.target.value)} onFocus={() => setActive(key)} placeholder={placeholder} autoComplete="off"/></label>{active === key && rows}</div>)}<p className="partner-hint">Партнёру не нужно создавать аккаунт до записи. Имя и фамилия появятся в составе вашей пары.</p></div>}</>}{full && <div className="partner-info"><Icon name="clock"/><p>Ваша готовая пара попадёт в резерв. Место в основном составе пока не подтверждено.</p></div>}</div> : value.mode === 'add-later' ? <div className="partner-info"><Icon name="clock"/><div><strong>{full ? 'В резерве без партнёра' : expired ? 'Срок добавления партнёра уже прошёл' : 'Завершите пару за 24 часа до начала'}</strong><p>{full ? 'Добавьте партнёра в «Моих записях». Пока пара не готова, готовые пары проходят вперёд. После добавления партнёра вы займёте место в конце очереди готовых пар.' : expired ? 'Заявка сохранится в резерве без партнёра. Добавить его можно позже; готовая пара займёт место в конце очереди.' : 'Заявка ещё не является готовой парой. Добавьте партнёра в «Моих записях». Если не успеете к сроку, заявка перейдёт в резерв.'}</p></div></div> : <div className="partner-info"><Icon name="chat"/><div><strong>Найдите партнёра вместе с нами</strong><p>Вы появитесь в списке ищущих партнёра на это мероприятие. Можно предложить пару другому игроку. Пара сформируется только после принятия предложения.</p>{full && <p>Вы останетесь в резерве. Готовые пары имеют приоритет; после принятия предложения ваша пара встанет в конец очереди готовых пар.</p>}<label className="partner-contact-consent"><input type="checkbox" checked={!!value.allowContact} onChange={e => onChange({ ...value, allowContact: e.target.checked })}/><span>Разрешаю показывать мои контакты в списке поиска партнёра.</span></label><small>Необязательно. Без согласия ваши контакты скрыты.</small></div></div>}
  </div>;
}
