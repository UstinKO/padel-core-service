(() => {
    const section = document.querySelector('[data-elo-feedback]');
    if (!section) return;

    const dialog = section.querySelector('dialog');
    const form = dialog.querySelector('form');
    const fields = form.querySelector('fieldset');
    const status = form.querySelector('[data-feedback-status]');
    const close = form.querySelector('[data-feedback-close]');
    const send = form.querySelector('[type="submit"]');
    const requestKey = form.elements.namedItem('requestKey');

    section.querySelector('[data-feedback-open]').addEventListener('click', () => {
        if (fields.hidden) {
            form.reset();
            fields.hidden = false;
            status.textContent = '';
        }
        if (!requestKey.value) requestKey.value = crypto.randomUUID();
        dialog.showModal();
    });
    close.addEventListener('click', () => dialog.close());

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (send.disabled) return;
        send.disabled = true;
        status.textContent = '';
        try {
            const response = await fetch(form.action, {
                method: 'POST',
                body: new URLSearchParams(new FormData(form)),
                headers: { Accept: 'application/json' }
            });
            if (response.status !== 204) throw new Error('Feedback was not saved');
            fields.hidden = true;
            status.textContent = dialog.dataset.savedMessage;
            if (dialog.open) close.focus();
        } catch {
            status.textContent = dialog.dataset.errorMessage;
        } finally {
            send.disabled = false;
        }
    });
})();
