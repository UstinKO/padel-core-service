(() => {
    const panel = document.querySelector('#participationRequests');
    if (!panel) return;

    const requests = panel.querySelector('[data-requests]');
    const template = panel.querySelector('[data-request-template]');
    const feedback = panel.querySelector('[data-feedback]');
    const retry = panel.querySelector('[data-retry]');
    const login = panel.querySelector('[data-login-link]');
    const declineDialog = panel.querySelector('[data-decline-dialog]');

    async function read(url, options) {
        const response = await fetch(url, {
            ...options,
            headers: { Accept: 'application/json', ...options?.headers },
            redirect: 'manual'
        });
        if (response.status === 401 || response.type === 'opaqueredirect') {
            throw new Error('session');
        }
        if (!response.ok) throw new Error('request');
        return response.json();
    }

    function showFeedback(key) {
        feedback.textContent = key ? panel.dataset[key] : '';
        login.hidden = key !== 'session';
        retry.hidden = key !== 'error' && key !== 'uncertain';
        if (key) panel.hidden = false;
    }

    function render(card, review) {
        card.querySelector('[data-event]').textContent = review.tournamentName;
        card.querySelector('[data-player]').textContent = review.playerName;
        card.querySelector('[data-delegated]').hidden = !review.delegated;
        card.querySelector('[data-status]').textContent = panel.dataset[review.status.toLowerCase()];
        const confirm = card.querySelector('[data-confirm]');
        const decline = card.querySelector('[data-decline]');
        confirm.hidden = !review.canRespond;
        decline.hidden = !review.canDecline;
        confirm.disabled = false;
        decline.disabled = false;
    }

    function applyAnswer(card, review) {
        render(card, review);
        if (!review.delegated && review.status === 'DECLINED') {
            document.dispatchEvent(new CustomEvent('participation:declined', { detail: review.tournamentId }));
        }
    }

    async function answer(card, url, decision) {
        showFeedback();
        const buttons = card.querySelectorAll('button');
        if (Array.from(buttons).some(button => button.disabled)) return;
        buttons.forEach(button => { button.disabled = true; });
        try {
            const review = await read(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ decision })
            });
            applyAnswer(card, review);
        } catch (error) {
            if (error.message === 'session') {
                showFeedback('session');
                return;
            }
            try {
                applyAnswer(card, await read(url));
                showFeedback('uncertain');
            } catch (readError) {
                showFeedback(readError.message === 'session' ? 'session' : 'uncertain');
            }
        }
    }

    function addCard(review) {
        const card = template.content.firstElementChild.cloneNode(true);
        const url = panel.dataset.source + '/' + encodeURIComponent(review.requestId);
        card.querySelector('[data-confirm]').addEventListener('click', () => answer(card, url, 'CONFIRM'));
        card.querySelector('[data-decline]').addEventListener('click', () => {
            if (declineDialog.open) return;
            declineDialog.returnValue = '';
            declineDialog.addEventListener('close', () => {
                if (declineDialog.returnValue === 'decline') answer(card, url, 'DECLINE');
            }, { once: true });
            declineDialog.showModal();
        });
        render(card, review);
        requests.append(card);
    }

    async function load() {
        showFeedback();
        retry.disabled = true;
        try {
            const summaries = await read(panel.dataset.source);
            const reviews = await Promise.all(summaries.map(({ requestId }) =>
                read(panel.dataset.source + '/' + encodeURIComponent(requestId))));
            requests.replaceChildren();
            reviews.forEach(addCard);
            panel.hidden = reviews.length === 0;
        } catch (error) {
            showFeedback(error.message === 'session' ? 'session' : 'error');
        } finally {
            retry.disabled = false;
        }
    }

    login.href = panel.dataset.login;
    retry.addEventListener('click', load);
    load();
})();
