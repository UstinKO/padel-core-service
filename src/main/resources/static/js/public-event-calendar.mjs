import {readState, commonUrl, filterKeys, dateValue, addDays, shiftMonth, monthDays,
    dayEvents, extraCount, visibleEvents, cardOrder} from './public-event-calendar-state.mjs';

const root = document.getElementById('publicCalendar');
const $ = id => document.getElementById(id);
const tr = (key, ...args) => window.t(`public_calendar.${key}`, ...args);
const escape = value => String(value ?? '').replace(/[&<>"']/g, char =>
    ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
const storage = {
    get(key) { try { return localStorage.getItem(key); } catch { return ''; } },
    set(key, value) { try { localStorage.setItem(key, value); } catch { /* Настройка необязательна. */ } }
};
const lang = ['es','ru','en'].includes(document.documentElement.lang) ? document.documentElement.lang : 'es';
const locale = {es:'es-AR', ru:'ru-RU', en:'en-GB'}[lang];
let state = readState(location.search, storage.get('padel.public.view'));
state.lang = lang;
let feed, own = new Map(), myOnly = false, actor = '', controller, generation = 0;
const dateText = (day, options) => new Intl.DateTimeFormat(locale, {...options, timeZone:'UTC'}).format(dateValue(day));
function countText(count) {
    const rule = new Intl.PluralRules(locale).select(count);
    return tr(`events_${rule === 'one' ? 'one' : rule === 'few' ? 'few' : 'many'}`, count);
}
function levelText(value, short = false) {
    if (value === 'TODOS') return tr(short ? 'all' : 'all_levels');
    if (value === 'PRINCIPIANTES') return tr('beginner');
    return (value || '').replace('SUMA_', 'SUMA ').replaceAll('_', '/');
}
function saveState(push = false) {
    const url = commonUrl(state);
    if (`${location.pathname}${location.search}` !== url) {
        history[push ? 'pushState' : 'replaceState']({publicCalendar:true}, '', url);
    }
    storage.set('padel.public.view', state.view);
}
async function json(url, signal) {
    const response = await fetch(url, {signal, cache:'no-store', credentials:'same-origin', headers:{Accept:'application/json'}});
    if (!response.ok || !(response.headers.get('content-type') || '').includes('json')) throw new Error('calendar-read');
    return response.json();
}
function query() {
    const params = new URLSearchParams();
    for (const key of filterKeys.filter(key => key !== 'status')) if (state[key]) params.set(key, state[key]);
    if (state.view === 'calendar' && state.month) params.set('month', state.month);
    return params.toString();
}
async function refresh() {
    const current = ++generation;
    controller?.abort(); controller = new AbortController();
    const signal = controller.signal;
    own = new Map();
    $('pcContent').hidden = true;
    $('pcNotice').hidden = false; $('pcNotice').classList.remove('pc-error'); $('pcNotice').textContent = tr('loading');
    $('pcRetry').hidden = true; $('pcPersonalNotice').hidden = true; $('pcMineLabel').hidden = true;
    try {
        const [data, sessionResult] = await Promise.all([
            json(`/api/public/calendar${state.view === 'cards' ? '/catalog' : ''}?${query()}`, signal),
            json('/api/auth/session', signal).then(value => ({value}), () => ({error:true}))
        ]);
        if (current !== generation) return;
        feed = data;
        if (!state.month) state.month = data.month || data.today.slice(0,7);
        if (!state.day || !monthDays(state.month).includes(state.day)) state.day =
            data.today.startsWith(state.month) ? data.today : `${state.month}-01`;
        const session = sessionResult.value;
        const nextActor = session?.authenticated && session.userType === 'PLAYER' ? String(session.id) : '';
        if (actor !== nextActor) myOnly = false;
        actor = nextActor;
        if (actor) {
            $('pcMineLabel').hidden = false;
            try {
                const personalQuery = state.view === 'calendar' ? `?month=${state.month}` : '';
                const personal = await json(`/api/players/me/calendar${personalQuery}`, signal);
                if (current !== generation) return;
                if (String(personal.playerId) !== actor) throw new Error('calendar-account-changed');
                own = new Map(personal.registrations.map(row => [row.eventId, row.status]));
            } catch (error) {
                if (signal.aborted) return;
                myOnly = false; personalError();
            }
        } else if (sessionResult.error) {
            myOnly = false; personalError();
        }
        if (current !== generation) return;
        $('pcMine').checked = myOnly;
        saveState(); renderFilters(); render();
        $('pcNotice').hidden = true; $('pcContent').hidden = false;
    } catch (error) {
        if (signal.aborted || current !== generation) return;
        $('pcNotice').classList.add('pc-error'); $('pcNotice').textContent = tr('error');
        $('pcRetry').hidden = false;
    }
}
function personalError() {
    $('pcPersonalNotice').textContent = tr('personal_error');
    $('pcPersonalNotice').hidden = false; $('pcRetry').hidden = false;
}
function renderFilters() {
    const facets = feed.facets;
    const options = {
        level: facets.levels.map(value => [value, levelText(value)]),
        format: facets.formats.map(value => [value, tr(`format.${value}`)]),
        gender: facets.genders.map(value => [value, tr(`gender.${value}`)]),
        clubId: facets.clubs.map(value => [String(value.id), value.name]),
        organizerId: facets.organizers.map(value => [String(value.id), value.name]),
        status: ['PUBLICADO','REGISTRO_ABIERTO','CERRADO','FINALIZADO','CANCELADO'].map(value => [value, tr(`status.${value}`)])
    };
    root.querySelectorAll('[data-filter]').forEach(select => {
        const key = select.dataset.filter, rows = options[key];
        if (state[key] && !rows.some(([value]) => value === state[key])) rows.push([state[key],
            key === 'clubId' ? tr('unavailable_club') : key === 'organizerId' ? tr('unavailable_organizer') : state[key]]);
        select.replaceChildren(new Option(tr('all'), ''), ...rows.map(([value, label]) => new Option(label, value)));
        select.value = state[key];
    });
    const extra = filterKeys.filter(key => key !== 'level' && state[key]).length;
    $('pcFiltersToggle').textContent = `${tr('filters')}${extra ? ` · ${extra}` : ''}`;
}
function render() {
    const events = visibleEvents(feed.events, own, myOnly, state.status);
    root.querySelectorAll('[data-view]').forEach(button => button.setAttribute('aria-pressed', button.dataset.view === state.view));
    $('pcCatalog').hidden = state.view !== 'cards'; $('pcCalendar').hidden = state.view !== 'calendar';
    if (state.view === 'cards') {
        $('pcCatalogCount').textContent = countText(events.length);
        $('pcCards').innerHTML = cardOrder(events, feed.today).map(event => card(event, true)).join('') || empty('empty_catalog');
    } else renderCalendar(events);
}
function empty(key) { return `<p class="pc-message">${escape(tr(key))}</p>`; }
function card(event, withDate = false) {
    const title = `${(event.time || '').slice(0,5)} · ${tr(`format.${event.format}`)}`;
    const back = commonUrl(state);
    const path = `/torneo/${Number(event.id)}?publicCalendarReturn=${encodeURIComponent(back)}`;
    const personal = own.get(event.id);
    return `<article class="pc-card">
        <div class="pc-badges"><span class="pc-badge">${escape(levelText(event.level))}</span>
            <span class="pc-badge">${escape(tr(`gender.${event.gender}`))}</span></div>
        <h3>${escape(title)}</h3>
        ${withDate ? `<p>${escape(dateText(event.date, {day:'numeric',month:'long',year:'numeric'}))}</p>
            <p>${escape(tr('price'))}: ${escape(event.price ?? '0')} ${escape(event.currency || '')}</p>` : ''}
        <p><span class="pc-meta">${escape(tr('club'))}:</span> ${escape(event.club?.name || '—')}</p>
        <p><span class="pc-meta">${escape(tr('organizer'))}:</span> ${escape(event.organizer?.name || '—')}</p>
        <p><span class="pc-availability ${escape(event.availability)}">${escape(tr(`availability.${event.availability}`))}</span></p>
        ${personal ? `<p class="pc-personal">${escape(tr(`personal.${personal}`))}</p>` : ''}
        <div class="pc-actions"><a class="pc-details" href="${escape(path)}" data-event="${Number(event.id)}">${escape(tr('details'))}</a>
        ${withDate && event.availability !== 'CLOSED' ? `<a class="pc-details pc-register" href="${escape(path)}" data-event="${Number(event.id)}" data-registration-intent="${Number(event.id)}">${escape(tr(event.availability === 'WAITLIST' ? 'join_waitlist' : 'register'))}</a>` : ''}</div>
    </article>`;
}
function renderCalendar(events) {
    $('pcMonthTitle').textContent = dateText(`${state.month}-01`, {month:'long',year:'numeric'});
    $('pcWeekdays').innerHTML = Array.from({length:7}, (_, index) =>
        `<span>${escape(dateText(addDays('2026-11-23', index), {weekday:'short'}))}</span>`).join('');
    $('pcGrid').innerHTML = monthDays(state.month).map(day => {
        const rows = dayEvents(events, day), extra = extraCount(rows);
        const label = `${dateText(day, {weekday:'long',day:'numeric',month:'long'})}: ${countText(rows.length)}`;
        return `<button type="button" class="pc-day${day.startsWith(state.month) ? '' : ' pc-out'}${day === feed.today ? ' pc-today' : ''}"
            data-day="${day}" aria-label="${escape(label)}" aria-pressed="${day === state.day}" tabindex="${day === state.day ? 0 : -1}">
            <span class="pc-number">${Number(day.slice(-2))}</span>
            ${rows.slice(0,3).map(event => `<span class="pc-short">${escape((event.time || '').slice(0,5))} · ${escape(levelText(event.level, true))}</span>`).join('')}
            ${extra ? `<span class="pc-more">${escape(tr('more', extra))}</span>` : ''}
            ${rows.length ? `<span class="pc-mobile-count" aria-hidden="true">${rows.length}</span>` : ''}
        </button>`;
    }).join('');
    $('pcDayTitle').textContent = dateText(state.day, {weekday:'long',day:'numeric',month:'long'});
    const rows = dayEvents(events, state.day);
    $('pcDayCount').textContent = countText(rows.length);
    $('pcDayEvents').innerHTML = rows.map(event => card(event)).join('') || empty('empty');
}
function selectDay(day, focus = false) {
    if (!monthDays(state.month).includes(day)) {
        state.month = day.slice(0,7); state.day = day; saveState(true); refresh(); return;
    }
    state.day = day; saveState(true); render();
    if (focus) root.querySelector(`[data-day="${day}"]`)?.focus();
}
root.addEventListener('click', event => {
    const view = event.target.closest('[data-view]');
    if (view && state.view !== view.dataset.view) {
        state.view = view.dataset.view; saveState(true); refresh();
    }
    const day = event.target.closest('[data-day]');
    if (day) selectDay(day.dataset.day, true);
    const details = event.target.closest('[data-event]');
    if (details) {
        try { sessionStorage.setItem('padel.public.return', JSON.stringify({eventId:Number(details.dataset.event), url:commonUrl(state)})); } catch { /* Ссылка содержит тот же безопасный маршрут. */ }
    }
});
root.addEventListener('change', event => {
    const key = event.target.dataset.filter;
    if (key) { state[key] = event.target.value; saveState(true); refresh(); }
});
$('pcGrid').addEventListener('keydown', event => {
    const day = event.target.closest('[data-day]')?.dataset.day;
    if (!day) return;
    const steps = {ArrowLeft:-1,ArrowRight:1,ArrowUp:-7,ArrowDown:7};
    if (event.key in steps) { event.preventDefault(); selectDay(addDays(day, steps[event.key]), true); }
});
$('pcMine').addEventListener('change', event => { myOnly = event.target.checked; render(); });
$('pcReset').addEventListener('click', () => {
    filterKeys.forEach(key => { state[key] = ''; }); myOnly = false; saveState(true); refresh();
});
$('pcFiltersToggle').addEventListener('click', () => {
    const open = $('pcExtra').dataset.collapsed === 'true';
    $('pcExtra').dataset.collapsed = String(!open); $('pcFiltersToggle').setAttribute('aria-expanded', open);
});
function moveMonth(step) {
    const month = shiftMonth(state.month, step);
    if (Number(month.slice(0,4)) < 100 || Number(month.slice(0,4)) > 9998) return;
    state.month = month; state.day = `${month}-01`; saveState(true); refresh();
}
$('pcPrevious').addEventListener('click', () => moveMonth(-1));
$('pcNext').addEventListener('click', () => moveMonth(1));
$('pcToday').addEventListener('click', () => {
    state.month = feed.today.slice(0,7); state.day = feed.today; saveState(true); refresh();
});
$('pcRetry').addEventListener('click', refresh);
$('pcShare').addEventListener('click', async () => {
    const url = new URL(commonUrl(state), location.origin).href;
    try { await navigator.clipboard.writeText(url); $('pcShare').textContent = tr('copied'); }
    catch { $('pcShareField').value = url; $('pcShareField').hidden = false; $('pcShareField').select(); }
});
window.addEventListener('popstate', () => { state = readState(location.search); state.lang = lang; refresh(); });
window.addEventListener('pageshow', refresh);
window.addEventListener('focus', refresh);
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') refresh(); });
refresh();
