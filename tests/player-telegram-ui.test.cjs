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
    const panel = window.document.querySelector('[data-tg-' + kind + ']');
    Object.assign(panel.dataset, { loading: 'Loading', error: 'Error', connected: 'Connected', disconnected: 'Disconnected',
        unavailable: 'Unavailable', openHint: 'Open bot', pending: 'Pending', confirmed: 'Confirmed', notRequired: 'Not requested',
        declined: 'Declined', noTelegram: 'No Telegram', deliveryError: 'Delivery issue', sent: 'Processed', summarySent: 'Summary requested', tournamentId: '42' });
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
const response = data => ({ ok: true, status: 200, json: async () => data });
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
