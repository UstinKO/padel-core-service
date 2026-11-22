import {dateValue, addDays, shiftMonth, monthDays, eventsOnDay} from './admin-event-calendar-dates.mjs';

const root = document.getElementById('eventCalendarView');
const form = document.getElementById('tournamentListFilters');
if (root && form) initialize();

function initialize() {
    const locale = document.documentElement.lang || 'es';
    const text = root.dataset;
    const byId = id => document.getElementById(id);
    const compatible = ['clubId', 'format', 'gender', 'level', 'status', 'lang'];
    const fields = ['view', 'month', 'day'].map(name => {
        const input = document.createElement('input'); input.type = 'hidden'; input.name = name;
        form.append(input); return input;
    });
    const labels = name => Object.fromEntries(Array.from(form.elements[name].options, option => [option.value, option.text]));
    const formats = labels('format'), genders = labels('gender'), statuses = labels('status');
    let request;
    let response;
    let today = new Intl.DateTimeFormat('sv-SE', {timeZone:'America/Argentina/Buenos_Aires'}).format(new Date());
    let state = readState();
    const title = byId('calendarMonth');
    const grid = byId('calendarGrid');
    const message = byId('calendarMessage');
    const error = byId('calendarError');
    const reset = byId('calendarReset');
    const fullDate = day => new Intl.DateTimeFormat(locale, {dateStyle:'full', timeZone:'UTC'}).format(dateValue(day));

    function readState() {
        const params = new URLSearchParams(location.search);
        const month = /^\d{4}-(0[1-9]|1[0-2])$/.test(params.get('month') || '') ? params.get('month') : today.slice(0,7);
        const selected = params.get('day');
        return {calendar:params.get('view') === 'calendar', month,
            day: selected && /^\d{4}-\d{2}-\d{2}$/.test(selected) && Number.isFinite(dateValue(selected).getTime()) && dateValue(selected).toISOString().slice(0,10) === selected && selected.startsWith(month) ? selected : (today.startsWith(month) ? today : `${month}-01`)};
    }
    function url(calendar, month = state.month, day = state.day, clear = false) {
        const next = new URL(location.href);
        if (calendar) {
            next.searchParams.delete('from'); next.searchParams.delete('to');
            next.searchParams.set('view','calendar'); next.searchParams.set('month',month); next.searchParams.set('day',day);
        } else {
            next.searchParams.delete('view'); next.searchParams.set('month',month); next.searchParams.set('day',day);
        }
        if (clear) compatible.filter(key => key !== 'lang').forEach(key => next.searchParams.delete(key));
        return next.pathname + next.search;
    }
    function links() {
        byId('eventListSwitch').href = url(false);
        byId('eventCalendarSwitch').href = url(true);
        byId('eventListSwitch').setAttribute('aria-current', state.calendar ? 'false' : 'page');
        byId('eventCalendarSwitch').setAttribute('aria-current', state.calendar ? 'page' : 'false');
        fields.forEach((input, index) => {input.disabled = index === 0 && !state.calendar; input.value = ['calendar',state.month,state.day][index];});
        form.querySelectorAll('input[type=date]').forEach(input => {input.disabled = state.calendar;});
        form.querySelector('fieldset').hidden = state.calendar;
        if (state.calendar) form.elements.format.options[0].text = text.allFormats;
        if (state.calendar) form.querySelectorAll('a').forEach(link => {link.href = url(true,state.month,state.day,true);});
    }
    function mode() {
        byId('eventListView').hidden = state.calendar; root.hidden = !state.calendar;
        links();
        if (state.calendar) {
            document.querySelector('.admin-title span').textContent = text.title;
            const next = url(true); history.replaceState(null,'',next);
            load();
        }
    }
    async function load() {
        request?.abort(); request = new AbortController();
        const signal = request.signal;
        response = undefined; grid.replaceChildren(); byId('calendarDayEvents').replaceChildren();
        byId('calendarDayTitle').textContent = ''; reset.hidden = true; error.hidden = true;
        root.setAttribute('aria-busy','true'); message.textContent = text.loading;
        title.textContent = new Intl.DateTimeFormat(locale,{month:'long',year:'numeric',timeZone:'UTC'}).format(dateValue(`${state.month}-01`));
        const query = new URLSearchParams({month:state.month});
        compatible.forEach(key => {const value = new URLSearchParams(location.search).get(key); if (value) query.set(key,value);});
        try {
            const result = await fetch(`/api/admin/events/calendar?${query}`, {signal, cache:'no-store', headers:{Accept:'application/json'}});
            if (!result.ok || !result.headers.get('content-type')?.includes('application/json')) throw new Error('calendar-load');
            const data = await result.json();
            if (signal.aborted) return;
            response = data; today = data.today;
            const select = form.elements.clubId;
            const selected = select.value;
            const options = [new Option(text.allClubs, '')];
            data.clubs.forEach(club => options.push(new Option(club.name, String(club.id))));
            select.replaceChildren(...options); select.value = selected;
            if (selected && !select.value) {
                const unavailable = new Option('—', selected); unavailable.selected = true; select.append(unavailable);
            }
            message.textContent = data.events.length ? text.events.replace('{0}',data.events.length)
                : data.periodCount ? text.filtered : text.empty;
            const filtersApplied = compatible.some(key => key !== 'lang' && new URLSearchParams(location.search).get(key));
            reset.hidden = data.events.length > 0 || !filtersApplied; reset.href = url(true,state.month,state.day,true);
            render();
        } catch (failure) {
            if (failure.name !== 'AbortError') {message.textContent = ''; error.hidden = false;}
        } finally {if (!signal.aborted) root.removeAttribute('aria-busy');}
    }
    function element(tag, className, value) {
        const node = document.createElement(tag); if (className) node.className = className;
        if (value !== undefined) node.textContent = value;
        return node;
    }
    function level(event) {
        if (event.level === 'TODOS') return text.allLevels;
        if (event.level === 'PRINCIPIANTES') return text.beginner;
        return (event.level || '').replaceAll('_', event.level?.startsWith('SUMA_') ? ' ' : '/');
    }
    function eventLink(event, day, compact) {
        const link = element('a',`event-calendar__event event-calendar__event--${event.format.toLowerCase()}`);
        const query = new URLSearchParams({lang:locale,calendarReturn:url(true)});
        link.href = `/admin/tournaments/${event.id}?${query}`;
        const time = event.date < day ? text.continues : (event.time || '').slice(0,5);
        link.append(element('span','event-calendar__event-title',`${time} · ${formats[event.format] || event.format}`));
        link.append(element('span','event-calendar__event-meta',`${genders[event.gender] || ''} · ${level(event)}`));
        link.append(element('span','event-calendar__event-meta',event.clubName || ''));
        link.append(element('span','event-calendar__event-status',statuses[event.status] || event.status));
        if (!compact && event.endDate && event.endDate > event.date) {
            link.append(element('span','event-calendar__event-meta',`${event.date} — ${event.endDate} ${(event.endTime || '').slice(0,5)}`));
        }
        return link;
    }
    function selectDay(day, focus = false) {
        if (!day.startsWith(state.month)) {navigate(day.slice(0,7),day); return;}
        state.day = day; history.replaceState(null,'',url(true)); links(); render();
        if (focus) byId('calendarDayTitle').focus({preventScroll:false});
        else grid.querySelector(`[data-day="${day}"]`)?.focus({preventScroll:true});
    }
    function render() {
        byId('calendarWeekdays').replaceChildren(...Array.from({length:7},(_,i) =>
            element('span','',new Intl.DateTimeFormat(locale,{weekday:'short',timeZone:'UTC'}).format(dateValue(addDays('2026-11-16',i))))));
        grid.replaceChildren(...monthDays(state.month).map(day => {
            const outside = !day.startsWith(state.month);
            const events = outside ? [] : eventsOnDay(response.events, day);
            const cell = element('div','event-calendar__cell');
            if (outside) cell.classList.add('is-outside');
            if (day === today) cell.classList.add('is-today');
            if (day === state.day) cell.classList.add('is-selected');
            const button = element('button','event-calendar__date',Number(day.slice(-2)));
            button.type = 'button'; button.dataset.day = day;
            button.setAttribute('aria-label',`${fullDate(day)} · ${text.events.replace('{0}',events.length)}`);
            button.setAttribute('aria-pressed', String(day === state.day));
            if (day === today) button.setAttribute('aria-current','date');
            button.addEventListener('click', () => selectDay(day)); cell.append(button);
            if (events.length) {
                cell.append(element('span','event-calendar__count',String(events.length)));
                const preview = element('div','event-calendar__preview');
                events.slice(0,2).forEach(event => preview.append(eventLink(event,day,true)));
                if (events.length > 2) {
                    const more = element('button','event-calendar__more',text.more.replace('{0}',events.length - 2));
                    more.type = 'button'; more.addEventListener('click',() => selectDay(day,true)); preview.append(more);
                }
                cell.append(preview);
            }
            return cell;
        }));
        byId('calendarDayTitle').textContent = fullDate(state.day);
        const dayEvents = eventsOnDay(response.events,state.day);
        byId('calendarDayEvents').replaceChildren(...(dayEvents.length ? dayEvents.map(event => eventLink(event,state.day,false)) : [element('p','',text.noDay)]));
    }
    function navigate(month, day = `${month}-01`) {
        state.month = month; state.day = day; history.pushState(null,'',url(true)); links(); load();
    }
    byId('calendarPrevious').addEventListener('click',() => navigate(shiftMonth(state.month,-1)));
    byId('calendarNext').addEventListener('click',() => navigate(shiftMonth(state.month,1)));
    byId('calendarToday').addEventListener('click',() => navigate(today.slice(0,7),today));
    byId('calendarRetry').addEventListener('click',load);
    window.addEventListener('popstate',() => {state = readState(); mode();});
    window.addEventListener('pageshow',event => {if (event.persisted && state.calendar) load();});
    form.addEventListener('submit',event => {
        if (!state.calendar) return;
        event.preventDefault();
        const query = new URLSearchParams(new FormData(form)); query.delete('from'); query.delete('to');
        history.pushState(null,'',`${location.pathname}?${query}`); links(); load();
    });
    mode();
}
