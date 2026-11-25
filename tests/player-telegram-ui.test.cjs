const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { JSDOM } = require('jsdom');
const fragment = fs.readFileSync('src/main/resources/templates/fragments/player-telegram.html', 'utf8');
const source = fs.readFileSync('src/main/resources/static/js/player-telegram.js', 'utf8');
const csrf = fs.readFileSync('src/main/resources/static/js/csrf.js', 'utf8');
const settle = () => new Promise(resolve => setImmediate(resolve));
async function fixture(kind, handler) {
    const dom = new JSDOM(fragment, { url: 'https://onepadel.example.test/perfil', runScripts: 'outside-only' });
    const window = dom.window;
    window.document.querySelector(kind === 'profile' ? '[data-tg-admin]' : '[data-tg-profile]').remove();
    if (kind === 'registration') window.document.querySelector('[data-tg-admin]').remove();
    const panel = kind === 'registration' ? window.document.querySelector('template').content.firstElementChild
        : window.document.querySelector('[data-tg-' + kind + ']');
    Object.assign(panel.dataset, { loading: 'Loading', error: 'Error', connected: 'Connected', disconnected: 'Disconnected',
        unavailable: 'Unavailable', openHint: 'Open bot', pending: 'Pending', confirmed: 'Confirmed', notRequired: 'Not requested',
        declined: 'Declined', noTelegram: 'No Telegram', deliveryError: 'Delivery issue', sent: 'Processed', summarySent: 'Summary requested', tournamentId: '42',
        whatsappSaved: 'WhatsApp saved; no automatic WhatsApp reminders', whatsappTitle: 'WhatsApp preference',
        connectedTitle: 'Telegram connected', promptTitle: 'Get reminders', whatsappManual: 'WhatsApp — contact manually', manualContact: 'Contact manually' });
    const calls = [];
    window.Headers = Headers;
    window.document.cookie = 'XSRF-TOKEN=fixture-csrf';
    window.fetch = async (url, options) => {
        calls.push({ url, options });
        return handler(url, options);
    };
    window.eval(csrf);
    window.eval(source);
    await settle(); await settle();
    return { dom, panel, calls };
}
const response = data => ({ ok: true, status: 200, json: async () => ({ eligible: true, ...data }) });
test('profile link requires an explicit click and a double click issues one token with CSRF', async () => {
    const f = await fixture('profile', async (url, options) => options.method === 'POST'
        ? response({ url: 'https://t.me/FixtureBot?start=fixture', expiresAt: new Date(Date.now() + 600000).toISOString() })
        : response({ available: true, connected: false }));
    assert.equal(f.calls.length, 1);
    const button = f.panel.querySelector('[data-tg-connect]');
    button.click(); button.click();
    await settle(); await settle();
    assert.equal(f.calls.filter(c => c.options.method === 'POST').length, 1);
    assert.equal(f.calls[1].options.headers.get('X-XSRF-TOKEN'), 'fixture-csrf');
    const open = f.panel.querySelector('[data-tg-open]');
    assert.equal(open.hidden, false);
    assert.equal(new URL(open.href).hostname, 't.me');
    assert.equal(open.rel, 'noopener noreferrer');
    f.dom.window.close();
});
test('disconnect stops the connection and refreshes the displayed state', async () => {
    let connected = true;
    const f = await fixture('profile', async (url, options) => {
        if (options.method === 'DELETE') { connected = false; return { ok: true, status: 204 }; }
        return response({ available: true, connected });
    });
    f.panel.querySelector('[data-tg-disconnect]').click();
    await settle(); await settle();
    assert.equal(f.calls.filter(c => c.options.method === 'DELETE').length, 1);
    assert.equal(f.panel.querySelector('[data-tg-status]').textContent, 'Disconnected');
    assert.equal(f.panel.querySelector('[data-tg-disconnect]').hidden, true);
    f.dom.window.close();
});
test('admin displays escaped names, missing connections and uncertain deliveries', async () => {
    const f = await fixture('admin', async () => response({ participants: [
        { name: '<img src=x onerror=alert(1)>', response: 'PENDING', connected: false, delivery: 'UNKNOWN' },
        { name: 'Fixture Two', response: 'CONFIRMED', connected: true, delivery: 'SENT' }
    ] }));
    assert.equal(f.panel.querySelectorAll('img').length, 0);
    assert.equal(f.panel.querySelector('[data-tg-total]').textContent, '2');
    assert.equal(f.panel.querySelector('[data-tg-confirmed]').textContent, '1');
    assert.equal(f.panel.querySelector('[data-tg-missing]').textContent, '1');
    assert.match(f.panel.querySelector('[data-tg-participants]').textContent, /Delivery issue/);
    f.dom.window.close();
});
test('an uncertain admin mutation is not retried automatically or by a double click', async () => {
    const f = await fixture('admin', async (url, options) => {
        if (options.method === 'POST') throw new Error('fixture network interruption');
        return response({ participants: [] });
    });
    const button = f.panel.querySelector('[data-tg-request]');
    button.click(); button.click();
    await settle(); await settle();
    assert.equal(f.calls.filter(c => c.options.method === 'POST').length, 1);
    assert.equal(f.panel.querySelector('[data-tg-status]').textContent, 'Error');
    assert.equal(button.disabled, false);
    f.dom.window.close();
});

function registrationModal(f) {
    const modal = f.dom.window.document.createElement('div');
    modal.className = 'modal show';
    modal.innerHTML = '<div class="modal-body"><p>Existing waitlist result and cancellation rules</p></div>';
    f.dom.window.document.body.append(modal);
    return modal;
}

test('registration offers Telegram only after success and persists WhatsApp with CSRF', async () => {
    let preference = 'UNSPECIFIED';
    const f = await fixture('registration', async (url, options) => {
        if (options.method === 'PUT') preference = JSON.parse(options.body).preference;
        return response({ available: true, connected: false, preference });
    });
    assert.equal(f.calls.length, 0);
    const modal = registrationModal(f);
    assert.equal(await f.dom.window.PlayerTelegramRegistration.mount(modal), true);
    assert.match(modal.textContent, /Existing waitlist result and cancellation rules/);
    const prompt = modal.querySelector('[data-tg-prompt]');
    assert.equal([...prompt.querySelectorAll('button')].filter(button => !button.hidden).length, 2);
    const button = prompt.querySelector('[data-tg-whatsapp]');
    button.click(); button.click();
    await settle(); await settle();
    const writes = f.calls.filter(call => call.options.method === 'PUT');
    assert.equal(writes.length, 1);
    assert.equal(writes[0].options.headers.get('X-XSRF-TOKEN'), 'fixture-csrf');
    assert.deepEqual(JSON.parse(writes[0].options.body), { preference: 'WHATSAPP' });
    assert.match(prompt.querySelector('[data-tg-status]').textContent, /WhatsApp saved/);
    assert.equal(prompt.querySelector('[data-tg-connect]').hidden, true);
    assert.equal(prompt.querySelector('[data-tg-profile-link]').hidden, false);
    assert.equal(await f.dom.window.PlayerTelegramRegistration.mount(registrationModal(f)), false);
    f.dom.window.close();
});

test('connected accounts, WhatsApp preference and unavailable bot suppress the registration offer', async () => {
    for (const state of [
        { available: true, connected: true, preference: 'TELEGRAM' },
        { available: true, connected: false, preference: 'WHATSAPP' },
        { available: false, connected: false, preference: 'UNSPECIFIED' }
    ]) {
        const f = await fixture('registration', async () => response(state));
        const modal = registrationModal(f);
        assert.equal(await f.dom.window.PlayerTelegramRegistration.mount(modal), false);
        assert.equal(modal.querySelector('[data-tg-prompt]'), null);
        f.dom.window.close();
    }
});

test('failed preference write leaves the offer available and never claims a saved choice', async () => {
    const f = await fixture('registration', async (url, options) => {
        if (options.method === 'PUT') throw new Error('network interruption');
        return response({ available: true, connected: false, preference: 'UNSPECIFIED' });
    });
    const modal = registrationModal(f);
    await f.dom.window.PlayerTelegramRegistration.mount(modal);
    const prompt = modal.querySelector('[data-tg-prompt]');
    prompt.querySelector('[data-tg-whatsapp]').click();
    await settle(); await settle();
    assert.equal(prompt.querySelector('[data-tg-status]').textContent, 'Error');
    assert.equal(prompt.querySelector('[data-tg-whatsapp]').hidden, false);
    assert.equal(f.calls.filter(call => call.options.method === 'PUT').length, 1);
    f.dom.window.close();
});

test('choosing WhatsApp hides an issued Telegram bearer link', async () => {
    let preference = 'UNSPECIFIED';
    const f = await fixture('registration', async (url, options) => {
        if (options.method === 'POST') return response({url:'https://t.me/FixtureBot?start=fixture', expiresAt:new Date(Date.now()+600000).toISOString()});
        if (options.method === 'PUT') preference = JSON.parse(options.body).preference;
        return response({available:true,connected:false,preference});
    });
    const modal = registrationModal(f);
    await f.dom.window.PlayerTelegramRegistration.mount(modal);
    const prompt = modal.querySelector('[data-tg-prompt]');
    prompt.querySelector('[data-tg-connect]').click();
    await settle(); await settle();
    const open = prompt.querySelector('[data-tg-open]');
    assert.equal(open.hidden, false);
    prompt.querySelector('[data-tg-whatsapp]').click();
    await settle(); await settle();
    assert.equal(open.hidden, true);
    assert.equal(open.hasAttribute('href'), false);
    f.dom.window.close();
});

test('profile can change WhatsApp preference without automatically connecting or messaging', async () => {
    let preference = 'WHATSAPP';
    const f = await fixture('profile', async (url, options) => {
        if (options.method === 'PUT') preference = JSON.parse(options.body).preference;
        return response({available:true,connected:false,preference});
    });
    const select = f.panel.querySelector('[data-tg-preference]');
    assert.equal(select.value, 'WHATSAPP');
    select.value = 'TELEGRAM';
    f.panel.querySelector('[data-tg-save-preference]').click();
    await settle(); await settle();
    assert.equal(preference, 'TELEGRAM');
    assert.equal(f.calls.filter(call => call.options.method === 'POST').length, 0);
    assert.equal(f.panel.querySelector('[data-tg-connect]').hidden, false);
    f.dom.window.close();
});

test('a late status response cannot attach the offer to a closed or replaced result', async () => {
    let resolveStatus;
    const f = await fixture('registration', async () => new Promise(resolve => { resolveStatus = resolve; }));
    const modal = registrationModal(f);
    const pending = f.dom.window.PlayerTelegramRegistration.mount(modal);
    modal.querySelector('.modal-body').innerHTML = '<p>A later error result</p>';
    resolveStatus(response({available:true,connected:false,preference:'UNSPECIFIED'}));
    assert.equal(await pending, false);
    assert.equal(modal.querySelector('[data-tg-prompt]'), null);
    f.dom.window.close();
});

test('a profile outside the pilot remains hidden and registration never offers Telegram', async () => {
    const hidden = await fixture('profile', async () => response({ eligible: false, available: false, connected: false }));
    assert.equal(hidden.panel.hidden, true);
    assert.equal(hidden.calls.length, 1);
    hidden.dom.window.close();
    const f = await fixture('registration', async () => response({ eligible: false, available: false, connected: false }));
    const modal = registrationModal(f);
    assert.equal(await f.dom.window.PlayerTelegramRegistration.mount(modal), false);
    assert.equal(modal.querySelector('[data-tg-prompt]'), null);
    f.dom.window.close();
});
test('eligible profile is shown only after a successful eligibility read', async () => {
    const f = await fixture('profile', async () => response({ eligible: true, available: true, connected: false }));
    assert.equal(f.panel.hidden, false);
    f.dom.window.close();
});
