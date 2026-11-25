import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const fixture = JSON.parse(await readFile(process.argv[2], 'utf8'));
const base = new URL(fixture.baseUrl);
assert.equal(base.hostname, '127.0.0.1', 'Only the local synthetic fixture is allowed');
const { chromium } = await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE_PATH).href);
const browser = await chromium.launch({ executablePath: process.env.CHROME_EXECUTABLE });
const output = resolve('target/guest-merge-browser-qa');
await mkdir(output, { recursive: true });
const messages = {
    ru: { resolve: /Нужно завершить/, available: /Объединение доступно/, complete: /Объединение уже выполнено/, forbidden: /Объединение запрещено/, phone: /некорректный контакт/, event: /турнире/ },
    es: { resolve: /Completá la verificación/, available: /La unión está disponible/, complete: /Los perfiles ya fueron unidos/, forbidden: /No se pueden unir/, phone: /contacto inválido/, event: /torneo/ },
    en: { resolve: /Complete verification/, available: /The merge is available/, complete: /The merge is complete/, forbidden: /cannot be merged/, phone: /invalid contact/, event: /tournament/ }
};
const report = [];

async function session(language, mobile) {
    const context = await browser.newContext({ viewport: { width: mobile ? 390 : 1440, height: 900 }, isMobile: mobile });
    await context.route('**/*', route => new URL(route.request().url()).origin === base.origin ? route.continue() : route.abort());
    await context.addCookies([{ name: 'cookieConsent', value: 'rejected', url: base.href }]);
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(new URL('/login?lang=' + language, base).href);
    await page.locator('#username').fill(fixture.adminEmail);
    await page.locator('#password').fill(fixture.password);
    await Promise.all([page.waitForURL(url => url.pathname !== '/login'), page.locator('form.auth-form button[type=submit]').click()]);
    const response = await page.goto(new URL('/admin/players?lang=' + language, base).href);
    assert.equal(response.status(), 200);
    const manual = page.locator('[data-guest-claim=manual]');
    await manual.locator('summary').first().click();
    return { context, page, manual, errors };
}

async function selectProfiles(manual, guestId, accountId) {
    for (const [side, id] of [['guest', guestId], ['account', accountId]]) {
        const picker = manual.locator(`[data-claim-picker=${side}]`);
        await picker.locator('input').fill(String(id));
        await picker.locator('button[type=submit]').click();
        await picker.locator('[data-claim-options] button').filter({ hasText: '#' + id }).click();
    }
    await manual.locator('[data-claim-manual-review]').click();
    await manual.locator('[data-claim-detail]').waitFor();
}

try {
    for (const scenario of fixture.scenarios) {
        const { context, page, manual, errors } = await session(scenario.language, scenario.mobile);
        const texts = messages[scenario.language];
        await selectProfiles(manual, scenario.guestId, scenario.accountId);
        const review = manual.locator('[data-claim-detail]');
        const form = review.locator('[data-claim-merge]');
        const button = form.locator('button[type=submit]');
        assert.equal(await button.isVisible(), true);
        assert.equal(await button.isDisabled(), true);
        assert.match(await review.locator('[data-claim-readiness]').innerText(), texts.resolve);
        assert.match(await review.locator('[data-claim-conflicts]').innerText(), texts.phone);
        assert.match(await review.locator('[data-claim-direction]').innerText(), new RegExp(scenario.guestId + '.*' + scenario.accountId));
        assert.equal(await review.locator('[data-claim-state]').innerText(), '');
        assert.match(await review.locator('[data-claim-keep-account]').innerText(), /C7/);
        await form.locator('[name=contactChoice]').selectOption('KEEP_ACCOUNT');
        await form.locator('[name=sideChoice]').selectOption('ACCOUNT');
        await form.locator('[name=method]').selectOption('OTHER');
        await form.locator('[name=verified]').check();
        assert.equal(await button.isEnabled(), true);
        assert.match(await review.locator('[data-claim-readiness]').innerText(), texts.available);
        assert.match(await review.locator('[data-claim-contact-outcome]').innerText(), /@QaMerge/);
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1), true);
        await review.screenshot({ path: resolve(output, `ready-${scenario.language}-${scenario.mobile ? 'phone' : 'desktop'}.png`) });
        let mutations = 0;
        page.on('request', request => {
            if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/admin/guest-profile-claims/manual') mutations++;
        });
        if (scenario.language === 'en' && scenario.mobile) {
            await page.route('**/api/admin/guest-profile-claims/manual', async route => {
                if (route.request().method() === 'POST') {
                    await route.fetch();
                    await route.abort('failed');
                } else await route.continue();
            });
        }
        await form.evaluate(element => { element.requestSubmit(); element.requestSubmit(); });
        await review.locator('[data-claim-readiness]').filter({ hasText: texts.complete }).waitFor();
        assert.equal(mutations, 1, 'Repeated submit and lost response must not send another mutation');
        assert.deepEqual(errors, []);
        if (scenario.language === 'ru' && !scenario.mobile) {
            const mainContext = await browser.newContext();
            await mainContext.route('**/*', route => new URL(route.request().url()).origin === base.origin ? route.continue() : route.abort());
            const mainPage = await mainContext.newPage();
            await mainPage.goto(new URL('/login?lang=ru', base).href);
            await mainPage.locator('#username').fill(scenario.accountEmail);
            await mainPage.locator('#password').fill(fixture.password);
            await Promise.all([mainPage.waitForURL(url => url.pathname !== '/login'), mainPage.locator('form.auth-form button[type=submit]').click()]);
            const profileResponse = await mainPage.goto(new URL('/perfil?lang=ru', base).href);
            assert.equal(profileResponse.status(), 200, 'Main account must still sign in and open its profile');
            assert.equal(new URL(mainPage.url()).pathname, '/perfil');
            await mainContext.close();
        }
        report.push({ language: scenario.language, mobile: scenario.mobile, merged: true, mutations, lostResponse: scenario.language === 'en' && scenario.mobile });
        await context.close();
    }
    for (const [language, mobile] of [['ru', false], ['es', true], ['en', true]]) {
        const { context, page, manual, errors } = await session(language, mobile);
        await selectProfiles(manual, fixture.conflictGuestId, fixture.conflictAccountId);
        const review = manual.locator('[data-claim-detail]');
        const button = review.locator('[data-claim-merge] button[type=submit]');
        assert.equal(await button.isVisible(), true);
        assert.equal(await button.isDisabled(), true);
        assert.match(await review.locator('[data-claim-readiness]').innerText(), messages[language].forbidden);
        assert.match(await review.locator('[data-claim-conflicts]').innerText(), new RegExp(String(fixture.conflictEventId)));
        assert.ok((await review.locator('[data-claim-next-step]').innerText()).length > 0);
        assert.deepEqual(errors, []);
        await review.screenshot({ path: resolve(output, `forbidden-${language}.png`) });
        report.push({ language, mobile, overlapBlocked: true });
        await context.close();
    }
    await writeFile(resolve(output, 'report.json'), JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report));
} finally {
    await browser.close();
}
