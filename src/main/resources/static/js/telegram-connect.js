(() => {
    'use strict';
    const root = document.querySelector('[data-telegram-connect]');
    if (!root) return;
    const el = key => root.querySelector(`[data-${key}]`);
    const strings = Object.fromEntries([...root.querySelectorAll('[data-connect-strings] [data-key]')].map(node => [node.dataset.key,node.textContent]));
    const t = key => strings[key] || key;
    let busy = false, state = root.dataset.state, botUrl = root.dataset.botUrl, generation = 0;
    const endpoint = '/api/players/me/telegram/onboarding?request=' + encodeURIComponent(root.dataset.requestId || '');
    const contextPath = '/telegram/connect?request=' + encodeURIComponent(root.dataset.requestId || '');
    el('login').href = '/login?destination=' + encodeURIComponent(contextPath);
    const bot = value => {
        try {
            const url = new URL(value);
            return url.protocol === 'https:' && url.hostname === 't.me' && /^[A-Za-z0-9_]{5,32}$/.test(url.pathname.slice(1))
                && (!url.search || url.search === '?start=connect') && !url.hash && !url.username && !url.password && !url.port ? url.href : '';
        } catch { return ''; }
    };
    const status = (key, error = false) => {
        el('status').textContent = key ? t(key) : '';
        el('status').dataset.error = String(error);
        if (error) el('status').focus();
    };
    const render = () => {
        root.dataset.state = state;
        root.setAttribute('aria-busy',String(busy));
        root.querySelectorAll('.tg-connect-languages a').forEach(link => link.setAttribute('aria-disabled',String(busy)));
        el('state-message').textContent = t(state === 'INVALID' ? 'invalid' : state);
        el('account').hidden = !['READY','PENDING','CONNECTED'].includes(state);
        el('consent').hidden = state !== 'READY';
        el('confirm').hidden = state !== 'READY';
        el('confirm').disabled = busy;
        el('refresh').hidden = !['READY','PENDING'].includes(state);
        el('refresh').disabled = busy;
        const target = bot(botUrl);
        el('open-bot').hidden = !target || !['PENDING','INVALID'].includes(state);
        if (target) el('open-bot').href = target;
    };
    const apply = result => {
        if (!['READY','PENDING','CONNECTED'].includes(result.state) || typeof result.playerName !== 'string') throw new Error('invalid');
        state = result.state; botUrl = bot(result.botUrl);
        el('player-name').textContent = result.playerName;
    };
    const request = async (path, init = {}) => {
        const response = await fetch(path, { credentials:'same-origin', cache:'no-store', redirect:'error',
            ...init, headers:{ Accept:'application/json', 'Content-Type':'application/json' } });
        if ([401,403].includes(response.status)) throw new Error('session');
        if (!response.ok) throw new Error(response.status === 409 ? 'invalid' : 'error');
        return response.json();
    };
    const perform = async confirm => {
        if (busy) return;
        const current = generation;
        busy = true; render(); status('loading');
        try {
            const result = await request(endpoint, confirm ? {method:'POST',body:'{}'} : {});
            if (current !== generation) return;
            apply(result);
            status('');
        } catch (error) {
            if (current !== generation) return;
            if (error.message === 'session') {
                el('login').hidden = false; state = 'INVALID'; status('session',true);
            } else if (error.message === 'invalid') {
                state = 'INVALID'; status('invalid',true);
            } else {
                state = 'PENDING'; status(confirm ? 'uncertain' : 'error',true);
            }
        } finally { if (current === generation) { busy = false; render(); } }
    };
    el('confirm').addEventListener('click', () => { if (state === 'READY') perform(true); });
    el('refresh').addEventListener('click', () => perform(false));
    root.querySelectorAll('.tg-connect-languages a').forEach(link => link.addEventListener('click',event => {
        if (busy) event.preventDefault();
    }));
    render();
    const openFragment = () => {
        if (!window.location.hash) return;
        const token = new URLSearchParams(window.location.hash.slice(1)).get('token');
        history.replaceState(null,'',window.location.pathname + window.location.search);
        const current = ++generation;
        state = 'ENTRY'; busy = true; render(); status('loading');
        if (!/^[A-Za-z0-9_-]{32}$/.test(token || '')) { state = 'INVALID'; busy = false; render(); status('invalid',true); return; }
        request('/telegram/connect/context', {method:'POST',body:JSON.stringify({token})})
            .then(result => {
                if (current !== generation) return;
                if (!/^\/telegram\/connect\?request=[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(result.next)) throw new Error('invalid');
                window.location.replace(result.next);
            }).catch(() => {
                if (current === generation) { busy = false; state = 'INVALID'; render(); status('invalid',true); }
            });
    };
    window.addEventListener('hashchange',openFragment);
    openFragment();
})();
