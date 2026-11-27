(() => {
    'use strict';
    const root = document.querySelector('[data-telegram-invitation]');
    if (!root) return;
    const input = root.querySelector('input');
    const button = root.querySelector('button');
    const status = root.querySelector('[role=status]');
    fetch('/api/admin/telegram/invitation', {credentials:'same-origin',cache:'no-store',redirect:'error',headers:{Accept:'application/json'}})
        .then(response => response.ok ? response.json() : null)
        .then(result => {
            if (!result?.available || typeof result.url !== 'string' || !/^https:\/\/t\.me\/[A-Za-z0-9_]{5,32}\?start=connect$/.test(result.url)) return;
            input.value = result.url; root.hidden = false;
        }).catch(() => {});
    button.addEventListener('click', async () => {
        button.disabled = true;
        try { await navigator.clipboard.writeText(input.value); status.textContent = root.dataset.copied; }
        catch { input.focus(); input.select(); status.textContent = root.dataset.manual; }
        finally { button.disabled = false; }
    });
})();
