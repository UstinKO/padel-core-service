(function () {
    'use strict';
    const root = document.getElementById('qr-report');
    if (!root) return;
    const form = document.getElementById('qr-filter-form');
    const status = document.getElementById('qr-status');
    const results = document.getElementById('qr-results');
    const totals = document.getElementById('qr-totals');
    const days = document.getElementById('qr-days');
    const names = Object.fromEntries(Array.from(form.elements.source.options).map(option => [option.value, option.textContent]));
    const locale = document.documentElement.lang || 'es';
    const numbers = new Intl.NumberFormat(locale);
    const dates = new Intl.DateTimeFormat(locale, { timeZone: 'UTC' });
    const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Argentina/Buenos_Aires', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
    const part = name => today.find(value => value.type === name).value;
    const year = Number(part('year'));
    const month = Number(part('month'));
    const prefix = part('year') + '-' + part('month') + '-';
    form.elements.from.value = prefix + '01';
    form.elements.to.value = prefix + new Date(Date.UTC(year, month, 0)).getUTCDate();
    let current;

    function row(parent, cells) {
        const tr = document.createElement('tr');
        cells.forEach(value => {
            const td = document.createElement('td');
            td.textContent = value;
            tr.appendChild(td);
        });
        parent.appendChild(tr);
    }

    function render(report) {
        totals.replaceChildren();
        days.replaceChildren();
        report.sources.forEach(source => {
            if (!Object.hasOwn(names, source.source)) throw new Error('source');
            row(totals, [names[source.source], numbers.format(source.site), numbers.format(source.telegram), numbers.format(source.site + source.telegram)]);
        });
        const entries = report.sources.flatMap(source => source.days.map(day => ({ ...day, source: source.source })));
        entries.sort((a, b) => a.date.localeCompare(b.date) || a.source.localeCompare(b.source));
        entries.forEach(day => row(days, [dates.format(new Date(day.date + 'T00:00:00Z')), names[day.source], numbers.format(day.site), numbers.format(day.telegram)]));
        document.getElementById('qr-empty').hidden = entries.length > 0;
        document.getElementById('qr-daily-card').hidden = entries.length === 0;
    }

    async function load(event) {
        if (event) event.preventDefault();
        if (current) current.abort();
        results.hidden = true;
        const params = new URLSearchParams(new FormData(form));
        const duration = (Date.parse(params.get('to')) - Date.parse(params.get('from'))) / 86400000;
        if (!form.reportValidity() || !Number.isFinite(duration) || duration < 0 || duration >= 366) {
            status.textContent = root.dataset.invalid;
            return;
        }
        const request = new AbortController();
        current = request;
        status.textContent = root.dataset.loading;
        root.setAttribute('aria-busy', 'true');
        try {
            const response = await fetch('/api/admin/marketing/qr-summary?' + params, { credentials: 'same-origin', cache: 'no-store', signal: request.signal });
            if (response.redirected || response.status === 401 || response.status === 403) throw new Error('access');
            if (response.status === 400) throw new Error('invalid');
            if (!response.ok || !(response.headers.get('Content-Type') || '').includes('application/json')) throw new Error('error');
            const report = await response.json();
            if (current !== request) return;
            render(report);
            status.textContent = '';
            results.hidden = false;
        } catch (error) {
            if (current !== request || request.signal.aborted) return;
            status.textContent = root.dataset[error.message] || root.dataset.error;
        } finally {
            if (current === request) root.removeAttribute('aria-busy');
        }
    }
    form.addEventListener('submit', load);
    load();
})();
