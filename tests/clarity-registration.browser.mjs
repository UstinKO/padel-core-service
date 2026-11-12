import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import test, { before, after } from 'node:test';

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright-core');
const scripts = new URL('../src/main/resources/static/js/', import.meta.url);
let browser;
let server;
let origin;

before(async () => {
    server = createServer(async (request, response) => {
        const url = new URL(request.url, 'http://localhost');
        if (url.pathname.startsWith('/js/')) {
            response.setHeader('Content-Type', 'text/javascript');
            response.end(await readFile(new URL(url.pathname.slice(4), scripts)));
        } else if (url.pathname === '/api/cookies/reject') {
            response.setHeader('Set-Cookie', 'cookieConsent=rejected; Path=/');
            response.end('{}');
        } else if (url.pathname === '/api/players/me/contact-status') {
            response.end(JSON.stringify({ hasContact: true }));
        } else if (url.pathname === '/api/tournaments/double/155/register') {
            response.writeHead(422).end(JSON.stringify({ message: 'test response text' }));
        } else if (url.pathname.startsWith('/api/tournaments/double/')) {
            response.end(JSON.stringify({ status: 'CONFIRMED' }));
        } else {
            const id = url.pathname.split('/').at(-1);
            response.setHeader('Content-Type', 'text/html');
            response.end(`<!doctype html><script>
                const t = key => key;
                window.tournament = { modalidad: 'DOBLES' };
                window.PairTerms = { reviewBooking: async () => ({}) };
            </script>
            <button class="btn-register" data-tournament-id="${id}" data-registration-intent="${id}">Записаться</button>
            <div id="partnerModal">
                <form id="partnerForm">
                    <input id="modalTournamentId">
                    <input id="registrationMode" value="WITH_PARTNER">
                    <input id="partnerFirstName"><input id="partnerLastName">
                    <input id="partnerPhone"><input id="partnerTelegram"><input id="selectedPartnerId">
                </form>
                <div id="partnerInfoMessage"><i></i><span id="infoMessageText"></span></div>
                <button type="button" id="submitPartnerBtn">Отправить</button>
            </div>
            <div id="resultModal"><div id="resultModalBody"></div></div>
            <script src="/js/contact-check.js"></script><script src="/js/cookie-consent.js"></script>
            <script src="/js/registration-tracking.js"></script><script src="/js/tournament-details.js"></script>`);
        }
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    origin = 'http://127.0.0.1:' + server.address().port;
    browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH });
});

after(async () => {
    await browser?.close();
    if (server) await new Promise(resolve => server.close(resolve));
});

async function fixture(t, consent = 'accepted') {
    const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    t.after(() => ctx.close());
    await ctx.addCookies([{ name: 'cookieConsent', value: consent, url: origin }]);
    await ctx.addInitScript(() => {
        window.calls = [];
        window.ga4 = [];
        window.gtag = (...args) => window.ga4.push(args);
        window.clarity = (method, ...args) => {
            if (method === 'metadata') {
                args[0]({ sessionId: window.testSession, userId: 'unused-user-id' });
            } else {
                window.calls.push([method, ...args]);
            }
        };
    });
    const page = await ctx.newPage();
    const open = async (session, id = '153') => {
        await page.goto(origin + '/torneo/' + id);
        await page.evaluate(s => { window.testSession = s; }, session);
    };
    const events = () => page.evaluate(() => window.calls.filter(([method]) => method === 'event').map(([, event]) => event));
    const complete = async () => {
        await page.locator('.btn-register').click();
        await page.locator('#partnerModal.show').waitFor();
        await page.locator('#partnerFirstName').fill('Example');
        await page.locator('#partnerLastName').fill('Player');
        await page.locator('#partnerPhone').fill('+541112345678');
        await page.locator('#submitPartnerBtn').click();
        await page.locator('#resultModal.show').waitFor();
    };
    return { page, open, events, complete };
}

test('форма, попытка, ошибка полей и подтверждение по тому же турниру в новом сеансе', async t => {
    const { page, open, events, complete } = await fixture(t);
    await open('visit-a');
    await page.locator('.btn-register').click();
    await page.locator('#partnerModal.show').waitFor();
    await page.locator('#submitPartnerBtn').click();
    assert.deepEqual(await events(), ['registration_intent', 'registration_form_opened',
        'registration_submit_attempt', 'registration_error', 'registration_error_required_fields']);
    assert.deepEqual(await page.evaluate(() => window.calls.find(call => call[1] === 'registration_error_code')),
        ['set', 'registration_error_code', 'required_fields']);

    await open('visit-b');
    await complete();
    assert.deepEqual(await events(), ['registration_intent', 'registration_form_opened',
        'registration_submit_attempt', 'registration_confirmed', 'registration_confirmed_after_return']);
    const calls = await page.evaluate(() => window.calls);
    assert.deepEqual(calls.at(-2), ['set', 'registration_confirmed_after_return_tournament', '153']);
    assert.doesNotMatch(JSON.stringify(calls), /visit-[ab]|unused-user-id|Example|541112345678/);
    assert.deepEqual(await page.evaluate(() => window.ga4.map(call => call[1])),
        ['tournament_registration_start', 'tournament_registration_complete']);
    assert.equal(await page.evaluate(() => localStorage.getItem('clarity-registration-visits')), null);
});

test('повторная попытка в том же сеансе и другой турнир не отмечаются как возврат', async t => {
    const { page, open, events, complete } = await fixture(t);
    await open('visit-a');
    await page.locator('.btn-register').click();
    await page.locator('#partnerModal.show').waitFor();
    await open('visit-a');
    await complete();
    assert.equal((await events()).includes('registration_confirmed_after_return'), false);
    await open('visit-b');
    await page.locator('.btn-register').click();
    await page.locator('#partnerModal.show').waitFor();
    await open('visit-c', '154');
    await complete();
    assert.equal((await events()).includes('registration_confirmed_after_return'), false);
});

test('резерв и поиск партнёра имеют отдельные события, истёкшая история не считается возвратом', async t => {
    const { page, open, events, complete } = await fixture(t);
    await open('visit-a');
    await page.locator('.btn-register').click();
    await page.locator('#partnerModal.show').waitFor();
    await open('visit-b');
    await page.evaluate(() => {
        window.trackTournamentRegistrationComplete('153', 'WAITLIST');
        window.trackTournamentRegistrationComplete('153', 'SEARCH');
        localStorage.setItem('clarity-registration-visits', JSON.stringify({
            153: { session: 'visit-a', expires: Date.now() - 1 }
        }));
    });
    assert.deepEqual(await events(), ['registration_waitlisted', 'registration_solo_requested']);
    await complete();
    assert.equal((await events()).includes('registration_confirmed_after_return'), false);
});

test('отклонение сервером отмечается без текста ответа и события завершения', async t => {
    const { page, open, events } = await fixture(t);
    await open('visit-a', '155');
    await page.locator('.btn-register').click();
    await page.locator('#partnerModal.show').waitFor();
    await page.locator('#selectedPartnerId').fill('42');
    await page.locator('#submitPartnerBtn').click();
    await page.getByText('test response text', { exact: true }).waitFor();
    assert.deepEqual((await events()).slice(-3), ['registration_submit_attempt', 'registration_error',
        'registration_error_registration_rejected']);
    assert.doesNotMatch(JSON.stringify(await page.evaluate(() => window.calls)), /test response text/);
});

test('без согласия UI записи работает, а Clarity не получает событий или истории', async t => {
    const { page, open, events, complete } = await fixture(t, 'rejected');
    await open('visit-a');
    await complete();
    assert.deepEqual(await events(), []);
    assert.equal(await page.evaluate(() => localStorage.getItem('clarity-registration-visits')), null);
});

test('отзыв согласия удаляет локальную историю', async t => {
    const { page, open } = await fixture(t);
    await open('visit-a');
    await page.locator('.btn-register').click();
    await page.locator('#partnerModal.show').waitFor();
    assert.notEqual(await page.evaluate(() => localStorage.getItem('clarity-registration-visits')), null);
    await page.evaluate(() => window.rejectCookies());
    await page.waitForFunction(() => localStorage.getItem('clarity-registration-visits') === null);
});
