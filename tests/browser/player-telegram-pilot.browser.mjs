import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE_PATH);
const fixture = JSON.parse(await readFile(process.argv[2], 'utf8'));
const origin = new URL(fixture.baseUrl);
assert.equal(origin.hostname, '127.0.0.1');
const browser = await chromium.launch();
const checks = [];
let page;
async function session(email, destination) {
    const context = await browser.newContext({ locale: 'es-AR', viewport: { width: 390, height: 844 } });
    await context.route('**/*', route => {
        const url = new URL(route.request().url());
        const asset = ['stylesheet', 'script', 'font', 'image'].includes(route.request().resourceType());
        const cdn = ['cdnjs.cloudflare.com', 'cdn.jsdelivr.net', 'fonts.googleapis.com', 'fonts.gstatic.com'];
        return url.origin === origin.origin || asset && cdn.includes(url.hostname) ? route.continue() : route.abort();
    });
    page = await context.newPage();
    page.setDefaultTimeout(15000);
    await page.goto(fixture.baseUrl + '/login');
    await page.locator('#cookieConsentBanner .cookie-btn-reject').waitFor({ state: 'visible' });
    await page.locator('#cookieConsentBanner .cookie-btn-reject').click();
    await page.locator('#username').fill(email);
    await page.locator('#password').fill(fixture.password);
    await Promise.all([page.waitForURL(url => url.pathname === destination),
        page.locator('form.auth-form button[type=submit]').click()]);
    return page;
}
async function request(path, method = 'GET', body) {
    return page.evaluate(async ({ path, method, body }) => {
        const csrf = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='))?.split('=')[1];
        const response = await fetch(path, { method, credentials: 'same-origin', headers: {
            Accept: 'application/json', 'Content-Type': 'application/json', 'X-XSRF-TOKEN': decodeURIComponent(csrf || '')
        }, body: body ? JSON.stringify(body) : undefined });
        return { status: response.status, data: response.status === 200 ? await response.json() : null };
    }, { path, method, body });
}
try {
    const selected = fixture.scenarios[0], outsider = fixture.scenarios[1];
    await session(outsider.email, '/players/dashboard');
    await page.goto(fixture.baseUrl + '/perfil');
    await page.waitForFunction(() => document.querySelector('[data-tg-profile]')?.dataset.busy === 'false');
    assert.equal(await page.locator('[data-tg-profile]').isVisible(), false);
    assert.equal((await request('/api/players/me/telegram')).data.eligible, false);
    assert.equal((await request('/api/players/me/telegram/link', 'POST')).status, 409);
    assert.equal((await request('/api/players/me/telegram/preference', 'PUT', { preference: 'WHATSAPP' })).status, 409);
    await page.screenshot({ path: 'target/telegram-browser-qa/pilot-outsider-profile.png', fullPage: true });
    await page.goto(fixture.baseUrl + '/torneo/' + outsider.eventId);
    const response = page.waitForResponse(r => new URL(r.url()).pathname === '/players/tournaments/' + outsider.eventId + '/register');
    await page.locator('#registerButton').click();
    assert.equal((await response).status(), 200);
    await page.waitForFunction(() => document.querySelector('#resultModal')?.classList.contains('show'));
    // Ждём серверную проверку предложения, а не произвольную задержку.
    await page.waitForFunction(() => window.PlayerTelegramRegistration !== undefined);
    const mounted = await page.evaluate(() => window.PlayerTelegramRegistration.mount(document.querySelector('#resultModal')));
    assert.equal(mounted, false);
    assert.equal(await page.locator('[data-tg-prompt]').count(), 0);
    checks.push('Другой аккаунт: профиль скрыт, API 409, обычная запись доступна без предложения');
    await session(selected.email, '/players/dashboard');
    await page.goto(fixture.baseUrl + '/perfil');
    await page.locator('[data-tg-profile]').waitFor({ state: 'visible' });
    const link = page.waitForResponse(r => new URL(r.url()).pathname === '/api/players/me/telegram/link');
    await page.locator('[data-tg-connect]').click();
    assert.equal((await link).status(), 200);
    await page.locator('[data-tg-open]').waitFor({ state: 'visible' });
    await page.screenshot({ path: 'target/telegram-browser-qa/pilot-selected-profile.png', fullPage: true });
    checks.push('Тестовый аккаунт: профиль виден, персональная ссылка выдаётся без открытия Telegram');
    await session(fixture.adminEmail, '/admin');
    await page.goto(fixture.baseUrl + '/admin/tournaments/' + selected.eventId + '/payments');
    await page.locator('[data-tg-admin]').waitFor({ state: 'visible' });
    await page.goto(fixture.baseUrl + '/admin/tournaments/' + outsider.eventId + '/payments');
    assert.equal(await page.locator('[data-tg-admin]').count(), 0);
    assert.equal((await request('/api/admin/tournaments/' + outsider.eventId + '/telegram/confirmations', 'POST')).status, 409);
    checks.push('Организатор: блок только в тестовом мероприятии, другое событие API 409');
    await writeFile('target/telegram-browser-qa/pilot-report.json', JSON.stringify({ checks }, null, 2));
    console.log(checks.join('\n'));
} catch (error) {
    if (page) await page.screenshot({ path: 'target/telegram-browser-qa/pilot-failure.png', fullPage: true }).catch(() => {});
    throw error;
} finally {
    await browser.close();
}
