import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const { chromium, devices } = await import(process.env.PLAYWRIGHT_MODULE_PATH ?? 'playwright');
const { fixture, bookerPath, guestPath } = JSON.parse(await readFile(process.argv[2], 'utf8'));
const { baseUrl, email, strangerEmail, password } = fixture;
const browser = await chromium.launch({ executablePath: process.env.PARTICIPATION_CHROME || undefined });
const desktop = await browser.newContext({ locale: 'es-AR' });
const mobile = await browser.newContext({ ...devices['iPhone 13'], locale: 'es-AR' });
for (const context of [desktop, mobile]) {
    await context.addCookies([{ name: 'cookieConsent', value: 'rejected', url: baseUrl }]);
    await context.route('**/*', route => route.request().url().startsWith(baseUrl + '/') ? route.continue() : route.abort());
}

async function login(page, playerEmail, destination) {
    await page.locator('#username').fill(playerEmail);
    await page.locator('#password').fill(password);
    await Promise.all([
        page.waitForURL(url => url.pathname === destination),
        page.locator('form[action="/login"] button[type="submit"]').click()
    ]);
}

try {
    const stranger = await desktop.newPage();
    await stranger.goto(baseUrl + guestPath);
    await login(stranger, strangerEmail, guestPath);
    assert.equal(await stranger.locator('form[action^="/participacion/"]').count(), 0);
    assert.equal(await stranger.getByText('Invitado QA', { exact: true }).count(), 0);

    const page = await mobile.newPage();
    page.setDefaultTimeout(10000);
    await page.goto(baseUrl + guestPath);
    await login(page, email, guestPath);
    assert.equal(await page.locator('html').getAttribute('lang'), 'es');
    await page.getByText('Pendiente de respuesta', { exact: true }).waitFor();
    await page.reload();
    await page.getByText('Pendiente de respuesta', { exact: true }).waitFor();
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true);
    const form = page.locator('form').filter({ has: page.getByRole('button', { name: 'Confirmar participación' }) });
    assert.equal(await form.locator('input[name="_csrf"]').count(), 1);
    const [submitted] = await Promise.all([
        page.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith(guestPath)),
        page.getByRole('button', { name: 'Confirmar participación' }).click()
    ]);
    assert.equal(submitted.status(), 302, submitted.status() === 302 ? '' : await submitted.text());
    assert.equal(new URL(submitted.headers()['location'], baseUrl).pathname, guestPath);
    await page.waitForLoadState();
    assert.equal(await page.getByRole('alert').count(), 0, await page.getByRole('alert').allTextContents());
    await page.getByText('Participación confirmada', { exact: true }).waitFor();
    await page.getByRole('link', { name: 'Mis eventos' }).click();

    const cabinet = page.locator('#participationRequests');
    const own = cabinet.locator('article').filter({ hasText: 'Titular QA' });
    const guest = cabinet.locator('article').filter({ hasText: 'Invitado QA' });
    await guest.getByText('Participación confirmada', { exact: true }).waitFor();
    await own.getByRole('button', { name: 'Confirmar participación' }).click();
    await own.getByText('Participación confirmada', { exact: true }).waitFor();
    await own.getByRole('button', { name: 'Rechazar participación' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: 'Volver' }).click();
    await own.getByText('Participación confirmada', { exact: true }).waitFor();
    await own.getByRole('button', { name: 'Rechazar participación' }).click();
    await dialog.getByRole('button', { name: 'Rechazar participación' }).click();
    await own.getByText('Participación rechazada', { exact: true }).waitFor();
    await guest.getByText('Participación confirmada', { exact: true }).waitFor();

    await page.goto(baseUrl + bookerPath + '?lang=ru');
    await page.getByText('Участие отклонено', { exact: true }).waitFor();
    await page.goto(baseUrl + guestPath + '?lang=en');
    await page.getByText('Participation confirmed', { exact: true }).waitFor();
    await page.locator('summary').filter({ hasText: 'Decline participation' }).click();
    await page.getByRole('button', { name: 'Decline participation' }).click();
    await page.getByText('Participation declined', { exact: true }).waitFor();
    await page.reload();
    await page.getByText('Participation declined', { exact: true }).waitFor();
    console.log('Chromium: real email link, login return, guest delegation, cabinet, refusal, ES/RU/EN');
} finally {
    await browser.close();
}
