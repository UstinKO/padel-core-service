(() => {
    'use strict';
    const root = document.querySelector('[data-telegram-invitation]');
    if (!root) return;
    const input = root.querySelector('input');
    const button = root.querySelector('button');
    const status = root.querySelector('[role=status]');
    fetch('/api/admin/telegram/site-invitation', {credentials:'same-origin',cache:'no-store',redirect:'error',headers:{Accept:'application/json'}})
        .then(response => response.ok ? response.json() : null)
        .then(result => {
            if (!result?.available || typeof result.url !== 'string') return;
            const url = new URL(result.url);
            if (url.protocol !== 'https:' || url.username || url.password || url.pathname !== '/telegram/join' || url.search || url.hash) return;
            input.value = result.url; root.hidden = false;
        }).catch(() => {});
    button.addEventListener('click', async () => {
        button.disabled = true;
        try { await navigator.clipboard.writeText(input.value); status.textContent = root.dataset.copied; }
        catch { input.focus(); input.select(); status.textContent = root.dataset.manual; }
        finally { button.disabled = false; }
    });
})();
