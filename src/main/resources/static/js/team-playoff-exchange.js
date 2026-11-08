(() => {
    const dialog = document.querySelector('#playoff-exchange');
    if (!dialog) return;
    const find = selector => dialog.querySelector(selector);
    const copy = Object.fromEntries([...dialog.querySelectorAll('[data-copy]')]
        .map(el => [el.dataset.copy, el.textContent]));
    const stages = Object.fromEntries([...dialog.querySelectorAll('[data-stage]')]
        .map(el => [el.dataset.stage, el.textContent]));
    const selection = find('[data-exchange-select]');
    const select = selection.querySelector('select');
    const status = find('[data-exchange-status]');
    const blocked = find('[data-exchange-blocked]');
    const dependencies = find('[data-exchange-dependencies]');
    const preview = find('[data-exchange-preview]');
    const rematch = find('[data-exchange-rematch]');
    const legacy = find('[data-exchange-legacy]');
    const confirmRematch = find('[data-confirm-rematch]');
    const confirmNotStarted = find('[data-confirm-not-started]');
    const correction = find('[data-exchange-correction]');
    const previous = find('[data-exchange-previous]');
    const correctFields = [...correction.querySelectorAll('[data-correct-score]')];
    const confirmCorrection = find('[data-confirm-correction]');
    const save = find('[data-exchange-save]');
    const close = find('[data-exchange-close]');
    const prefix = '/tournaments/team-playoff/api/matches/';
    let operation = null;
    let pending = false;

    function teamName(team) {
        return team ? [team.number == null ? '' : '№' + team.number, team.name].filter(Boolean).join(' ') : '—';
    }

    function pairLabel(pair) {
        const court = pair.court == null ? '' : ' · ' + copy.court + ' ' + pair.court;
        const score = pair.firstGames == null || pair.secondGames == null
            ? '' : ' · ' + pair.firstGames + ':' + pair.secondGames;
        return copy.match + ' ' + pair.matchNumber + court + ': '
            + teamName(pair.first) + ' — ' + teamName(pair.second) + score;
    }

    function list(target, values) {
        target.replaceChildren(...values.map(value => {
            const item = document.createElement('li');
            item.textContent = value;
            return item;
        }));
        target.hidden = values.length === 0;
    }

    function reason(key) {
        return copy[key?.split('.').at(-1)] || copy.error;
    }

    function choice() {
        return operation?.candidates[select.value];
    }

    function updateSave() {
        const chosen = choice();
        save.disabled = pending || !chosen || (!legacy.hidden && !confirmNotStarted.checked)
            || (!rematch.hidden && !confirmRematch.checked)
            || (!correction.hidden && (!confirmCorrection.checked
                || correctFields.some(field => !field.hidden && !validScore(field))));
    }

    function validScore(field) {
        const [first, second] = field.querySelectorAll('input');
        return first.value !== '' && second.value !== '' && first.validity.valid && second.validity.valid
            && first.valueAsNumber !== second.valueAsNumber;
    }

    function readScore(field) {
        if (field.hidden) return null;
        const [first, second] = field.querySelectorAll('input');
        return {firstGames: first.valueAsNumber, secondGames: second.valueAsNumber};
    }

    function showCorrection(chosen) {
        const visible = Boolean(chosen && operation.correction);
        previous.hidden = !visible;
        correction.hidden = !visible;
        confirmCorrection.checked = false;
        if (!visible) return;
        list(previous.querySelector('ul'), [pairLabel(operation.current), pairLabel(chosen.current)]);
        const required = [chosen.firstScoreRequired, chosen.secondScoreRequired];
        const pairs = [chosen.firstAfter, chosen.secondAfter];
        correctFields.forEach((field, index) => {
            field.hidden = !required[index];
            field.querySelector('legend').textContent = pairLabel(pairs[index]);
            field.querySelectorAll('input').forEach(input => { input.value = ''; });
        });
    }

    function showChoice() {
        const chosen = choice();
        preview.hidden = !chosen;
        confirmNotStarted.checked = false;
        confirmRematch.checked = false;
        rematch.hidden = !chosen?.encounters.length;
        legacy.hidden = !chosen?.confirmNotStarted;
        showCorrection(chosen);
        if (chosen) {
            list(find('[data-exchange-pairs]'), [pairLabel(chosen.firstAfter), pairLabel(chosen.secondAfter)]);
            list(find('[data-exchange-encounters]'), chosen.encounters.map(encounter =>
                (stages[encounter.stage] || copy.qualification) + ' · ' + pairLabel(encounter.pair)));
        }
        updateSave();
    }

    async function open(button) {
        const opened = {match: button.dataset.swapMatch, slot: Number(button.dataset.swapSlot),
            correction: button.dataset.correction === 'true', candidates: []};
        operation = opened;
        find('#playoff-exchange-title').textContent = opened.correction ? copy.correct : copy.title;
        status.textContent = copy.loading;
        selection.hidden = true;
        blocked.hidden = true;
        dependencies.hidden = true;
        select.replaceChildren();
        showChoice();
        dialog.showModal();
        try {
            const response = await fetch(prefix + opened.match + '/pair-options?slot=' + opened.slot
                + '&correction=' + opened.correction);
            const data = await response.json();
            if (opened !== operation || !dialog.open) return;
            if (!response.ok) {
                status.textContent = data.message || copy.error;
                return;
            }
            opened.version = data.version;
            opened.current = data.options.current;
            opened.candidates = data.options.candidates.filter(candidate => !candidate.block);
            const unavailable = new Map(data.options.candidates.filter(candidate => candidate.block)
                .map(candidate => [candidate.matchId, candidate]));
            list(blocked, [...unavailable.values()].map(candidate =>
                pairLabel(candidate.current) + ' — ' + reason(candidate.block)));
            dependencies.hidden = !data.options.dependentMatches.length;
            list(dependencies.querySelector('ul'), data.options.dependentMatches.map(pairLabel));
            status.textContent = data.options.block
                ? pairLabel(data.options.current) + ' — ' + reason(data.options.block)
                : opened.candidates.length ? '' : copy.none;
            selection.hidden = opened.candidates.length === 0;
            select.replaceChildren(new Option('—', ''), ...opened.candidates.map((candidate, index) =>
                new Option(teamName(candidate.team) + ' · ' + copy.match + ' ' + candidate.current.matchNumber, String(index))));
        } catch {
            if (opened === operation) status.textContent = copy.error;
        }
    }

    async function mutate(url, body) {
        const options = {method: 'POST'};
        if (body) {
            options.headers = {'Content-Type': 'application/json'};
            options.body = JSON.stringify(body);
        }
        const response = await window.conductFetch(url, options);
        if (response.headers.has('X-Conduct-Replayed')) return '';
        const data = await response.json();
        return response.ok && data.success ? '' : data.message || copy.error;
    }

    save.addEventListener('click', async () => {
        const chosen = choice();
        if (!chosen || save.disabled) return;
        pending = true;
        close.disabled = true;
        select.disabled = true;
        updateSave();
        try {
            const exchange = {slot: operation.slot, otherMatchId: chosen.matchId, otherSlot: chosen.slot,
                confirmNotStarted: confirmNotStarted.checked, confirmRematch: confirmRematch.checked};
            const route = operation.correction ? '/correct-pair' : '/swap-team';
            const body = operation.correction ? {exchange, confirmCorrection: confirmCorrection.checked,
                firstScore: readScore(correctFields[0]), secondScore: readScore(correctFields[1])} : exchange;
            const error = await mutate(prefix + operation.match + route + '?expectedConductVersion=' + operation.version, body);
            if (!error) {
                location.reload();
                return;
            }
            status.textContent = error;
        } catch {
            status.textContent = copy.error;
        }
        pending = false;
        close.disabled = false;
        select.disabled = false;
        updateSave();
    });

    async function start(button) {
        if (!window.confirm(copy.confirm_start)) return;
        button.disabled = true;
        const errorNode = document.querySelector('[data-start-error]');
        errorNode.hidden = true;
        try {
            const error = await mutate(prefix + button.dataset.startMatch + '/play-start?expectedConductVersion='
                + dialog.dataset.version);
            if (!error) {
                location.reload();
                return;
            }
            errorNode.textContent = error;
        } catch {
            errorNode.textContent = copy.error;
        }
        errorNode.hidden = false;
        errorNode.scrollIntoView({block: 'nearest'});
        button.disabled = false;
    }

    select.addEventListener('change', showChoice);
    confirmRematch.addEventListener('change', updateSave);
    confirmNotStarted.addEventListener('change', updateSave);
    confirmCorrection.addEventListener('change', updateSave);
    correction.addEventListener('input', updateSave);
    close.addEventListener('click', () => dialog.close());
    dialog.addEventListener('cancel', event => { if (pending) event.preventDefault(); });
    document.addEventListener('click', event => {
        const swap = event.target.closest('[data-swap-match]');
        const startMatch = event.target.closest('[data-start-match]');
        if (swap && !dialog.open) open(swap);
        if (startMatch) start(startMatch);
    });
})();
