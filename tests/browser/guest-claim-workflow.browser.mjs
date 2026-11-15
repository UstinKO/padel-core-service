import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const base = new URL(process.env.GUEST_CLAIM_QA_URL);
assert.equal(base.hostname, '127.0.0.1', 'Run only against the local synthetic fixture');
const { chromium, devices } = await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE_PATH).href);
const password = process.env.GUEST_CLAIM_QA_PASSWORD;
assert.ok(password, 'Set the synthetic fixture password');
const browser = await chromium.launch({ executablePath: process.env.CHROME_EXECUTABLE });
const evidence = resolve(process.env.GUEST_CLAIM_QA_EVIDENCE || 'target/guest-claim-browser');
await mkdir(evidence, { recursive: true });
const mainPhone = '011 15 2233-7755';
const conflictPhone = '011 15 2233-7766';

async function login(email, lang, mobile) {
    const context = await browser.newContext(mobile ? devices['iPhone 13'] : { viewport: { width: 1280, height: 900 } });
    await context.route('**/*', route => new URL(route.request().url()).origin === base.origin
        ? route.continue() : route.abort());
    await context.addCookies([{ name: 'cookieConsent', value: 'rejected', url: base.href }]);
    const page = await context.newPage();
    const pageErrors = [];
    page.on('pageerror', error => pageErrors.push(error.message));
    await page.goto(new URL('/login?lang=' + lang, base).href);
    await page.locator('#username').fill(email);
    await page.locator('#password').fill(password);
    await Promise.all([
        page.waitForURL(url => url.pathname !== '/login'),
        page.locator('form.auth-form button[type="submit"]').click()
    ]);
    return { page, context, pageErrors };
}

async function visit(page, path, lang) {
    const target = new URL(path, base);
    target.searchParams.set('lang', lang);
    const response = await page.goto(target.href);
    assert.equal(response.status(), 200, target.pathname);
    assert.equal(new URL(page.url()).pathname, target.pathname);
}

async function submit(form) {
    await form.locator('button[type="submit"]').click();
}

async function expectText(locator, regex) {
    await locator.filter({ hasText: regex }).waitFor();
}

const translations = {
    es: { pending: /Pendiente de verificación/, message: /Soy QA Main/, queue: /Solicitudes de unión/ },
    ru: { pending: /Ожидает проверки/, message: /Я QA Main/, queue: /Заявки на объединение/ },
    en: { pending: /Awaiting review/, message: /I am QA Main/, queue: /Profile merge requests/ }
};

let requestNumber;
let conflictNumber;
try {
    for (const [lang, labels] of Object.entries(translations)) {
        for (const mobile of [false, true]) {
            const session = await login('qa-main@example.test', lang, mobile);
            const { page } = session;
            await visit(page, '/perfil', lang);
            const section = page.locator('[data-guest-claim="request"]');
            if (lang === 'es' && !mobile) {
                await page.locator('form.profile-form input[name=telefono]').fill(mainPhone);
                await submit(page.locator('form.profile-form'));
                await page.locator('[data-open-guest-claim]').waitFor();
                assert.equal(await page.locator('#guest-claim-player-phone').inputValue(), mainPhone);
            }
            if (await section.getAttribute('open') === null) await section.locator('summary').first().click();
            await page.locator('#guest-claim-player-phone').fill(mainPhone);
            await submit(section.locator('form'));
            const card = section.locator('[data-reference]');
            await expectText(card.locator('[data-claim-state]'), labels.pending);
            assert.equal(await card.count(), 1);
            const link = new URL(await card.locator('[data-claim-whatsapp]').getAttribute('href'));
            assert.equal(link.origin + link.pathname, 'https://wa.me/12025550100');
            const message = link.searchParams.get('text');
            assert.match(message, labels.message);
            const number = Number(message.match(/(?:n\.º |#|№)(\d+)/u)?.[1]);
            assert.ok(number);
            requestNumber ??= number;
            assert.equal(requestNumber, number);
            if (lang === 'es' && !mobile) {
                const [popup] = await Promise.all([
                    session.context.waitForEvent('page'),
                    card.locator('[data-claim-whatsapp]').click()
                ]);
                await popup.close();
                await expectText(card.locator('[data-claim-state]'), labels.pending);
            }
            await page.reload();
            const reopened = page.locator('[data-guest-claim="request"]');
            if (await reopened.getAttribute('open') === null) await reopened.locator('summary').first().click();
            await expectText(reopened.locator('[data-claim-state]'), labels.pending);
            assert.equal(await reopened.locator('[data-claim-whatsapp]').getAttribute('href'), link.href);
            assert.deepEqual(session.pageErrors, []);
            if (mobile && lang === 'ru') await section.screenshot({ path: resolve(evidence, 'profile-mobile-ru.png') });
            await session.context.close();

            const admin = await login('qa-owner@example.test', lang, mobile);
            await visit(admin.page, '/admin/players', lang);
            const queue = admin.page.locator('[data-guest-claim="review"]');
            await expectText(queue.locator('summary'), labels.queue);
            await queue.locator('summary').click();
            await expectText(queue.locator('[data-claim-queue-list]'), /QA Main/);
            assert.deepEqual(admin.pageErrors, []);
            await admin.context.close();
        }
    }

    const conflict = await login('qa-conflict@example.test', 'en', false);
    await visit(conflict.page, '/perfil', 'en');
    const conflictArea = conflict.page.locator('[data-guest-claim="request"]');
    await conflictArea.locator('summary').first().click();
    await conflictArea.locator('input[name=phone]').fill(conflictPhone);
    await submit(conflictArea.locator('form'));
    const conflictTitle = conflictArea.locator('[data-claim-number]');
    await expectText(conflictTitle, /Request #/);
    conflictNumber = Number((await conflictTitle.innerText()).match(/\d+/u)[0]);
    await conflict.context.close();

    const admin = await login('qa-owner@example.test', 'en', false);
    await visit(admin.page, '/admin/players', 'en');
    const queue = admin.page.locator('[data-guest-claim="review"]');
    await queue.locator('summary').click();
    const find = queue.locator('[data-claim-queue-search]');
    const comparison = queue.locator('[data-claim-detail]');
    async function review(number) {
        await find.locator('input').fill(String(number));
        await submit(find);
        const result = queue.locator('.claim-card').filter({ has: admin.page.getByRole('heading', { name: 'Request #' + number, exact: true }) });
        await result.getByRole('button', { name: 'Review', exact: true }).click();
        await expectText(comparison.locator('[data-claim-title]'), new RegExp(String(number)));
    }
    await review(conflictNumber);
    await expectText(comparison.locator('[data-claim-conflicts]'), /Both profiles have records in tournament #/);
    assert.equal(await comparison.locator('[data-claim-merge]').isVisible(), false);
    await comparison.getByRole('button', { name: 'More information needed' }).click();
    await expectText(comparison.locator('[data-claim-state]'), /More information needed/);

    await review(requestNumber);
    await expectText(comparison.locator('[data-claim-direction]'), /Attach guest profile #\d+ to account #\d+/);
    assert.equal(await comparison.locator('[data-claim-profile="guest"] [data-field="registrations"]').innerText(), '1');
    await comparison.screenshot({ path: resolve(evidence, 'admin-review-desktop-en.png') });
    const merge = comparison.locator('[data-claim-merge]');
    await merge.locator('input[name=sender]').fill('+54 9 11 2233-7755');
    await merge.locator('input[name=verified]').check();
    await submit(merge);
    await expectText(comparison.locator('[data-claim-state]'), /^Merged$/);

    const manual = admin.page.locator('[data-guest-claim="manual"]');
    await manual.locator('summary').click();
    for (const [side, name] of [['guest', 'ManualGuest'], ['account', 'ManualMain']]) {
        const picker = manual.locator(`[data-claim-picker="${side}"]`);
        await picker.locator('input').fill(name);
        await submit(picker.locator('form'));
        await picker.getByRole('button', { name: new RegExp(name) }).click();
    }
    await manual.locator('[data-claim-manual-review]').click();
    const manualForm = manual.locator('[data-claim-merge]');
    await manualForm.locator('select[name=method]').selectOption('IN_PERSON');
    await manualForm.locator('input[name=verified]').check();
    await submit(manualForm);
    await expectText(manual.locator('[data-claim-state]'), /^Merged$/);
    await review(conflictNumber);
    await comparison.getByRole('button', { name: 'Reject request' }).click();
    await expectText(comparison.locator('[data-claim-state]'), /^Rejected$/);
    assert.deepEqual(admin.pageErrors, []);
    await admin.context.close();

    const player = await login('qa-main@example.test', 'en', false);
    await visit(player.page, '/perfil', 'en');
    const section = player.page.locator('[data-guest-claim="request"]');
    await section.locator('summary').first().click();
    await expectText(section.locator('[data-claim-state]'), /^Merged$/);
    await player.context.close();
    console.log('Guest claim workflows passed in desktop and mobile Chromium on ES/RU/EN.');
} finally {
    await browser.close();
}
