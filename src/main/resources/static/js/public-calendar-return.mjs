import {safeReturn} from './public-event-calendar-state.mjs';
const container = document.getElementById('publicCalendarReturn');
const params = new URLSearchParams(location.search);
let target = safeReturn(params.get('publicCalendarReturn'), location.origin);
if (!target) {
    try {
        const saved = JSON.parse(sessionStorage.getItem('padel.public.return'));
        if (location.pathname === `/torneo/${saved?.eventId}`) target = safeReturn(saved.url, location.origin);
    } catch { /* Возврат необязателен, основная страница работает без хранилища. */ }
}
if (target && container) {
    const link = document.createElement('a');
    link.href = target; link.className = 'breadcrumb-link';
    link.textContent = window.t('public_calendar.return');
    container.replaceChildren(link); container.hidden = false;
}
