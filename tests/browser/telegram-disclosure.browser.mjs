import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE_PATH);
const fixture = JSON.parse(await readFile(process.argv[2], 'utf8'));
const origin = new URL(fixture.baseUrl);
assert.equal(origin.hostname, '127.0.0.1');
const output = 'target/telegram-browser-qa';
await mkdir(output, { recursive: true });
const labels = { ru: ['Развернуть', 'Свернуть'], es: ['Expandir', 'Contraer'], en: ['Expand', 'Collapse'] };
const checks = [], errors = [], writes = [];
const browser = await chromium.launch({ executablePath: process.env.BROWSER_CHROMIUM_PATH });
const context = await browser.newContext({ viewport: { width: 1440, height: 1100 } });
await context.route('**/*', route => {
    const url = new URL(route.request().url());
    const asset = ['stylesheet', 'script', 'font', 'image'].includes(route.request().resourceType());
    const cdn = ['cdnjs.cloudflare.com', 'cdn.jsdelivr.net', 'fonts.googleapis.com', 'fonts.gstatic.com'];
    return url.origin === origin.origin || asset && cdn.includes(url.hostname) ? route.continue() : route.abort();
});
const page = await context.newPage();
page.setDefaultTimeout(15000);
page.on('pageerror', error => errors.push(String(error)));
page.on('request', request => { if (request.method() !== 'GET') writes.push(request.url()); });
page.on('dialog', dialog => dialog.type() === 'beforeunload' ? dialog.accept() : dialog.dismiss());
const panel = page.locator('[data-tg-admin]');
const disclosure = panel.locator('details');
const heading = panel.locator('summary');
const isOpen = () => disclosure.evaluate(el => el.open);
const ready = () => page.waitForFunction(() => document.querySelector('[data-tg-admin]')?.dataset.busy === 'false');
const record = name => { checks.push(name); console.log('PASS ' + name); };
const visit = async (id, language) => {
    const summaryRead = page.waitForResponse(r => new URL(r.url()).pathname === '/api/admin/tournaments/' + id + '/telegram');
    const response = await page.goto(fixture.baseUrl + '/admin/tournaments/' + id + '/payments?lang=' + language);
    assert.equal(response.status(), 200);
    assert.equal((await summaryRead).status(), 200, 'Сводка Telegram должна успешно загрузиться');
    await ready();
};
const formData = () => page.locator('#paymentForm').evaluate(form => [...new FormData(form)]);
async function fits(width) {
    await page.setViewportSize({ width, height: 1100 });
    const bounds = await panel.boundingBox();
    assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= width + 1, 'Панель выходит за экран ' + width);
    assert.ok(await panel.evaluate(el => el.scrollWidth <= el.clientWidth + 1), 'Горизонтальная прокрутка панели');
    assert.ok((await heading.boundingBox()).height >= 44);
}
try {
    for (const account of fixture.accounts) {
        // Все браузерные сценарии делят один IP; новый блок начинается после полного окна лимитов API и входа.
        await new Promise(resolve => setTimeout(resolve, 61_000));
        await context.clearCookies();
        await page.goto(fixture.baseUrl + '/login');
        const rejectCookies = page.locator('#cookieConsentBanner .cookie-btn-reject');
        if (await rejectCookies.isVisible()) await rejectCookies.click();
        await page.locator('#username').fill(account.email);
        await page.locator('#password').fill(fixture.password);
        await Promise.all([page.waitForURL(url => url.pathname === '/admin'), page.locator('form.auth-form button[type=submit]').click()]);
        if (account.role === 'ADMIN') {
            await visit(fixture.accounts[0].eventIds[0], 'ru');
            assert.equal(await isOpen(), false);
            record('Состояние другого аккаунта в том же браузере не применяется');
        }
        const [eventId, secondId] = account.eventIds;
        for (const language of ['ru', 'es', 'en']) {
            await visit(eventId, language);
            assert.equal(await isOpen(), false);
            assert.equal(await disclosure.getAttribute('data-preference-user-id'), String(account.ownerId));
            assert.equal(await panel.locator('[data-tg-total]').textContent(), '20');
            assert.equal(await panel.locator('[data-tg-participants]').isVisible(), false);
            assert.equal(await panel.locator('[data-tg-request]').isVisible(), false);
            assert.equal(await panel.locator('.telegram-disclosure-expand').innerText(), labels[language][0]);
            for (const width of [1440, 390, 320]) {
                await fits(width);
                assert.ok((await panel.boundingBox()).height < 240, 'Свёрнутый блок слишком высокий');
                if (account.role === 'SUPER_ADMIN' && language === 'ru') {
                    await panel.screenshot({ path: `${output}/disclosure-closed-${width}.png` });
                }
            }
            const row = page.locator('#paymentForm tbody tr').first();
            await row.locator('input[name$=".amount"]').fill('123.45');
            await row.locator('input[name$=".attended"]').check();
            await row.locator('input[name$=".participationConfirmed"]').check();
            await row.locator('select[name$=".currency"]').selectOption('USD');
            await row.locator('select[name$=".paymentStatus"]').selectOption('PAID');
            await row.locator('input[name$=".notes"]').fill('QA draft, do not save');
            const draft = await formData();
            const writeCount = writes.length;
            await heading.click();
            assert.equal(await isOpen(), true);
            assert.equal(await panel.locator('.telegram-disclosure-collapse').innerText(), labels[language][1]);
            assert.equal(await panel.locator('[data-tg-summary]').isVisible(), account.role === 'SUPER_ADMIN');
            for (const width of [1440, 390, 320]) {
                await fits(width);
                if (account.role === 'SUPER_ADMIN' && language === 'ru') {
                    await panel.screenshot({ path: `${output}/disclosure-open-${width}.png` });
                }
            }
            await heading.press('Enter');
            assert.equal(await isOpen(), false);
            await heading.press('Space');
            assert.equal(await isOpen(), true);
            assert.deepEqual(await formData(), draft);
            assert.equal(writes.length, writeCount);
            const refreshed = page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/telegram') && r.request().method() === 'GET');
            await panel.locator('[data-tg-refresh]').click();
            const summary = await (await refreshed).json();
            await ready();
            assert.equal(await isOpen(), true);
            let finish, started;
            const pending = new Promise(resolve => { started = resolve; });
            const routeHandler = async route => {
                started();
                await new Promise(resolve => { finish = resolve; });
                await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(summary) });
            };
            await page.route('**/telegram/confirmations', routeHandler);
            await panel.locator('[data-tg-request]').click();
            await pending;
            assert.equal(await panel.locator('[data-tg-request]').isDisabled(), true);
            await heading.click();
            assert.equal(await isOpen(), false);
            finish();
            await ready();
            assert.equal(await isOpen(), false);
            assert.equal(await panel.locator('[data-tg-status]').isVisible(), true);
            assert.ok((await panel.locator('[data-tg-status]').textContent()).trim());
            assert.deepEqual(await formData(), draft);
            await page.unroute('**/telegram/confirmations', routeHandler);
            await heading.click();
            const failed = async route => route.fulfill({ status: 500, contentType: 'application/json', body: '{}' });
            await page.route('**/telegram/confirmations', failed);
            await panel.locator('[data-tg-request]').click();
            await ready();
            await heading.click();
            assert.equal(await panel.locator('[data-tg-status]').getAttribute('data-error'), 'true');
            assert.equal(await panel.locator('[data-tg-status]').isVisible(), true);
            assert.deepEqual(await formData(), draft);
            await page.unroute('**/telegram/confirmations', failed);
            await page.reload(); await ready();
            assert.equal(await isOpen(), false);
            await heading.click();
            await page.reload(); await ready();
            assert.equal(await isOpen(), true);
            await visit(secondId, language);
            assert.equal(await isOpen(), false);
            await visit(eventId, language);
            assert.equal(await isOpen(), true);
            await heading.click();
            record(`${account.role} / ${language}: сводка, клавиатура, 320/390/1440, черновик, запросы и сохранение состояния`);
        }
        await heading.click();
        if (['ORGANIZER', 'CLUB_ADMIN'].includes(account.role)) {
            const forbidden = await context.request.get(fixture.baseUrl + '/api/admin/tournaments/' + fixture.accounts[0].eventIds[0] + '/telegram');
            assert.equal(forbidden.status(), 403);
            record(`${account.role}: чужое мероприятие недоступно`);
        }
    }
    assert.equal(writes.some(url => url.includes('/payments/save')), false);
    assert.deepEqual(errors, []);
    await writeFile(output + '/disclosure-report.json', JSON.stringify({ ok: true, checks }, null, 2));
} catch (error) {
    console.error(error);
    await page.screenshot({ path: output + '/disclosure-failure.png', fullPage: true }).catch(() => {});
    await writeFile(output + '/disclosure-report.json', JSON.stringify({ ok: false, checks, error: String(error), errors }, null, 2));
    process.exitCode = 1;
} finally {
    await browser.close();
}
