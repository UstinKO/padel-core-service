(() => {
    const accept = document.getElementById('acceptBtn');
    const decline = document.getElementById('declineBtn');
    const result = document.getElementById('resultMsg');
    const token = document.getElementById('pairProposalToken').dataset.token;

    function show(message, success) {
        result.className = success ? 'success' : 'error';
        result.textContent = result.dataset[message];
        result.style.display = 'block';
    }

    async function answer(action) {
        accept.disabled = true;
        decline.disabled = true;
        let final = false;
        try {
            const response = await fetch(
                '/api/tournaments/double/' + action + '-pair?token=' + encodeURIComponent(token),
                { method: 'POST', headers: { Accept: 'application/json' } }
            );
            const data = await response.json();
            if (response.ok && action === 'accept' && data.success) {
                show(data.status === 'WAITLIST' ? 'waitlist' : 'confirmed', true);
                window.trackTournamentRegistrationComplete?.(null, data.status);
                final = true;
            } else if (response.ok && action === 'decline') {
                const message = { DECLINED: 'declined', ACCEPTED: 'resolved', PAIR_FORMED: 'resolved', EXPIRED: 'expired' }[data.status];
                show(message || 'error', data.status === 'DECLINED');
                final = Boolean(message);
            } else {
                show(response.status === 400 ? 'expired' : 'error', false);
                final = response.status === 400;
            }
        } catch {
            show('connection', false);
        } finally {
            accept.disabled = final;
            decline.disabled = final;
        }
    }

    accept.addEventListener('click', () => void answer('accept'));
    decline.addEventListener('click', () => void answer('decline'));
})();
