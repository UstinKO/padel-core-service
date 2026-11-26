(() => {
    'use strict';
    const root = document.querySelector('[data-message-composer]');
    if (!root) return;
    const element = key => root.querySelector(`[data-${key}]`);
    const strings = Object.fromEntries([...root.querySelectorAll('[data-compose-strings] [data-key]')].map(node => [node.dataset.key, node.textContent]));
    const t = key => strings[key] || key;
    const tournamentId = root.dataset.tournamentId ? Number(root.dataset.tournamentId) : null;
    const groups = new Set(), categories = new Set(), included = new Set(), excluded = new Set();
    let candidates = [], version = 0, loadVersion = 0, busy = false, loaded = false;
    const status = (message, error = false) => {
        element('compose-status').textContent = message;
        element('compose-status').dataset.error = String(error);
    };
    const selected = person => !excluded.has(person.playerId) && (included.has(person.playerId)
        || person.groups.some(group => groups.has(group)) || categories.has(person.category));
    const invalidate = () => {
        version++;
        element('preview-result').hidden = true;
        element('preview-text').textContent = '';
        element('ready-list').replaceChildren();
        element('unavailable-list').replaceChildren();
        element('text-length').textContent = String(element('message-text').value.length);
        element('preview').disabled = !loaded || busy || !element('message-text').value.trim();
        status(t('stale'));
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
    const request = async (path, body) => {
        const response = await fetch('/api/admin/telegram/messages/' + path, { method: body ? 'POST' : 'GET',
            credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}) },
            body: body ? JSON.stringify(body) : undefined });
        if (!response.ok || response.redirected) throw new Error('request-failed');
        return response.json();
    };
    const load = async () => {
        const current = ++loadVersion;
        loaded = false; invalidate(); element('refresh').disabled = true; status(t('loading'));
        try {
            const audience = await request('audience' + (tournamentId ? '?tournamentId=' + tournamentId : ''));
            if (current !== loadVersion) return;
            candidates = audience.candidates; loaded = true;
            const ids = new Set(candidates.map(p => p.playerId));
            for (const id of included) if (!ids.has(id)) included.delete(id);
            for (const id of excluded) if (!ids.has(id)) excluded.delete(id);
            element('event-name').textContent = audience.name;
            element('scope-hint').hidden = !audience.globalAccess;
            choices(element('groups'), tournamentId ? ['ALL', 'MAIN', 'RESERVE', 'UNPAIRED', 'PENDING'] : ['ALL'], groups, true);
            choices(element('categories'), [...new Set(candidates.map(p => p.category))].sort(), categories, false);
            invalidate(); renderCandidates(); status('');
        } catch { if (current === loadVersion) { candidates = []; renderCandidates(); status(t('error'), true); } }
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
        if (busy || !loaded || !element('message-text').value.trim()) return;
        busy = true; element('preview').disabled = true;
        const current = version; status(t('loading'));
        try {
            const result = await request('preview', { tournamentId, groups: [...groups], categories: [...categories],
                includedPlayerIds: [...included], excludedPlayerIds: [...excluded], text: element('message-text').value });
            if (current !== version) return;
            const refreshed = new Map([...result.recipients, ...result.unavailable].map(person => [person.playerId, person]));
            candidates = candidates.map(person => refreshed.get(person.playerId) || person);
            renderCandidates();
            element('preview-text').textContent = result.text;
            showList('ready-list', result.recipients, false); showList('unavailable-list', result.unavailable, true);
            element('preview-result').hidden = false; element('preview-result').focus(); status('');
        } catch { if (current === version) status(t('error'), true); }
        finally { busy = false; element('preview').disabled = !loaded || !element('message-text').value.trim(); }
    });
    element('message-text').addEventListener('input', invalidate);
    element('person-search').addEventListener('input', renderCandidates);
    element('refresh').addEventListener('click', load);
    element('reset').addEventListener('click', () => {
        groups.clear(); categories.clear(); included.clear(); excluded.clear();
        root.querySelectorAll('fieldset input').forEach(box => { box.checked = false; });
        invalidate(); renderCandidates();
    });
    load();
})();
