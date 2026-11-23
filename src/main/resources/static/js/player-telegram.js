(() => {
    'use strict';
    const profile = document.querySelector('[data-tg-profile]');
    const admin = document.querySelector('[data-tg-admin]');
    const text = (panel, key) => panel.dataset[key] || '';
    const status = (panel, message, error = false) => {
        const element = panel.querySelector('[data-tg-status]');
        element.textContent = message;
        element.dataset.error = String(error);
    };
    const request = async (url, method = 'GET') => {
        const response = await fetch(url, { method, credentials: 'same-origin', headers: { Accept: 'application/json' }, cache: 'no-store' });
        if (!response.ok || response.redirected) throw new Error('telegram-request-failed');
        return response.status === 204 ? null : response.json();
    };
    const locked = async (panel, operation) => {
        if (panel.dataset.busy === 'true') return;
        panel.dataset.busy = 'true';
        panel.querySelectorAll('button').forEach(button => { button.disabled = true; });
        status(panel, text(panel, 'loading'));
        try { await operation(); }
        catch { status(panel, text(panel, 'error'), true); }
        finally {
            panel.dataset.busy = 'false';
            panel.querySelectorAll('button').forEach(button => { button.disabled = false; });
            if (panel === profile) panel.querySelector('[data-tg-connect]').disabled = panel.dataset.available !== 'true';
        }
    };
    if (profile) {
        const connect = profile.querySelector('[data-tg-connect]');
        const disconnect = profile.querySelector('[data-tg-disconnect]');
        const open = profile.querySelector('[data-tg-open]');
        let expiresAt = 0;
        const refresh = async () => {
            const state = await request('/api/players/me/telegram');
            profile.dataset.available = String(state.available);
            connect.hidden = state.connected;
            disconnect.hidden = !state.connected;
            connect.disabled = !state.available;
            if (state.connected || Date.now() >= expiresAt) { open.hidden = true; open.removeAttribute('href'); }
            status(profile, text(profile, state.connected ? 'connected' : state.available ? 'disconnected' : 'unavailable'));
        };
        connect.addEventListener('click', () => locked(profile, async () => {
            const link = await request('/api/players/me/telegram/link', 'POST');
            const url = new URL(link.url);
            if (url.protocol !== 'https:' || url.hostname !== 't.me') throw new Error('invalid-link');
            expiresAt = Date.parse(link.expiresAt);
            open.href = url.href;
            open.hidden = false;
            status(profile, text(profile, 'openHint'));
        }));
        disconnect.addEventListener('click', () => locked(profile, async () => {
            await request('/api/players/me/telegram', 'DELETE');
            expiresAt = 0;
            await refresh();
        }));
        profile.querySelector('[data-tg-refresh]').addEventListener('click', () => locked(profile, refresh));
        document.addEventListener('visibilitychange', () => { if (!document.hidden) locked(profile, refresh); });
        locked(profile, refresh);
    }
    if (admin) {
        const base = '/api/admin/tournaments/' + encodeURIComponent(admin.dataset.tournamentId) + '/telegram';
        const render = summary => {
            const rows = summary.participants;
            const confirmed = rows.filter(row => row.response === 'CONFIRMED').length;
            admin.querySelector('[data-tg-total]').textContent = String(rows.length);
            admin.querySelector('[data-tg-confirmed]').textContent = String(confirmed);
            admin.querySelector('[data-tg-pending]').textContent = String(rows.length - confirmed);
            admin.querySelector('[data-tg-missing]').textContent = String(rows.filter(row => !row.connected).length);
            const list = admin.querySelector('[data-tg-participants]');
            list.replaceChildren();
            rows.forEach(row => {
                const item = document.createElement('li');
                const name = document.createElement('strong');
                name.textContent = row.name;
                const detail = document.createElement('span');
                const responseKey = { CONFIRMED: 'confirmed', PENDING: 'pending', NOT_REQUIRED: 'notRequired', DECLINED: 'declined' }[row.response] || 'pending';
                const notes = [text(admin, responseKey)];
                if (!row.connected) notes.push(text(admin, 'noTelegram'));
                if (['FAILED', 'BLOCKED', 'UNKNOWN'].includes(row.delivery)) notes.push(text(admin, 'deliveryError'));
                detail.textContent = notes.join(' · ');
                item.append(name, detail);
                list.append(item);
            });
        };
        const refresh = async () => { render(await request(base)); status(admin, ''); };
        admin.querySelector('[data-tg-refresh]').addEventListener('click', () => locked(admin, refresh));
        admin.querySelector('[data-tg-request]').addEventListener('click', () => locked(admin, async () => {
            render(await request(base + '/confirmations', 'POST'));
            status(admin, text(admin, 'sent'));
        }));
        const summary = admin.querySelector('[data-tg-summary]');
        if (summary) summary.addEventListener('click', () => locked(admin, async () => {
            await request('/api/admin/telegram/summary', 'POST');
            status(admin, text(admin, 'summarySent'));
        }));
        locked(admin, refresh);
    }
})();
