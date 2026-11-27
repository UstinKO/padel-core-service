(() => {
    'use strict';
    document.querySelectorAll('[data-tg-connections]').forEach(panel => {
        const button = panel.querySelector('[data-tg-connections-refresh]');
        const values = panel.querySelector('[data-tg-connection-values]');
        const status = panel.querySelector('[data-tg-connections-status]');
        const keys = ['registered', 'connected', 'notConnected', 'whatsapp', 'declined', 'guests'];
        const refresh = async () => {
            if (button.disabled) return;
            button.disabled = true;
            values.hidden = true;
            status.textContent = panel.dataset.loading;
            status.removeAttribute('data-error');
            try {
                const response = await fetch('/api/admin/telegram/connections', {
                    credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json' }
                });
                if (!response.ok || response.redirected) throw new Error('connections-unavailable');
                const data = await response.json();
                if (!keys.every(key => Number.isSafeInteger(data[key]) && data[key] >= 0)
                    || data.registered !== data.connected + data.notConnected + data.whatsapp + data.declined) {
                    throw new Error('invalid-connections');
                }
                const numbers = new Intl.NumberFormat(document.documentElement.lang || 'es');
                panel.querySelectorAll('[data-tg-count]').forEach(element => {
                    element.textContent = numbers.format(data[element.dataset.tgCount]);
                });
                const progress = panel.querySelector('[data-tg-connection-progress]');
                progress.max = Math.max(1, data.registered);
                progress.value = data.connected;
                panel.querySelector('[data-tg-connection-percent]').textContent = new Intl.NumberFormat(
                    document.documentElement.lang || 'es', { style: 'percent', maximumFractionDigits: 1 }
                ).format(data.registered ? data.connected / data.registered : 0);
                values.hidden = false;
                status.textContent = '';
            } catch {
                status.textContent = panel.dataset.error;
                status.dataset.error = 'true';
            } finally { button.disabled = false; }
        };
        button.addEventListener('click', refresh);
        refresh();
    });
})();
