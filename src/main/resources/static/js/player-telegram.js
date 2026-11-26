(() => {
    'use strict';
    const profiles = new Map();
    const admin = document.querySelector('[data-tg-admin]');
    const text = (panel, key) => panel.dataset[key] || '';
    const status = (panel, message, error = false) => {
        const element = panel.querySelector('[data-tg-status]');
        element.textContent = message;
        element.dataset.error = String(error);
    };
    const request = async (url, method = 'GET', body) => {
        const headers = { Accept: 'application/json' };
        if (body) headers['Content-Type'] = 'application/json';
        const response = await fetch(url, { method, credentials: 'same-origin', headers,
            cache: 'no-store', body: body ? JSON.stringify(body) : undefined });
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
            const connect = panel.querySelector('[data-tg-connect]');
            if (connect) connect.disabled = panel.dataset.available !== 'true';
        }
    };
    const initProfile = (profile, initialState) => {
        const connect = profile.querySelector('[data-tg-connect]');
        const disconnect = profile.querySelector('[data-tg-disconnect]');
        const open = profile.querySelector('[data-tg-open]');
        let expiresAt = 0;
        const preference = profile.querySelector('[data-tg-preference]');
        const whatsapp = profile.querySelector('[data-tg-whatsapp]');
        const refreshButton = profile.querySelector('[data-tg-refresh]');
        const render = state => {
            profile.hidden = state.eligible !== true;
            profile.dataset.available = String(state.available);
            connect.hidden = state.connected;
            if (disconnect) disconnect.hidden = !state.connected;
            connect.disabled = !state.available;
            if (preference) preference.value = state.preference || 'UNSPECIFIED';
            if (state.connected || state.preference === 'WHATSAPP' || Date.now() >= expiresAt) { open.hidden = true; open.removeAttribute('href'); }
            const usesWhatsapp = state.preference === 'WHATSAPP';
            status(profile, text(profile, state.connected ? 'connected' : usesWhatsapp ? 'whatsappSaved' : state.available ? 'disconnected' : 'unavailable'));
            if (whatsapp) whatsapp.hidden = state.connected || usesWhatsapp;
            if (profile.hasAttribute('data-tg-prompt')) {
                profile.querySelector('[data-tg-title]').textContent = text(profile,
                    state.connected ? 'connectedTitle' : usesWhatsapp ? 'whatsappTitle' : 'promptTitle');
                profile.querySelector('[data-tg-offer]').hidden = state.connected || usesWhatsapp;
                profile.querySelector('[data-tg-profile-link]').hidden = !usesWhatsapp;
                connect.hidden = state.connected || usesWhatsapp || !open.hidden;
                refreshButton.hidden = open.hidden;
            }
        };
        const refresh = async () => render(await request('/api/players/me/telegram'));
        connect.addEventListener('click', () => locked(profile, async () => {
            const link = await request('/api/players/me/telegram/link', 'POST');
            const url = new URL(link.url);
            if (url.protocol !== 'https:' || url.hostname !== 't.me') throw new Error('invalid-link');
            expiresAt = Date.parse(link.expiresAt);
            open.href = url.href;
            open.hidden = false;
            if (profile.hasAttribute('data-tg-prompt')) {
                connect.hidden = true;
                refreshButton.hidden = false;
            }
            status(profile, text(profile, 'openHint'));
        }));
        if (disconnect) disconnect.addEventListener('click', () => locked(profile, async () => {
            await request('/api/players/me/telegram', 'DELETE');
            expiresAt = 0;
            await refresh();
        }));
        const savePreference = value => locked(profile, async () => {
            render(await request('/api/players/me/telegram/preference', 'PUT', { preference: value }));
        });
        if (whatsapp) whatsapp.addEventListener('click', () => savePreference('WHATSAPP'));
        profile.querySelector('[data-tg-save-preference]')?.addEventListener('click', () => savePreference(preference.value));
        refreshButton.addEventListener('click', () => locked(profile, refresh));
        profiles.set(profile, refresh);
        if (initialState) render(initialState); else locked(profile, refresh);
    };
    document.querySelectorAll('[data-tg-profile]').forEach(profile => initProfile(profile));
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) return;
        profiles.forEach((refresh, panel) => {
            if (panel.isConnected) locked(panel, refresh); else profiles.delete(panel);
        });
    });
    const template = document.querySelector('template[data-tg-prompt-template]');
    if (template) window.PlayerTelegramRegistration = {
        async mount(modal) {
            try {
                const body = modal.querySelector('.modal-body');
                const content = body?.firstElementChild;
                const state = await request('/api/players/me/telegram');
                if (!body || !state.eligible || !state.available || state.connected || state.preference === 'WHATSAPP') return false;
                if (!modal.isConnected || !modal.classList.contains('show')) return false;
                if (body.firstElementChild !== content) return false;
                if (body.querySelector('[data-tg-prompt]')) return true;
                const panel = template.content.firstElementChild.cloneNode(true);
                body.append(panel);
                initProfile(panel, state);
                return true;
            } catch { return false; }
        }
    };
    if (admin) {
        const disclosure = admin.querySelector('[data-tg-disclosure]');
        const userId = disclosure.dataset.preferenceUserId;
        const disclosureKey = userId ? 'telegram-admin-expanded:' + userId + ':' + admin.dataset.tournamentId : null;
        try { disclosure.open = disclosureKey !== null && localStorage.getItem(disclosureKey) === 'true'; }
        catch { /* Без хранилища первое открытие остаётся свёрнутым. */ }
        disclosure.addEventListener('toggle', () => {
            if (disclosureKey === null) return;
            try { localStorage.setItem(disclosureKey, String(disclosure.open)); }
            catch { /* Ограничение хранилища не блокирует управление оплатами. */ }
        });
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
                if (row.preference === 'WHATSAPP') notes.push(text(admin, 'whatsappManual'));
                else if (!row.connected) notes.push(text(admin, 'manualContact'));
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
