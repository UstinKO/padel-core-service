(() => {
    const dialog = document.getElementById('playoff-courts');
    if (!dialog) return;
    const find = selector => dialog.querySelector(selector);
    const copy = Object.fromEntries([...dialog.querySelectorAll('[data-copy]')]
        .map(node => [node.dataset.copy, node.textContent]));
    const stages = Object.fromEntries([...dialog.querySelectorAll('[data-stage]')]
        .map(node => [node.dataset.stage, node.textContent]));
    const choices = find('[data-court-options]');
    const status = find('[data-court-status]');
    const preview = find('[data-court-preview]');
    const consent = find('[data-court-consent]');
    const save = find('[data-court-save]');
    const reload = find('[data-court-reload]');
    let sourceButton;
    let options;
    let chosen;
    let pending = false;
    let generation = 0;

    function completed(match) {
        return match.isCompleted || match.status === 'COMPLETED';
    }

    function team(match, side) {
        const number = match['team' + side + 'Number'];
        const name = match['team' + side + 'DisplayName']
            || [match['team' + side + 'Player1Name'], match['team' + side + 'Player2Name']].filter(Boolean).join(' / ');
        return (number == null ? '' : '№' + number + ' ') + (name || '—');
    }

    function matchLabel(match) {
        return (stages[match.playoffStage] || copy.qualification) + ' · ' + team(match, 1) + ' — ' + team(match, 2);
    }

    function courtLabel(number) {
        return number == null ? copy.queue : copy.court + ' ' + number;
    }

    function line(parent, value, tag = 'p') {
        const node = document.createElement(tag);
        node.textContent = value;
        parent.append(node);
        return node;
    }

    function currentOccupants(court) {
        return court.occupants.filter(match => match.id !== options.match.id);
    }

    function updatePreview() {
        consent.checked = false;
        preview.replaceChildren();
        save.textContent = copy.save;
        if (!options || chosen === undefined) return;
        const source = options.match;
        const target = options.courts.find(court => court.number === chosen);
        const other = target && !completed(source) ? currentOccupants(target)[0] : undefined;
        line(preview, matchLabel(source));
        line(preview, courtLabel(source.courtNumber) + ' → ' + courtLabel(chosen));
        if (other) {
            line(preview, copy.swap);
            line(preview, matchLabel(other));
            line(preview, courtLabel(other.courtNumber) + ' → ' + courtLabel(source.courtNumber));
            save.textContent = copy.swap;
        }
        if (completed(source)) line(preview, copy.historical);
        save.disabled = pending || chosen === source.courtNumber
            || !choices.querySelector('input:checked:not(:disabled)');
    }

    function choice(number, title, details, disabled = false) {
        const label = document.createElement('label');
        label.className = 'court-choice';
        const input = document.createElement('input');
        input.type = 'radio';
        input.name = 'court-assignment';
        input.value = number == null ? 'queue' : String(number);
        input.disabled = disabled;
        input.checked = chosen === number;
        input.addEventListener('change', () => {
            chosen = number;
            updatePreview();
        });
        label.append(input);
        const content = document.createElement('span');
        line(content, title, 'strong');
        details.forEach(detail => line(content, detail, 'small'));
        label.append(content);
        choices.append(label);
    }

    async function load() {
        const request = ++generation;
        status.textContent = copy.loading;
        save.disabled = true;
        choices.replaceChildren();
        preview.replaceChildren();
        consent.checked = false;
        try {
            const response = await fetch(endpoint());
            if (response.redirected || !response.ok) throw new Error(copy.error);
            const next = await response.json();
            if (!dialog.open || request !== generation) return;
            options = next;
            if (chosen === undefined) chosen = options.match.courtNumber;
            const source = options.match;
            for (const court of options.courts) {
                const occupants = currentOccupants(court);
                const current = court.number === source.courtNumber;
                const state = current ? copy.current : occupants.length ? copy.occupied : copy.free;
                const swappable = !completed(source) && source.status === 'IN_PROGRESS'
                    && occupants.length === 1 && source.courtNumber != null;
                const disabled = !current && occupants.length > 0 && !swappable && !completed(source);
                choice(court.number, courtLabel(court.number) + ' — ' + state,
                    [...occupants.map(matchLabel), ...(disabled ? [copy.occupied_hint] : [])], disabled);
            }
            if (options.canQueue) choice(null, copy.queue, []);
            status.textContent = '';
            updatePreview();
        } catch (error) {
            if (request === generation) status.textContent = copy.error;
        }
    }

    function endpoint() {
        return '/tournaments/team-playoff/api/matches/' + sourceButton.dataset.courtMatch + '/court-assignment';
    }

    document.addEventListener('click', event => {
        const button = event.target.closest('[data-court-match]');
        if (!button) return;
        sourceButton = button;
        options = undefined;
        chosen = undefined;
        dialog.showModal();
        load();
    });
    find('[data-court-close]').addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => {
        generation++;
        sourceButton?.focus();
    });
    reload.addEventListener('click', load);
    save.addEventListener('click', async () => {
        if (pending || !options || chosen === undefined) return;
        if (!consent.checked) {
            status.textContent = copy.confirm;
            consent.focus();
            return;
        }
        const target = options.courts.find(court => court.number === chosen);
        const occupant = target && !completed(options.match) ? currentOccupants(target)[0] : undefined;
        pending = true;
        save.disabled = true;
        reload.disabled = true;
        status.textContent = copy.saving;
        try {
            const response = await conductFetch(endpoint(), {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({version: options.version, courtNumber: chosen,
                    swapMatchId: occupant?.id || null, confirmed: true}),
            });
            if (response.headers.has('X-Conduct-Replayed')) {
                location.reload();
                return;
            }
            const result = await response.json();
            if (response.ok && result.success) {
                location.reload();
                return;
            }
            await load();
            status.textContent = result.message || copy.error;
        } catch {
            status.textContent = copy.error;
        } finally {
            pending = false;
            reload.disabled = false;
            save.disabled = chosen === options?.match.courtNumber
                || !choices.querySelector('input:checked:not(:disabled)');
        }
    });
})();
