(() => {
    const opener = document.querySelector('[data-results-export-open]');
    const dialog = document.getElementById('results-export-dialog');
    if (!opener || !dialog) return;
    const download = dialog.querySelector('[data-results-export-download]');
    const contacts = dialog.querySelector('[data-results-export-contacts]');
    const acknowledged = dialog.querySelector('[data-results-export-ack]');
    const privacy = dialog.querySelector('[data-results-export-privacy]');
    const message = dialog.querySelector('[data-results-export-message]');
    const summary = dialog.querySelector('[data-results-export-summary]');
    const warnings = dialog.querySelector('[data-results-export-warnings]');
    const notice = document.querySelector('[data-results-export-notice]');
    const base = opener.dataset.exportUrl;
    let ready = false;
    let busy = false;
    let request;
    const update = () => { download.disabled = !ready || busy || (contacts.checked && !acknowledged.checked); };
    const showMessage = (text, error = false) => {
        message.textContent = text;
        message.toggleAttribute('data-error', error);
    };
    const failure = response => {
        if (response.status === 403 || response.url.endsWith('/error/403')) return dialog.dataset.denied;
        if (response.redirected || response.status === 401) return dialog.dataset.session;
        return dialog.dataset.error;
    };
    const warningText = {
        PROVISIONAL: 'provisional', NO_MATCHES: 'noMatches', NO_STANDINGS: 'noStandings',
        MISSING_PLACEMENTS: 'missingPlacements', LEGACY_GUEST_WITHOUT_PROFILE: 'legacyGuest',
        MISSING_LINEUP: 'missingLineup', INVALID_PHONE_EXCLUDED: 'invalidPhone'
    };
    const preview = async () => {
        ready = false;
        update();
        summary.textContent = dialog.dataset.loading;
        warnings.replaceChildren();
        warnings.hidden = true;
        request = new AbortController();
        try {
            const response = await fetch(base + '/preview', { signal: request.signal, cache: 'no-store', headers: { Accept: 'application/json' } });
            if (!response.ok || response.redirected || !response.headers.get('content-type')?.includes('application/json')) throw new Error(failure(response), { cause: 'response' });
            const data = await response.json();
            if (!dialog.open) return;
            summary.textContent = dialog.dataset.summary.replace('{participants}', String(data.participants)).replace('{matches}', String(data.matches))
                + ' ' + (data.preliminary ? dialog.dataset.provisional : dialog.dataset.ready);
            for (const code of data.warnings) {
                if (!warningText[code] || code === 'PROVISIONAL') continue;
                const item = document.createElement('li');
                item.textContent = dialog.dataset[warningText[code]];
                warnings.append(item);
            }
            warnings.hidden = !warnings.childElementCount;
            ready = true;
        } catch (error) {
            if (error.name !== 'AbortError') summary.textContent = error.cause === 'response' ? error.message : dialog.dataset.error;
        } finally { update(); }
    };
    opener.addEventListener('click', () => {
        contacts.checked = false;
        acknowledged.checked = false;
        privacy.hidden = true;
        showMessage('');
        notice.textContent = '';
        dialog.showModal();
        preview();
    });
    contacts.addEventListener('change', () => {
        privacy.hidden = !contacts.checked;
        acknowledged.checked = false;
        update();
    });
    acknowledged.addEventListener('change', update);
    for (const close of dialog.querySelectorAll('[data-results-export-close]')) close.addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => { request?.abort(); opener.focus(); });
    download.addEventListener('click', async () => {
        if (download.disabled) return;
        busy = true;
        update();
        showMessage(dialog.dataset.loading);
        request = new AbortController();
        const timeout = setTimeout(() => request.abort(), 45000);
        try {
            const format = dialog.querySelector('[name="resultsExportFormat"]:checked').value;
            const response = await fetch(base, {
                method: 'POST', signal: request.signal,
                body: new URLSearchParams({ format, contacts: String(contacts.checked), acknowledged: String(acknowledged.checked) })
            });
            const mime = format === 'XLSX' ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' : 'application/zip';
            if (!response.ok || response.redirected || !response.headers.get('content-type')?.includes(mime)) throw new Error(failure(response), { cause: 'response' });
            const blob = await response.blob();
            if (!dialog.open) return;
            const name = response.headers.get('content-disposition')?.match(/filename="(1padel-results-\d+-(?:final|provisional)(?:-contacts)?\.(?:xlsx|zip))"/);
            const link = document.createElement('a');
            const url = URL.createObjectURL(blob);
            link.href = url;
            link.download = name?.[1] || ('1padel-results.' + (format === 'XLSX' ? 'xlsx' : 'zip'));
            document.body.append(link);
            link.click();
            link.remove();
            setTimeout(() => URL.revokeObjectURL(url), 60000);
            const text = response.headers.get('X-Results-State') === 'PROVISIONAL' ? dialog.dataset.doneProvisional : dialog.dataset.done;
            showMessage(text);
            notice.textContent = text;
        } catch (error) {
            if (dialog.open) showMessage(error.cause === 'response' ? error.message : dialog.dataset.error, true);
        } finally {
            clearTimeout(timeout);
            busy = false;
            update();
        }
    });
})();
