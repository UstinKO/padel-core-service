/* Управление одиночными заявками на обычной странице оплат. */
(function () {
    'use strict';
    const host = document.getElementById('solo-roster');
    if (!host) return;
    const m = window.soloRosterMessages;
    const base = '/admin/tournaments/' + host.dataset.eventId + '/participants';
    const get = id => document.getElementById('solo-' + id);
    const dialog = get('dialog');
    const paymentForm = document.getElementById('paymentForm');
    let board = {singles: [], cancelled: [], history: []};
    let action, source, selected, preview, command, busy = false, dirty = false, searchTimer, searchVersion = 0, trigger, submitted = false;
    const mode = get('mode');
    const disclosureKey = 'solo-roster-expanded:' + host.dataset.eventId;
    try { host.open = sessionStorage.getItem(disclosureKey) === 'true'; }
    catch { /* Без хранилища блок по умолчанию остаётся свёрнутым. */ }
    host.addEventListener('toggle', () => {
        try { sessionStorage.setItem(disclosureKey, String(host.open)); }
        catch { /* Недоступное хранилище не мешает обычному сворачиванию. */ }
    });
    const say = (id, value) => { get(id).textContent = value || ''; };
    const node = (tag, text, cls) => {
        const element = document.createElement(tag);
        if (text !== undefined) element.textContent = text;
        if (cls) element.className = cls;
        return element;
    };
    const button = (text, run, cls = 'btn btn-outline') => {
        const element = node('button', text, cls); element.type = 'button'; element.addEventListener('click', run); return element;
    };
    function status(value) {
        return ({CONFIRMED:m.main, WAITLIST:m.reserve, SOLO_SEARCH:m.looking, SOLO_ADD_LATER:m.later,
            CANCELLED:m.cancelled, NEW:m.newPlayer, PAIR_REGISTERED:m.main, PARTNER_INVITED:m.invited})[value] || value;
    }
    function money(payment) {
        return [payment.amount, payment.currency || 'ARS', m['payment_' + payment.status] || payment.status,
            m['method_' + payment.method] || payment.method, payment.transactionId, payment.notes].filter(Boolean).join(' · ');
    }
    function preference(row) {
        return window.PadelPlayingSide?.format(row.level, row.playingSide, true)
            || (row.level && row.level !== 'SIN_ESPECIFICAR' ? row.level : m.unknown);
    }
    function playerLabel(row) {
        return [row.name, preference(row)].filter(Boolean).join(' · ');
    }
    function person(row, actions) {
        const card = node('article', undefined, 'solo-person');
        card.append(node('h3', row.name));
        card.append(node('span', status(row.status), 'solo-tag'));
        if (row.status.startsWith('SOLO_') || row.status === 'NEW') card.append(node('span', m.unplaced, 'solo-tag'));
        card.append(node('p', preference(row) + ' · ' + (row.attended ? m.present : m.absent)));
        if (!row.payments.length) card.append(node('p', m.unpaid));
        row.payments.forEach(payment => card.append(node('p', money(payment))));
        if (actions) {
            const bar = node('div', undefined, 'solo-actions');
            const actionsWithHelp = [
                [button(m.partner, event => open('PAIR', row, 'search', event.currentTarget)), 'solo-partner'],
                [button(m.merge, event => open('PAIR', row, 'single', event.currentTarget)), 'solo-pair'],
                [button(m.remove, event => open('REMOVE', row, 'search', event.currentTarget)), 'solo-remove']
            ];
            actionsWithHelp.forEach(([actionButton, topic]) => {
                const group = node('span', undefined, 'solo-action-help');
                group.append(actionButton);
                const help = document.querySelector('#solo-action-help-copies [data-help-copy="' + topic + '"] button');
                if (help) group.append(help.cloneNode(true));
                bar.append(group);
            });
            bar.append(button(m.payment, () => focusPayment(row.registrationId)));
            card.append(bar);
        }
        return card;
    }
    function focusPayment(id) {
        const row = document.querySelector('#paymentForm tr[data-registration-id="' + Number(id) + '"]');
        if (!row) return;
        row.scrollIntoView({block:'center',behavior:'smooth'});
        row.classList.add('solo-payment-focus');
        row.querySelector('input:not([type=hidden]),select,button')?.focus({preventScroll:true});
        setTimeout(() => row.classList.remove('solo-payment-focus'), 3000);
    }
    async function api(path, payload) {
        const response = await fetch(base + path, {
            method: payload ? 'POST' : 'GET', credentials:'same-origin', cache:'no-store',
            headers:{Accept:'application/json', ...(payload ? {'Content-Type':'application/json'} : {})},
            ...(payload ? {body:JSON.stringify(payload)} : {})
        });
        if (response.redirected || !response.headers.get('content-type')?.includes('json')) throw new Error(m.session);
        const result = await response.json();
        if (!response.ok) throw new Error(result.detail || m.error);
        return result;
    }
    async function load() {
        try {
            board = await api('');
            say('count', '(' + board.singles.length + ')');
            get('count').hidden = false;
            get('list').replaceChildren(...board.singles.map(row => person(row, true)));
            if (!board.singles.length) get('list').append(node('p', m.empty));
            get('cancelled').replaceChildren(...board.cancelled.map(row => person(row, false)));
            get('history').replaceChildren(...board.history.map(entry => {
                let people = [];
                try { people = JSON.parse(entry.before); } catch { /* Старый журнал может не содержать состава. */ }
                return node('li', [new Date(entry.at).toLocaleString(document.documentElement.lang),
                    m['action_' + entry.action], m.actor + ' #' + entry.actorId,
                    people.map(row => row.name).join(' / ')].filter(Boolean).join(' · '));
            }));
            say('notice','');
        } catch (error) { host.open = true; say('notice', error.message); }
    }
    function busyState(value) {
        busy = value;
        dialog.querySelectorAll('button,input,select').forEach(element => { element.disabled = value; });
        get('override').disabled = value || submitted;
        dialog.setAttribute('aria-busy', String(value));
    }
    function resetPreview() {
        if (busy) return;
        preview = null; command = null; submitted = false;
        get('preview').hidden = true; get('form').hidden = false;
        say('error','');
    }
    function updateMode() {
        resetPreview(); selected = null; searchVersion++;
        get('search-pane').hidden = mode.value !== 'search';
        get('single-pane').hidden = mode.value !== 'single';
        get('guest-pane').hidden = mode.value !== 'guest';
        get('kind-pane').hidden = action !== 'ADD';
        get('mode').querySelector('option[value=single]').disabled = action === 'ADD';
        get('search-results').replaceChildren();say('selected','');
        say('single-selected','');
    }
    function open(nextAction, row, nextMode, opener) {
        if (dirty) {
            say('notice',m.unsaved);
            paymentForm.querySelector('button[type=submit]')?.focus();return;
        }
        action = nextAction;source = row;trigger = opener;
        get('form').reset();resetPreview();mode.value = nextMode;updateMode();
        get('single').replaceChildren(node('option', m.choose));get('single').firstChild.value = '';
        board.singles.filter(candidate => candidate.registrationId !== row?.registrationId).forEach(candidate => {
            const option = node('option',playerLabel(candidate) + ' · ' + status(candidate.status));option.value = candidate.registrationId;
            get('single').append(option);
        });
        say('dialog-title', action === 'ADD' ? m.add : action === 'REMOVE' ? m.remove : m.partner);
        say('source', row ? playerLabel(row) : '');
        get('picker').hidden = action === 'REMOVE';get('remove-note').hidden = action !== 'REMOVE';
        dialog.showModal();
        (action === 'REMOVE' ? get('review') : get(nextMode === 'single' ? 'single' : 'search')).focus();
    }
    function close() { if (!busy) { dialog.close();trigger?.focus(); } }
    function input() {
        if (action === 'REMOVE') return null;
        if (mode.value === 'guest') return {firstName:get('first').value.trim(),lastName:get('last').value.trim(),phone:get('phone').value.trim(),telegram:get('telegram').value.trim(),playingSide:get('side').value || null};
        if (mode.value === 'single') return null;
        if (!selected) throw new Error(m.choose);
        return {id:selected.playerId};
    }
    async function review(event) {
        event.preventDefault();if (busy) return;
        say('error','');busyState(true);
        try {
            const partnerId = action === 'PAIR' && mode.value === 'single' ? Number(get('single').value) : null;
            if (action === 'PAIR' && mode.value === 'single' && !partnerId) throw new Error(m.choose);
            command = {requestId:crypto.randomUUID(),action,registrationId:source?.registrationId || null,
                partnerRegistrationId:partnerId,player:input(),kind:action === 'ADD' ? get('kind').value : null,overrideLevel:false};
            preview = await api('/preview', command);command.expectedState = preview.state;
            get('preview-people').replaceChildren(...preview.participants.map(row => person(row,false)));
            say('result',status(preview.resultingStatus));
            say('preview-note', action === 'REMOVE' ? m.removeNote : m.preserve);
            get('preview-note').classList.toggle('solo-warning', action === 'REMOVE');
            get('level-warning').hidden = !preview.levelOverrideRequired;get('override').checked = false;
            get('confirm').textContent = preview.existingRegistrationId ? m.openExisting : m.confirm;
            if (preview.existingPair) get('preview-people').append(node('p',m.existing + ' ' + preview.existingPair));
            get('form').hidden = true;get('preview').hidden = false;
        } catch (error) { say('error',error.message); }
        finally { busyState(false); }
        if (preview) get('confirm').focus();
    }
    async function confirm() {
        if (busy || !preview) return;
        if (preview.existingRegistrationId) {const id=preview.existingRegistrationId;close();focusPayment(id);return;}
        if (preview.levelOverrideRequired && !get('override').checked) {say('error',m.override);get('override').focus();return;}
        if (!submitted) command.overrideLevel = get('override').checked;
        submitted = true;
        busyState(true);say('error','');
        try {
            await api('', command);
            dialog.close();say('notice',m.saved);window.location.reload();
        } catch (error) { say('error',error.message); }
        finally { busyState(false); }
    }
    mode.addEventListener('change',updateMode);
    get('single').addEventListener('change', () => {
        const candidate = board.singles.find(row => String(row.registrationId) === get('single').value);
        say('single-selected', candidate ? playerLabel(candidate) + ' · ' + status(candidate.status) : '');
    });
    get('form').addEventListener('submit',review);
    get('confirm').addEventListener('click',confirm);
    get('back').addEventListener('click',resetPreview);
    dialog.querySelector('[data-roster-close]').addEventListener('click',close);
    dialog.addEventListener('cancel',event => {if(busy) event.preventDefault();});
    get('search').addEventListener('input',() => {
        selected=null;preview=null;command=null;say('selected','');clearTimeout(searchTimer);
        const version=++searchVersion, query=get('search').value.trim();
        get('search-results').replaceChildren();
        if(query.length<2) return;
        searchTimer=setTimeout(async () => {
            try {
                const found=await api('/search?q='+encodeURIComponent(query));
                if(version !== searchVersion) return;
                get('search-results').replaceChildren(...found.filter(player => player.playerId !== source?.playerId).map(player =>
                    button(playerLabel(player)
                        + (player.registrationId ? ' · '+(player.single?status(player.status):player.pair) : ''), () => {
                        selected=player;say('selected',m.selected+' '+playerLabel(player));get('search-results').replaceChildren();
                    },'solo-search-option')));
                if(!found.length) get('search-results').append(node('p',m.noResults));
            } catch(error) {if(version===searchVersion) say('error',error.message);}
        },250);
    });
    document.querySelectorAll('[data-roster-action=ADD]').forEach(element => element.addEventListener('click',event => open('ADD',null,'search',event.currentTarget)));
    paymentForm?.addEventListener('input',() => {dirty=true;});
    paymentForm?.addEventListener('change',() => {dirty=true;});
    load();
})();
