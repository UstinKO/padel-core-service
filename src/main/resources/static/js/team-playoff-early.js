(() => {
    const panel = document.getElementById('earlyPlayoff');
    if (!panel) return;

    const text = panel.dataset;
    text.title = document.getElementById('earlyPlayoffTitle').textContent;
    const first = document.getElementById('earlyFirst');
    const second = document.getElementById('earlySecond');
    const manualResult = document.getElementById('earlyManualResult');
    let open = false;
    let hasEarlyBracket = document.getElementById('playoffSection')?.dataset.early === 'true';
    let requestNumber = 0;
    let bracketRequest = 0;

    function teamLabel(team) {
        return (team.number == null ? '' : '№' + team.number + ' ') + team.name;
    }

    function renderTeams(select, teams) {
        const value = select.value;
        select.replaceChildren(new Option(text.choose, ''), ...teams.map(team => new Option(teamLabel(team), team.id)));
        if (teams.some(team => String(team.id) === value)) select.value = value;
    }

    function bodyText(parent, value) {
        const element = document.createElement('p');
        element.textContent = value;
        parent.appendChild(element);
    }

    function matchCard(match, courts, selectedCourt, override = false, semifinal = false) {
        const card = document.createElement('div');
        card.className = 'early-suggestion';
        card.dataset.match = [match.first.id, match.second.id].sort().join(':');
        bodyText(card, (semifinal ? text.semifinalStage : text.stage) + ' · ' + text.ready);
        const names = document.createElement('p');
        for (const [index, team] of [match.first, match.second].entries()) {
            if (index) names.append(' — ');
            if (team.number != null) {
                const number = document.createElement('strong');
                number.textContent = '№' + team.number;
                names.append(number, ' ');
            }
            names.append(team.name);
        }
        card.append(names);
        if (!semifinal) bodyText(card, override ? text.override : text.safe);

        const form = document.createElement('form');
        const court = document.createElement('select');
        court.className = 'form-control';
        court.setAttribute('aria-label', text.court);
        court.append(...courts.map(value => new Option(text.court + ' ' + value, value)), new Option(text.queued, ''));
        if (selectedCourt === '' || courts.some(value => String(value) === selectedCourt)) court.value = selectedCourt;
        const button = document.createElement('button');
        button.type = 'submit';
        button.className = 'btn btn-primary btn-sm';
        button.textContent = text.confirm;
        form.append(court, button);
        form.addEventListener('submit', async event => {
            event.preventDefault();
            button.disabled = true;
            try {
                const response = await conductFetch(text.url, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({
                        firstId: match.first.id,
                        secondId: match.second.id,
                        court: court.value ? Number(court.value) : null,
                        approvedOverride: override
                    })
                });
                const result = await response.json();
                if (!result.success) {
                    showToast(result.message || text.error);
                    return;
                }
                hasEarlyBracket = true;
                refreshCourtBoard();
                refreshBracket();
            } catch {
                showToast(text.error);
            } finally {
                button.disabled = false;
            }
        });
        card.appendChild(form);
        return card;
    }

    async function refresh() {
        const current = ++requestNumber;
        const selection = new URLSearchParams();
        if (first.value && second.value) {
            selection.set('firstId', first.value);
            selection.set('secondId', second.value);
        }
        try {
            const response = await fetch(text.url + (selection.size ? '?' + selection : ''), {cache: 'no-store'});
            if (!response.ok) throw new Error();
            const state = await response.json();
            if (current !== requestNumber) return;
            panel.hidden = !state.available;
            if (!state.available) {
                if (open && hasEarlyBracket) refreshBracket();
                open = false;
                return;
            }

            open = true;
            const cards = document.getElementById('earlySuggestions');
            const selectedCourts = new Map([...cards.querySelectorAll('[data-match]')]
                .map(card => [card.dataset.match, card.querySelector('select').value]));
            document.getElementById('earlyPlayoffTitle').textContent = state.semifinal ? text.semifinal : text.title;
            document.getElementById('earlySummary').textContent = (state.semifinal ? text.semifinalSummary : text.summary)
                .replace('{0}', state.finishedTeams).replace('{1}', state.remainingMatches)
                .replace('{2}', state.matches.length).replace('{3}', state.freeCourts.length);
            const projected = document.getElementById('earlyProjected');
            const byes = state.projectedByes || [];
            projected.hidden = byes.length === 0;
            const byeHelp = document.getElementById('earlyByeHelp');
            if (byeHelp) byeHelp.hidden = byes.length === 0;
            projected.textContent = text.projected.replace('{0}', byes.map(teamLabel).join(', '));
            const pending = (state.pendingMatchNumbers || []).map(number => '№' + number).join(', ');
            const waiting = state.reason === 'results' && pending
                ? text.pending.replace('{0}', pending) : text[state.reason];
            document.getElementById('earlyReason').textContent = state.matches.length ? '' : waiting;
            renderTeams(first, state.readyTeams);
            renderTeams(second, state.readyTeams);
            cards.replaceChildren(...state.matches.map(match => matchCard(match, state.freeCourts,
                selectedCourts.get([match.first.id, match.second.id].sort().join(':')), false, state.semifinal)));
            const checkedCurrentPair = selection.get('firstId') === first.value && selection.get('secondId') === second.value;
            const status = checkedCurrentPair ? state.manualStatus : null;
            const manualFirst = state.readyTeams.find(team => String(team.id) === first.value);
            const manualSecond = state.readyTeams.find(team => String(team.id) === second.value);
            if (status === 'override' && manualFirst && manualSecond) {
                const previousCourt = manualResult.querySelector('select')?.value;
                manualResult.replaceChildren(matchCard({first: manualFirst, second: manualSecond},
                    state.freeCourts, previousCourt, true));
            } else {
                manualResult.textContent = status ? (status === 'results' ? waiting : status === 'safe' && state.semifinal ? text.ready : text[status]) : '';
            }
        } catch {
            if (current === requestNumber && open) showToast(text.error);
        }
    }

    async function refreshBracket(savedMatch) {
        const current = ++bracketRequest;
        const bracket = document.getElementById('playoffSection');
        if (!bracket) return;
        try {
            const response = await fetch(location.pathname + location.search, {cache: 'no-store'});
            if (!response.ok) throw new Error();
            const page = new DOMParser().parseFromString(await response.text(), 'text/html');
            const next = page.getElementById('playoffSection');
            if (!next || current !== bracketRequest) return;
            const inputValues = [...bracket.querySelectorAll('input.score-inp[id]')]
                .filter(input => !savedMatch || !input.id.endsWith('-' + savedMatch))
                .map(input => [input.id, input.value]);
            bracket.replaceChildren(...next.childNodes);
            for (const [id, value] of inputValues) {
                const input = document.getElementById(id);
                if (input) input.value = value;
            }
        } catch {
            if (current === bracketRequest) showToast(text.error);
        }
    }

    document.getElementById('earlyCheck').addEventListener('click', () => {
        if (!first.value || !second.value || first.value === second.value) {
            manualResult.textContent = text.teams;
            return;
        }
        refresh();
    });
    first.addEventListener('change', () => { manualResult.textContent = ''; });
    second.addEventListener('change', () => { manualResult.textContent = ''; });
    window.teamPlayoffEarly = {refresh, refreshBracket, hasBracket: () => hasEarlyBracket};
})();
