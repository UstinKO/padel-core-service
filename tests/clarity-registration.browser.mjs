import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import test, { before, after } from 'node:test';

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || 'playwright-core');
const scripts = new URL('../src/main/resources/static/js/', import.meta.url);
let browser;
let server;
let origin;
const tournamentTemplate = await readFile(new URL('../src/main/resources/templates/tournament-details.html', import.meta.url), 'utf8');
const levelDialog = tournamentTemplate.match(/<dialog[^>]+id="registrationLevelDialog"[\s\S]*?<\/dialog>/)[0]
    .replace('</select>', '<option value="C7">C7</option></select>');

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
        } else if (url.pathname === '/api/players/me/level') {
            response.writeHead(204).end();
        } else if (url.pathname === '/api/players/search') {
            response.end('[]');
        } else if (url.pathname === '/api/tournaments/double/155/register') {
            response.writeHead(422).end(JSON.stringify({ code: 'TEST_REJECTION', message: 'test response text' }));
        } else if (url.pathname.startsWith('/api/tournaments/double/')) {
            response.end(JSON.stringify({ success: true, status: 'CONFIRMED' }));
        } else {
            const id = url.pathname.split('/').at(-1);
            response.setHeader('Content-Type', 'text/html; charset=utf-8');
            response.end(`<!doctype html><meta name="viewport" content="width=device-width, initial-scale=1"><script>
                const t = key => key;
                window.tournament = { modalidad: '${id === '159' ? 'INDIVIDUAL' : 'DOBLES'}' };
                window.PairTerms = { reviewBooking: async () => ({}) };
            </script>
            <button class="btn-register" data-tournament-id="${id}" data-registration-intent="${id}" data-registration-level-missing="${id === '158' || id === '159'}">Записаться</button>
            <p id="registrationLevelStatus" role="status"></p>
            ${levelDialog}
            <div id="partnerFields"></div>
            <div id="partnerModal" style="display:none"><style>#partnerModal.show { display: block !important; }</style>
                <form id="partnerForm">
                    <input id="modalTournamentId">
                    <input id="registrationMode" value="WITH_PARTNER">
                    <input id="partnerFirstName"><input id="partnerLastName">
                    <input id="partnerPhone"><input id="partnerTelegram"><input id="selectedPartnerId"><span id="selectedPartnerName">Test partner</span>
                </form>
                <div id="partnerInfoMessage"><i></i><span id="infoMessageText"></span></div>
                <button type="button" id="submitPartnerBtn">Отправить</button>
            </div>
            <div id="resultModal"><div id="resultModalBody"></div></div>
            <script src="/js/level-check.js"></script><script src="/js/pair-registration-response.js"></script>
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
        ['tournament_registration_form_opened', 'tournament_registration_start', 'tournament_registration_complete']);
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
    assert.equal(await page.evaluate(() => window.ga4.some(([, event]) => event === 'tournament_registration_form_opened')), false);
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

test('на телефоне уровень запрашивается до формы пары и сохраняется без отправки заявки', async t => {
    const { page, open } = await fixture(t);
    const submissions = [];
    page.on('request', request => {
        if (request.method() === 'POST') submissions.push(new URL(request.url()).pathname);
    });
    await open('visit-a', '158');
    await page.locator('.btn-register').tap();
    await page.locator('#registrationLevelDialog[open]').waitFor();
    assert.equal(await page.locator('#partnerModal').isVisible(), false);
    assert.deepEqual(await page.evaluate(() => window.ga4.map(([, event]) => event)), ['tournament_registration_click']);
    await page.locator('#registrationLevel').selectOption('C7');
    const levelRequest = page.waitForRequest(request => request.method() === 'PATCH');
    await page.locator('#registrationLevelSave').tap();
    assert.deepEqual((await levelRequest).postDataJSON(), { nivelJugador: 'C7' });
    await page.locator('#partnerModal.show').waitFor();
    assert.deepEqual(submissions, []);
    const ga4 = await page.evaluate(() => window.ga4);
    assert.deepEqual(ga4.map(([, event]) => event), ['tournament_registration_click', 'tournament_registration_form_opened']);
    assert.deepEqual(ga4.at(-1)[2], { tournament_id: '158' });
    assert.equal(new URL(page.url()).pathname, '/torneo/158');
});

test('поздний запрос уровня сохраняет партнёра и требует повторной отправки игроком', async t => {
    const { page, open, events } = await fixture(t);
    let submissions = 0;
    await page.route('**/api/tournaments/double/157/register', async route => {
        submissions++;
        if (submissions === 1) {
            await route.fulfill({ status: 400, json: { needLevel: true } });
        } else {
            await route.continue();
        }
    });
    await open('visit-a', '157');
    await page.locator('.btn-register').tap();
    await page.locator('#partnerModal.show').waitFor();
    await page.locator('#selectedPartnerId').fill('42');
    await page.locator('#submitPartnerBtn').tap();
    await page.locator('#registrationLevelDialog[open]').waitFor();
    assert.ok((await events()).includes('registration_error_level_required'));
    await page.locator('#registrationLevel').selectOption('C7');
    await page.locator('#registrationLevelSave').tap();
    await page.locator('#registrationLevelDialog').waitFor({ state: 'hidden' });
    assert.equal(await page.locator('#selectedPartnerId').inputValue(), '42');
    assert.equal(submissions, 1);
    await page.locator('#submitPartnerBtn').tap();
    await page.locator('#resultModal.show').waitFor();
    assert.equal(submissions, 2);
});

test('сохранение уровня не отправляет одиночную заявку', async t => {
    const { page, open } = await fixture(t);
    let submissions = 0;
    await page.route('**/players/tournaments/159/register', async route => {
        submissions++;
        await route.fulfill({ json: { success: true, status: 'WAITLIST' } });
    });
    await open('visit-a', '159');
    await page.locator('.btn-register').tap();
    await page.locator('#registrationLevel').selectOption('C7');
    await page.locator('#registrationLevelSave').tap();
    await page.getByRole('status').getByText('level.registration.saved').waitFor();
    assert.equal(submissions, 0);
    await page.locator('.btn-register').tap();
    await page.locator('#resultModal.show').waitFor();
    assert.equal(submissions, 1);
});
