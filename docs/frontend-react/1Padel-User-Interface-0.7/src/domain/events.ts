import type { Event, Filters, Registration } from './types';
export const emptyFilters: Filters = { type: 'all', gender: 'all', level: 'all', club: 'all' };
export const eventKind = (e: Event): Filters['type'] => e.format === 'open' ? 'open' : e.format === 'training' ? 'training' : 'tournament';
export const entryLabel = (e: Event) => e.format === 'pairs' ? 'Запись парой' : 'Индивидуальная запись';
export const formatDescription = (e: Event) => ({ king: 'Партнёр не нужен. Побеждаете — переходите на корт выше.', americano: 'Партнёр не нужен. В каждом раунде — новая пара.', pairs: 'Выберите партнёра сейчас, добавьте позже или найдите его здесь. Играете постоянной парой.', open: 'Свободные матчи с другими участниками, без турнирной сетки.', training: 'Техника с тренером, затем практика в игре.' }[e.format]);
export const kindLabel = (e: Event) => e.format === 'training' ? 'Тренировка' : e.format === 'open' ? 'Свободная игра' : 'Турнир';
export const matchesFilter = (e: Event, f: Filters) => (f.type === 'all' || eventKind(e) === f.type) && (f.gender === 'all' || f.gender === e.gender) && (f.club === 'all' || e.club === f.club) && (f.level === 'all' || (e.format === 'open' ? ['C9', 'C8', 'C7', 'C6', 'D8', 'D7', 'D6', 'D5'] : e.level.split(/\s*\/\s*/)).includes(f.level));
export const occupancy = (e: Event, r?: Registration) => Math.min(e.max, e.filled + (r?.status === 'confirmed' && !e.live ? (e.format === 'pairs' ? 2 : 1) : 0));
export const money = (n: number) => '$' + n.toLocaleString('es-AR');
export const arrival = (time: string) => { const [h, m] = time.split(':').map(Number); const t = h * 60 + m - 15; return `${String(Math.floor(t / 60)).padStart(2, '0')}:${String(t % 60).padStart(2, '0')}`; };
export const fullName = (u: {
    first: string;
    last: string;
}) => `${u.first} ${u.last}`;
export const initials = (s: string) => s.split(' ').filter(Boolean).map(s => s[0]).join('').slice(0, 2);
