(() => {
    'use strict';

    const dialog = document.getElementById('tournamentPublicationDialog');
    if (!dialog) return;

    const urlField = dialog.querySelector('#publicationLinkUrl');
    const copyButton = dialog.querySelector('[data-publication-copy]');
    const copyLabel = copyButton.querySelector('span');
    const defaultCopyLabel = copyLabel.textContent;
    const feedback = dialog.querySelector('[role="status"]');
    const channelButtons = [...dialog.querySelectorAll('[data-publication-channel]')];
    const channels = {
        telegram: {source: 'telegram', campaign: 'telegram_posts'},
        stories: {source: 'instagram', campaign: 'instagram_stories'}
    };
    let eventId;
    let opener;
    let copyRevision = 0;

    function resizeUrl() {
        if (!dialog.open) return;
        urlField.style.height = 'auto';
        urlField.style.height = `${urlField.scrollHeight + 2}px`;
    }

    function selectChannel(channel) {
        const tags = channels[channel];
        const base = new URL(dialog.dataset.siteBaseUrl);
        base.pathname = `${base.pathname.replace(/\/+$/, '')}/torneo/${eventId}`;
        base.search = '';
        base.hash = '';
        base.searchParams.set('utm_source', tags.source);
        base.searchParams.set('utm_medium', 'social');
        base.searchParams.set('utm_campaign', tags.campaign);
        base.searchParams.set('utm_content', `torneo_${eventId}`);
        urlField.value = base.href;
        channelButtons.forEach(button => {
            button.setAttribute('aria-pressed', String(button.dataset.publicationChannel === channel));
        });
        copyRevision++;
        copyButton.disabled = false;
        copyLabel.textContent = defaultCopyLabel;
        feedback.textContent = '';
        resizeUrl();
    }

    document.querySelectorAll('[data-publication-id]').forEach(button => {
        button.addEventListener('click', () => {
            const data = button.dataset;
            if (!/^[1-9]\d*$/.test(data.publicationId)) return;
            eventId = data.publicationId;
            opener = button;
            dialog.querySelector('#publicationLinkEvent').textContent =
                [data.publicationName, data.publicationLevel].filter(Boolean).join(' · ');
            dialog.querySelector('#publicationLinkDetails').textContent =
                [data.publicationDate, data.publicationTime, data.publicationClub].filter(Boolean).join(' · ');
            selectChannel('telegram');
            dialog.showModal();
            document.body.classList.add('publication-link-open');
            resizeUrl();
        });
    });

    channelButtons.forEach(button => {
        button.addEventListener('click', () => selectChannel(button.dataset.publicationChannel));
    });
    dialog.querySelector('[data-publication-close]').addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => {
        copyRevision++;
        document.body.classList.remove('publication-link-open');
        opener?.focus({preventScroll: true});
    });

    copyButton.addEventListener('click', async () => {
        const revision = ++copyRevision;
        const link = urlField.value;
        copyButton.disabled = true;
        try {
            await navigator.clipboard.writeText(link);
            if (revision !== copyRevision || !dialog.open) return;
            copyLabel.textContent = dialog.dataset.copied;
            feedback.textContent = dialog.dataset.ready;
        } catch {
            if (revision !== copyRevision || !dialog.open) return;
            urlField.focus();
            urlField.select();
            urlField.setSelectionRange(0, link.length);
            feedback.textContent = dialog.dataset.manual;
        } finally {
            if (revision === copyRevision) copyButton.disabled = false;
        }
    });
    window.addEventListener('resize', resizeUrl);
})();
