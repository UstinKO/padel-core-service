(() => {
    'use strict';
    const text = (key, ...args) => t('dashboard.actions.' + key, ...args);
    function node(tag, content, className) {
        const element = document.createElement(tag);
        if (content) element.textContent = content;
        if (className) element.className = className;
        return element;
    }
    function button(label, action, callback) {
        const control = node('button', label, 'btn btn-outline btn-small');
        control.type = 'button';
        control.dataset.eventAction = action;
        control.addEventListener('click', callback);
        return control;
    }
    async function read(path, options = {}) {
        const response = await fetch(path, {
            ...options, redirect: 'manual', cache: 'no-store',
            signal: AbortSignal.timeout(12000),
            headers: { Accept: 'application/json', ...options.headers }
        });
        if (response.status === 401 || response.type === 'opaqueredirect') throw new Error('session');
        if (!response.ok) throw new Error('request');
        return response.status === 204 ? null : response.json();
    }
    const send = (path, body, method = 'POST') => read(path, {
        method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
    });
    function confirmAction(title, help) {
        return new Promise(resolve => {
            const dialog = node('dialog', '', 'participation-dialog dashboard-action-dialog');
            const heading = node('h3', title);
            heading.id = 'dashboardActionTitle';
            dialog.setAttribute('aria-labelledby', heading.id);
            const back = button(text('back'), 'back', () => dialog.close('back'));
            back.autofocus = true;
            dialog.append(heading, node('p', help), back,
                button(text('apply'), 'apply', () => dialog.close('apply')));
            document.body.append(dialog);
            dialog.addEventListener('close', () => {
                const accepted = dialog.returnValue === 'apply';
                dialog.remove();
                resolve(accepted);
            }, { once: true });
            dialog.showModal();
        });
    }
    function tournament(entry, original) {
        const event = entry.event;
        const parts = new Intl.DateTimeFormat('en-CA', {
            timeZone: 'America/Argentina/Buenos_Aires', year: 'numeric', month: '2-digit',
            day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
        }).formatToParts(new Date(event.startsAt));
        const date = Object.fromEntries(parts.map(part => [part.type, part.value]));
        return { ...original, id: event.id, nombre: event.name, tipo: event.type,
            modalidad: event.modality, generoFormato: event.gender, categoriaNivel: event.level,
            clubNombre: event.clubName, estado: event.status,
            fechaInicio: [Number(date.year), Number(date.month), Number(date.day)],
            horaInicio: [Number(date.hour), Number(date.minute)], dashboardEntry: entry, dashboardSummaryOnly: !original };
    }
    const needsAnswer = entry => entry.participation.some(request => request.availableActions.includes('CONFIRM'));

    window.DashboardEventActions = {
        create(onChange) {
            const panel = document.getElementById('dashboardEventActions');
            if (!panel) return null;
            const feedback = panel.querySelector('[data-event-feedback]');
            const attention = panel.querySelector('[data-event-attention]');
            const retry = panel.querySelector('[data-event-retry]');
            const login = panel.querySelector('[data-event-login]');
            let overview = null, busy = false, loading = false, unavailable = false;
            let notice = '';
            const registrations = new Map();
            function inform(key) {
                feedback.textContent = key ? text(key) : '';
                login.hidden = key !== 'session';
                retry.hidden = !['error', 'uncertain'].includes(key);
            }
            function updateAttention() {
                const count = overview?.upcoming.filter(needsAnswer).length || 0;
                attention.hidden = count === 0;
                attention.textContent = text('attention', count);
            }
            async function load(force = false) {
                if (loading || (busy && !force)) return;
                loading = true;
                retry.disabled = true;
                try {
                    const next = await read('/api/players/me/events');
                    const changed = unavailable || JSON.stringify(next) !== JSON.stringify(overview);
                    overview = next;
                    unavailable = false;
                    inform(notice);
                    updateAttention();
                    if (changed || force) onChange();
                } catch (error) {
                    unavailable = true;
                    inform(error.message === 'session' ? 'session' : 'error');
                    onChange();
                } finally {
                    loading = false;
                    retry.disabled = false;
                }
            }
            async function mutate(entry, action, operation) {
                if (busy || unavailable) return;
                busy = true;
                notice = '';
                document.querySelectorAll('#dashboardTorneosGrid button').forEach(control => { control.disabled = true; });
                try {
                    await operation();
                } catch (error) {
                    notice = error.message === 'session' ? 'session' : 'uncertain';
                } finally {
                    busy = false;
                    await load(true);
                    const selector = '[data-tournament-id="' + entry.event.id + '"] [data-event-action="' + action + '"]';
                    const target = document.querySelector('#dashboardTorneosGrid ' + selector)
                        || document.querySelector('.dashboard-tabs .tab-btn.active');
                    target?.focus({ preventScroll: true });
                }
            }
            function decorate(card, item, history) {
                const entry = item.dashboardEntry;
                if (!entry) {
                    if (unavailable && card.classList.contains('my-tournament')) {
                        card.querySelectorAll('button').forEach(control => { control.disabled = true; });
                        const badge = card.querySelector('.my-tournament-badge');
                        if (badge) badge.textContent = text('error');
                    }
                    return;
                }
                card.dataset.registrationId = entry.registrationId;
                card.setAttribute('aria-label', entry.event.name);
                card.classList.toggle('needs-participation', !history && needsAnswer(entry));
                card.classList.toggle('my-tournament', !history);
                const body = card.querySelector('.torneo-card-body');
                const footer = card.querySelector('.torneo-footer');
                if (entry.event.name !== body.querySelector('.torneo-title').textContent) {
                    body.querySelector('.torneo-title').after(node('p', entry.event.name, 'dashboard-event-name'));
                }
                card.querySelector('.my-tournament-badge')?.remove();
                card.querySelector('.btn-register')?.remove();
                card.querySelector('.btn-cancel')?.remove();
                const state = node('p', text('registration.' + entry.registrationStatus), 'dashboard-registration-status');
                state.dataset.registrationStatus = entry.registrationStatus;
                if (entry.registrationStatus.startsWith('SOLO_')) state.textContent = text('registration.WAITLIST') + ' · ' + state.textContent;
                if (item.dashboardSummaryOnly) Array.from(body.querySelectorAll('.torneo-info-item')).slice(2).forEach(row => row.remove());
                if (entry.registrationStatus === 'WAITLIST' || entry.registrationStatus.startsWith('SOLO_')) state.classList.add('in-reserve');
                body.querySelector('.tournament-modalidad').after(state);
                const facts = node('div', '', 'dashboard-registration-facts');
                const fact = (label, value) => facts.append(node('p', text(label) + ': ' + value));
                if (entry.partnerName) fact('partner', entry.partnerName);
                if (entry.queuePosition != null) fact('queue', entry.queuePosition);
                if (entry.paymentStatus) fact('payment', text('payments.' + entry.paymentStatus));
                if (entry.replacementNeeded) facts.append(node('p', text('replacementNeeded')));
                if (facts.childElementCount) body.querySelector('.torneo-info').after(facts);
                const controls = node('div', '', 'dashboard-inline-actions');
                if (!history) {
                    for (const request of entry.participation) {
                        const section = node('section', '', 'dashboard-participation');
                        section.append(node('p', request.playerName + ' · ' + text('participation.' + request.status)));
                        if (request.delegated) section.append(node('small', text('delegated')));
                        for (const decision of request.availableActions) {
                            const key = decision === 'CONFIRM' ? 'confirm' : 'decline';
                            const control = button(text(key), request.requestId + '-' + key, () => mutate(entry, request.requestId + '-' + key, async () => {
                                if (decision === 'DECLINE' && !await confirmAction(text(key), text('declinePrompt'))) return;
                                await send('/api/players/me/participation/' + encodeURIComponent(request.requestId), { decision });
                            }));
                            if (decision === 'CONFIRM') control.classList.replace('btn-outline', 'btn-primary');
                            section.append(control);
                        }
                        controls.append(section);
                    }
                    function add(action, label, operation, pressed) {
                        if (!entry.availableActions.includes(action)) return;
                        const control = button(text(label), action, () => mutate(entry, action, operation));
                        if (pressed != null) control.setAttribute('aria-pressed', String(pressed));
                        controls.append(control);
                    }
                    add('WITHDRAW', 'withdraw', async () => {
                        if (entry.mode === 'PAIR') return window.PairTerms.withdraw(entry.event.id);
                        if (await confirmAction(text('withdraw'), text('withdrawPrompt')))
                            await send('/api/players/me/events/' + entry.registrationId + '/withdraw', { version: entry.version });
                    });
                    add('CANCEL_PAIR', 'cancelPair', () => window.PairTerms.cancel(entry.event.id));
                    add('CHANGE_CONTACT_VISIBILITY', 'shareContacts', () => send('/api/players/me/events/' + entry.registrationId + '/contact-visibility', {
                        version: entry.version, shareContacts: !entry.shareContacts
                    }, 'PUT'), entry.shareContacts);
                    add('SEARCH_REPLACEMENT', 'searchReplacement', () => send('/api/players/me/tournaments/' + entry.event.id + '/replacement-search', {
                        enabled: !entry.replacementSearch
                    }, 'PUT'), entry.replacementSearch);
                }
                for (const cancellation of entry.cancellations) {
                    const reason = node('p', text('initiator.' + cancellation.initiator) + ' · ' + text('reason.' + cancellation.reasonCode));
                    if (cancellation.organizerComment) reason.append(node('span', ' · ' + cancellation.organizerComment));
                    controls.append(reason);
                }
                if (controls.childElementCount) footer.before(controls);
                if (busy || unavailable) card.querySelectorAll('button').forEach(control => { control.disabled = true; });
            }
            retry.textContent = text('retry');
            retry.addEventListener('click', () => { notice = ''; load(); });
            attention.addEventListener('click', () => {
                window.switchTab('mis');
                document.querySelector('.needs-participation')?.scrollIntoView({ block: 'center', behavior: 'instant' });
                document.querySelector('.needs-participation [data-event-action]')?.focus({ preventScroll: true });
            });
            window.addEventListener('focus', () => { if (!document.querySelector('dialog[open]')) load(); });
            document.addEventListener('visibilitychange', () => { if (!document.hidden && !document.querySelector('dialog[open]')) load(); });
            return {
                load, decorate,
                get ready() { return overview !== null; },
                ids: () => overview?.upcoming.map(entry => entry.event.id) || [],
                registration(item) {
                    const signature = JSON.stringify(item.dashboardEntry);
                    return () => {
                        const cached = registrations.get(item.id);
                        if (cached?.signature === signature && Date.now() - cached.at < 30000) return cached.promise;
                        const promise = read('/players/tournaments/' + item.id + '/my-registration');
                        registrations.set(item.id, { signature, at: Date.now(), promise });
                        promise.catch(() => {
                            if (registrations.get(item.id)?.promise === promise) registrations.delete(item.id);
                        });
                        return promise;
                    };
                },
                list(tab, active, historical) {
                    const originals = new Map([...historical, ...active].map(item => [item.id, item]));
                    const rows = tab === 'historial' ? overview.history : overview.upcoming;
                    if (tab === 'todos') return active.filter(item => !overview.upcoming.some(entry => entry.event.id === item.id));
                    const entries = rows.map(entry => tournament(entry, originals.get(entry.event.id)));
                    if (tab === 'historial') {
                        const seen = new Set(rows.map(entry => entry.event.id));
                        entries.push(...historical.filter(item => !seen.has(item.id)));
                    } else entries.sort((a, b) => Number(needsAnswer(b.dashboardEntry)) - Number(needsAnswer(a.dashboardEntry)));
                    return entries;
                }
            };
        }
    };
})();
