(() => {
    const motion = document.querySelector('[data-motion-toggle]');
    if (motion) {
        const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
        const hero = motion.closest('.elo-preview-hero');
        let playing = true;
        const updateMotion = () => {
            motion.hidden = reducedMotion.matches;
            hero.toggleAttribute('data-motion-paused', !playing || reducedMotion.matches);
            motion.setAttribute('aria-pressed', String(playing));
            motion.querySelector('span').textContent = playing ? 'Ⅱ' : '▶';
        };
        motion.addEventListener('click', () => { playing = !playing; updateMotion(); });
        reducedMotion.addEventListener('change', updateMotion);
        updateMotion();
    }
    const panel = document.querySelector('[data-elo-evolution]');
    if (!panel) return;

    const canvas = panel.querySelector('canvas');
    const context = canvas.getContext('2d');
    const period = panel.querySelector('[data-period]');
    const picker = panel.querySelector('[data-event-picker]');
    const tooltip = panel.querySelector('[data-chart-tooltip]');
    const locale = document.documentElement.lang;
    const number = new Intl.NumberFormat(locale, { maximumFractionDigits: 2 });
    const signed = new Intl.NumberFormat(locale, { maximumFractionDigits: 2, signDisplay: 'exceptZero' });
    const percent = new Intl.NumberFormat(locale, { style: 'percent' });
    const date = new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeZone: 'UTC' });
    const history = Array.from(panel.querySelectorAll('li[data-day]'), item => ({
        day: Date.parse(item.dataset.day),
        elo: Number(item.dataset.elo),
        change: Number(item.dataset.change),
        wins: Number(item.dataset.wins),
        matches: Number(item.dataset.matches),
        category: item.textContent,
        title: item.dataset.title,
        format: item.dataset.format,
        resultsUrl: item.dataset.resultsUrl
    }));
    let visible;
    let points = [];

    const recent = history.slice(-3);
    const matches = recent.reduce((total, event) => total + event.matches, 0);
    const wins = recent.reduce((total, event) => total + event.wins, 0);
    const card = panel.closest('.elo-preview-card');
    const winRate = matches ? wins / matches : 0;
    card.querySelector('[data-recent-wins]').textContent = `${percent.format(winRate)} · ${wins} / ${matches}`;
    card.querySelector('[data-win-percent]').textContent = percent.format(winRate);
    card.querySelector('.elo-preview-win-ring').style.setProperty('--win-angle', `${winRate * 360}deg`);

    const recentEvents = panel.querySelector('[data-recent-events]');
    const formats = {
        americano: recentEvents.dataset.americanoLabel,
        king: recentEvents.dataset.kingLabel,
        pair: recentEvents.dataset.pairLabel
    };
    const eventButtons = [];
    history.slice().reverse().forEach((event, index) => {
        const row = document.createElement('div');
        row.className = 'elo-preview-event-row';
        row.hidden = index >= 3;
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'elo-preview-event' + (event.change < 0 ? ' loss' : '');
        const label = document.createElement('span');
        const format = formats[event.format] || recentEvents.dataset.unknownLabel;
        const title = event.title || `${format} · ${event.category}`;
        const name = document.createElement('span');
        name.className = 'elo-preview-event-title';
        name.textContent = title;
        const meta = document.createElement('span');
        meta.className = 'elo-preview-event-meta';
        meta.textContent = `${format} · ${event.category} · ${date.format(event.day)}`;
        const detail = document.createElement('b');
        detail.textContent = `${number.format(event.elo)} Elo`;
        label.append(name, meta, detail);
        const change = document.createElement('strong');
        change.textContent = `${signed.format(event.change)} Elo`;
        button.append(label, change);
        button.addEventListener('click', () => {
            if (!visible.includes(event)) {
                period.value = 'all';
                showPeriod();
            }
            picker.selectedIndex = visible.indexOf(event);
            showPoint();
        });
        row.append(button);
        let url;
        try {
            const candidate = event.resultsUrl && new URL(event.resultsUrl, window.location.href);
            if (candidate && ['http:', 'https:'].includes(candidate.protocol)) url = candidate.href;
        } catch { /* Missing or invalid sources remain visible without a broken link. */ }
        const results = document.createElement(url ? 'a' : 'span');
        results.className = 'elo-preview-event-results';
        results.textContent = url ? `${recentEvents.dataset.resultsLabel} ↗` : recentEvents.dataset.unavailableLabel;
        if (url) {
            results.href = url;
            results.target = '_blank';
            results.rel = 'noopener noreferrer';
            results.setAttribute('aria-label', `${recentEvents.dataset.resultsLabel}: ${title}, ${date.format(event.day)}`);
        }
        row.append(results);
        recentEvents.append(row);
        eventButtons.push({ button, row, event });
    });
    const toggle = panel.querySelector('[data-events-toggle]');
    if (toggle) {
        toggle.hidden = false;
        toggle.querySelector('[data-events-label]').textContent = toggle.dataset.expandLabel;
        toggle.addEventListener('click', () => {
            const expanded = toggle.getAttribute('aria-expanded') !== 'true';
            toggle.setAttribute('aria-expanded', String(expanded));
            toggle.querySelector('[data-events-label]').textContent = expanded
                ? toggle.dataset.collapseLabel : toggle.dataset.expandLabel;
            eventButtons.forEach(({ row }, i) => { row.hidden = !expanded && i >= 3; });
        });
    }

    function draw() {
        const width = canvas.clientWidth;
        const height = canvas.clientHeight;
        if (!context || width < 70 || !height) return;
        const ratio = window.devicePixelRatio || 1;
        canvas.width = width * ratio;
        canvas.height = height * ratio;
        context.scale(ratio, ratio);

        const values = visible.map(event => event.elo);
        const low = Math.floor((Math.min(...values) - 8) / 20) * 20;
        const high = Math.ceil((Math.max(...values) + 8) / 20) * 20;
        const left = 45;
        const right = width - 13;
        const bottom = height - 36;
        const start = visible[0].day;
        const end = visible.at(-1).day;
        const axisDate = new Intl.DateTimeFormat(locale, {
            month: 'short', timeZone: 'UTC',
            ...(end - start > 180 * 86400000 ? { year: '2-digit' } : { day: 'numeric' })
        });
        const y = value => bottom - (value - low) / (high - low) * (bottom - 13);
        points = visible.map(event => [
            start === end ? (left + right) / 2 : left + (right - left) * (event.day - start) / (end - start),
            y(event.elo)
        ]);
        const style = getComputedStyle(canvas);
        context.font = '12px Inter, sans-serif';
        context.textAlign = 'right';
        context.fillStyle = style.color;
        context.strokeStyle = style.getPropertyValue('--elo-grid').trim();
        for (const value of [low, (low + high) / 2, high]) {
            context.beginPath();
            context.moveTo(left, y(value));
            context.lineTo(right, y(value));
            context.stroke();
            context.fillText(number.format(value), left - 6, y(value) + 4);
        }
        let lastLabelRight = -Infinity;
        let lastLabelText;
        const endLabel = axisDate.format(end);
        const endLabelLeft = right - context.measureText(endLabel).width;
        points.forEach(([x], i) => {
            const text = axisDate.format(visible[i].day);
            const labelWidth = context.measureText(text).width;
            const labelLeft = Math.max(left, Math.min(x - labelWidth / 2, right - labelWidth));
            if (i && (text === lastLabelText || labelLeft < lastLabelRight + 18 ||
                (i !== points.length - 1 && labelLeft + labelWidth + 18 > endLabelLeft))) return;
            context.textAlign = 'left';
            context.fillText(text, labelLeft, bottom + 23);
            lastLabelRight = labelLeft + labelWidth;
            lastLabelText = text;
        });
        const accent = style.getPropertyValue('--elo-line').trim();
        context.strokeStyle = accent;
        context.fillStyle = accent;
        const fill = context.createLinearGradient(0, 0, 0, bottom);
        fill.addColorStop(0, '#9556df45');
        fill.addColorStop(1, '#9556df00');
        context.beginPath();
        context.moveTo(points[0][0], bottom);
        points.forEach(([x, position]) => context.lineTo(x, position));
        context.lineTo(points.at(-1)[0], bottom);
        context.closePath();
        context.fillStyle = fill;
        context.fill();
        context.fillStyle = accent;
        context.lineWidth = 3;
        context.lineJoin = 'round';
        context.beginPath();
        points.forEach(([x, position], i) => i ? context.lineTo(x, position) : context.moveTo(x, position));
        context.stroke();
        points.forEach(([x, position], i) => {
            if (i === picker.selectedIndex) {
                context.fillStyle = '#f58b3a35';
                context.beginPath();
                context.arc(x, position, 12, 0, 2 * Math.PI);
                context.fill();
            }
            context.beginPath();
            context.arc(x, position, i === picker.selectedIndex ? 6 : 4, 0, 2 * Math.PI);
            context.fillStyle = i === picker.selectedIndex ? '#f36b2e' : '#fff';
            context.strokeStyle = i === picker.selectedIndex ? '#b94112' : accent;
            context.lineWidth = 2;
            context.fill();
            context.stroke();
        });
    }

    function showPoint() {
        const event = visible[picker.selectedIndex];
        panel.querySelector('[data-elo-point]').textContent =
            `${number.format(event.elo)} Elo (${signed.format(event.change)}) · ${event.wins} / ${event.matches}`;
        eventButtons.forEach(item => item.button.setAttribute('aria-current', String(item.event === event)));
        tooltip.hidden = true;
        draw();
    }

    function showPeriod() {
        visible = period.value === 'all' ? history : history.slice(-8);
        picker.replaceChildren(...visible.map(event =>
            new Option([date.format(event.day), event.category, `${number.format(event.elo)} Elo`].filter(Boolean).join(' · '))));
        picker.selectedIndex = visible.length - 1;
        showPoint();
    }

    period.addEventListener('change', showPeriod);
    picker.addEventListener('change', showPoint);
    canvas.addEventListener('click', event => {
        if (!points.length) return;
        const rect = canvas.getBoundingClientRect();
        const distance = ([x, y]) => Math.hypot(event.clientX - rect.left - x, event.clientY - rect.top - y);
        picker.selectedIndex = points.reduce((best, point, i) => distance(point) < distance(points[best]) ? i : best, 0);
        showPoint();
    });
    canvas.addEventListener('pointermove', event => {
        if (!points.length || event.pointerType === 'touch') return;
        const rect = canvas.getBoundingClientRect();
        const distance = ([x, y]) => Math.hypot(event.clientX - rect.left - x, event.clientY - rect.top - y);
        const index = points.reduce((best, point, i) => distance(point) < distance(points[best]) ? i : best, 0);
        tooltip.hidden = distance(points[index]) > 24;
        if (tooltip.hidden) return;
        const item = visible[index];
        tooltip.textContent = `${date.format(item.day)} · ${item.category}\n${number.format(item.elo)} Elo (${signed.format(item.change)})`;
        tooltip.style.left = `${Math.max(0, Math.min(points[index][0] - tooltip.offsetWidth / 2, rect.width - tooltip.offsetWidth))}px`;
        tooltip.style.top = `${Math.max(0, points[index][1] - tooltip.offsetHeight - 14)}px`;
    });
    canvas.addEventListener('pointerleave', () => { tooltip.hidden = true; });
    showPeriod();
    new ResizeObserver(draw).observe(canvas);
})();
