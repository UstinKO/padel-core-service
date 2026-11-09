import type { PairSelection } from './types';
export interface PartnerCandidate {
    id: string;
    first: string;
    last: string;
    level: string;
    phone: string;
    telegram: string;
    available: boolean;
    searching: boolean;
    shareContacts: boolean;
}
export interface PairValidationOptions {
    user?: {
        id: string;
        first: string;
        last: string;
        phone?: string;
        telegram?: string;
    } | null;
    eventLevel?: string;
    full?: boolean;
}
/** Fictional people for the interaction prototype. These are never fetched or messaged. */
export const partnerCandidates: PartnerCandidate[] = [
    { id: 'partner-alex', first: 'Алексей', last: 'Петров', level: 'C7', phone: '+54 11 5555 0101', telegram: '@alex_demo', available: true, searching: true, shareContacts: true },
    { id: 'partner-martin', first: 'Martín', last: 'Ferreyra', level: 'C8', phone: '+54 11 5555 0102', telegram: '@martin_demo', available: true, searching: true, shareContacts: false },
    { id: 'partner-roman', first: 'Роман', last: 'Куликов', level: 'C6', phone: '+54 11 5555 0103', telegram: '@roman_demo', available: true, searching: true, shareContacts: false },
    { id: 'partner-ivan', first: 'Иван', last: 'Смирнов', level: 'C7', phone: '+54 11 5555 0104', telegram: '@ivan_demo', available: false, searching: false, shareContacts: false },
    { id: 'partner-lucas', first: 'Lucas', last: 'Acosta', level: 'C4', phone: '+54 11 5555 0105', telegram: '@lucas_demo', available: true, searching: true, shareContacts: false },
    { id: 'partner-sofia', first: 'Sofía', last: 'Pérez', level: 'D7', phone: '+54 11 5555 0106', telegram: '@sofia_demo', available: true, searching: true, shareContacts: true },
];
export const partnerName = (p: Pick<PartnerCandidate, 'first' | 'last'>) => `${p.first} ${p.last}`;
const normal = (s: string) => s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLowerCase().replace(/\s+/g, ' ');
export const phoneDigits = (s: string) => s.replace(/\D/g, '');
export function searchPartnerCandidates(query: string) { const q = normal(query); if (q.length < 2)
    return []; const digits = phoneDigits(q); return partnerCandidates.filter(p => normal(`${p.first} ${p.last} ${p.telegram}`).includes(q) || (digits.length >= 3 && phoneDigits(p.phone).includes(digits))); }
export function partnerUnavailable(p: PartnerCandidate, { user, eventLevel }: PairValidationOptions = {}) {
    if (user && (p.id === user.id || normal(partnerName(p)) === normal(`${user.first} ${user.last}`) || (user.phone && phoneDigits(user.phone) === phoneDigits(p.phone))))
        return 'Нельзя выбрать себя в качестве партнёра.';
    if (!p.available)
        return 'Этот игрок уже записан на мероприятие.';
    // Mirrors the simple-category rule only; composite categories are resolved by the server.
    const category = eventLevel?.match(/^([CD])(\d)$/);
    const level = p.level.match(/^([CD])(\d)$/);
    if (category && level && (category[1] !== level[1] || Math.abs(+category[2] - +level[2]) > 1))
        return `Категория ${p.level} не подходит для этого турнира ${eventLevel}.`;
    return null;
}
export function candidateSelection(p: PartnerCandidate): PairSelection { return { mode: 'with-partner', partner: partnerName(p), partnerId: p.id, first: p.first, last: p.last, phone: p.phone, telegram: p.telegram }; }
export function validatePairSelection(value: PairSelection, options: PairValidationOptions = {}): string | null {
    if (!['with-partner', 'add-later', 'search'].includes(value.mode))
        return 'Выберите способ участия.';
    if (value.mode !== 'with-partner')
        return null;
    if (value.partnerId) {
        const candidate = partnerCandidates.find(p => p.id === value.partnerId);
        return candidate ? partnerUnavailable(candidate, options) : 'Выберите игрока из списка ещё раз.';
    }
    if (!value.first?.trim() || !value.last?.trim())
        return 'Укажите имя и фамилию партнёра.';
    if (!value.phone?.trim() || !/^\+?[\d\s()-]{8,20}$/.test(value.phone.trim()) || phoneDigits(value.phone).length < 8)
        return 'Укажите WhatsApp партнёра с кодом страны.';
    if (value.telegram?.trim() && !/^@?[A-Za-z][A-Za-z0-9_]{4,31}$/.test(value.telegram.trim()))
        return 'Проверьте имя пользователя Telegram.';
    const user = options.user;
    if (user && (normal(`${value.first} ${value.last}`) === normal(`${user.first} ${user.last}`) || (user.phone && phoneDigits(user.phone) === phoneDigits(value.phone)) || (user.telegram && value.telegram && normal(user.telegram).replace(/^@/, '') === normal(value.telegram).replace(/^@/, ''))))
        return 'Укажите данные другого игрока: вы не можете быть своим партнёром.';
    const duplicate = partnerCandidates.find(p => phoneDigits(p.phone) === phoneDigits(value.phone || '') || (value.telegram && normal(p.telegram).replace(/^@/, '') === normal(value.telegram).replace(/^@/, '')));
    if (duplicate)
        return `Игрок ${partnerName(duplicate)} уже есть в списке. Выберите его, чтобы не создать дубль.`;
    return null;
}
