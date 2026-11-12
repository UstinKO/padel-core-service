(() => {
    'use strict';

    async function request(endpoint, body, fallback) {
        const options = { headers: { Accept: 'application/json' } };
        if (body) {
            options.method = 'POST';
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(body);
        }
        const response = await fetch(endpoint, options);
        const result = await response.json();
        if (!response.ok) throw new Error(result.detail || fallback);
        return result;
    }

    function bind(form, action) {
        form.addEventListener('submit', async event => {
            event.preventDefault();
            const submit = form.querySelector('button[type="submit"]');
            if (submit.disabled) return;
            const status = form.querySelector('[data-claim-status]');
            status.textContent = '';
            submit.disabled = true;
            try {
                const fields = Object.fromEntries(new FormData(form).entries());
                const complete = await action(fields, status);
                if (!complete) submit.disabled = false;
            } catch (error) {
                status.textContent = error.message && error.name === 'Error' ? error.message : form.dataset.error;
                submit.disabled = false;
            }
        });
    }

    const player = document.querySelector('[data-claim-request]');
    if (player) {
        const link = player.querySelector('[data-claim-whatsapp]');
        player.addEventListener('input', () => {
            link.hidden = true;
            player.querySelector('[data-claim-status]').textContent = '';
        });
        bind(player, async (fields, status) => {
            link.hidden = true;
            const result = await request(player.dataset.endpoint, fields, player.dataset.error);
            if (fields.phone !== player.elements.phone.value) return;
            const whatsapp = new URL(result.whatsappUrl);
            whatsapp.searchParams.set('text', player.dataset.whatsappText.replace('{reference}', result.reference));
            link.href = whatsapp.href;
            link.hidden = false;
            status.textContent = result.reference;
            link.focus();
        });
    }

    const review = document.querySelector('[data-claim-review]');
    if (review) {
        const container = review.closest('[data-guest-claim]');
        const approve = container.querySelector('[data-claim-approve]');
        const approved = container.querySelector('[data-claim-approved]');
        let reviewedEndpoint;
        const clearReview = () => {
            approve.hidden = true;
            approved.hidden = true;
            reviewedEndpoint = undefined;
        };
        review.addEventListener('input', clearReview);
        bind(review, async ({reference}) => {
            clearReview();
            const endpoint = review.dataset.endpoint + '/' + encodeURIComponent(reference.trim());
            const result = await request(endpoint, undefined, review.dataset.error);
            if (reference !== review.elements.reference.value) return;
            if (result.approved) {
                approved.hidden = false;
                return;
            }
            approve.reset();
            approve.querySelector('button[type="submit"]').disabled = false;
            approve.querySelector('[data-claim-status]').textContent = '';
            approve.querySelector('[data-claim-account]').textContent = result.accountEmail;
            reviewedEndpoint = endpoint;
            approve.hidden = false;
            approve.elements.whatsappSenderPhone.focus();
        });
        bind(approve, async (fields, status) => {
            if (!reviewedEndpoint) return;
            await request(reviewedEndpoint + '/approve', fields, approve.dataset.error);
            status.textContent = approve.dataset.success;
            return true;
        });
    }

    const activate = document.querySelector('[data-claim-activate]');
    if (activate) {
        bind(activate, async (fields, status) => {
            await request(activate.dataset.endpoint, fields, activate.dataset.error);
            status.textContent = activate.dataset.success;
            return true;
        });
    }
})();
