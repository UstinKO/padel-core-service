import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE_PATH);
const fixture = JSON.parse(await readFile(process.argv[2], 'utf8'));
const origin = new URL(fixture.baseUrl);
assert.equal(origin.hostname, '127.0.0.1');
const browser = await chromium.launch();
const checks = [];
let page;
const endpoint = '/api/admin/telegram/messages/preview';
async function session(email, language = 'ru', width = 1440) {
    const context = await browser.newContext({ locale: language, viewport: { width, height: 950 } });
    await context.route('**/*', route => {
        const url = new URL(route.request().url());
        return url.origin === origin.origin ? route.continue() : route.abort();
    });
    page = await context.newPage(); page.setDefaultTimeout(15000);
    await page.goto(fixture.baseUrl + '/login?lang=' + language);
    const banner = page.locator('#cookieConsentBanner');
    if (await banner.isVisible()) await banner.locator('.cookie-btn-reject').click();
    await page.locator('#username').fill(email); await page.locator('#password').fill(fixture.password);
    await Promise.all([page.waitForURL(url => url.pathname === (email === fixture.playerEmail ? '/players/dashboard' : '/admin')),
        page.locator('form.auth-form button[type=submit]').click()]);
    return context;
}
async function openEvent() {
    const response = await page.goto(fixture.baseUrl + '/admin/tournaments/' + fixture.eventId + '/telegram/messages');
    assert.equal(response.status(), 200);
    await page.locator('[data-candidates] input').first().waitFor();
    await page.waitForFunction(() => !document.querySelector('[data-refresh]').disabled);
}
async function preview() {
    const response = page.waitForResponse(r => new URL(r.url()).pathname === endpoint);
    await page.locator('[data-preview]').click();
    const result = await response;
    assert.equal(result.status(), 200);
    await page.locator('[data-preview-result]').waitFor({ state: 'visible' });
    return result.json();
}
try {
    for (const [language, width] of [['ru',1440], ['es',390], ['en',320]]) {
        const context = await session(fixture.ownerEmail, language, width);
        await openEvent();
        assert.equal(await page.locator('[data-candidates] input').count(), 3);
        assert.equal(await page.locator('[data-candidates] input:checked').count(), 0);
        await page.locator('[data-categories] input[value=C8]').check();
        await page.locator('[data-groups] input[value=MAIN]').check();
        assert.equal(await page.locator('[data-candidates] input:checked').count(), 2);
        await page.locator('[data-candidates] input[data-player-id="' + fixture.bravoId + '"]').uncheck();
        const malicious = '<img src=x onerror="window.previewXss=true"> Привет';
        await page.locator('[data-message-text]').fill(malicious);
        const result = await preview();
        assert.deepEqual(result.recipients.map(p => p.playerId), [fixture.alphaId]);
        assert.equal(result.selectedCount, 1); assert.equal(result.sendingAvailable, false);
        assert.equal(await page.locator('[data-preview-text]').innerText(), malicious);
        assert.equal(await page.locator('[data-preview-text] img').count(), 0);
        assert.equal(await page.evaluate(() => window.previewXss), undefined);
        await page.locator('[data-message-text]').fill('Обновлённый текст');
        assert.equal(await page.locator('[data-preview-result]').isVisible(), false);
        await page.locator('[data-reset]').click();
        await page.locator('[data-groups] input[value=RESERVE]').check();
        const reserve = await preview();
        assert.deepEqual(reserve.unavailable.map(p => p.playerId).sort(), [fixture.bravoId, fixture.charlieId].sort());
        await page.locator('[data-reset]').click();
        await page.locator('[data-groups] input[value=UNPAIRED]').check();
        const solo = await preview(); assert.equal(solo.selectedCount, 2);
        await page.locator('[data-person-search]').fill('Alpha');
        assert.equal(await page.locator('[data-candidates] input').count(), 1);
        await page.locator('[data-candidates] input').check();
        const combined = await preview(); assert.equal(combined.selectedCount, 3);
        await page.locator('[data-person-search]').fill('');
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1), false);
        const text = await page.locator('h1').innerText();
        assert.equal(text, { ru:'Сообщения Telegram', es:'Mensajes de Telegram', en:'Telegram messages' }[language]);
        await page.evaluate(() => window.scrollTo({top:0,behavior:'instant'}));
        await page.screenshot({path:`target/telegram-message-preview-qa/${language}-${width}.png`, fullPage:true});
        checks.push(`${language} ${width}: категории, группы, исключения, поиск, безопасный текст и устаревание`);
        await context.close();
    }
    const adminContext = await session(fixture.adminEmail);
    await page.locator('a[href="/admin/telegram/messages"]').click();
    await page.locator('[data-candidates] input').first().waitFor();
    assert.equal(await page.locator('[data-groups] input[value=MAIN]').count(), 0);
    await page.locator('[data-person-search]').fill('QA Alpha');
    await page.locator('[data-candidates] input[data-player-id="' + fixture.alphaId + '"]').check();
    await page.locator('[data-message-text]').fill('Отдельному игроку');
    assert.equal((await preview()).selectedCount, 1);
    // Ответ, пришедший после изменения текста, не возвращает старый предпросмотр.
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    await page.route('**/api/admin/telegram/messages/preview', async route => {
        const response = await route.fetch(); await gate; await route.fulfill({response});
    });
    await page.locator('[data-preview]').click();
    await page.locator('[data-message-text]').fill('Новый текст во время запроса');
    release();
    await page.waitForFunction(() => !document.querySelector('[data-preview]').disabled);
    assert.equal(await page.locator('[data-preview-result]').isVisible(), false);
    await page.unroute('**/api/admin/telegram/messages/preview');
    checks.push('SUPER_ADMIN: общая аудитория; устаревший ответ не показывается');
    await adminContext.close();
    const otherContext = await session(fixture.otherEmail);
    const foreign = await page.goto(fixture.baseUrl + '/admin/tournaments/' + fixture.eventId + '/telegram/messages');
    assert.equal(foreign.status(), 403);
    const global = await page.goto(fixture.baseUrl + '/admin/telegram/messages'); assert.equal(global.status(),403);
    await otherContext.close();
    const playerContext = await session(fixture.playerEmail);
    const forbidden = await page.goto(fixture.baseUrl + '/admin/telegram/messages'); assert.equal(forbidden.status(),403);
    await playerContext.close();
    checks.push('Другой организатор и игрок: доступ запрещён');
    await writeFile('target/telegram-message-preview-qa/report.json',JSON.stringify({checks},null,2));
    console.log(checks.join('\n'));
} catch (error) {
    if (page && !page.isClosed()) await page.screenshot({path:'target/telegram-message-preview-qa/failure.png',fullPage:true}).catch(()=>{});
    throw error;
} finally { await browser.close(); }
