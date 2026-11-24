import {addDays, dateValue, monthDays, shiftMonth} from './admin-event-calendar-dates.mjs';
export {addDays, dateValue, monthDays, shiftMonth};
export const filterKeys = ['level', 'format', 'clubId', 'organizerId', 'gender', 'status'];
const commonKeys = ['view', 'month', 'day', ...filterKeys, 'lang'];
const validDay = value => /^\d{4}-\d{2}-\d{2}$/.test(value || '') &&
    Number(value.slice(0, 4)) >= 100 && Number(value.slice(0, 4)) < 9999 &&
    !Number.isNaN(dateValue(value).valueOf()) && dateValue(value).toISOString().slice(0, 10) === value;
export function readState(search, preferredView = 'cards') {
    const params = new URLSearchParams(search);
    const state = {view: preferredView === 'calendar' ? 'calendar' : 'cards', month: '', day: ''};
    if (['cards', 'calendar'].includes(params.get('view'))) state.view = params.get('view');
    if (validDay(`${params.get('month')}-01`)) state.month = params.get('month');
    if (validDay(params.get('day'))) state.day = params.get('day');
    for (const key of filterKeys) {
        const value = params.get(key) || '';
        state[key] = (key.endsWith('Id') ? /^[1-9]\d{0,14}$/ : /^[A-Z0-9_]{1,40}$/).test(value) ? value : '';
    }
    state.lang = ['ru', 'es', 'en'].includes(params.get('lang')) ? params.get('lang') : '';
    return state;
}
export function commonUrl(state) {
    const params = new URLSearchParams();
    for (const key of commonKeys) if (state[key]) params.set(key, state[key]);
    return `/torneos?${params}`;
}
export function safeReturn(value, origin) {
    if (!value || value.length > 2000) return '';
    try {
        const url = new URL(value, origin);
        if (url.origin !== origin || url.pathname !== '/torneos') return '';
        return commonUrl(readState(url.search));
    } catch { return ''; }
}
export const dayEvents = (events, day) => events.filter(event => event.date === day)
    .sort((a, b) => (a.time || '99:99').localeCompare(b.time || '99:99') || a.id - b.id);
export const extraCount = events => Math.max(events.length - 3, 0);
export function visibleEvents(events, personal, myOnly, status) {
    return events.filter(event => (!myOnly || personal.has(event.id)) && (!status || event.status === status));
}
export function cardOrder(events, today) {
    return [...events].sort((a, b) => {
        const aPast = a.date < today, bPast = b.date < today;
        if (aPast !== bPast) return aPast ? 1 : -1;
        const chronological = a.date.localeCompare(b.date) || (a.time || '').localeCompare(b.time || '') || a.id - b.id;
        return aPast ? -chronological : chronological;
    });
}
