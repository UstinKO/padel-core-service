(function () {
    'use strict';
    const root = document.getElementById('registration-sources');
    if (!root) return;
    const form = document.getElementById('source-filters');
    const status = document.getElementById('source-status');
    const results = document.getElementById('source-results');
    const rows = document.getElementById('source-rows');
    const previous = document.getElementById('source-prev');
    const next = document.getElementById('source-next');
    const locale = document.documentElement.lang || 'es';
    const numbers = new Intl.NumberFormat(locale);
    const placements = Object.fromEntries(Array.from(form.elements.placement.options).map(option => [option.value, option.textContent]));
    const fields = ['participants', 'firstRegistrations', 'pairs', 'confirmed', 'waitlist', 'pending', 'cancelled'];
    const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Argentina/Buenos_Aires', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
    const part = name => parts.find(value => value.type === name).value;
    form.elements.from.value = part('year') + '-' + part('month') + '-01';
    form.elements.to.value = part('year') + '-' + part('month') + '-' + new Date(Date.UTC(Number(part('year')), Number(part('month')), 0)).getUTCDate();
    let page = 0;
    let current;
    let submitted = new URLSearchParams();

    function cell(tr, value) {
        const td = document.createElement('td');
        td.textContent = value;
        tr.appendChild(td);
        return td;
    }

    function render(report) {
        fields.forEach(field => {
            if (!Number.isSafeInteger(report.totals[field]) || report.totals[field] < 0) throw new Error('error');
            document.getElementById('source-' + field).textContent = numbers.format(report.totals[field]);
        });
        document.getElementById('source-coverage').textContent = root.dataset.coverage
            .replace('{measured}', numbers.format(report.measuredParticipants))
            .replace('{total}', numbers.format(report.totals.participants));
        const units = document.getElementById('source-unit-statuses');
        units.replaceChildren();
        [['participants', '', report.measuredParticipants], ['firstRegistrations', 'first', report.measuredFirstRegistrations], ['pairs', 'pair', report.measuredPairs]].forEach(([unit, prefix, measured]) => {
            const tr = document.createElement('tr');
            cell(tr, document.getElementById('source-' + unit).previousElementSibling.textContent);
            ['confirmed', 'waitlist', 'pending', 'cancelled'].forEach(bucket => {
                const field = prefix ? prefix + bucket[0].toUpperCase() + bucket.slice(1) : bucket;
                cell(tr, numbers.format(report.totals[field]));
            });
            cell(tr, numbers.format(measured) + ' / ' + numbers.format(report.totals[unit]));
            units.appendChild(tr);
        });
        document.getElementById('source-asof').textContent = new Intl.DateTimeFormat(locale, { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Argentina/Buenos_Aires' }).format(new Date(report.asOf));
        rows.replaceChildren();
        report.rows.forEach(entry => {
            const tr = document.createElement('tr');
            const description = [entry.source === 'unknown' ? root.dataset.unknown : entry.source, entry.medium, entry.content, entry.term].filter(Boolean).join(' · ');
            const sourceCell = cell(tr, description);
            const detail = document.createElement('small');
            detail.textContent = [placements[entry.placement] || root.dataset.unknown, entry.campaign].filter(Boolean).join(' · ');
            sourceCell.appendChild(detail);
            cell(tr, entry.tournament + ' (#' + entry.tournamentId + ')');
            fields.forEach(field => cell(tr, numbers.format(entry.counts[field])));
            rows.appendChild(tr);
        });
        document.getElementById('source-empty').hidden = report.totalGroups > 0;
        previous.disabled = report.page === 0;
        next.disabled = (report.page + 1) * report.pageSize >= report.totalGroups;
        document.getElementById('source-page').textContent = numbers.format(report.page + 1) + ' / ' + numbers.format(Math.max(1, Math.ceil(report.totalGroups / report.pageSize)));
    }

    async function load() {
        if (current) current.abort();
        const request = new AbortController();
        current = request;
        results.hidden = true;
        status.textContent = root.dataset.loading;
        root.setAttribute('aria-busy', 'true');
        const params = new URLSearchParams(submitted);
        params.set('page', String(page));
        params.set('pageSize', '50');
        try {
            const response = await fetch('/api/admin/marketing/registration-sources?' + params, { credentials: 'same-origin', cache: 'no-store', signal: request.signal });
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

    function submit(event) {
        if (event) event.preventDefault();
        if (current) { current.abort(); current = undefined; }
        if (!form.reportValidity()) {
            results.hidden = true;
            root.removeAttribute('aria-busy');
            status.textContent = root.dataset.invalid;
            return;
        }
        submitted = new URLSearchParams(new FormData(form));
        const duration = (Date.parse(submitted.get('to')) - Date.parse(submitted.get('from'))) / 86400000;
        if (!Number.isFinite(duration) || duration < 0 || duration >= 366) {
            results.hidden = true;
            root.removeAttribute('aria-busy');
            status.textContent = root.dataset.invalid;
            return;
        }
        page = 0;
        void load();
    }
    function setPeriod(preset) {
        const year = Number(part('year'));
        const month = Number(part('month')) - 1;
        const today = new Date(Date.UTC(year, month, Number(part('day'))));
        const from = preset === 'last30' ? new Date(today.getTime() - 29 * 86400000)
            : new Date(Date.UTC(year, month - (preset === 'lastMonth' ? 1 : 0), 1));
        const to = preset === 'last30' ? today : new Date(Date.UTC(year, month + (preset === 'lastMonth' ? 0 : 1), 0));
        form.elements.from.value = from.toISOString().slice(0, 10);
        form.elements.to.value = to.toISOString().slice(0, 10);
        submit();
    }
    document.getElementById('source-this-month').addEventListener('click', () => setPeriod('thisMonth'));
    document.getElementById('source-last-month').addEventListener('click', () => setPeriod('lastMonth'));
    document.getElementById('source-last-30').addEventListener('click', () => setPeriod('last30'));
    form.addEventListener('submit', submit);
    previous.addEventListener('click', () => { if (page > 0) { page--; void load(); } });
    next.addEventListener('click', () => { page++; void load(); });
    submit();
})();
