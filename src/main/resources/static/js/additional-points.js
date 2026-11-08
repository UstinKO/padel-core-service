(() => {
    const copy = document.getElementById('additional-points-copy')?.dataset ?? {};
    const sign = points => points > 0 ? `+${points}` : String(points ?? 0);
    const stage = round => round === 0 ? copy.before : `${copy.round} ${round}`;
    const escape = value => String(value ?? '').replace(/[&<>"']/g, char =>
        ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'})[char]);

    window.tournamentPoints = {
        copy,
        escape,
        cell(points) {
            const total = sign(points?.total);
            const lines = (points?.stages ?? []).map(item =>
                `<dt>${escape(stage(item.roundNumber))}</dt><dd>${sign(item.points)}</dd>`).join('');
            return `<td class="additional-points-cell"><details class="additional-points-breakdown">
                <summary aria-label="${escape(copy.title)}: ${total}">${total}</summary>
                <dl>${lines}<dt>${escape(copy.total)}</dt><dd>${total}</dd></dl></details></td>`;
        },
        action(playerId = '', roundNumber = '', label = copy.action) {
            return `<button type="button" class="btn btn-outline btn-sm" data-open-points
                data-player-id="${escape(playerId)}" data-round-number="${escape(roundNumber)}">${escape(label)}</button>`;
        }
    };

    const dialog = document.getElementById('additional-points-editor');
    if (!dialog) return;
    const endpoint = () => dialog.dataset.url +
        (dialog.dataset.finished === 'true' || window.kingOfCourt?.isFinished ? '/finished-results' : '') + '/additional-points';
    const form = dialog.querySelector('[data-entry-form]');
    const fields = form.elements;
    const status = dialog.querySelector('[data-status]');
    const list = dialog.querySelector('[data-entries]');
    const audit = dialog.querySelector('[data-audit]');
    const newButton = dialog.querySelector('[data-new]');
    const saveButton = form.querySelector('[type=submit]');
    let editor;
    let editing;
    let saving = false;

    function preview() {
        form.querySelector('[data-preview]').textContent = [
            fields.playerId.selectedOptions[0]?.text,
            fields.roundNumber.selectedOptions[0]?.text,
            fields.amount.value ? sign(Number(fields.amount.value) * Number(fields.direction.value)) : ''
        ].filter(Boolean).join(' · ');
    }

    function selectEntry(playerId, roundNumber, entry) {
        editing = entry?.id;
        fields.playerId.value = playerId;
        if (!fields.playerId.value) fields.playerId.selectedIndex = 0;
        fields.roundNumber.value = roundNumber;
        if (!fields.roundNumber.value) fields.roundNumber.value = '0';
        fields.direction.value = entry?.delta < 0 ? '-1' : '1';
        fields.amount.value = entry ? Math.abs(entry.delta) : '';
        fields.note.value = entry?.note ?? '';
        newButton.hidden = !entry;
        preview();
    }

    function entryButton(label, onClick) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'btn btn-outline btn-sm';
        button.textContent = label;
        button.addEventListener('click', onClick);
        return button;
    }

    function renderEntries() {
        const playerName = id => editor.players.find(player => player.id === id)?.name ?? `#${id}`;
        list.replaceChildren(...editor.entries.map(entry => {
            const row = document.createElement('div');
            row.className = 'additional-points-entry';
            const label = document.createElement('span');
            label.textContent = [playerName(entry.playerId), stage(entry.roundNumber), sign(entry.delta),
                entry.note, entry.cancelled ? dialog.dataset.cancelled : ''].filter(Boolean).join(' · ');
            row.append(label);
            if (!entry.cancelled) {
                row.append(entryButton(dialog.dataset.edit, () => selectEntry(entry.playerId, entry.roundNumber, entry)),
                    entryButton(dialog.dataset.cancelEntry, () => {
                        if (window.confirm(dialog.dataset.confirmCancel)) {
                            mutate(`/${entry.id}/cancel`, new URLSearchParams());
                        }
                    }));
            }
            return row;
        }));
        audit.hidden = editor.history.length === 0;
        const actions = {ADD_POINTS: 'addAction', EDIT_POINTS: 'editAction', CANCEL_POINTS: 'cancelAction'};
        const formatter = new Intl.DateTimeFormat(document.documentElement.lang, {dateStyle: 'short', timeStyle: 'short'});
        audit.querySelector('[data-audit-list]').replaceChildren(...editor.history.map(item => {
            const line = document.createElement('li');
            line.textContent = [formatter.format(new Date(item.time)), item.actor, dialog.dataset[actions[item.action]],
                item.playerName, stage(item.roundNumber), sign(item.delta), item.note].filter(Boolean).join(' · ');
            return line;
        }));
    }

    async function load(playerId = '', roundNumber = '', keepStatus = false) {
        form.hidden = true;
        if (!keepStatus) status.textContent = '';
        try {
            const response = await fetch(endpoint(), {cache: 'no-store'});
            if (!response.ok || response.redirected) throw new Error();
            editor = await response.json();
            fields.playerId.replaceChildren(...editor.players.map(player => new Option(player.name, player.id)));
            fields.roundNumber.replaceChildren(...[0, ...editor.rounds].map(round => new Option(stage(round), round)));
            selectEntry(playerId, roundNumber);
            renderEntries();
            form.hidden = false;
        } catch {
            list.replaceChildren();
            audit.hidden = true;
            status.textContent = dialog.dataset.loadError;
        }
    }

    async function mutate(suffix, body) {
        if (saving || !editor) return;
        saving = true;
        saveButton.disabled = true;
        status.textContent = '';
        const before = editor.conductVersion;
        body.set('expectedConductVersion', before);
        const player = fields.playerId.value;
        const round = fields.roundNumber.value;
        try {
            const response = await conductFetch(endpoint() + suffix, {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: body.toString()
            });
            if (response.ok || response.headers.has('X-Conduct-Replayed')) {
                status.textContent = dialog.dataset.saved;
                await load(player, round, true);
                const after = Number(response.headers.get('X-Conduct-Version'));
                document.dispatchEvent(new CustomEvent('additional-points-saved', {detail: {before, after}}));
            } else if (response.headers.has('X-Conduct-Stale')) {
                status.textContent = dialog.dataset.stale;
                await load(player, round, true);
            } else {
                status.textContent = dialog.dataset.saveError;
            }
        } catch {
            status.textContent = dialog.dataset.saveError;
        } finally {
            saving = false;
            saveButton.disabled = false;
        }
    }

    document.addEventListener('click', event => {
        const button = event.target.closest('[data-open-points]');
        if (!button || dialog.open) return;
        dialog.showModal();
        load(button.dataset.playerId, button.dataset.roundNumber);
    });
    form.addEventListener('input', preview);
    form.addEventListener('submit', event => {
        event.preventDefault();
        const body = new URLSearchParams({
            playerId: fields.playerId.value,
            roundNumber: fields.roundNumber.value,
            delta: Number(fields.amount.value) * Number(fields.direction.value),
            note: fields.note.value
        });
        mutate(editing ? `/${editing}` : '', body);
    });
    newButton.addEventListener('click', () => selectEntry(fields.playerId.value, fields.roundNumber.value));
})();
