import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import test, { before, after, beforeEach } from 'node:test';

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright-core');
const source = new URL('../src/main/resources/static/js/', import.meta.url);
const eventId = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';
const externalId = createHash('sha256').update('test-visitor').digest('hex');
let browser;
let server;
let base;
let state;

beforeEach(() => {
    state = { enabled: true, events: [], acknowledged: new Set(), captures: [], sdkLoads: 0, registrations: 0,
        successful: true, category: 'EventRegistration', status: 'CONFIRMED' };
});

before(async () => {
    server = createServer(async (request, response) => {
        const url = new URL(request.url, 'http://localhost');
        let body = '';
        for await (const chunk of request) body += chunk;
        const data = body ? JSON.parse(body) : {};
        response.setHeader('Cache-Control', 'no-store');
        response.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self' 'unsafe-inline' https://connect.facebook.net; connect-src 'self' https://www.facebook.com https://connect.facebook.net; style-src 'self' 'unsafe-inline'");
        if (url.pathname.startsWith('/js/')) {
            response.setHeader('Content-Type', 'text/javascript');
            response.end(await readFile(new URL(url.pathname.slice(4), source)));
        } else if (url.pathname === '/api/marketing/config') {
            response.setHeader('Content-Type', 'application/json');
            response.end(JSON.stringify({ enabled: state.enabled, pixelId: '930260823500064', testMode: true }));
        } else if (url.pathname === '/api/marketing/context') {
            if (state.failContext) {
                response.writeHead(503).end('{}');
                return;
            }
            if (state.delayContext) await new Promise(resolve => setTimeout(resolve, 300));
            state.captures.push(data);
            if (data.utm && Object.values(data.utm).some(Boolean)) {
                state.first ||= data.utm;
                state.last = data.utm;
            }
            response.setHeader('Set-Cookie', 'meta_visitor=test-visitor; Path=/; HttpOnly; SameSite=Lax');
            response.setHeader('Content-Type', 'application/json');
            state.contextDone = true;
            response.end(JSON.stringify({ externalId, fbc: 'fb.1.1700000000000.real_click' }));
        } else if (url.pathname === '/api/marketing/events') {
            response.setHeader('Content-Type', 'application/json');
            response.end(JSON.stringify(state.events.filter(event => !state.acknowledged.has(event.eventId))));
        } else if (url.pathname === '/api/marketing/ack') {
            state.acknowledged.add(data.eventId);
            response.writeHead(204).end();
        } else if (url.pathname.startsWith('/api/cookies/')) {
            const value = url.pathname.endsWith('/accept') ? 'accepted' : 'rejected';
            response.setHeader('Set-Cookie', 'cookieConsent=' + value + '; Path=/; SameSite=Lax');
            response.end('{}');
        } else if (url.pathname === '/api/tournaments/double/1/register') {
            state.registrations++;
            state.registrationSawContext = state.contextDone;
            if (state.successful && !state.events.length) {
                state.events.push({ eventId, eventName: state.category, tournamentId: 1, registrationStatus: state.status });
            }
            response.setHeader('Content-Type', 'application/json');
            response.writeHead(state.successful ? 200 : 400).end(JSON.stringify({ success: state.successful }));
        } else {
            response.setHeader('Set-Cookie', 'XSRF-TOKEN=test-csrf; Path=/; SameSite=Lax');
            response.setHeader('Content-Type', 'text/html');
            response.end(`<!doctype html><html><head><script>function t(key) { return key; }</script>
                <script src="/js/csrf.js"></script><script src="/js/cookie-consent.js"></script>
                <script src="/js/meta-marketing.js"></script></head><body>
                <button id="register">Регистрация</button><a href="/torneo/1" id="event">Событие</a>
                <script>document.getElementById('register').onclick = async () => {
                    const response = await fetch('/api/tournaments/double/1/register', {method:'POST'});
                    window.lastRegistration = await response.json();
                };</script></body></html>`);
        }
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    base = 'http://127.0.0.1:' + server.address().port;
    browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || '/usr/bin/chromium', headless: true, args: ['--no-sandbox'] });
});

after(async () => {
    await browser?.close();
    await new Promise(resolve => server.close(resolve));
});

async function pageWithConsent(cookies = [{ name: 'cookieConsent', value: 'accepted' }]) {
    const context = await browser.newContext();
    await context.addCookies(cookies.map(cookie => ({ ...cookie, url: base })));
    await context.route('https://**/*', async route => {
        if (route.request().url() === 'https://connect.facebook.net/en_US/fbevents.js') {
            state.sdkLoads++;
            await route.fulfill({ contentType: 'text/javascript', body: `
                window.metaCalls = [];
                const pending = window.fbq.queue;
                window.fbq = function () { window.metaCalls.push(Array.from(arguments)); };
                for (const item of pending) window.fbq.apply(null, item);
                document.cookie = '_fbp=fb.1.1700000000000.12345; Path=/';` });
        } else await route.abort();
    });
    const page = await context.newPage();
    return { page, context };
}

async function waitForPixel(page) {
    await page.waitForFunction(() => window.metaCalls?.some(call => call[1] === 'PageView'));
}

async function register(page) {
    await page.click('#register');
    await page.waitForFunction(() => window.lastRegistration !== undefined);
    await page.evaluate(() => window.metaMarketing?.flush());
}

test('до согласия и при согласии только на аналитику Pixel и атрибуция отсутствуют', async () => {
    for (const cookies of [[], [{ name: 'cookieConsent', value: 'customized' }, { name: 'analyticsConsent', value: 'true' }, { name: 'marketingConsent', value: 'false' }]]) {
        const { page, context } = await pageWithConsent(cookies);
        await page.goto(base + '/?utm_source=meta&utm_campaign=campaign1');
        await page.evaluate(() => window.metaMarketing.ready());
        assert.equal(state.sdkLoads, 0);
        assert.equal(state.captures.length, 0);
        await context.close();
    }
});

test('отключённая интеграция не загружает Pixel даже при согласии', async () => {
    state.enabled = false;
    const { page, context } = await pageWithConsent();
    await page.goto(base + '/');
    await page.evaluate(() => window.metaMarketing.ready());
    assert.equal(state.sdkLoads, 0);
    assert.equal(state.captures.length, 0);
    await context.close();
});

test('согласие через настоящий cookie-баннер запускает PageView и сохраняет UTM', async () => {
    const { page, context } = await pageWithConsent([]);
    await page.goto(base + '/?utm_source=meta&utm_medium=paid_social&utm_campaign=campaign1&fbclid=real_click');
    await page.locator('#cookieConsentBanner .cookie-btn-accept').click();
    await waitForPixel(page);
    assert.equal(state.sdkLoads, 1);
    assert.equal(state.first.campaign, 'campaign1');
    assert.equal(new URL(page.url()).search, '');
    const calls = await page.evaluate(() => window.metaCalls);
    assert.equal(calls.filter(call => call[1] === 'PageView').length, 1);
    assert.deepEqual(calls.find(call => call[0] === 'init')[2], { external_id: externalId });
    assert.equal(calls.find(call => call[0] === 'set')[1], 'autoConfig');
    await context.close();
});

test('ViewContent передаёт ID события, успешная запись — серверный eventID без дублей', async () => {
    const { page, context } = await pageWithConsent();
    await page.goto(base + '/torneo/1?utm_source=meta&utm_campaign=campaign1');
    await waitForPixel(page);
    await register(page);
    let calls = await page.evaluate(() => window.metaCalls);
    assert.deepEqual(calls.find(call => call[1] === 'ViewContent')[2].content_ids, ['1']);
    assert.equal(calls.filter(call => call[1] === 'EventRegistration').length, 1);
    assert.equal(calls.find(call => call[1] === 'EventRegistration')[3].eventID, eventId);
    await register(page);
    calls = await page.evaluate(() => window.metaCalls);
    assert.equal(calls.filter(call => call[1] === 'EventRegistration').length, 1);
    await page.reload();
    await waitForPixel(page);
    await page.evaluate(() => window.metaMarketing.flush());
    assert.equal((await page.evaluate(() => window.metaCalls)).filter(call => call[1] === 'EventRegistration').length, 0);
    assert.equal(state.first.campaign, 'campaign1');
    assert.equal(state.last.campaign, 'campaign1');
    await context.close();
});

test('ошибка записи не создаёт успешную конверсию и не ломает ответ приложения', async () => {
    state.successful = false;
    const { page, context } = await pageWithConsent();
    await page.goto(base + '/torneo/1');
    await waitForPixel(page);
    await register(page);
    assert.equal((await page.evaluate(() => window.metaCalls)).filter(call => call[0] === 'trackCustom').length, 0);
    assert.equal(await page.evaluate(() => window.lastRegistration.success), false);
    await context.close();
});

test('резерв и ожидание партнёра не становятся EventRegistration', async () => {
    for (const [category, status] of [['EventWaitlist', 'WAITLIST'], ['EventRegistrationPending', 'SOLO_SEARCH']]) {
        state.category = category;
        state.status = status;
        state.events = [];
        state.acknowledged.clear();
        const { page, context } = await pageWithConsent();
        await page.goto(base + '/torneo/1');
        await waitForPixel(page);
        await register(page);
        const calls = await page.evaluate(() => window.metaCalls);
        assert.equal(calls.filter(call => call[1] === 'EventRegistration').length, 0);
        assert.equal(calls.find(call => call[1] === category)[2].registration_status, status);
        await context.close();
    }
});

test('контакты и токены в URL или referrer блокируют загрузку SDK', async () => {
    const { page, context } = await pageWithConsent();
    await page.goto(base + '/?email=person%40example.test&token=private-token&utm_campaign=person%40example.test');
    await page.evaluate(() => window.metaMarketing.ready());
    assert.equal(state.sdkLoads, 0);
    assert.ok(!JSON.stringify(state.captures).includes('person@example.test'));
    await page.click('#event');
    await page.waitForFunction(() => window.metaMarketing);
    await page.evaluate(() => window.metaMarketing.ready());
    assert.equal(state.sdkLoads, 0);
    await context.close();
});

test('отзыв согласия останавливает события; отказ SDK не мешает регистрации', async () => {
    const { page, context } = await pageWithConsent();
    await page.goto(base + '/torneo/1');
    await waitForPixel(page);
    await page.evaluate(() => window.rejectCookies());
    await page.waitForFunction(() => document.cookie.includes('cookieConsent=rejected'));
    await register(page);
    const calls = await page.evaluate(() => window.metaCalls);
    assert.ok(calls.some(call => call[0] === 'consent' && call[1] === 'revoke'));
    assert.ok(!calls.some(call => call[0] === 'trackCustom'));
    await context.close();
    state.events = [];
    const second = await pageWithConsent();
    await second.context.route('https://connect.facebook.net/**', route => route.abort());
    await second.page.goto(base + '/torneo/1');
    await register(second.page);
    assert.equal(await second.page.evaluate(() => window.lastRegistration.success), true);
    assert.equal(state.acknowledged.size, 0);
    await second.context.close();
});

test('запись из dashboard доставляет конверсию без PageView приватной страницы', async () => {
    const { page, context } = await pageWithConsent();
    await page.goto(base + '/players/dashboard');
    await page.waitForFunction(() => window.metaCalls?.some(call => call[0] === 'init'));
    await register(page);
    const calls = await page.evaluate(() => window.metaCalls);
    assert.ok(calls.some(call => call[1] === 'EventRegistration'));
    assert.ok(!calls.some(call => call[1] === 'PageView' || call[1] === 'ViewContent'));
    await context.close();
});

test('быстрый запрос записи ждёт атрибуцию, но сбой её API не ломает запись', async () => {
    state.delayContext = true;
    const { page, context } = await pageWithConsent();
    await page.goto(base + '/torneo/1');
    await register(page);
    assert.equal(state.registrationSawContext, true);
    await context.close();
    state.failContext = true;
    state.events = [];
    const next = await pageWithConsent();
    await next.page.goto(base + '/torneo/1');
    await register(next.page);
    assert.equal(await next.page.evaluate(() => window.lastRegistration.success), true);
    await next.context.close();
});

test('страница регистрации аккаунта не загружает Pixel и не выдаёт EventRegistration', async () => {
    const { page, context } = await pageWithConsent();
    await page.goto(base + '/players/registro');
    assert.equal(state.sdkLoads, 0);
    assert.equal(state.captures.length, 0);
    await context.close();
});
