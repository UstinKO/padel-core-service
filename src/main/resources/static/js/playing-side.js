(() => {
    'use strict';
    const messages = window.playingSideMessages || {};
    const label = (side, admin = false) => messages[side] || (admin ? messages.missing : '');
    const format = (level, side, admin = false) => [level && level !== 'SIN_ESPECIFICAR' ? level : '', label(side, admin)].filter(Boolean).join(' · ');
    window.PadelPlayingSide = {label, format};
    const roster = document.querySelector('[data-preferences-event-id]');
    if (roster) {
        fetch('/api/admin/events/' + encodeURIComponent(roster.dataset.preferencesEventId) + '/participant-preferences',
            {credentials:'same-origin', cache:'no-store', headers:{Accept:'application/json'}})
            .then(response => {if (!response.ok || response.redirected) throw new Error();return response.json();})
            .then(rows => {
                const byId = new Map(rows.map(row => [String(row.playerId), row]));
                const update = () => roster.querySelectorAll('[data-playing-side-player]').forEach(node => {
                    const row = byId.get(node.dataset.playingSidePlayer);
                    const value = row ? format(row.level, row.playingSide, true) : '';
                    if (node.textContent !== value) node.textContent = value;
                });
                update();new MutationObserver(update).observe(roster,{childList:true,subtree:true});
            }).catch(() => {roster.querySelectorAll('[data-playing-side-player]').forEach(node => {node.textContent = messages.loadError;});});
    }
    const endpoint = '/api/players/me/playing-side';
    async function request(path, method = 'POST', body) {
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
        const response = await fetch(endpoint + path, {method, credentials:'same-origin', cache:'no-store',
            headers:{Accept:'application/json', ...(body ? {'Content-Type':'application/json'} : {}), ...(token ? {[header]:token} : {})},
            ...(body ? {body:JSON.stringify(body)} : {})});
        if (!response.ok || response.redirected) throw new Error(messages.error);
        return response.status === 204 ? null : response.json();
    }
    function applied(state) {
        document.querySelectorAll('[data-my-playing-side]').forEach(node => {node.textContent = label(state.playingSide);});
        const editor = document.querySelector('[data-playing-side-editor]');
        if (editor) {editor.querySelector('select').value = state.playingSide || '';editor.dataset.saved = state.playingSide || '';}
    }
    const editor = document.querySelector('[data-playing-side-editor]');
    if (editor) {
        const select = editor.querySelector('select'); const button = editor.querySelector('[data-side-save]');
        const status = editor.querySelector('[data-side-status]'); const form = editor.closest('form');
        let busy = false; editor.dataset.saved = select.value;
        async function save() {
            if (!select.value) {status.textContent = messages.choose;select.focus();return false;}
            if (busy) return false;
            busy = true;button.disabled = true;status.textContent = '';
            try {applied(await request('', 'PATCH', {playingSide:select.value}));status.textContent = messages.saved;return true;}
            catch {status.textContent = messages.error;return false;}
            finally {busy = false;button.disabled = false;}
        }
        button.addEventListener('click', save);
        form?.addEventListener('submit', async event => {
            if (!select.value || select.value === editor.dataset.saved) return;
            event.preventDefault();
            if (await save()) HTMLFormElement.prototype.submit.call(form);
        });
    }
    const dialog = document.querySelector('[data-playing-side-survey]');
    if (!dialog) return;
    const form = dialog.querySelector('form'); const error = dialog.querySelector('[data-side-error]');
    let busy = false;
    const controls = () => Array.from(dialog.querySelectorAll('button'));
    async function action(work) {
        if (busy) return;
        busy = true;controls().forEach(button => {button.disabled = true;});error.textContent = '';
        try {await work();} catch {error.textContent = messages.error;}
        finally {busy = false;controls().forEach(button => {button.disabled = false;});}
    }
    const later = () => action(async () => {await request('/defer');dialog.close();});
    dialog.querySelectorAll('[data-side-later]').forEach(button => button.addEventListener('click', later));
    dialog.addEventListener('cancel', event => {event.preventDefault();later();});
    dialog.addEventListener('click', event => {if (event.target === dialog) {
        const box = dialog.getBoundingClientRect();
        if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) later();
    }});
    form.addEventListener('submit', event => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        action(async () => {
            const data = new FormData(form); const body = {playingSide:data.get('playingSide')};
            const level = data.get('nivelJugador');
            if (!dialog.querySelector('[data-side-level]').hidden && level && level !== 'SIN_ESPECIFICAR') body.nivelJugador = level;
            const state = await request('', 'PATCH', body);applied(state);
            if (body.nivelJugador) {const levelSelect = document.getElementById('nivelJugador');if (levelSelect) levelSelect.value = body.nivelJugador;}
            dialog.close();
        });
    });
    request('/visit').then(state => {
        if (!state.ask) return;
        dialog.querySelector('[data-side-level]').hidden = !state.levelMissing;
        form.elements.nivelJugador.disabled = !state.levelMissing;
        form.elements.nivelJugador.required = state.levelMissing;
        dialog.showModal();
    }).catch(() => { /* Опрос не мешает работе кабинета при недоступности сервера. */ });
})();
