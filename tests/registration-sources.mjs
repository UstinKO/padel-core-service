import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

const reportScript = await readFile(new URL('../src/main/resources/static/js/registration-sources.js', import.meta.url), 'utf8');
const marketingScript = await readFile(new URL('../src/main/resources/static/js/meta-marketing.js', import.meta.url), 'utf8');
const tick = () => new Promise(resolve => setTimeout(resolve, 0));
class Element {
    children = []; hidden = false; textContent = ''; dataset = {}; attributes = {}; handlers = {};
    previousElementSibling = { textContent: 'unit' };
    appendChild(value) { this.children.push(value); }
    replaceChildren() { this.children = []; }
    setAttribute(key, value) { this.attributes[key] = value; }
    removeAttribute(key) { delete this.attributes[key]; }
    addEventListener(key, handler) { this.handlers[key] = handler; }
}
const counts = (participants = 2) => ({ participants, firstRegistrations: participants, pairs: participants / 2,
    confirmed: participants, waitlist: 0, pending: 0, cancelled: 0,
    firstConfirmed: participants, firstWaitlist: 0, firstPending: 0, firstCancelled: 0,
    pairConfirmed: participants / 2, pairWaitlist: 0, pairPending: 0, pairCancelled: 0 });
function data(value = 2) {
    return { totals: counts(value), measuredParticipants: value, measuredFirstRegistrations: value, measuredPairs: value / 2,
        asOf: '2026-11-02T12:00:00Z', page: 0, pageSize: 50, totalGroups: value ? 1 : 0,
        rows: value ? [{ source: 'reby', placement: 'qr', medium: 'qr', campaign: 'offline_tournaments',
            content: '', term: '', tournamentId: 1, tournament: 'Evento', counts: counts(value) }] : [] };
}
function response(body = data(), code = 200) {
    return { ok: code === 200, status: code, redirected: false,
        headers: { get: () => 'application/json' }, json: async () => body };
}
async function interfaceHarness(fetch) {
    const nodes = {};
    const ids = ['registration-sources','source-filters','source-status','source-results','source-rows','source-prev',
        'source-next','source-asof','source-empty','source-page','source-unit-statuses'];
    Object.keys(counts()).forEach(key => ids.push('source-' + key));
    ids.forEach(id => { nodes[id] = new Element(); });
    nodes['registration-sources'].dataset = { loading: 'loading', error: 'error', invalid: 'invalid', access: 'access', unknown: 'unknown' };
    const form = nodes['source-filters'];
    form.elements = Object.fromEntries(['from','to','model','source','placement','medium','campaign','content','term','tournamentId','clubId'].map(key => [key, { value: key === 'model' ? 'last_non_direct' : '' }]));
    form.elements.placement.options = [{ value: '', textContent: 'all' }, { value: 'qr', textContent: 'QR' }];
    form.reportValidity = () => true;
    class FormData { constructor(value) { this.values = Object.entries(value.elements).map(([key, item]) => [key, item.value]); } [Symbol.iterator]() { return this.values[Symbol.iterator](); } }
    vm.runInNewContext(reportScript, { document: { getElementById: id => nodes[id], createElement: () => new Element(), documentElement: { lang: 'ru' } },
        fetch, URLSearchParams, FormData, AbortController, Intl, Date, Number });
    await tick(); await tick();
    return { nodes, form, submit: () => form.handlers.submit({ preventDefault() {} }) };
}
async function captureHarness({ consent = true, failCapture = false, sdkEnabled = false } = {}) {
    const calls = []; const scripts = []; const events = {};
    const location = new URL('https://example.test/?utm_source=reby&utm_medium=qr&utm_campaign=offline_tournaments');
    const document = { cookie: consent ? 'cookieConsent=accepted' : '', referrer: '',
        head: { appendChild: value => { scripts.push(value); if (value.onerror) value.onerror(); } },
        createElement: () => ({}), addEventListener() {} };
    const fetch = async (path, options = {}) => {
        calls.push({ path, options });
        if (path === '/api/marketing/config') return response({ enabled: sdkEnabled, attributionEnabled: true, pixelId: sdkEnabled ? '123456' : '', testMode: true });
        if (path === '/api/marketing/context') {
            if (failCapture) throw new Error('offline');
            return response({ externalId: sdkEnabled ? 'a'.repeat(64) : '', fbc: '' });
        }
        return response({});
    };
    const window = { location, fetch, crypto: { randomUUID: () => 'test-page' }, addEventListener: (name, callback) => { events[name] = callback; } };
    vm.runInNewContext(marketingScript, { window, document, location, URL, AbortController, setTimeout, clearTimeout,
        history: { state: {}, replaceState() {} }, HTMLFormElement: class {} });
    await window.metaMarketing.ready(); await tick();
    return { calls, scripts, window, events, document };
}
export async function run() {
    const passed = [];
    const check = async (name, action) => { await action(); passed.push(name); };
    await check('report totals, rows and units', async () => {
        const { nodes } = await interfaceHarness(async () => response());
        assert.equal(nodes['source-status'].textContent, '');
        assert.equal(nodes['source-results'].hidden, false);
        assert.equal(nodes['source-rows'].children.length, 1);
        assert.equal(nodes['source-unit-statuses'].children.length, 3);
        assert.equal(nodes['source-participants'].textContent, '2');
    });
    await check('empty result is zero, not error', async () => {
        const { nodes } = await interfaceHarness(async () => response(data(0)));
        assert.equal(nodes['source-empty'].hidden, false);
        assert.equal(nodes['source-results'].hidden, false);
    });
    for (const [code, message] of [[500,'error'],[403,'access'],[400,'invalid']]) {
        await check('HTTP ' + code, async () => {
            const { nodes } = await interfaceHarness(async () => response({},code));
            assert.equal(nodes['source-status'].textContent, message);
            assert.equal(nodes['source-results'].hidden, true);
        });
    }
    await check('login redirect is access error', async () => {
        const { nodes } = await interfaceHarness(async () => ({ ...response(), redirected: true }));
        assert.equal(nodes['source-status'].textContent, 'access');
    });
    await check('late response cannot overwrite new result', async () => {
        let resolveOld; let call = 0;
        const h = await interfaceHarness(() => ++call === 1 ? new Promise(resolve => { resolveOld = resolve; }) : Promise.resolve(response(data(0))));
        h.submit(); await tick();
        resolveOld(response()); await tick(); await tick();
        assert.equal(h.nodes['source-participants'].textContent, '0');
    });
    await check('invalid period makes no additional request', async () => {
        let calls = 0;
        const h = await interfaceHarness(async () => { calls++; return response(); });
        h.form.elements.from.value = '2025-01-01'; h.form.elements.to.value = '2026-11-02';
        h.submit(); await tick();
        assert.equal(calls, 1); assert.equal(h.nodes['source-status'].textContent, 'invalid');
    });
    await check('no consent means no capture', async () => {
        const h = await captureHarness({ consent: false });
        assert.equal(h.calls.length, 0); assert.equal(h.scripts.length, 0);
    });
    await check('Meta off still captures UTM and does not load SDK', async () => {
        const h = await captureHarness();
        const capture = h.calls.find(call => call.path === '/api/marketing/context');
        assert.equal(JSON.parse(capture.options.body).utm.source, 'reby');
        assert.equal(JSON.parse(capture.options.body).fbclid, null);
        assert.equal(h.scripts.length, 0);
    });
    await check('capture failure does not block registration fetch', async () => {
        const h = await captureHarness({ failCapture: true });
        const answer = await h.window.fetch('/players/tournaments/1/register', { method: 'POST' });
        assert.equal(answer.ok, true);
    });
    await check('SDK failure happens after internal capture', async () => {
        const h = await captureHarness({ sdkEnabled: true });
        assert.ok(h.calls.some(call => call.path === '/api/marketing/context'));
        assert.equal(h.scripts.length, 1);
    });
    return passed;
}
