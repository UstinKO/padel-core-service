(() => {
    'use strict';
    const root = document.querySelector('[data-message-composer]');
    if (!root) return;
    const element = key => root.querySelector(`[data-${key}]`);
    const strings = Object.fromEntries([...root.querySelectorAll('[data-compose-strings] [data-key]')].map(node => [node.dataset.key, node.textContent]));
    const t = key => strings[key] || key;
    const campaignStrings = Object.fromEntries([...root.querySelectorAll('[data-campaign-strings] [data-key]')].map(node => [node.dataset.key,node.textContent]));
    const ct = key => campaignStrings[key] || t(key);
    const tournamentId = root.dataset.tournamentId ? Number(root.dataset.tournamentId) : null;
    const groups = new Set(), categories = new Set(), included = new Set(), excluded = new Set();
    let candidates = [], version = 0, loadVersion = 0, busy = false, loaded = false;
    let prepared = null, prepareKey = crypto.randomUUID(), testKey = crypto.randomUUID(), testQueued = false, sending = false, uncertain = false;
    let historyPage = 0, historyVersion = 0, historyTimer, confirmation = null, historyDetail = null;
    let sessionRequired = false;
    class SessionRequired extends Error {}
    const status = (message, error = false) => {
        element('compose-status').textContent = message;
        element('compose-status').dataset.error = String(error);
    };
    const selected = person => !excluded.has(person.playerId) && (included.has(person.playerId)
        || person.groups.some(group => groups.has(group)) || categories.has(person.category));
    const invalidate = () => {
        version++;
        prepared = null; prepareKey = crypto.randomUUID(); testKey = crypto.randomUUID(); testQueued = false; uncertain = false;
        element('send').disabled = true; element('test').disabled = true;
        element('preview-result').hidden = true;
        element('preview-text').textContent = '';
        element('ready-list').replaceChildren();
        element('unavailable-list').replaceChildren();
        element('text-length').textContent = String(element('message-text').value.length);
        element('preview').disabled = !loaded || busy || sessionRequired || !element('message-text').value.trim();
        status(t(sessionRequired ? 'sessionExpiredShort' : 'stale'), sessionRequired);
    };
    const normalize = value => value.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLocaleLowerCase();
    const renderCandidates = () => {
        const query = normalize(element('person-search').value.trim());
        const shown = candidates.filter(person => normalize(`${person.name} ${person.category}`).includes(query));
        const list = element('candidates');
        list.replaceChildren();
        for (const person of shown) {
            const label = document.createElement('label'); label.className = 'tg-compose-person';
            const checkbox = document.createElement('input'); checkbox.type = 'checkbox';
            checkbox.checked = selected(person); checkbox.dataset.playerId = String(person.playerId);
            checkbox.addEventListener('change', () => {
                if (checkbox.checked) { excluded.delete(person.playerId); included.add(person.playerId); }
                else { included.delete(person.playerId); excluded.add(person.playerId); }
                invalidate(); renderCandidates();
            });
            const detail = document.createElement('span'); detail.textContent = `${person.name} · ${person.category}`;
            const reason = document.createElement('small'); reason.textContent = t(person.availability);
            detail.append(reason); label.append(checkbox, detail); list.append(label);
        }
        if (!shown.length) list.textContent = t('empty');
        const chosen = candidates.filter(selected);
        element('selection-count').textContent = `${t('selected')}: ${chosen.length} · ${t('ready')}: ${chosen.filter(p => p.availability === 'AVAILABLE').length}`;
    };
    const choices = (target, values, chosen, translate) => {
        target.replaceChildren();
        for (const value of values) {
            const label = document.createElement('label'); label.className = 'tg-compose-option';
            const box = document.createElement('input'); box.type = 'checkbox'; box.value = value; box.checked = chosen.has(value);
            box.addEventListener('change', () => {
                if (box.checked) chosen.add(value); else chosen.delete(value);
                invalidate(); renderCandidates();
            });
            label.append(box, document.createTextNode(translate ? t(value) : value)); target.append(label);
        }
    };
    const requireSession = () => {
        sessionRequired = true; clearTimeout(historyTimer);
        element('session-warning').hidden = false;
        element('preview').disabled = true; element('send').disabled = true; element('test').disabled = true;
        element('history-retry').hidden = true;
        if (element('send-dialog').open) element('send-dialog').close();
        status(t('sessionExpiredShort'), true); element('history-status').textContent = t('sessionExpiredShort');
    };
    const reportError = error => {
        if (!(error instanceof SessionRequired) && !sessionRequired) status(t('error'), true);
    };
    const request = async (path, body) => {
        const response = await fetch('/api/admin/telegram/messages/' + path, { method: body ? 'POST' : 'GET',
            credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}) },
            body: body ? JSON.stringify(body) : undefined });
        if (response.status === 401 || response.redirected) {
            requireSession(); throw new SessionRequired('session-required');
        }
        if (!response.ok) throw new Error('request-failed');
        return response.json();
    };
    const load = async () => {
        const current = ++loadVersion;
        loaded = false; invalidate(); element('refresh').disabled = true; status(t('loading'));
        try {
            const audience = await request('audience' + (tournamentId ? '?tournamentId=' + tournamentId : ''));
            if (current !== loadVersion) return;
            candidates = audience.candidates; loaded = true;
            sessionRequired = false; element('session-warning').hidden = true;
            const ids = new Set(candidates.map(p => p.playerId));
            for (const id of included) if (!ids.has(id)) included.delete(id);
            for (const id of excluded) if (!ids.has(id)) excluded.delete(id);
            element('event-name').textContent = audience.name;
            element('scope-hint').hidden = !audience.globalAccess;
            choices(element('groups'), tournamentId ? ['ALL', 'MAIN', 'RESERVE', 'UNPAIRED', 'PENDING'] : ['ALL'], groups, true);
            choices(element('categories'), [...new Set(candidates.map(p => p.category))].sort(), categories, false);
            invalidate(); renderCandidates(); status('');
        } catch (error) { if (current === loadVersion) { if (!(error instanceof SessionRequired)) { candidates = []; renderCandidates(); } reportError(error); } }
        finally { if (current === loadVersion) element('refresh').disabled = false; }
    };
    const showList = (key, people, reasons) => {
        const list = element(key); list.replaceChildren();
        for (const person of people) {
            const li = document.createElement('li'); li.textContent = `${person.name} · ${person.category}${reasons ? ' — ' + t(person.availability) : ''}`;
            list.append(li);
        }
        if (!people.length) list.textContent = t('empty');
    };
    element('preview').addEventListener('click', async () => {
        if (busy || !loaded || sessionRequired || !element('message-text').value.trim()) return;
        busy = true; element('preview').disabled = true;
        const current = version; status(t('loading'));
        try {
            const result = await request('campaigns', { requestId: prepareKey, selection: { tournamentId, groups: [...groups], categories: [...categories],
                includedPlayerIds: [...included], excludedPlayerIds: [...excluded], text: element('message-text').value } });
            if (current !== version) return;
            const refreshed = new Map([...result.recipients, ...result.unavailable].map(person => [person.playerId, person]));
            candidates = candidates.map(person => refreshed.get(person.playerId) || person);
            renderCandidates();
            element('preview-text').textContent = result.text;
            showList('ready-list', result.recipients, false); showList('unavailable-list', result.unavailable, true);
            prepared = result;
            element('preview-result').hidden = false; element('preview-result').focus(); status('');
            updateActions(); refreshHistory();
        } catch (error) { if (current === version) reportError(error); }
        finally { busy = false; element('preview').disabled = !loaded || sessionRequired || !element('message-text').value.trim(); }
    });
    element('message-text').addEventListener('input', invalidate);
    element('person-search').addEventListener('input', renderCandidates);
    element('refresh').addEventListener('click', load);
    element('session-refresh').addEventListener('click', async () => { await load(); if (!sessionRequired) refreshHistory(); });
    element('reset').addEventListener('click', () => {
        groups.clear(); categories.clear(); included.clear(); excluded.clear();
        root.querySelectorAll('fieldset input').forEach(box => { box.checked = false; });
        invalidate(); renderCandidates();
    });
    const updateActions = () => {
        element('send').disabled = !prepared || !prepared.sendingAvailable || !prepared.recipients.length || sending || uncertain || sessionRequired;
        element('test').disabled = !prepared || !prepared.canTest || testQueued || sending || uncertain || sessionRequired;
        element('send-hint').textContent = sessionRequired ? t('sessionExpiredShort') : uncertain ? ct('uncertain') : prepared?.dispatched ? ct('queued') : !prepared?.sendingAvailable ? ct('sendDisabled')
            : !prepared.canTest ? ct('testUnavailable') : '';
    };
    const meta = campaign => `${ct(campaign.kind)} · ${ct(campaign.state)} · ${campaign.author} · ${new Date(campaign.createdAt).toLocaleString()}`;
    const details = campaign => {
        historyDetail = campaign;
        element('history-meta').textContent = meta(campaign); element('history-text').textContent = campaign.text;
        element('history-people').replaceChildren();
        for (const person of campaign.recipients) {
            const li = document.createElement('li');
            li.textContent = `${person.name} — ${ct(person.state)}${person.reason !== person.state ? ' · ' + ct(person.reason) : ''}`;
            element('history-people').append(li);
        }
        element('history-retry').hidden = !campaign.canRetry || uncertain || sessionRequired;
    };
    const refreshHistory = async () => {
        clearTimeout(historyTimer);
        const current = ++historyVersion; element('history-status').textContent = ct('loading');
        try {
            const rows = await request('campaigns?page=' + historyPage + (tournamentId ? '&tournamentId=' + tournamentId : ''));
            if (current !== historyVersion) return;
            element('history-list').replaceChildren();
            for (const campaign of rows) {
                const article = document.createElement('article'); article.dataset.campaignId = campaign.id;
                article.dataset.campaignKind = campaign.kind; article.dataset.campaignState = campaign.state;
                const summary = document.createElement('p'); summary.textContent = meta(campaign);
                const counts = document.createElement('p');
                counts.textContent = [...new Set(campaign.recipients.map(p => p.state))].map(state => `${ct(state)}: ${campaign.recipients.filter(p => p.state === state).length}`).join(' · ');
                const button = document.createElement('button'); button.type = 'button'; button.className = 'btn btn-outline';
                button.textContent = ct('details'); button.addEventListener('click', () => {
                    details(campaign); element('history-dialog').showModal();
                });
                article.append(summary, counts, button); element('history-list').append(article);
            }
            element('history-status').textContent = rows.length ? '' : ct('empty');
            element('history-previous').disabled = historyPage === 0; element('history-next').disabled = rows.length < 20;
            if (historyDetail && element('history-dialog').open) {
                const id = historyDetail.id;
                const refreshed = await request('campaigns/' + id);
                if (current === historyVersion && element('history-dialog').open && historyDetail?.id === id) details(refreshed);
            }
            if (rows.some(c => c.state === 'QUEUED') && !document.hidden && !sessionRequired) historyTimer = setTimeout(refreshHistory, 2500);
        } catch (error) { if (current === historyVersion && !(error instanceof SessionRequired)) element('history-status').textContent = ct('error'); }
    };
    const openConfirmation = (campaign, kind) => {
        confirmation = { campaign, kind, requestId: crypto.randomUUID() };
        const people = campaign.recipients.filter(p => p.state === (kind === 'retry' ? 'FAILED' : 'PENDING'));
        element('confirm-summary').textContent = `${ct(kind === 'retry' ? 'confirmRetry' : 'confirmSend')}: ${people.length}`;
        element('confirm-text').textContent = campaign.text; element('confirm-people').replaceChildren();
        for (const person of people) { const li = document.createElement('li'); li.textContent = person.name; element('confirm-people').append(li); }
        element('confirm-submit').disabled = people.length === 0 || sending || uncertain || sessionRequired; element('send-dialog').showModal();
    };
    element('send').addEventListener('click', async () => {
        if (!prepared || sending || uncertain || sessionRequired) return;
        try {
            const campaign = await request('campaigns/' + prepared.campaignId);
            if (prepared?.campaignId === campaign.id && campaign.canConfirm) openConfirmation(campaign, 'confirm');
            else { element('send').disabled = true; refreshHistory(); }
        } catch (error) { reportError(error); }
    });
    element('confirm-cancel').addEventListener('click', () => element('send-dialog').close());
    element('confirm-submit').addEventListener('click', async () => {
        if (!confirmation || sending || uncertain || sessionRequired) return;
        const {campaign,kind,requestId} = confirmation;
        sending = true; element('confirm-submit').disabled = true; updateActions();
        try {
            await request('campaigns/' + campaign.id + '/' + kind, kind === 'retry' ? {confirmed:true,requestId} : {confirmed:true});
            if (prepared?.campaignId === campaign.id) { prepared.sendingAvailable = false; prepared.canTest = false; prepared.dispatched = true; }
            status(ct('queued')); element('send-dialog').close(); refreshHistory();
        } catch (error) {
            uncertain = true; if (!(error instanceof SessionRequired) && !sessionRequired) status(ct('uncertain'), true);
            element('send-dialog').close(); if (!sessionRequired) refreshHistory();
        } finally { sending = false; updateActions(); }
    });
    element('test').addEventListener('click', async () => {
        if (!prepared?.canTest || sending || uncertain || testQueued || sessionRequired) return;
        const id = prepared.campaignId; sending = true; updateActions();
        try {
            await request('campaigns/' + id + '/test', {confirmed:true,requestId:testKey});
            if (prepared?.campaignId === id) testQueued = true;
            status(ct('queued')); refreshHistory();
        } catch (error) { uncertain = true; if (!(error instanceof SessionRequired) && !sessionRequired) status(ct('uncertain'), true); if (!sessionRequired) refreshHistory(); }
        finally { sending = false; updateActions(); }
    });
    element('history-retry').addEventListener('click', () => {
        if (historyDetail?.canRetry && !sending && !uncertain && !sessionRequired) openConfirmation(historyDetail,'retry');
    });
    element('history-close').addEventListener('click', () => element('history-dialog').close());
    element('history-refresh').addEventListener('click', refreshHistory);
    element('history-previous').addEventListener('click', () => { if (historyPage > 0) { historyPage--; refreshHistory(); } });
    element('history-next').addEventListener('click', () => { historyPage++; refreshHistory(); });
    window.addEventListener('pagehide', () => clearTimeout(historyTimer));
    load(); refreshHistory();
})();
