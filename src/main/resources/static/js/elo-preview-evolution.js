(() => {
    const panel = document.querySelector('[data-elo-evolution]');
    if (!panel) return;

    const canvas = panel.querySelector('canvas');
    const context = canvas.getContext('2d');
    const period = panel.querySelector('[data-period]');
    const picker = panel.querySelector('[data-event-picker]');
    const locale = document.documentElement.lang;
    const number = new Intl.NumberFormat(locale, { maximumFractionDigits: 2 });
    const signed = new Intl.NumberFormat(locale, { maximumFractionDigits: 2, signDisplay: 'exceptZero' });
    const percent = new Intl.NumberFormat(locale, { style: 'percent' });
    const date = new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeZone: 'UTC' });
    const shortDate = new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric', timeZone: 'UTC' });
    const history = Array.from(panel.querySelectorAll('li[data-day]'), item => ({
        day: Date.parse(item.dataset.day),
        elo: Number(item.dataset.elo),
        change: Number(item.dataset.change),
        wins: Number(item.dataset.wins),
        matches: Number(item.dataset.matches),
        category: item.textContent
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
    recent.slice().reverse().forEach(event => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'elo-preview-event' + (event.change < 0 ? ' loss' : '');
        const label = document.createElement('span');
        label.textContent = `${event.category} · ${date.format(event.day)}`;
        const detail = document.createElement('b');
        detail.textContent = `${number.format(event.elo)} Elo`;
        label.append(detail);
        const change = document.createElement('strong');
        change.textContent = `${signed.format(event.change)} Elo`;
        button.append(label, change);
        button.addEventListener('click', () => {
            picker.selectedIndex = visible.indexOf(event);
            showPoint();
        });
        recentEvents.append(button);
    });

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
        const bottom = height - 26;
        const start = visible[0].day;
        const end = visible.at(-1).day;
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
        context.textAlign = 'left';
        context.fillText(shortDate.format(start), left, bottom + 18);
        if (start !== end) {
            context.textAlign = 'right';
            context.fillText(shortDate.format(end), right, bottom + 18);
        }
        const accent = style.getPropertyValue('--elo-line').trim();
        context.strokeStyle = accent;
        context.fillStyle = accent;
        const fill = context.createLinearGradient(0, 0, 0, bottom);
        fill.addColorStop(0, '#f1844740');
        fill.addColorStop(1, '#f1844700');
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
            context.beginPath();
            context.arc(x, position, i === picker.selectedIndex ? 5 : 2.5, 0, 2 * Math.PI);
            context.fill();
        });
    }

    function showPoint() {
        const event = visible[picker.selectedIndex];
        panel.querySelector('[data-elo-point]').textContent =
            `${number.format(event.elo)} Elo (${signed.format(event.change)}) · ${event.wins} / ${event.matches}`;
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
    showPeriod();
    new ResizeObserver(draw).observe(canvas);
})();
